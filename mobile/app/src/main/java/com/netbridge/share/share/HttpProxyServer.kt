package com.netbridge.share.share

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HTTP proxy shared over the LAN.
 *
 * Outbound sockets are opened *from the phone* with a plain [Socket], so they inherit
 * whatever route Android has installed as the default — which is the phone's active VPN
 * tunnel. That is the whole trick: no root, no server, and the PC's traffic ends up
 * inside the tunnel.
 *
 * [start] binds the ServerSocket on the calling thread and only returns true when the
 * port is actually listening. An async bind used to report success before the port was
 * open, so the desktop thought sharing was up while every request got 502.
 */
class HttpProxyServer(
    private val port: Int,
    private val onEvent: (Event) -> Unit
) {
    sealed class Event {
        data class Opened(val id: Long, val peer: String) : Event()
        data class Closed(val id: Long, val peer: String) : Event()
        data class Traffic(val peer: String, val up: Long, val down: Long) : Event()
        data class Error(val message: String) : Event()

        /** The accept loop died on its own — nothing is listening any more. */
        data class Stopped(val reason: String) : Event()
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var dispatcher: ExecutorCoroutineDispatcher? = null
    private var idSeq = 0L

    /**
     * Every socket belonging to a live tunnel. A tunnel blocks its thread for its whole
     * life, so [stop] has to close these explicitly — closing only the listening socket
     * left established connections forwarding traffic after the user pressed Stop.
     */
    private val liveLock = Any()
    private val live = mutableSetOf<Socket>()

    private fun track(socket: Socket) {
        synchronized(liveLock) { live.add(socket) }
    }

    private fun untrack(socket: Socket) {
        synchronized(liveLock) { live.remove(socket) }
    }

    /** Closes every socket of a live tunnel. Safe to call more than once. */
    private fun closeLiveSockets() {
        val open = synchronized(liveLock) {
            val snapshot = live.toList()
            live.clear()
            snapshot
        }
        for (s in open) {
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Returns true only when the proxy port is accepting connections. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return serverSocket?.isBound == true
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress("0.0.0.0", port), 128)
            serverSocket = ss
            // See ServerThreads: a tunnel holds a thread for its whole life, so the
            // proxy must never share a pool with the control API's accept loop.
            val disp = ServerThreads.pool("http")
            dispatcher = disp
            val sc = CoroutineScope(SupervisorJob() + disp)
            scope = sc
            sc.launch {
                while (running.get()) {
                    val socket = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    val id = synchronized(this) { ++idSeq }
                    launch { handle(socket, id) }
                }
                if (running.getAndSet(false)) {
                    // Accept loop died: drop the socket instead of leaving it bound
                    // with nobody accepting (a black hole callers report as timeout).
                    try {
                        ss.close()
                    } catch (_: Exception) {
                    }
                    serverSocket = null
                    // Nothing else can clean up now: `running` is already false, so a
                    // later stop() returns immediately and would leak the tunnels and
                    // this server's private thread pool.
                    closeLiveSockets()
                    scope = null
                    dispatcher?.close()
                    dispatcher = null
                    onEvent(Event.Stopped("HTTP Proxy: accept loop stopped :$port"))
                }
            }
            true
        } catch (e: Exception) {
            running.set(false)
            serverSocket = null
            onEvent(Event.Error("HTTP Proxy: ${e.message ?: "bind failed on :$port"}"))
            false
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        closeLiveSockets()
        val sc = scope
        val disp = dispatcher
        scope = null
        dispatcher = null
        sc?.cancel()
        disp?.close()
    }

    private suspend fun handle(client: Socket, id: Long) {
        val peer = client.inetAddress?.hostAddress ?: "unknown"
        onEvent(Event.Opened(id, peer))
        track(client)
        var upstream: Socket? = null
        try {
            client.use { c ->
                c.tcpNoDelay = true
                val input = c.getInputStream()
                val output = c.getOutputStream()

                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 3) return
                val method = parts[0]
                val target = parts[1]
                val version = parts[2]

                val headerLines = mutableListOf<Pair<String, String>>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) {
                        headerLines.add(
                            line.substring(0, i).trim() to line.substring(i + 1).trim()
                        )
                    }
                }

                if (method.equals("CONNECT", true)) {
                    val (host, p) = parseAuthority(target, 443)
                    val up = connectUpstream(host, p)
                    if (up == null) {
                        output.write(RESP_502)
                        output.flush()
                        return
                    }
                    upstream = up
                    track(up)
                    output.write(RESP_200_CONNECT)
                    output.flush()
                    pipe(c, up, id, peer)
                } else {
                    val uri = runCatching { URI(target) }.getOrNull()
                    if (uri == null || uri.host.isNullOrBlank()) {
                        output.write(RESP_400)
                        output.flush()
                        return
                    }
                    val host = uri.host
                    val p = if (uri.port != -1) uri.port else 80
                    val up = connectUpstream(host, p)
                    if (up == null) {
                        output.write(RESP_502)
                        output.flush()
                        return
                    }
                    upstream = up
                    track(up)

                    val path = buildString {
                        val raw = uri.rawPath
                        append(if (raw.isNullOrEmpty()) "/" else raw)
                        uri.rawQuery?.let {
                            append("?").append(it)
                        }
                    }

                    // The body is read up-front and re-emitted with an explicit
                    // Content-Length. The old code only understood Content-Length, so a
                    // chunked upload was forwarded with its Transfer-Encoding header but
                    // an empty body — the upstream then hung or answered 400.
                    val chunked = headerLines.any {
                        it.first.equals("transfer-encoding", true) &&
                            it.second.contains("chunked", true)
                    }
                    val declaredLength = headerLines
                        .firstOrNull { it.first.equals("content-length", true) }
                        ?.second?.toIntOrNull()

                    val body: ByteArray = if (chunked) {
                        val decoded = runCatching { readChunkedBody(input, MAX_BODY) }.getOrNull()
                        if (decoded == null) {
                            output.write(RESP_400)
                            output.flush()
                            return
                        }
                        decoded
                    } else if (declaredLength != null && declaredLength > MAX_BODY) {
                        output.write(RESP_413)
                        output.flush()
                        return
                    } else if (declaredLength != null && declaredLength > 0) {
                        readFully(input, declaredLength)
                    } else {
                        ByteArray(0)
                    }

                    val out = ByteArrayOutputStream()
                    out.write("$method $path $version\r\n".toByteArray())
                    for ((k, v) in headerLines) {
                        // Hop-by-hop headers belong to *this* connection, not the next
                        // one. Forwarding the client's "Connection: keep-alive" and then
                        // appending "Connection: close" produced two conflicting headers.
                        if (STRIPPED_HEADERS.contains(k.lowercase())) continue
                        // The client believes it is talking to the origin; keep the
                        // Host header it sent rather than inventing a new one.
                        out.write("$k: $v\r\n".toByteArray())
                    }
                    out.write("Content-Length: ${body.size}\r\n".toByteArray())
                    out.write("Connection: close\r\n\r\n".toByteArray())

                    val upOut = up.getOutputStream()
                    upOut.write(out.toByteArray())
                    if (body.isNotEmpty()) upOut.write(body)
                    upOut.flush()

                    pipe(c, up, id, peer)
                }
            }
        } catch (_: Exception) {
            // connection dropped
        } finally {
            try {
                upstream?.close()
            } catch (_: Exception) {
            }
            untrack(client)
            upstream?.let { untrack(it) }
            onEvent(Event.Closed(id, peer))
        }
    }

    private suspend fun pipe(client: Socket, upstream: Socket, id: Long, peer: String) =
        kotlinx.coroutines.coroutineScope {
            val j1 = launch {
                try {
                    copyStream(upstream.getInputStream(), client.getOutputStream(), peer, up = false)
                } catch (_: Exception) {
                } finally {
                    try { client.shutdownOutput() } catch (_: Exception) {}
                }
            }
            val j2 = launch {
                try {
                    copyStream(client.getInputStream(), upstream.getOutputStream(), peer, up = true)
                } catch (_: Exception) {
                } finally {
                    try { upstream.shutdownOutput() } catch (_: Exception) {}
                }
            }
            j1.join()
            j2.join()
        }

    private fun copyStream(input: InputStream, output: OutputStream, peer: String, up: Boolean) {
        val buf = ByteArray(32768)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
            output.flush()
            onEvent(
                if (up) Event.Traffic(peer, up = n.toLong(), down = 0)
                else Event.Traffic(peer, up = 0, down = n.toLong())
            )
        }
    }

    /**
     * Connect outbound to target host and port.
     *
     * The socket is intentionally *not* bound to a specific [android.net.Network]: Android
     * routes it through the active default network, which is the VPN tunnel whenever a VPN
     * is up. Binding to the physical WAN here would bypass the tunnel.
     * IPv4 is tried first to avoid 5s dual-stack timeouts on mobile networks.
     */
    private fun connectUpstream(host: String, port: Int): Socket? {
        return try {
            val addresses = InetAddress.getAllByName(host)
            val sorted = addresses.sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            for (addr in sorted) {
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.soTimeout = 0
                    s.connect(InetSocketAddress(addr, port), 4_000)
                    return s
                } catch (_: Exception) {
                    // Try next address if multi-homed
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Largest request body we are willing to buffer. */
        private const val MAX_BODY = 4 * 1024 * 1024

        private val RESP_200_CONNECT =
            "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()
        private val RESP_502 =
            "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()
        private val RESP_400 =
            "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()
        private val RESP_413 =
            "HTTP/1.1 413 Payload Too Large\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()

        /** Never forwarded to the upstream hop; we write our own length and framing. */
        private val STRIPPED_HEADERS = setOf(
            "proxy-connection",
            "proxy-authorization",
            "connection",
            "keep-alive",
            "transfer-encoding",
            "te",
            "trailer",
            "upgrade",
            "content-length"
        )

        fun parseAuthority(target: String, defaultPort: Int): Pair<String, Int> {
            val t = target.removePrefix("http://").removePrefix("https://")
            val hostPort = t.substringBefore("/")
            if (hostPort.startsWith("[")) {
                val h = hostPort.substringAfter("[").substringBefore("]")
                val p = hostPort.substringAfter("]:", "").toIntOrNull() ?: defaultPort
                return h to p
            }
            if (hostPort.contains(":")) {
                val h = hostPort.substringBefore(":")
                val p = hostPort.substringAfter(":").toIntOrNull() ?: defaultPort
                return h to p
            }
            return hostPort to defaultPort
        }

        fun readLine(input: InputStream, max: Int = 16384): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) {
                    if (sb.isNotEmpty() && sb.last() == '\r') sb.setLength(sb.length - 1)
                    return sb.toString()
                }
                sb.append(b.toChar())
                if (sb.length > max) return null
            }
        }

        fun readFully(input: InputStream, length: Int): ByteArray {
            val buf = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = input.read(buf, off, length - off)
                if (n < 0) throw EOFException()
                off += n
            }
            return buf
        }

        /**
         * Decode a `Transfer-Encoding: chunked` body so it can be re-sent with a
         * Content-Length. Throws [IOException] on malformed input or when the decoded
         * body would exceed [max].
         */
        fun readChunkedBody(input: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine(input) ?: throw EOFException()
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16)
                    ?: throw IOException("bad chunk size: $sizeLine")
                if (size == 0) {
                    // Consume trailer headers up to the terminating blank line.
                    while (true) {
                        val trailer = readLine(input) ?: break
                        if (trailer.isEmpty()) break
                    }
                    return out.toByteArray()
                }
                if (out.size() + size > max) throw IOException("chunked body exceeds $max bytes")
                out.write(readFully(input, size))
                readLine(input) // CRLF that terminates the chunk data
            }
        }
    }
}

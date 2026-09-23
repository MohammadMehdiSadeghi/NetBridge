package com.netbridge.share.share

import android.content.Context
import android.net.Network
import com.netbridge.share.net.Interfaces
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HTTP proxy shared over the LAN.
 *
 * Outbound sockets are opened *from the phone*, so they inherit whatever route the
 * phone's VPN app has installed. That is the whole trick: no root, no server, and
 * the PC's traffic ends up inside the tunnel.
 */
class HttpProxyServer(
    private val port: Int,
    private val onEvent: (Event) -> Unit,
    private val appContext: Context? = null
) {
    sealed class Event {
        data class Opened(val id: Long, val peer: String) : Event()
        data class Closed(val id: Long, val peer: String) : Event()
        data class Traffic(val peer: String, val up: Long, val down: Long) : Event()
        data class Error(val message: String) : Event()
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var idSeq = 0L

    /** Resolved route; refreshed lazily so a mid-session VPN toggle is picked up. */
    @Volatile
    private var cachedRoute: Network? = null
    @Volatile
    private var routeResolvedAt = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        sc.launch {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", port), 128)
                serverSocket = ss
                while (running.get()) {
                    val socket = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    val id = synchronized(this) { ++idSeq }
                    launch { handle(socket, id) }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    onEvent(Event.Error("HTTP Proxy: ${e.message ?: "error"}"))
                }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    private fun route(): Network? {
        val ctx = appContext ?: return null
        val now = System.currentTimeMillis()
        if (now - routeResolvedAt > 5000) {
            cachedRoute = Interfaces.preferredRoute(ctx)
            routeResolvedAt = now
        }
        return cachedRoute
    }

    private fun handle(client: Socket, id: Long) {
        val peer = client.inetAddress?.hostAddress ?: "unknown"
        onEvent(Event.Opened(id, peer))
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

                    val path = buildString {
                        val raw = uri.rawPath
                        append(if (raw.isNullOrEmpty()) "/" else raw)
                        uri.rawQuery?.let {
                            append("?").append(it)
                        }
                    }

                    val out = ByteArrayOutputStream()
                    out.write("$method $path $version\r\n".toByteArray())
                    for ((k, v) in headerLines) {
                        val lk = k.lowercase()
                        if (lk == "proxy-connection" || lk == "proxy-authorization") continue
                        // The client believes it is talking to the origin; keep the
                        // Host header it sent rather than inventing a new one.
                        out.write("$k: $v\r\n".toByteArray())
                    }
                    out.write("Connection: close\r\n\r\n".toByteArray())

                    val contentLength = headerLines
                        .firstOrNull { it.first.equals("content-length", true) }
                        ?.second?.toIntOrNull() ?: 0
                    val body = if (contentLength in 1..4_194_304) {
                        readFully(input, contentLength)
                    } else {
                        ByteArray(0)
                    }

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
            onEvent(Event.Closed(id, peer))
        }
    }

    private fun pipe(client: Socket, upstream: Socket, id: Long, peer: String) {
        val lock = Any()
        var done = false
        val t1 = Thread {
            try {
                copy(upstream.getInputStream(), client.getOutputStream(), peer, up = false)
            } finally {
                synchronized(lock) {
                    if (!done) {
                        done = true
                        closeQuietly(client, upstream)
                    }
                }
            }
        }
        val t2 = Thread {
            try {
                copy(client.getInputStream(), upstream.getOutputStream(), peer, up = true)
            } finally {
                synchronized(lock) {
                    if (!done) {
                        done = true
                        closeQuietly(client, upstream)
                    }
                }
            }
        }
        t1.isDaemon = true
        t2.isDaemon = true
        t1.start()
        t2.start()
        try {
            t1.join()
            t2.join()
        } catch (_: InterruptedException) {
        }
    }

    private fun copy(input: InputStream, output: OutputStream, peer: String, up: Boolean) {
        val buf = ByteArray(16384)
        try {
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
        } catch (_: Exception) {
        } finally {
            closeQuietly(input, output)
        }
    }

    /**
     * Open the outbound socket. When we know which network carries the tunnel we bind
     * to it explicitly; otherwise we let Android pick, which is the correct behaviour
     * for a normal handset VPN and keeps working on ROMs that hide network handles.
     */
    private fun connectUpstream(host: String, port: Int): Socket? {
        val net = route()
        if (net != null) {
            try {
                val s = net.socketFactory.createSocket() as Socket
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 12_000)
                return s
            } catch (_: Exception) {
                // Fall through to the default route rather than failing the request.
            }
        }
        return try {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 12_000)
            s
        } catch (_: Exception) {
            null
        }
    }

    private fun closeQuietly(vararg things: Any?) {
        for (t in things) {
            try {
                when (t) {
                    is Socket -> t.close()
                    is InputStream -> t.close()
                    is OutputStream -> t.close()
                }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private val RESP_200_CONNECT =
            "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()
        private val RESP_502 =
            "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()
        private val RESP_400 =
            "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()

        fun parseAuthority(target: String, defaultPort: Int): Pair<String, Int> {
            val t = target.removePrefix("http://").removePrefix("https://")
            val hostPort = t.substringBefore("/")
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
    }
}

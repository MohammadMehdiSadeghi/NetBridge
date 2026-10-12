package com.netbridge.share.share

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SOCKS5 proxy shared over the LAN.
 *
 * Same routing policy as [HttpProxyServer]: DNS resolution happens on the phone and the
 * outbound socket is a plain [Socket], so Android routes it through the active default
 * network — the phone's VPN tunnel whenever one is up.
 *
 * Like [HttpProxyServer], [start] binds synchronously and reports whether the port is
 * actually listening, so the caller never believes SOCKS is up when it is not.
 */
class Socks5Server(
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

    /** Sockets of live tunnels; closed by [stop] so "stop sharing" really stops traffic. */
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

    /** Returns true only when the SOCKS port is accepting connections. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return serverSocket?.isBound == true
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress("0.0.0.0", port), 128)
            serverSocket = ss
            // Its own pool — see ServerThreads. Sharing Dispatchers.IO with the proxy
            // tunnels is what let a busy session stall every accept loop in the app.
            val disp = ServerThreads.pool("socks")
            dispatcher = disp
            val sc = CoroutineScope(SupervisorJob() + disp)
            scope = sc
            sc.launch {
                while (running.get()) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    val id = synchronized(this) { ++idSeq }
                    launch { handle(client, id) }
                }
                // stop() clears `running` first. A set here means the loop died while we
                // still believed we were listening: close the socket so callers get a
                // refusal instead of a silent backlog of dropped SYNs.
                if (running.getAndSet(false)) {
                    try {
                        ss.close()
                    } catch (_: Exception) {
                    }
                    serverSocket = null
                    // `running` is already false, so a later stop() would return
                    // immediately and leak the tunnels and this private thread pool.
                    closeLiveSockets()
                    scope = null
                    dispatcher?.close()
                    dispatcher = null
                    onEvent(Event.Stopped("SOCKS5: accept loop stopped :$port"))
                }
            }
            true
        } catch (e: Exception) {
            running.set(false)
            serverSocket = null
            onEvent(Event.Error("SOCKS5: ${e.message ?: "bind failed on :$port"}"))
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

                // greeting: VER NMETHODS METHODS...
                val ver = input.read()
                if (ver != 5) return
                val nMethods = input.read()
                if (nMethods <= 0) return
                val methods = HttpProxyServer.readFully(input, nMethods)
                // RFC 1928: the server may only pick a method the client offered. We
                // support "no authentication" (0x00) and nothing else.
                if (!methods.contains(0x00.toByte())) {
                    output.write(byteArrayOf(0x05, 0xFF.toByte()))
                    output.flush()
                    return
                }
                output.write(byteArrayOf(0x05, 0x00))
                output.flush()

                // request
                val rVer = input.read()
                if (rVer != 5) return
                val cmd = input.read()
                input.read() // rsv
                val atyp = input.read()
                val host = when (atyp) {
                    0x01 -> {
                        val b = HttpProxyServer.readFully(input, 4)
                        InetAddress.getByAddress(b).hostAddress ?: return
                    }
                    0x03 -> {
                        val len = input.read()
                        if (len <= 0) return
                        String(HttpProxyServer.readFully(input, len), Charsets.ISO_8859_1)
                    }
                    0x04 -> {
                        val b = HttpProxyServer.readFully(input, 16)
                        InetAddress.getByAddress(b).hostAddress ?: return
                    }
                    else -> {
                        output.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        output.flush()
                        return
                    }
                }
                val p1 = input.read()
                val p2 = input.read()
                if (p1 < 0 || p2 < 0) return
                val port = (p1 shl 8) or p2

                if (cmd != 0x01) {
                    output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                    return
                }

                val up = connectUpstream(host, port)
                if (up == null) {
                    output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                    return
                }
                upstream = up
                track(up)
                output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()

                pipe(c, up, id, peer)
            }
        } catch (_: Exception) {
            // dropped
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
     * See [HttpProxyServer.connectUpstream]: no explicit [android.net.Network] binding, so
     * the socket follows Android's default route and therefore the phone's VPN.
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
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

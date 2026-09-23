package com.netbridge.share.share

import android.content.Context
import android.net.Network
import com.netbridge.share.net.Interfaces
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * SOCKS5 proxy shared over the LAN.
 *
 * Same routing policy as [HttpProxyServer]: DNS resolution happens on the phone and
 * the outbound socket is bound to the tunnel-carrying network when one is known.
 */
class Socks5Server(
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
                    onEvent(Event.Error("SOCKS5: ${e.message ?: "error"}"))
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

                // greeting: VER NMETHODS METHODS...
                val ver = input.read()
                if (ver != 5) return
                val nMethods = input.read()
                if (nMethods <= 0) return
                HttpProxyServer.readFully(input, nMethods)
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

    private fun connectUpstream(host: String, port: Int): Socket? {
        val net = route()
        if (net != null) {
            try {
                val s = net.socketFactory.createSocket() as Socket
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 12_000)
                return s
            } catch (_: Exception) {
                // fall through
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
}

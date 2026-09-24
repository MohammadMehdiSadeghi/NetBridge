package com.netbridge.share.share

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ControlApiServer(
    private val port: Int,
    private val verifyPair: (code: String) -> String?,
    private val readToken: () -> String,
    private val onCommand: (Command) -> Any?,
    private val appContext: Context,
    /** Called if the accept loop dies on its own, so the owner can rebind. */
    private val onDied: (() -> Unit)? = null
) {
    enum class Command { START, STOP, RECYCLE }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var dispatcher: ExecutorCoroutineDispatcher? = null

    /** Returns true only when the control port is actually listening. */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return serverSocket?.isBound == true
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress("0.0.0.0", port), 32)
            serverSocket = ss
            // Its own pool: the control API must keep accepting even when the proxy
            // servers are busy enough to exhaust the shared IO pool.
            val disp = ServerThreads.pool("control")
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
                    launch { handle(socket) }
                }
                // stop() clears `running` first; a set here means accept() failed by
                // itself. Leaving the socket bound with nobody accepting queues SYNs
                // until the backlog is full and then drops them — every caller sees
                // "no answer" instead of a refusal. Close it and let the owner rebind.
                if (running.getAndSet(false)) {
                    try {
                        ss.close()
                    } catch (_: Exception) {
                    }
                    serverSocket = null
                    onDied?.invoke()
                }
            }
            true
        } catch (_: Exception) {
            running.set(false)
            serverSocket = null
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
        scope?.cancel()
        scope = null
        dispatcher?.close()
        dispatcher = null
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 15_000
                val input = s.getInputStream()
                val output = s.getOutputStream()
                val requestLine = HttpProxyServer.readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 3) return
                val method = parts[0]
                val path = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = HttpProxyServer.readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) {
                        headers[line.substring(0, i).trim().lowercase()] =
                            line.substring(i + 1).trim()
                    }
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyBytes = if (contentLength in 0..65_536) {
                    HttpProxyServer.readFully(input, contentLength)
                } else {
                    ByteArray(0)
                }
                val bodyText = String(bodyBytes, Charsets.UTF_8)

                val (status, json) = route(method, path, headers, bodyText)
                val payload = json.toString().toByteArray(Charsets.UTF_8)
                val statusLine = when (status) {
                    200 -> "HTTP/1.1 200 OK"
                    400 -> "HTTP/1.1 400 Bad Request"
                    401 -> "HTTP/1.1 401 Unauthorized"
                    404 -> "HTTP/1.1 404 Not Found"
                    405 -> "HTTP/1.1 405 Method Not Allowed"
                    else -> "HTTP/1.1 500 Internal Server Error"
                }
                val response = buildString {
                    append(statusLine).append("\r\n")
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ").append(payload.size).append("\r\n")
                    append("Connection: close\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("\r\n")
                }.toByteArray()
                output.write(response)
                output.write(payload)
                output.flush()
            }
        } catch (_: Exception) {
            // Best-effort 500 so a broken handler does not look like a dead phone
            // (the desktop would otherwise hang until its fetch timeout).
            try {
                socket.use { s ->
                    val body = err("internal error").toString().toByteArray(Charsets.UTF_8)
                    val head = (
                        "HTTP/1.1 500 Internal Server Error\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${body.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray()
                    s.getOutputStream().write(head)
                    s.getOutputStream().write(body)
                    s.getOutputStream().flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun route(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String
    ): Pair<Int, JSONObject> {
        val cleanPath = path.substringBefore("?")

        if (method == "POST" && cleanPath == "/api/v1/pair") {
            val code = runCatching { JSONObject(body).optString("code") }.getOrDefault("")
            if (code.isEmpty()) return 400 to err("code required")
            val token = verifyPair(code)
                ?: return 401 to err("invalid code")
            return 200 to JSONObject().put("token", token)
        }

        val auth = headers["authorization"] ?: ""
        val token = auth.removePrefix("Bearer ").trim()
        if (token.isEmpty() || token != readToken()) {
            return 401 to err("unauthorized")
        }

        return when {
            method == "GET" && cleanPath == "/api/v1/status" -> {
                val st = ShareManager.state.value
                val ips = JSONArray(ShareManager.shareAddresses())
                // A ready proxy with no reachable address is the single most common
                // reason the desktop app cannot connect, so say it explicitly.
                val reachable = st.sharing && ips.length() > 0
                200 to JSONObject()
                    .put("sharing", st.sharing)
                    .put("vpnActive", st.vpnActive)
                    .put("internetReachable", st.internetReachable)
                    .put("transport", st.transport.name)
                    .put("transportLabel", st.transportLabel)
                    .put("reachable", reachable)
                    .put("clients", st.clients)
                    .put("bytesIn", st.bytesIn)
                    .put("bytesOut", st.bytesOut)
                    .put("ips", ips)
                    .put("httpPort", st.httpPort)
                    .put("socksPort", st.socksPort)
                    .put("controlPort", st.controlPort)
                    .put("code", st.pairingCode)
                    .put("warning", st.warning ?: JSONObject.NULL)
                    .put("version", st.version)
            }

            method == "GET" && cleanPath == "/api/v1/clients" -> {
                val arr = JSONArray()
                for (c in ShareManager.clients()) {
                    arr.put(
                        JSONObject()
                            .put("address", c.address)
                            .put("connections", c.connections)
                            .put("bytesUp", c.bytesUp)
                            .put("bytesDown", c.bytesDown)
                            .put("firstSeen", c.firstSeen)
                            .put("lastSeen", c.lastSeen)
                    )
                }
                200 to JSONObject().put("clients", arr)
            }

            method == "POST" && cleanPath == "/api/v1/start" -> {
                // Only report success when the HTTP proxy is really listening.
                // An unconditional ok:true made the desktop skip its own start
                // and dial a dead :8080 while the UI said connected.
                val ok = ShareManager.startSharing(background = true)
                if (ok) {
                    200 to JSONObject().put("ok", true)
                } else {
                    val detail = ShareManager.state.value.error ?: "proxy failed to bind"
                    500 to err(detail)
                }
            }

            method == "POST" && cleanPath == "/api/v1/stop" -> {
                ShareManager.stopSharing()
                200 to JSONObject().put("ok", true)
            }

            method == "POST" && cleanPath == "/api/v1/recycle" -> {
                val code = onCommand(Command.RECYCLE) as? String ?: ""
                200 to JSONObject().put("code", code)
            }

            cleanPath.startsWith("/api/") -> 404 to err("not found")
            else -> 404 to err("not found")
        }
    }

    private fun err(message: String) = JSONObject().put("error", message)
}

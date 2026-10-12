package com.netbridge.share.share

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import com.netbridge.share.R
import com.netbridge.share.data.SettingsStore
import com.netbridge.share.net.Interfaces
import com.netbridge.share.net.NetworkMonitor
import com.netbridge.share.net.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

object ShareManager {

    data class State(
        val sharing: Boolean = false,
        val controlAlive: Boolean = false,
        val vpnActive: Boolean = false,
        /** The phone itself currently has a working route to the internet. */
        val internetReachable: Boolean = false,
        val transport: Transport = Transport.NONE,
        val clients: Int = 0,
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val ips: List<String> = emptyList(),
        val httpPort: Int = 8080,
        val socksPort: Int = 1080,
        val controlPort: Int = 7777,
        val pairingCode: String = "",
        val error: String? = null,
        /** Stable code: no_internet | no_vpn | no_transport — UI resolves the text. */
        val warning: String? = null,
        val version: String = "1.0.0"
    ) {
        val transportLabel: String
            get() = when (transport) {
                Transport.USB_TETHER -> "USB"
                Transport.WIFI_HOTSPOT -> "Hotspot"
                Transport.NONE -> "No link"
            }

        /** True when the PC can reach the proxy right now. */
        val reachable: Boolean get() = ips.isNotEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: HttpProxyServer? = null
    private var socksServer: Socks5Server? = null
    private var controlServer: ControlApiServer? = null
    /** Consecutive auto-rebinds of the control API; reset on every normal start. */
    private var controlRestarts = 0
    private var monitorJob: Job? = null
    private var nsdAdvertiser: NsdHolder? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var appContext: Context? = null
    private lateinit var store: SettingsStore
    private val random = SecureRandom()

    /** Guards [init]: it is called from both the Application and BootReceiver. */
    private var initialized = false

    /**
     * Traffic totals. These live in atomics rather than in [State] because every tunnel
     * thread reports bytes concurrently; a read-modify-write on `_state.value` lost
     * updates whenever two threads interleaved.
     */
    private val bytesInTotal = AtomicLong(0)
    private val bytesOutTotal = AtomicLong(0)

    /** Notified after a route change so the native helper can re-resolve. */
    var onRouteChanged: (() -> Unit)? = null

    interface NsdHolder {
        fun register(port: Int)
        fun unregister()
    }

    fun init(context: Context, settingsStore: SettingsStore) {
        // BootReceiver re-initialises defensively, but running this twice replaced
        // `_state` with a fresh State and wiped sharing/clients/byte counters of a
        // session that was already live.
        if (initialized) return
        initialized = true

        appContext = context.applicationContext
        store = settingsStore
        val rawCode = store.pairingCode.ifEmpty { newPairingCode().also { store.pairingCode = it } }
        val code = normalizeLatinDigits(rawCode).also { store.pairingCode = it }
        val token = store.apiToken.ifEmpty {
            ByteArray(24).also { random.nextBytes(it) }
                .joinToString("") { String.format(java.util.Locale.US, "%02x", it) }
                .also { store.apiToken = it }
        }
        // A full reset is intentional here: this is the one place that seeds the state.
        _state.value = State(
            httpPort = store.httpPort,
            socksPort = store.socksPort,
            controlPort = store.controlPort,
            pairingCode = code,
            version = "1.0.0"
        )
        check(token.isNotEmpty())
        startMonitor()
        startControl()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onRouteChanged?.invoke()
            }

            override fun onLost(network: Network) {
                onRouteChanged?.invoke()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                onRouteChanged?.invoke()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            val ctx = appContext ?: return@launch
            val monitor = NetworkMonitor(ctx)
            monitor.observe().collect { snapshot ->
                val warning = buildWarning(snapshot)
                _state.update {
                    it.copy(
                        vpnActive = snapshot.vpnActive,
                        internetReachable = snapshot.internetReachable,
                        transport = snapshot.transport,
                        ips = snapshot.ips,
                        warning = warning
                    )
                }
            }
        }
    }

    /**
     * Stable codes for the situations that look identical in the UI but need opposite
     * fixes. The phone UI and the desktop each resolve the code into their own locale.
     *
     * Order matters: the most actionable problem is reported first. With no PC-facing
     * link nothing can connect at all, so "turn the VPN off" would be the wrong advice.
     */
    private fun buildWarning(s: com.netbridge.share.net.NetworkSnapshot): String? {
        if (s.transport == Transport.NONE) return "no_transport"
        if (!s.internetReachable) return "no_internet"
        if (!s.vpnActive) return "no_vpn"
        return null
    }

    fun startControl(fromRestart: Boolean = false) {
        if (_state.value.controlAlive) return
        if (fromRestart) {
            // The accept loop only dies on its own if something is genuinely wrong;
            // three attempts (1.5s apart) is enough to survive a port being released.
            if (controlRestarts >= 3) return
            controlRestarts += 1
        } else {
            controlRestarts = 0
        }
        val ctx = appContext ?: return
        val server = ControlApiServer(
            port = store.controlPort,
            verifyPair = { code ->
                if (normalizeLatinDigits(code.trim()) == normalizeLatinDigits(store.pairingCode.trim())) store.apiToken else null
            },
            readToken = { store.apiToken },
            onCommand = { cmd ->
                when (cmd) {
                    ControlApiServer.Command.START -> startSharing(background = true)
                    ControlApiServer.Command.STOP -> stopSharing()
                    ControlApiServer.Command.RECYCLE -> {
                        val c = newPairingCode()
                        store.pairingCode = c
                        _state.update { it.copy(pairingCode = c) }
                        c
                    }
                }
            },
            appContext = ctx,
            onDied = {
                _state.update { it.copy(controlAlive = false) }
                scope.launch {
                    kotlinx.coroutines.delay(500)
                    startControl(fromRestart = true)
                }
            }
        )
        controlServer = server
        val bound = server.start()
        _state.update {
            it.copy(
                controlAlive = bound,
                error = if (bound) null else "control API failed to bind :${store.controlPort}"
            )
        }
    }

    /**
     * Starts the HTTP + SOCKS proxies. Returns true only when the HTTP proxy
     * (the path the desktop uses) is actually listening on [SettingsStore.httpPort].
     */
    fun startSharing(background: Boolean = false): Boolean {
        val ctx = appContext ?: return false
        if (_state.value.sharing) return true

        ClientRegistry.reset()
        bytesInTotal.set(0)
        bytesOutTotal.set(0)

        val http = HttpProxyServer(
            port = store.httpPort,
            onEvent = { ev -> handleEvent(ev) }
        )
        val socks = Socks5Server(
            port = store.socksPort,
            onEvent = { ev -> handleSocksEvent(ev) }
        )

        if (!http.start()) {
            _state.update {
                it.copy(error = ctx.getString(R.string.error_proxy) + " :${store.httpPort}")
            }
            return false
        }
        // SOCKS is optional for the desktop path; a busy port must not block sharing.
        socks.start()

        httpServer = http
        socksServer = socks
        startControl()
        _state.update { it.copy(sharing = true, error = null) }

        if (background) {
            val intent = Intent(ctx, ShareService::class.java)
            try {
                ctx.startForegroundService(intent)
            } catch (_: Exception) {
            }
        }
        return true
    }

    private fun handleEvent(ev: HttpProxyServer.Event) {
        when (ev) {
            is HttpProxyServer.Event.Opened -> {
                ClientRegistry.onOpen(ev.peer)
                refreshClientCount()
            }
            is HttpProxyServer.Event.Closed -> {
                ClientRegistry.onClose(ev.peer)
                refreshClientCount()
            }
            is HttpProxyServer.Event.Traffic -> {
                ClientRegistry.onTraffic(ev.peer, ev.up, ev.down)
                addBytes(ev.up, ev.down)
            }
            is HttpProxyServer.Event.Error -> _state.update { it.copy(error = ev.message) }
            is HttpProxyServer.Event.Stopped -> {
                // The accept loop died on its own, so nothing is listening on the HTTP
                // port any more. `sharing` has to follow, otherwise /api/v1/start would
                // answer {"ok": true} for a dead port and startSharing() would be a no-op.
                httpServer = null
                socksServer?.stop()
                socksServer = null
                _state.update { it.copy(sharing = false, clients = 0, error = ev.reason) }
            }
        }
    }

    private fun handleSocksEvent(ev: Socks5Server.Event) {
        when (ev) {
            is Socks5Server.Event.Opened -> {
                ClientRegistry.onOpen(ev.peer)
                refreshClientCount()
            }
            is Socks5Server.Event.Closed -> {
                ClientRegistry.onClose(ev.peer)
                refreshClientCount()
            }
            is Socks5Server.Event.Traffic -> {
                ClientRegistry.onTraffic(ev.peer, ev.up, ev.down)
                addBytes(ev.up, ev.down)
            }
            is Socks5Server.Event.Error -> _state.update { it.copy(error = ev.message) }
            is Socks5Server.Event.Stopped -> {
                // SOCKS is optional for the desktop path, so a dead accept loop only
                // means we forget the instance — sharing itself stays up.
                socksServer = null
            }
        }
    }

    fun stopSharing() {
        httpServer?.stop()
        socksServer?.stop()
        httpServer = null
        socksServer = null
        _state.update { it.copy(sharing = false, clients = 0) }
        appContext?.let { ctx ->
            val intent = Intent(ctx, ShareService::class.java)
            try {
                ctx.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleSharing() {
        if (_state.value.sharing) stopSharing() else startSharing(background = true)
    }

    fun setPorts(http: Int, socks: Int, control: Int) {
        val wasSharing = _state.value.sharing
        val wasControl = _state.value.controlAlive
        if (wasSharing) stopSharing()
        if (wasControl) {
            controlServer?.stop()
            _state.update { it.copy(controlAlive = false) }
        }
        store.httpPort = http
        store.socksPort = socks
        store.controlPort = control
        _state.update {
            it.copy(httpPort = http, socksPort = socks, controlPort = control)
        }
        startControl()
        // Re-register the mDNS record so the new control port is advertised.
        nsdAdvertiser?.let {
            it.unregister()
            it.register(control)
        }
        if (wasSharing) startSharing(background = false)
    }

    fun recycleCode(): String {
        val c = newPairingCode()
        store.pairingCode = c
        _state.update { it.copy(pairingCode = c) }
        return c
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clients(): List<ClientRegistry.Client> = ClientRegistry.snapshot()

    /** Addresses a PC can dial, preferring the PC-facing tether link. */
    fun shareAddresses(): List<String> {
        val fromState = _state.value.ips
        if (fromState.isNotEmpty()) return fromState
        return Interfaces.shareAddresses()
    }

    private fun newPairingCode(): String {
        val n = random.nextInt(1_000_000)
        return String.format(java.util.Locale.US, "%06d", n)
    }

    private fun refreshClientCount() {
        val active = ClientRegistry.activeCount()
        _state.update { it.copy(clients = active) }
    }

    private fun addBytes(fromClient: Long, toClient: Long) {
        if (fromClient > 0) bytesInTotal.addAndGet(fromClient)
        if (toClient > 0) bytesOutTotal.addAndGet(toClient)
        val inNow = bytesInTotal.get()
        val outNow = bytesOutTotal.get()
        // `maxOf` keeps the published counters monotonic: the atomics are the source of
        // truth, so an interleaved writer can never move the visible total backwards.
        // Every other writer uses `update {}` too — a plain `value = value.copy(...)`
        // would read a stale snapshot and clobber whatever addBytes just published.
        _state.update {
            it.copy(
                bytesIn = maxOf(it.bytesIn, inNow),
                bytesOut = maxOf(it.bytesOut, outNow)
            )
        }
    }

    fun bindNsd(advertiser: NsdHolder) {
        nsdAdvertiser = advertiser
        advertiser.register(store.controlPort)
    }

    fun normalizeLatinDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

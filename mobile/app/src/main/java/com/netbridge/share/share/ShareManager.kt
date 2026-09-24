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
import kotlinx.coroutines.launch
import java.security.SecureRandom

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
    private var monitorJob: Job? = null
    private var nsdAdvertiser: NsdHolder? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var appContext: Context? = null
    private lateinit var store: SettingsStore
    private val random = SecureRandom()

    /** Notified after a route change so the native helper can re-resolve. */
    var onRouteChanged: (() -> Unit)? = null

    interface NsdHolder {
        fun register(port: Int)
        fun unregister()
    }

    fun init(context: Context, settingsStore: SettingsStore) {
        appContext = context.applicationContext
        store = settingsStore
        val code = store.pairingCode.ifEmpty { newPairingCode().also { store.pairingCode = it } }
        val token = store.apiToken.ifEmpty {
            ByteArray(24).also { random.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
                .also { store.apiToken = it }
        }
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
                _state.value = _state.value.copy(
                    vpnActive = snapshot.vpnActive,
                    internetReachable = snapshot.internetReachable,
                    transport = snapshot.transport,
                    ips = snapshot.ips,
                    warning = warning
                )
            }
        }
    }

    /**
     * Stable codes for the two situations that look identical in the UI but need
     * opposite fixes: a tunnel with no uplink (turn the VPN off) versus no VPN at
     * all (traffic still flows, just unprotected). The phone UI and the desktop
     * each resolve the code into their own locale.
     */
    private fun buildWarning(s: com.netbridge.share.net.NetworkSnapshot): String? {
        if (!s.internetReachable) return "no_internet"
        if (!s.vpnActive) return "no_vpn"
        if (s.transport == Transport.NONE) return "no_transport"
        return null
    }

    fun startControl() {
        if (_state.value.controlAlive) return
        val ctx = appContext ?: return
        val server = ControlApiServer(
            port = store.controlPort,
            verifyPair = { code ->
                if (code == store.pairingCode) store.apiToken else null
            },
            readToken = { store.apiToken },
            onCommand = { cmd ->
                when (cmd) {
                    ControlApiServer.Command.START -> startSharing(background = true)
                    ControlApiServer.Command.STOP -> stopSharing()
                    ControlApiServer.Command.RECYCLE -> {
                        val c = newPairingCode()
                        store.pairingCode = c
                        _state.value = _state.value.copy(pairingCode = c)
                        c
                    }
                }
            },
            appContext = ctx
        )
        controlServer = server
        val bound = server.start()
        _state.value = _state.value.copy(
            controlAlive = bound,
            error = if (bound) null else "control API failed to bind :${store.controlPort}"
        )
    }

    /**
     * Starts the HTTP + SOCKS proxies. Returns true only when the HTTP proxy
     * (the path the desktop uses) is actually listening on [SettingsStore.httpPort].
     */
    fun startSharing(background: Boolean = false): Boolean {
        val ctx = appContext ?: return false
        if (_state.value.sharing) return true

        ClientRegistry.reset()

        val http = HttpProxyServer(
            port = store.httpPort,
            onEvent = { ev -> handleEvent(ev) },
            appContext = ctx
        )
        val socks = Socks5Server(
            port = store.socksPort,
            onEvent = { ev -> handleSocksEvent(ev) },
            appContext = ctx
        )

        if (!http.start()) {
            _state.value = _state.value.copy(
                error = ctx.getString(R.string.error_proxy) + " :${store.httpPort}"
            )
            socks.stop()
            return false
        }
        // SOCKS is optional for the desktop path; a busy port must not block sharing.
        try {
            socks.start()
        } catch (_: Exception) {
        }

        httpServer = http
        socksServer = socks
        startControl()
        _state.value = _state.value.copy(sharing = true, error = null)

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
            is HttpProxyServer.Event.Error -> _state.value =
                _state.value.copy(error = ev.message)
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
            is Socks5Server.Event.Error -> _state.value =
                _state.value.copy(error = ev.message)
        }
    }

    fun stopSharing() {
        httpServer?.stop()
        socksServer?.stop()
        httpServer = null
        socksServer = null
        _state.value = _state.value.copy(sharing = false, clients = 0)
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
            _state.value = _state.value.copy(controlAlive = false)
        }
        store.httpPort = http
        store.socksPort = socks
        store.controlPort = control
        _state.value = _state.value.copy(
            httpPort = http,
            socksPort = socks,
            controlPort = control
        )
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
        _state.value = _state.value.copy(pairingCode = c)
        return c
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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
        return "%06d".format(n)
    }

    private fun refreshClientCount() {
        _state.value = _state.value.copy(clients = ClientRegistry.activeCount())
    }

    private fun addBytes(fromClient: Long, toClient: Long) {
        _state.value = _state.value.copy(
            bytesIn = _state.value.bytesIn + fromClient,
            bytesOut = _state.value.bytesOut + toClient
        )
    }

    fun bindNsd(advertiser: NsdHolder) {
        nsdAdvertiser = advertiser
        advertiser.register(store.controlPort)
    }
}

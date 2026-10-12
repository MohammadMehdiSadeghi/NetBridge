package com.netbridge.share.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** How the PC is attached to this phone. */
enum class Transport {
    USB_TETHER,
    WIFI_HOTSPOT,
    NONE
}

data class NetworkSnapshot(
    val vpnActive: Boolean,
    /** Route to the internet exists on the phone itself. */
    val internetReachable: Boolean,
    val transport: Transport,
    val ips: List<String>,
    val usbTethering: Boolean,
    val hotspot: Boolean
) {
    /** Everything the PC needs is in place. */
    val ready: Boolean get() = internetReachable && ips.isNotEmpty()
}

class NetworkMonitor(private val context: Context) {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Emits on every connectivity change and also on a slow heartbeat, because
     * tethering interfaces can appear and disappear without firing a capabilities
     * callback on some ROMs.
     */
    fun observe(intervalMs: Long = 3000L): Flow<NetworkSnapshot> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshot())
            }

            override fun onLost(network: Network) {
                trySend(snapshot())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: android.net.NetworkCapabilities
            ) {
                trySend(snapshot())
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                trySend(snapshot())
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Some devices reject the default callback; the heartbeat still works.
        }

        trySend(snapshot())

        // Heartbeat for tether-interface changes that never reach the callback.
        val heartbeat = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            .launch {
                while (true) {
                    delay(intervalMs)
                    trySend(snapshot())
                }
            }

        awaitClose {
            heartbeat.cancel()
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    fun snapshot(): NetworkSnapshot {
        val report = Interfaces.enumerate(context)
        val usb = Interfaces.isUsbTetherUp()
        val hotspot = Interfaces.isWifiApUp()
        val transport = when {
            usb -> Transport.USB_TETHER
            hotspot -> Transport.WIFI_HOTSPOT
            else -> Transport.NONE
        }
        return NetworkSnapshot(
            vpnActive = report.vpnActive,
            // Only a WAN the OS validated counts. Treating "a VPN network exists" as
            // proof of internet made `internetReachable` permanently true whenever a VPN
            // was up, which is exactly the tunnel-with-a-dead-underlay case the UI is
            // supposed to warn about (see docs/protocol.md).
            internetReachable = report.hasValidatedWan,
            transport = transport,
            ips = Interfaces.shareAddresses(),
            usbTethering = usb,
            hotspot = hotspot
        )
    }
}

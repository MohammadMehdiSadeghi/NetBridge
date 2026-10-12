package com.netbridge.share.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Shared helpers for reading the phone's interfaces, tether state and VPN state.
 *
 * Kept free of Android framework types where possible so both the UI layer and
 * the proxy servers can use it.
 */
object Interfaces {

    /** Interface-name prefixes Android only creates when USB tethering is on. */
    private val USB_TETHER_HINTS = listOf("rndis", "usb", "ncm", "ecm")

    /**
     * Prefixes that only appear on a Wi-Fi hotspot (AP) interface.
     *
     * There is deliberately no bare `"wlan"` here. `wlan0` is the phone's own Wi-Fi
     * *client* link: matching it made `shareAddresses()` hand the PC the phone's
     * home-LAN address and made the UI report a hotspot that did not exist.
     */
    private val WIFI_AP_HINTS = listOf("ap", "swlan", "softap")

    /** Hotspots that reuse the Wi-Fi driver expose `wlan1`, `wlan2`, … — never `wlan0`. */
    private val WIFI_AP_WLAN = Regex("^wlan[1-9][0-9]*$")

    /** Modes that mean "this end is the one that dials out", not the one we plug the PC into. */
    private val WAN_IFACE_HINTS =
        listOf("rmnet", "ccmni", "pdp", "wwan", "ppp", "radio", "ril", "cellular")

    private val TUN_HINTS =
        listOf("tun", "tap", "utun", "ppp", "wg", "wireguard", "vpn", "ipsec", "dummy", "sit")

    data class Iface(val name: String, val address: String)

    private fun baseName(name: String): String = name.substringBefore(':').lowercase()

    /** True when [name] is a USB tethering interface created by Android. */
    fun isUsbTetherInterface(name: String): Boolean {
        val base = baseName(name)
        return USB_TETHER_HINTS.any { base.startsWith(it) }
    }

    /**
     * True when [name] is a Wi-Fi hotspot (AP) interface. Those are `ap0`, `swlan0`,
     * `softap0`, or `wlan1`/`wlan2`… — never `wlan0`, which is the client link.
     */
    fun isWifiApInterface(name: String): Boolean {
        val base = baseName(name)
        if (WIFI_AP_HINTS.any { base.startsWith(it) }) return true
        return WIFI_AP_WLAN.matches(base)
    }

    fun listIpv4(): List<Iface> {
        val out = mutableListOf<Iface>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("169.254.") || ip.startsWith("127.")) continue
                    out.add(Iface(name = nif.name, address = ip))
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return out
    }

    /** Interface names only — cheap, no address resolution. */
    fun names(): Set<String> {
        val out = mutableSetOf<String>()
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                out.add(nif.name.substringBefore(':'))
            }
        } catch (_: Exception) {
            // ignore
        }
        return out
    }

    fun isUsbTetherUp(): Boolean = names().any { isUsbTetherInterface(it) }

    fun isWifiApUp(): Boolean = names().any { isWifiApInterface(it) }

    /**
     * Addresses that a PC connected over tethering can actually reach. We prefer
     * downlink/tether interfaces and never expose the uplink (rmnet) or TUN addresses
     * to the control API, so the desktop app is never told to dial a useless IP.
     */
    fun shareAddresses(): List<String> {
        val ifaces = listIpv4()
        // Strictly exclude any VPN tunnels and cellular WAN uplinks
        val nonTunnel = ifaces
            .filterNot { isTunnel(it.name) }
            .filterNot { i -> WAN_IFACE_HINTS.any { i.name.startsWith(it, ignoreCase = true) } }

        val preferred = nonTunnel.filter {
            isUsbTetherInterface(it.name) || isWifiApInterface(it.name)
        }
        if (preferred.isNotEmpty()) return preferred.map { it.address }.distinct()
        return nonTunnel.map { it.address }.distinct()
    }

    fun isTunnel(name: String): Boolean {
        val base = baseName(name)
        return TUN_HINTS.any { base.startsWith(it) }
    }

    /**
     * Enumerate every network with an IPv4 address, whether or not it is the active one.
     *
     * This matters because Android marks only one network active: as soon as USB
     * tethering is switched on, the active network becomes the tether interface and a
     * VPN transport on the uplink stops showing up via `activeNetwork`.
     *
     * Each WAN carries its `NET_CAPABILITY_VALIDATED` flag so callers can tell "there is
     * a network" apart from "the OS confirmed that network reaches the internet" — the
     * distinction the tunnel-with-no-uplink warning depends on.
     */
    fun enumerate(context: Context): NetworkReport {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkReport(vpnActive = false, wanNetworks = emptyList())

        var vpn: Network? = null
        val wans = mutableListOf<WanNetwork>()

        try {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue

                val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val hasWan = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                // Detect the VPN before the IPv4 filter: some tunnels only expose an
                // IPv6 link address, and dropping them made the app claim "no VPN".
                if (hasVpn && vpn == null) vpn = network

                if (!hasWan) continue
                if (ipv4Of(cm.getLinkProperties(network)) == null) continue
                wans.add(
                    WanNetwork(
                        network = network,
                        validated = caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        )
                    )
                )
            }
        } catch (_: Exception) {
            // fall through with whatever we collected
        }

        return NetworkReport(vpnActive = vpn != null, wanNetworks = wans)
    }

    private fun ipv4Of(link: LinkProperties?): String? {
        val addrs = link?.linkAddresses ?: return null
        for (la in addrs) {
            val a = la.address
            if (a is Inet4Address && !a.isLoopbackAddress) {
                return a.hostAddress
            }
        }
        return null
    }

    data class WanNetwork(val network: Network, val validated: Boolean)

    data class NetworkReport(
        val vpnActive: Boolean,
        val wanNetworks: List<WanNetwork>
    ) {
        /**
         * The phone has an underlay that the OS itself confirmed can reach the internet.
         *
         * "A VPN network exists" is deliberately *not* treated as proof: a tunnel with a
         * dead underlay is precisely the state the UI has to warn about.
         */
        val hasValidatedWan: Boolean get() = wanNetworks.any { it.validated }
    }
}

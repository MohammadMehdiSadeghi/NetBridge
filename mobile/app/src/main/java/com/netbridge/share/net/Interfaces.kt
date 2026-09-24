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

    /** IPv4 addresses Android created for USB tethering — present iff tethering is on. */
    private val USB_IFACE_HINTS = listOf("rndis", "usb", "ncm", "ecm")

    private val WIFI_TETHER_HINTS = listOf("ap", "swlan", "softap", "wlan1")

    /** Modes that mean "this end is the one that dials out", not the one we plug the PC into. */
    private val WAN_IFACE_HINTS = listOf("rmnet", "ccmni", "pdp", "wwan", "ppp")

    private val TUN_HINTS = listOf("tun", "tap", "utun", "ppp")

    data class Iface(
        val name: String,
        val address: String,
        val prefixLength: Int,
        val flags: Int
    )

    /** Interface flags we care about. IFF_POINTOPOINT == 0x10. */
    private const val IFF_POINTOPOINT = 0x10

    fun isPointToPoint(flags: Int): Boolean = (flags and IFF_POINTOPOINT) != 0

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
                    if (ip.startsWith("169.254.")) continue
                    out.add(
                        Iface(
                            name = nif.name,
                            address = ip,
                            prefixLength = 24,
                            flags = nif.index
                        )
                    )
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

    /**
     * True when Android itself created a tethering interface. USB is detected purely
     * by interface presence (rndis/ncm), which works across OEM ROMs. Wi-Fi hotspot is
     * reported by ConnectivityManager, because only some ROMs expose an ap or swlan iface.
     */
    fun isTetherUp(context: Context): Boolean {
        val present = names()
        if (present.any { n -> USB_IFACE_HINTS.any { n.startsWith(it) } }) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.isActiveNetworkMetered && cm.activeNetwork == null
        } catch (_: Exception) {
            false
        }
    }

    fun isUsbTetherUp(): Boolean {
        val present = names()
        return present.any { n -> USB_IFACE_HINTS.any { n.startsWith(it) } }
    }

    /**
     * Addresses that a PC connected over tethering can actually reach. We prefer
     * downlink/tether interfaces and never expose the uplink (rmnet) or TUN addresses
     * to the control API, so the desktop app is never told to dial a useless IP.
     */
    fun shareAddresses(): List<String> {
        val ifaces = listIpv4()
        val preferred = ifaces.filter { i ->
            val n = i.name
            (WIFI_TETHER_HINTS + USB_IFACE_HINTS).any { n.startsWith(it) }
        }
        if (preferred.isNotEmpty()) return preferred.map { it.address }.distinct()
        return ifaces
            .filterNot { i -> WAN_IFACE_HINTS.any { i.name.startsWith(it) } }
            .filterNot { i -> TUN_HINTS.any { i.name.startsWith(it) } }
            .map { it.address }
            .distinct()
    }

    /** True when this interface is one Windows would consider the PC-facing link. */
    fun isShareLink(name: String): Boolean {
        val base = name.substringBefore(':')
        return (WIFI_TETHER_HINTS + USB_IFACE_HINTS).any { base.startsWith(it) }
    }

    fun isTunnel(name: String): Boolean {
        val base = name.substringBefore(':')
        return TUN_HINTS.any { base.startsWith(it) }
    }

    /**
     * The subnet a downstream client is on, derived from our own address on the
     * share link. 192.168.42.129/24 -> 192.168.42.0/24.
     */
    fun clientSubnet(ourAddress: String): String {
        val parts = ourAddress.split(".")
        if (parts.size != 4) return ourAddress
        return "${parts[0]}.${parts[1]}.${parts[2]}.0"
    }

    /** Given a client IP, guess the address the PC will use for us on that subnet. */
    fun gatewayFor(clientAddress: String, candidates: List<String>): String? {
        val subnet = clientSubnet(clientAddress)
        return candidates.firstOrNull { it.startsWith(subnet.substringBeforeLast(".")) }
    }

    /**
     * Enumerate every network with an IPv4 address, whether or not it is the active one.
     *
     * This matters because Android marks only one network active: as soon as USB
     * tethering is switched on, the active network becomes the tether interface and a
     * VPN transport on the uplink stops showing up via `activeNetwork`.
     */
    fun enumerate(context: Context): NetworkReport {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkReport(false, emptyList(), emptyList(), false)

        var vpn: Network? = null
        var vpnCaps: NetworkCapabilities? = null
        var vpnIsUplink = false
        val wans = mutableListOf<Network>()
        val all = mutableListOf<Network>()

        try {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue

                val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val hasWan = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                // Detect the VPN before the IPv4 filter: some tunnels only expose an
                // IPv6 link address, and dropping them made the app claim "no VPN".
                if (hasVpn && vpn == null) {
                    vpn = network
                    vpnCaps = caps
                    vpnIsUplink = hasWan
                }

                val link = cm.getLinkProperties(network)
                val ipv4 = ipv4Of(link)
                if (ipv4 == null) continue
                all.add(network)

                if (hasWan) wans.add(network)
            }
        } catch (_: Exception) {
            // fall through with whatever we collected
        }

        val validatedWan = wans.filter { n ->
            try {
                cm.getNetworkCapabilities(n)?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true
            } catch (_: Exception) {
                false
            }
        }

        return NetworkReport(
            vpnActive = vpn != null,
            vpnNetworks = listOfNotNull(vpn),
            wanNetworks = if (validatedWan.isNotEmpty()) validatedWan else wans,
            vpnIsUplink = vpnIsUplink
        )
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

    data class NetworkReport(
        val vpnActive: Boolean,
        val vpnNetworks: List<Network>,
        val wanNetworks: List<Network>,
        val vpnIsUplink: Boolean
    )

    /**
     * Does this device currently have a usable route to the internet on its own?
     *
     * A "VPN only" tunnel with no validated underlying network cannot carry traffic:
     * the tunnel exists but has nothing to send through. Reporting this separately is
     * what lets the app say "turn the VPN off" instead of silently sharing nothing.
     */
    fun hasInternetUplink(context: Context): Boolean {
        val report = enumerate(context)
        if (report.wanNetworks.isEmpty()) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return try {
            report.wanNetworks.any { n ->
                cm?.getNetworkCapabilities(n)?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true
            }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * The network we must bind outbound proxy sockets to so the traffic really leaves
     * through the phone's tunnel.
     *
     * Preference order:
     *  1. the VPN network whenever one exists — binding here forces traffic into the
     *     tunnel even if the tether link took over as default. Binding to a physical
     *     WAN while a VPN is up would *bypass* the tunnel (the original bug).
     *  2. a validated WAN network when there is no VPN (normal sharing).
     *  3. null, meaning "let Android choose".
     */
    fun preferredRoute(context: Context): Network? {
        val report = enumerate(context)
        report.vpnNetworks.firstOrNull()?.let { return it }
        report.wanNetworks.firstOrNull()?.let { return it }
        return null
    }
}

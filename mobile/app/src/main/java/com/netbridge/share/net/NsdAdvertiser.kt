package com.netbridge.share.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import com.netbridge.share.share.ShareManager

/**
 * Advertises the control API over mDNS/NSD.
 *
 * A single instance is shared process-wide. `MainActivity` is recreated on every
 * configuration change (rotation), and creating a fresh advertiser each time meant
 * `register()` ran again for a service the previous instance had already registered —
 * the `registered` flag is per-instance, so it could not tell. Android then rejected the
 * duplicate registration and the phone silently stopped being discoverable.
 */
class NsdAdvertiser private constructor(context: Context) : ShareManager.NsdHolder {

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Set once the framework confirms the service is live. */
    private var registered = false

    /** Set while a registration request is in flight — the callbacks are asynchronous. */
    private var registering = false

    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registering = false
            registered = true
        }

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            registering = false
            registered = false
        }

        override fun onServiceUnregistered(info: NsdServiceInfo) {
            registering = false
            registered = false
        }

        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            registering = false
            registered = false
        }
    }

    override fun register(port: Int) {
        if (registered || registering) return
        registering = true
        val suffix = try {
            (Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "netbridge").takeLast(4)
        } catch (_: Exception) {
            "netbridge"
        }
        val info = NsdServiceInfo().apply {
            serviceName = "NetBridge-$suffix"
            serviceType = "_netbridge._tcp"
            this.port = port
        }
        try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            registering = false
            registered = false
        }
    }

    override fun unregister() {
        if (!registered && !registering) return
        try {
            nsdManager.unregisterService(listener)
        } catch (_: Exception) {
        }
        registering = false
        registered = false
    }

    companion object {
        @Volatile
        private var instance: NsdAdvertiser? = null

        fun get(context: Context): NsdAdvertiser =
            instance ?: synchronized(this) {
                instance ?: NsdAdvertiser(context.applicationContext).also { instance = it }
            }
    }
}

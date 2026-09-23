package com.netbridge.share.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.netbridge.share.share.ShareManager

class NsdAdvertiser(context: Context) : ShareManager.NsdHolder {

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registered = false

    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registered = true
        }

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            registered = false
        }

        override fun onServiceUnregistered(info: NsdServiceInfo) {
            registered = false
        }

        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            registered = false
        }
    }

    override fun register(port: Int) {
        if (registered) return
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
            if (Build.VERSION.SDK_INT >= 34) {
                // nothing extra required for basic advertisement
            }
        }
        try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            registered = false
        }
    }

    override fun unregister() {
        if (!registered) return
        try {
            nsdManager.unregisterService(listener)
        } catch (_: Exception) {
        }
        registered = false
    }
}

package com.netbridge.share

import android.app.Application
import com.netbridge.share.data.SettingsStore
import com.netbridge.share.share.ShareManager

class NetBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)
        ShareManager.init(this, store)
    }
}

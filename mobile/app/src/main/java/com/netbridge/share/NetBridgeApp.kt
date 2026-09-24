package com.netbridge.share

import android.app.Application
import android.content.Context
import com.netbridge.share.data.AppLocale
import com.netbridge.share.data.SettingsStore
import com.netbridge.share.share.ShareManager

class NetBridgeApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)
        ShareManager.init(this, store)
    }
}

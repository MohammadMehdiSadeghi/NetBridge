package com.netbridge.share.share

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netbridge.share.data.SettingsStore

/**
 * Restarts sharing after a reboot when the user asked for it.
 *
 * The Application object normally initialises ShareManager, but it is not guaranteed
 * to have run before this receiver fires, so we re-initialise defensively. Calling
 * `init` twice is harmless: it only regenerates a code or token when one is missing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = SettingsStore(context)
        if (!store.autoShare) return
        ShareManager.init(context, store)
        ShareManager.startSharing(background = true)
    }
}

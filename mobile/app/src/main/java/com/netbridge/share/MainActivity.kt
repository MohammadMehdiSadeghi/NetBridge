package com.netbridge.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.netbridge.share.data.AppLocale
import com.netbridge.share.net.NsdAdvertiser
import com.netbridge.share.share.ShareManager
import com.netbridge.share.ui.AppNav
import com.netbridge.share.ui.theme.NetBridgeTheme

class MainActivity : ComponentActivity() {

    private var nsd: NsdAdvertiser? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // notification permission optional
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermission()
        nsd = NsdAdvertiser(this).also { ShareManager.bindNsd(it) }

        setContent {
            NetBridgeTheme {
                AppNav()
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            nsd?.unregister()
        }
        super.onDestroy()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

package com.netbridge.share.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.netbridge.share.MainActivity
import com.netbridge.share.R
import com.netbridge.share.share.ShareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShareService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ShareManager.stopSharing()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!ShareManager.state.value.sharing) {
            ShareManager.startSharing(background = false)
        }

        scope.launch {
            ShareManager.state.collect { state ->
                if (!state.sharing) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(
        state: ShareManager.State = ShareManager.state.value
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val vpn = if (state.vpnActive) getString(R.string.notif_vpn_on) else getString(R.string.notif_vpn_off)
        val body = buildString {
            append(getString(R.string.notif_proxy, state.httpPort)).append(" · ")
            append(getString(R.string.notif_clients, state.clients)).append(" · ")
            append(
                when (state.transport) {
                    com.netbridge.share.net.Transport.USB_TETHER -> getString(R.string.transport_usb)
                    com.netbridge.share.net.Transport.WIFI_HOTSPOT -> getString(R.string.transport_hotspot)
                    com.netbridge.share.net.Transport.NONE -> getString(R.string.transport_none)
                }
            ).append(" · ")
            append(vpn)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(body)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.netbridge.share.STOP"
        private const val CHANNEL_ID = "netbridge_share"
        private const val NOTIFICATION_ID = 42
    }
}

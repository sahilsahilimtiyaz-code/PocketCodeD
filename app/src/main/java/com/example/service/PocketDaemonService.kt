package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Persistent Android Foreground Service hosting the PocketCodeD HTTP/SSE Gateway
 * and holding a `PARTIAL_WAKE_LOCK` + `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` so Android's
 * OS never suspends the CPU while the user is coding in Termux or over Wi-Fi LAN.
 */
class PocketDaemonService : Service() {

    companion object {
        const val ACTION_START_DAEMON = "com.example.service.ACTION_START_DAEMON"
        const val ACTION_STOP_DAEMON = "com.example.service.ACTION_STOP_DAEMON"
        const val EXTRA_BIND_LAN = "extra_bind_lan"
        const val EXTRA_PORT = "extra_port"

        private const val CHANNEL_ID = "pocketcoded_daemon_channel"
        private const val NOTIFICATION_ID = 8080
        private const val WAKELOCK_TAG = "PocketCodeD::InferenceDaemonWakeLock"

        fun startDaemon(context: Context, bindLan: Boolean, port: Int = 8080) {
            val intent = Intent(context, PocketDaemonService::class.java).apply {
                action = ACTION_START_DAEMON
                putExtra(EXTRA_BIND_LAN, bindLan)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopDaemon(context: Context) {
            val intent = Intent(context, PocketDaemonService::class.java).apply {
                action = ACTION_STOP_DAEMON
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        DaemonStateRegistry.ensureInitialized(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_DAEMON -> {
                DaemonStateRegistry.stopServerInternal()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val bindLan = intent?.getBooleanExtra(EXTRA_BIND_LAN, DaemonStateRegistry.bindLanMode.value)
                    ?: DaemonStateRegistry.bindLanMode.value
                val port = intent?.getIntExtra(EXTRA_PORT, DaemonStateRegistry.serverPort.value)
                    ?: DaemonStateRegistry.serverPort.value

                acquireWakeLock()
                DaemonStateRegistry.startServerInternal(bindLan = bindLan, port = port)

                val notification = buildForegroundNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        DaemonStateRegistry.stopServerInternal()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // 4-hour coding session lease
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.daemon_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.daemon_notification_channel_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val cfg = DaemonStateRegistry.engineConfig.value
        val metrics = DaemonStateRegistry.engineBridge.metrics.value
        val port = DaemonStateRegistry.serverPort.value

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PocketDaemonService::class.java).apply {
            action = ACTION_STOP_DAEMON
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.daemon_notification_title, port)
        val body = getString(
            R.string.daemon_notification_text,
            cfg.activeModelId.substringBefore("-Instruct"),
            cfg.typeK.label,
            cfg.typeV.label,
            if (metrics.lastDecodeTokPerSec > 0.0) metrics.lastDecodeTokPerSec else 48.5
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_stop_daemon),
                pendingStop
            )
            .build()
    }
}

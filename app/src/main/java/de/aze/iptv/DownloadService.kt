package de.aze.iptv

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Hält die App am Leben, solange Filme/Folgen geladen werden (auch mit der Home-Taste im Hintergrund oder bei
 * ausgeschaltetem Bildschirm). Die eigentliche Arbeit macht [de.aze.iptv.data.Downloader].
 */
class DownloadService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val n = build(this, "Download startet …", -1)
        runCatching {
            ServiceCompat.startForeground(
                this, NOTE_ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        if (wake == null) runCatching {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aze:download").apply { setReferenceCounted(false); acquire(6 * 3600_000L) }
        }
        if (wifi == null) runCatching {
            @Suppress("DEPRECATION")
            wifi = (applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "aze:download").apply { setReferenceCounted(false); acquire() }
        }
        return START_NOT_STICKY
    }

    /** Android 15: Daten-Dienste dürfen höchstens 6 h am Tag laufen – dann sauber beenden (Download läuft ohne Schutz weiter) */
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        runCatching { wake?.release() }; wake = null
        runCatching { wifi?.release() }; wifi = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTE_ID = 4711
        private const val ACTION_STOP = "stop"
        @Volatile private var lastNote = 0L

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
        }

        /** Fortschritt in der Benachrichtigung (höchstens alle 2 s) */
        fun progress(ctx: Context, title: String, pct: Int) {
            val now = System.currentTimeMillis()
            if (now - lastNote < 2_000) return
            lastNote = now
            runCatching {
                (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, build(ctx, title, pct))
            }
        }

        private fun build(ctx: Context, title: String, pct: Int): Notification {
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (pct >= 0) "Lädt: $pct %" else "Lädt …")
                .setContentText(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setProgress(100, pct.coerceAtLeast(0), pct < 0)
                .setContentIntent(open)
                .build()
        }
    }
}

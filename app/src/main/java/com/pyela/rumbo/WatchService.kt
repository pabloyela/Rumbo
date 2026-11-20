package com.pyela.rumbo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Vigilante: corre en segundo plano, y si el GPS pasa demasiado tiempo sin posición
 * lo reinicia solo. Anota en el registro cuándo se pierde y se recupera la señal.
 */
class WatchService : Service() {

    private lateinit var w: GpsWatcher
    private val h = Handler(Looper.getMainLooper())
    private var lastReset = 0L
    private var consecutive = 0
    private var resets = 0
    private var lostSince = 0L
    private var waitingRecovery = false
    private var lastText = ""

    private val tick = object : Runnable {
        override fun run() {
            check()
            h.postDelayed(this, 5000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vigilante GPS", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            val n = build("Iniciando…")
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, n)
        } catch (e: Exception) {
            Store.log(this, "No se pudo iniciar el vigilante: ${e.message}")
            stopSelf()
            return
        }
        w = GpsWatcher(this)
        if (!w.start()) {
            Store.log(this, "Vigilante: falta permiso de ubicación")
            stopSelf()
            return
        }
        Store.log(this, "Vigilante iniciado (espera ${Store.threshold(this)} s sin señal)")
        h.postDelayed(tick, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        h.removeCallbacks(tick)
        if (::w.isInitialized) {
            w.stop()
            Store.log(this, "Vigilante detenido")
        }
        super.onDestroy()
    }

    private fun check() {
        val now = SystemClock.elapsedRealtime()
        if (!w.gpsEnabled()) {
            notifyText("La ubicación está apagada en los ajustes")
            return
        }
        val hadFix = w.lastFixElapsed > 0
        val gap = now - (if (hadFix) w.lastFixElapsed else w.startedElapsed)

        // Señal perdida / recuperada (para el registro)
        if (hadFix && gap > 10_000 && lostSince == 0L) {
            lostSince = w.lastFixElapsed
            Store.log(this, "Señal perdida (sats ${w.used}/${w.visible}, señal ${w.topSignal().toInt()} dB)")
        }
        if (hadFix && gap <= 3_000 && lostSince != 0L) {
            val secs = (now - lostSince) / 1000
            Store.log(this, if (waitingRecovery) "Señal recuperada tras reinicio (${secs} s sin señal)"
                            else "Señal recuperada sola (${secs} s sin señal)")
            lostSince = 0L
            waitingRecovery = false
            consecutive = 0
        }

        // ¿Toca reiniciar? La espera entre reinicios crece si no sirve: 2, 4, 8, 10 min.
        val limit = Store.threshold(this) * 1000L
        val effectiveLimit = if (hadFix) limit else maxOf(limit, 180_000L)
        val cooldown = minOf(120_000L shl minOf(consecutive, 3), 600_000L)
        if (gap > effectiveLimit && (lastReset == 0L || now - lastReset > cooldown)) {
            val r = GpsTools.softReset(this)
            w.restartUpdates()
            lastReset = now
            consecutive++
            resets++
            waitingRecovery = true
            Store.log(this, "Reinicio automático #$resets tras ${gap / 1000} s sin señal " +
                "(sats ${w.used}/${w.visible}). ${r.lines().firstOrNull() ?: ""}")
        }

        notifyText(
            if (hadFix && gap < 5_000) "Con señal · satélites ${w.used}/${w.visible} · reinicios: $resets"
            else "Sin señal hace ${gap / 1000} s · satélites ${w.used}/${w.visible} · reinicios: $resets"
        )
    }

    private fun notifyText(t: String) {
        if (t == lastText) return
        lastText = t
        getSystemService(NotificationManager::class.java).notify(ID, build(t))
    }

    private fun build(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Rumbo vigilando el GPS")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val CHANNEL = "vigilante"
        const val ID = 7

        fun start(c: Context) {
            try { c.startForegroundService(Intent(c, WatchService::class.java)) } catch (_: Exception) {}
        }
        fun stop(c: Context) {
            c.stopService(Intent(c, WatchService::class.java))
        }
    }
}

/** Si el vigilante estaba encendido, lo vuelve a arrancar cuando el radio enciende. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Store.watchOn(context)) WatchService.start(context)
    }
}

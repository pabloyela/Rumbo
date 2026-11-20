package com.pyela.rumbo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Escucha el GPS: posiciones y estado de satélites. Lo usan la pantalla y el vigilante. */
class GpsWatcher(private val ctx: Context) {

    data class Sat(val cons: Char, val svid: Int, val cn0: Float, val used: Boolean)

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile var lastFixElapsed = 0L
    @Volatile var lastLocation: Location? = null
    @Volatile var startedElapsed = 0L
    @Volatile var used = 0
    @Volatile var visible = 0
    @Volatile var sats: List<Sat> = emptyList()
    private var running = false

    // Se implementan los 4 métodos: en Android 10 no tienen implementación por defecto.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            lastFixElapsed = SystemClock.elapsedRealtime()
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            used = 0; visible = 0; sats = emptyList()
        }
    }

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val list = ArrayList<Sat>(status.satelliteCount)
            var u = 0
            for (i in 0 until status.satelliteCount) {
                val isUsed = status.usedInFix(i)
                if (isUsed) u++
                list.add(Sat(consChar(status.getConstellationType(i)), status.getSvid(i), status.getCn0DbHz(i), isUsed))
            }
            list.sortByDescending { it.cn0 }
            used = u
            visible = list.size
            sats = list
        }
        override fun onStopped() {
            used = 0; visible = 0; sats = emptyList()
        }
    }

    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(): Boolean = try {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) { false }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        return try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            lm.registerGnssStatusCallback(gnss, handler)
            startedElapsed = SystemClock.elapsedRealtime()
            running = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        if (!running) return
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
        try { lm.unregisterGnssStatusCallback(gnss) } catch (_: Exception) {}
        running = false
    }

    /** Suelta el GPS y lo vuelve a pedir, para que el chip arranque de nuevo. */
    fun restartUpdates() {
        stop()
        handler.postDelayed({ start() }, 1500)
    }

    /** Promedio de C/N0 de los 4 satélites más fuertes (0 si no hay). */
    fun topSignal(): Float {
        val top = sats.take(4)
        if (top.isEmpty()) return 0f
        return top.map { it.cn0 }.average().toFloat()
    }

    companion object {
        fun consChar(type: Int): Char = when (type) {
            GnssStatus.CONSTELLATION_GPS -> 'G'
            GnssStatus.CONSTELLATION_GLONASS -> 'R'
            GnssStatus.CONSTELLATION_BEIDOU -> 'C'
            GnssStatus.CONSTELLATION_GALILEO -> 'E'
            GnssStatus.CONSTELLATION_QZSS -> 'J'
            GnssStatus.CONSTELLATION_SBAS -> 'S'
            else -> '?'
        }
    }
}

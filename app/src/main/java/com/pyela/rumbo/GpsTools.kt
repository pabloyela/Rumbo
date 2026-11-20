package com.pyela.rumbo

import android.content.Context
import android.location.LocationManager
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object GpsTools {

    /**
     * Reinicio "suave": borra los datos de asistencia del GPS (efemérides, almanaque,
     * última posición, hora) y pide que vuelva a inyectar hora y datos de asistencia.
     * Obliga al chip a hacer un arranque en frío. No necesita root.
     */
    fun softReset(ctx: Context): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val out = StringBuilder()
        fun cmd(name: String, label: String) {
            val ok = try {
                lm.sendExtraCommand(LocationManager.GPS_PROVIDER, name, null)
            } catch (e: Exception) { false }
            out.append(label).append(if (ok) ": ok" else ": no soportado").append('\n')
        }
        cmd("delete_aiding_data", "Borrar datos de asistencia")
        cmd("force_time_injection", "Inyectar hora")
        if (Build.VERSION.SDK_INT >= 31) cmd("force_psds_injection", "Descargar datos A-GPS")
        else cmd("force_xtra_injection", "Descargar datos A-GPS")
        return out.toString().trim()
    }

    fun rootAvailable(): Boolean = listOf(
        "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/data/adb/magisk"
    ).any { File(it).exists() }

    /**
     * Reinicio "profundo" con root: apaga la ubicación, detiene el servicio del chip GPS
     * (Android lo vuelve a levantar solo) y vuelve a encender la ubicación.
     * Bloquea hasta ~25 s: llamar fuera del hilo principal.
     */
    fun deepReset(): String {
        val script = """
            settings put secure location_mode 0
            sleep 2
            ps -A -o PID,NAME | while read p n; do
              case "${'$'}n" in
                com.*|*rumbo*) ;;
                *gnss*|*Gnss*|*GNSS*|*gps*|*GPS*|mnld|lbs_server|locationd) echo "Detenido: ${'$'}n"; kill "${'$'}p" ;;
              esac
            done
            sleep 3
            settings put secure location_mode 3
            echo "Ubicación encendida de nuevo"
        """.trimIndent()
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val finished = proc.waitFor(25, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroy()
                return "El root no respondió (¿se negó el permiso?)"
            }
            val text = proc.inputStream.bufferedReader().readText().trim()
            val err = proc.errorStream.bufferedReader().readText().trim()
            when {
                proc.exitValue() != 0 && text.isEmpty() -> "Falló: ${err.ifEmpty { "sin permiso de root" }}"
                text.isEmpty() -> "Hecho"
                else -> text
            }
        } catch (e: Exception) {
            "No se pudo usar root: ${e.message}"
        }
    }
}

/** Preferencias y registro de eventos. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("rumbo", Context.MODE_PRIVATE)

    fun watchOn(c: Context) = p(c).getBoolean("watch", false)
    fun setWatch(c: Context, v: Boolean) = p(c).edit().putBoolean("watch", v).apply()

    fun threshold(c: Context) = p(c).getInt("thr", 60)
    fun setThreshold(c: Context, v: Int) = p(c).edit().putInt("thr", v).apply()

    @Synchronized
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        val old = p(c).getString("log", "") ?: ""
        val lines = (listOf("$t  $msg") + old.lines().filter { it.isNotBlank() }).take(100)
        p(c).edit().putString("log", lines.joinToString("\n")).apply()
    }

    fun logText(c: Context) = p(c).getString("log", "") ?: ""
    fun clearLog(c: Context) = p(c).edit().remove("log").apply()
}

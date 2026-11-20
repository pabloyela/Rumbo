package com.pyela.rumbo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0B1620")
    private val CARD = Color.parseColor("#13222F")
    private val TEXT = Color.parseColor("#E8EEF2")
    private val DIM = Color.parseColor("#8DA2B2")
    private val GREEN = Color.parseColor("#4FD1A5")
    private val AMBER = Color.parseColor("#F2B84B")
    private val RED = Color.parseColor("#F06A6A")
    private val BLUE = Color.parseColor("#3A7BD5")

    private lateinit var w: GpsWatcher
    private val h = Handler(Looper.getMainLooper())

    private lateinit var tvState: TextView
    private lateinit var tvSats: TextView
    private lateinit var tvPos: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvList: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnWatch: Button
    private lateinit var btnThr: Button

    private val tick = object : Runnable {
        override fun run() {
            render()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        w = GpsWatcher(this)
        buildUi()
        askPermissions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
        render()
    }

    override fun onResume() {
        super.onResume()
        w.start()
        h.post(tick)
    }

    override fun onPause() {
        h.removeCallbacks(tick)
        w.stop()
        super.onPause()
    }

    private fun askPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!w.hasPermission()) requestPermissions(perms.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (w.hasPermission()) {
            w.start()
            if (Store.watchOn(this)) WatchService.start(this)
        }
    }

    // ---------------- Interfaz ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(14).toFloat() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun label(size: Float, color: Int = TEXT, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun button(text: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 18f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
        minHeight = dp(64)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val landscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels

        // Columna izquierda: estado
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val c1 = card()
        tvState = label(30f, bold = true)
        tvSats = label(20f)
        tvPos = label(17f, DIM)
        tvClock = label(16f, DIM)
        c1.addView(tvState); c1.addView(tvSats); c1.addView(tvPos); c1.addView(tvClock)
        left.addView(c1)
        val c2 = card()
        c2.addView(label(15f, DIM).apply { text = "Satélites (más fuertes primero)" })
        tvList = label(15f).apply { typeface = Typeface.MONOSPACE }
        c2.addView(tvList)
        left.addView(c2)

        // Columna derecha: acciones y registro
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(button("Reiniciar GPS", BLUE) { manualReset() })
        if (GpsTools.rootAvailable()) {
            right.addView(button("Reinicio profundo (root)", Color.parseColor("#7A4FD1")) { confirmDeep() })
        }
        btnWatch = button("", CARD) { toggleWatch() }
        right.addView(btnWatch)
        btnThr = button("", CARD) { cycleThreshold() }
        right.addView(btnThr)
        right.addView(button("Ajustes de ubicación del radio", CARD) {
            try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {
                Toast.makeText(this, "El radio no tiene esa pantalla", Toast.LENGTH_SHORT).show()
            }
        })
        val c3 = card()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(label(15f, DIM).apply {
            text = "Registro"
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = "Borrar"
            textSize = 15f
            setTextColor(AMBER)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener { Store.clearLog(this@MainActivity); render() }
        })
        c3.addView(head)
        tvLog = label(13f, DIM).apply { typeface = Typeface.MONOSPACE }
        c3.addView(tvLog)
        right.addView(c3)

        val root: View = if (landscape) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(ScrollView(this@MainActivity).apply {
                    addView(left)
                    layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { rightMargin = dp(16) }
                })
                addView(ScrollView(this@MainActivity).apply {
                    addView(right)
                    layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
                })
            }
        } else {
            ScrollView(this).apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    addView(left); addView(right)
                })
            }
        }
        root.setBackgroundColor(BG)
        setContentView(root)
    }

    private fun render() {
        val now = SystemClock.elapsedRealtime()
        val enabled = w.gpsEnabled()
        val fix = w.lastFixElapsed
        val age = if (fix > 0) (now - fix) / 1000 else -1

        when {
            !w.hasPermission() -> { tvState.text = "Falta permiso de ubicación"; tvState.setTextColor(RED) }
            !enabled -> { tvState.text = "GPS APAGADO"; tvState.setTextColor(RED) }
            age in 0L..4L -> { tvState.text = "CON SEÑAL"; tvState.setTextColor(GREEN) }
            age >= 5L -> { tvState.text = "SIN SEÑAL hace ${fmtSecs(age)}"; tvState.setTextColor(if (age > 30) RED else AMBER) }
            else -> { tvState.text = "BUSCANDO…"; tvState.setTextColor(AMBER) }
        }

        val sig = w.topSignal()
        val sigText = when {
            w.visible == 0 -> "no se ve ningún satélite"
            sig >= 30 -> "señal buena (${sig.toInt()} dB)"
            sig >= 20 -> "señal regular (${sig.toInt()} dB)"
            else -> "señal débil (${sig.toInt()} dB)"
        }
        tvSats.text = "Satélites: ${w.used} en uso / ${w.visible} visibles\n$sigText"

        val loc = w.lastLocation
        tvPos.text = if (loc == null) "Sin posición todavía" else String.format(
            Locale.US, "%.5f, %.5f  ±%.0f m  ·  %.0f km/h",
            loc.latitude, loc.longitude, loc.accuracy, loc.speed * 3.6f
        )

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        var clock = "Hora del radio: ${sdf.format(Date())}"
        if (loc != null && loc.time > 0) {
            val diffMin = abs(System.currentTimeMillis() - loc.time) / 60_000
            if (age in 0L..10L && diffMin >= 2) clock += "\n⚠ El reloj del radio está desfasado $diffMin min respecto al GPS"
        }
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (year < 2025) clock += "\n⚠ La fecha del radio está mal; eso le cuesta mucho al GPS"
        tvClock.text = clock

        tvList.text = if (w.sats.isEmpty()) "—" else w.sats.take(14).joinToString("\n") { s ->
            val bars = (s.cn0 / 5).toInt().coerceIn(0, 10)
            String.format(Locale.US, "%c%-3d %-10s %2.0f %s",
                s.cons, s.svid, "■".repeat(bars), s.cn0, if (s.used) "✓" else "")
        }

        val on = Store.watchOn(this)
        btnWatch.text = if (on) "Reinicio automático: ENCENDIDO" else "Reinicio automático: apagado"
        (btnWatch.background as GradientDrawable).setColor(if (on) Color.parseColor("#1F7A5C") else CARD)
        btnThr.text = "Reiniciar tras ${Store.threshold(this)} s sin señal"
        tvLog.text = Store.logText(this).lines().take(25).joinToString("\n").ifEmpty { "Sin eventos" }
    }

    private fun fmtSecs(s: Long) = if (s < 60) "$s s" else "${s / 60} min ${s % 60} s"

    // ---------------- Acciones ----------------

    private fun manualReset() {
        val r = GpsTools.softReset(this)
        w.restartUpdates()
        Store.log(this, "Reinicio manual (sats ${w.used}/${w.visible}). ${r.lines().firstOrNull() ?: ""}")
        AlertDialog.Builder(this)
            .setTitle("GPS reiniciado")
            .setMessage("$r\n\nUn arranque en frío tarda de 30 s a 2 min en volver a fijar posición, mejor con el carro a cielo abierto.")
            .setPositiveButton("Ok", null)
            .show()
        render()
    }

    private fun confirmDeep() {
        AlertDialog.Builder(this)
            .setTitle("Reinicio profundo")
            .setMessage("Apaga la ubicación, detiene el servicio del chip GPS y lo vuelve a encender. " +
                "Úsalo cuando el reinicio normal no funcione. Si el GPS queda peor, reinicia el radio.")
            .setPositiveButton("Hacerlo") { _, _ ->
                Toast.makeText(this, "Reiniciando… (unos 10 s)", Toast.LENGTH_SHORT).show()
                Thread {
                    val r = GpsTools.deepReset()
                    Store.log(this, "Reinicio profundo: ${r.replace('\n', ' ')}")
                    runOnUiThread {
                        w.restartUpdates()
                        AlertDialog.Builder(this).setTitle("Reinicio profundo").setMessage(r)
                            .setPositiveButton("Ok", null).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleWatch() {
        if (!w.hasPermission()) { askPermissions(); return }
        val on = !Store.watchOn(this)
        Store.setWatch(this, on)
        if (on) WatchService.start(this) else WatchService.stop(this)
        render()
    }

    private fun cycleThreshold() {
        val opts = listOf(30, 60, 90, 120, 180)
        val cur = Store.threshold(this)
        val next = opts[(opts.indexOf(cur).coerceAtLeast(0) + 1) % opts.size]
        Store.setThreshold(this, next)
        render()
    }
}

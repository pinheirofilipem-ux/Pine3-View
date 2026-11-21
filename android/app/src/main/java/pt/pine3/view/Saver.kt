package pt.pine3.view

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Ecrã de descanso (.saver do painel web): relógio grande e escurecido que deriva devagar. Um toque fecha. */
class SaverView(private val kit: Kit, private val h24: Boolean, private val onClose: () -> Unit) : FrameLayout(kit.ctx) {
    private val clock = kit.text("", kit.clamp(90f, kit.vw(18f), 220f), Color.argb(235, 255, 255, 255), 200, display = true)
    private val date = kit.text("", kit.clamp(18f, kit.vw(2.4f), 28f), Color.argb(153, 255, 255, 255))
    private val meta = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER }
    private val inner = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private var drift: ValueAnimator? = null
    private val tick = object : Runnable { override fun run() { refresh(); postDelayed(this, 15_000) } }

    init {
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(Color.parseColor("#22121F"), Color.parseColor("#050305"))
            gradientRadius = kit.dp(900).toFloat(); setGradientCenter(0.5f, 0.4f)
        }
        isClickable = true
        setOnClickListener { onClose() }
        inner.addView(clock); inner.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(6) })
        inner.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(26) })
        addView(inner, LayoutParams(-2, -2, Gravity.CENTER))
        refresh()
    }

    private fun refresh() {
        val now = java.util.Date()
        clock.text = java.text.SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Tr.locale).format(now)
        date.text = java.text.SimpleDateFormat("EEEE d MMMM", Tr.locale).format(now).replaceFirstChar { it.uppercase() }
        meta.removeAllViews()
        val rooms = Ha.home?.rooms.orEmpty()
        val lit = rooms.flatMap { r -> Fmt.switchables(r.devices.filter { !it.hidden }.map { it.id }) }.count { Fmt.isOn(it) }
        fun item(icon: String, text: String) {
            val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(kit.icon(icon, Color.argb(140, 255, 255, 255), 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(8) })
            row.addView(kit.text(text, 18f, Color.argb(140, 255, 255, 255)))
            meta.addView(row, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(13); marginEnd = kit.dp(13) })
        }
        Ha.home?.weather?.let { Ha.states[it] }?.let { w ->
            val t = w.attrs.optDouble("temperature", Double.NaN)
            if (!t.isNaN()) item("mdi:weather-partly-cloudy", "${Math.round(t)}°")
        }
        if (lit > 0) item("mdi:lightbulb-on-outline", Tr.pl(lit, "luz acesa", "luzes acesas", "light on", "lights on"))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, 15_000)
        // deriva lenta para não queimar o painel
        drift = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = LinearInterpolator()
            addUpdateListener { val f = it.animatedValue as Float; inner.translationX = (-0.04f + 0.07f * f) * kit.wdp * kit.density; inner.translationY = (-0.03f + 0.07f * f) * kit.hdp * kit.density }
            start()
        }
    }

    override fun onDetachedFromWindow() { removeCallbacks(tick); drift?.cancel(); super.onDetachedFromWindow() }
}

package pt.pine3.view

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View

/**
 * Barra horizontal das folhas compactas (luminosidade, volume): arrastar ou tocar escolhe o valor, que só é enviado ao
 * levantar o dedo. O texto e o ícone mudam de cor onde a barra está cheia, para se lerem sempre.
 */
@SuppressLint("ViewConstructor")
class HBar(
    private val kit: Kit, private var pct: Int, icon: String, private val label: String,
    private val onDrag: (Boolean) -> Unit, private val onSet: (Int) -> Unit,
) : View(kit.ctx) {
    private val r = kit.dp(18).toFloat()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(36, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = kit.cream }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = kit.dp(16).toFloat(); isFakeBoldText = true }
    private val ic = kit.mdi(icon).mutate()
    private val clip = Path()
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        box.set(0f, 0f, w, h)
        clip.reset(); clip.addRoundRect(box, r, r, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        c.drawRect(box, track)
        val fx = w * pct / 100f
        c.drawRect(0f, 0f, fx, h, fill)
        // the labels twice: dark over the filled part, white over the rest
        for (dark in listOf(true, false)) {
            c.save()
            if (dark) c.clipRect(0f, 0f, fx, h) else c.clipRect(fx, 0f, w, h)
            val col = if (dark) kit.ink else Color.WHITE
            val s = kit.dp(20); val x0 = kit.dp(16); val y0 = ((h - s) / 2).toInt()
            ic.setBounds(x0, y0, x0 + s, y0 + s); ic.colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN); ic.draw(c)
            txt.color = col
            val base = h / 2 - (txt.descent() + txt.ascent()) / 2
            c.drawText(label, (x0 + s + kit.dp(10)).toFloat(), base, txt)
            val v = "$pct%"; c.drawText(v, w - kit.dp(18) - txt.measureText(v), base, txt)
            c.restore()
        }
        c.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val v = ((e.x / width.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); onDrag(true); pct = v; invalidate() }
            MotionEvent.ACTION_MOVE -> { pct = v; invalidate() }
            MotionEvent.ACTION_UP -> { pct = v; invalidate(); onDrag(false); onSet(v) }
            MotionEvent.ACTION_CANCEL -> onDrag(false)
        }
        return true
    }
}

/** Roda de cores (tom à volta, saturação para o centro) para escolher uma cor livre; devolve hs_color ao levantar o dedo. */
@SuppressLint("ViewConstructor")
class ColorWheel(private val kit: Kit, start: Int, private val onPick: (hue: Float, sat: Float) -> Unit) : View(kit.ctx) {
    private val wheel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(3).toFloat(); color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hue: Float; private var sat: Float

    init {
        val hsv = FloatArray(3); Color.colorToHSV(start, hsv); hue = hsv[0]; sat = hsv[1]
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f; val cy = h / 2f; val rad = minOf(w, h) / 2f
        val cols = IntArray(13) { Color.HSVToColor(floatArrayOf((it * 30f) % 360f, 1f, 1f)) }
        val sweep = SweepGradient(cx, cy, cols, null)
        val radial = RadialGradient(cx, cy, rad, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        wheel.shader = ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val rad = minOf(width, height) / 2f
        c.drawCircle(cx, cy, rad, wheel)
        val a = Math.toRadians(hue.toDouble()); val d = sat * (rad - kit.dp(12))
        val x = cx + (Math.cos(a) * d).toFloat(); val y = cy + (Math.sin(a) * d).toFloat()
        dot.color = Color.HSVToColor(floatArrayOf(hue, sat, 1f))
        c.drawCircle(x, y, kit.dp(12).toFloat(), dot); c.drawCircle(x, y, kit.dp(12).toFloat(), ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f; val rad = minOf(width, height) / 2f - kit.dp(12)
        val dx = e.x - cx; val dy = e.y - cy
        hue = ((Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) + 360) % 360).toFloat()
        sat = (Math.hypot(dx.toDouble(), dy.toDouble()) / rad).toFloat().coerceIn(0f, 1f)
        invalidate()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP -> onPick(hue, sat * 100f)
        }
        return true
    }
}

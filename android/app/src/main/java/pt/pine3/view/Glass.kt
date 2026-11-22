package pt.pine3.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Estilo do vidro escolhido nas definições (Aparência): 0 = atual, 1 = suave, 2 = Liquid Glass. */
object Glass {
    @Volatile var level = 0
}

/**
 * Superfície de vidro desenhada à mão: enchimento em gradiente, faixa de brilho no topo, aro de luz (mais claro em cima e à esquerda)
 * e, no nível 2, aro duplo, luz de canto e brilho diagonal. [kind]: 0 vidro claro, 1 cartão ligado (creme), 2 superfície escura (dock, janelas).
 */
class GlassDrawable(private val kit: Kit, private val radiusPx: Float, private val kind: Int, private val base: Int = Color.WHITE, private val level: Int = Glass.level) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val clip = Path()

    private fun a(c: Int, alpha: Float) = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun mix(c: Int, w: Int, t: Float) = Color.rgb((Color.red(c) + (Color.red(w) - Color.red(c)) * t).toInt(), (Color.green(c) + (Color.green(w) - Color.green(c)) * t).toInt(), (Color.blue(c) + (Color.blue(w) - Color.blue(c)) * t).toInt())

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val rad = minOf(radiusPx, minOf(w, h) / 2f)
        val one = kit.dp(1).toFloat()
        val light = kit.light
        val strong = level >= 2
        c.save(); c.translate(b.left.toFloat(), b.top.toFloat())
        r.set(0f, 0f, w, h)
        clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW)
        c.save(); c.clipPath(clip)

        // enchimento
        val top: Int; val bot: Int
        when (kind) {
            1 -> { top = mix(base, Color.WHITE, 0.55f); bot = mix(base, Color.rgb(200, 180, 140), 0.18f) }
            2 -> { top = if (light) a(Color.WHITE, .62f) else a(Color.rgb(70, 52, 88), if (strong) .62f else .78f); bot = if (light) a(Color.WHITE, .34f) else a(Color.rgb(28, 20, 36), if (strong) .78f else .9f) }
            else -> { top = a(if (light) Color.WHITE else Color.WHITE, if (light) .72f else if (strong) .22f else .26f); bot = a(Color.WHITE, if (light) .38f else .05f) }
        }
        p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, 0f, w * 0.6f, h, top, bot, Shader.TileMode.CLAMP); c.drawRect(r, p)

        // luz de canto (nível 2)
        if (strong) {
            p.shader = RadialGradient(w * 0.05f, h * 0.02f, maxOf(w, h) * 0.75f, intArrayOf(a(Color.WHITE, if (kind == 1) .8f else .34f), Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            // brilho diagonal
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.TRANSPARENT, a(Color.WHITE, .10f), Color.TRANSPARENT), floatArrayOf(0.38f, 0.5f, 0.62f), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
        }
        // faixa de brilho no topo
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, a(Color.WHITE, if (kind == 1) .5f else if (strong) .30f else .22f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.5f, p)
        c.restore()

        // aro de luz: mais claro em cima e à esquerda
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(a(Color.WHITE, if (strong) .85f else .6f), a(Color.WHITE, .08f), a(Color.WHITE, if (strong) .35f else .18f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE; p.strokeWidth = one * if (strong) 1.6f else 1.2f
        r.set(p.strokeWidth / 2, p.strokeWidth / 2, w - p.strokeWidth / 2, h - p.strokeWidth / 2)
        c.drawRoundRect(r, rad, rad, p)
        if (strong) {
            // aro interior suave e linha escura por fora: dá espessura ao vidro
            p.shader = null; p.strokeWidth = one; p.color = a(Color.WHITE, .22f)
            r.set(one * 2.2f, one * 2.2f, w - one * 2.2f, h - one * 2.2f); c.drawRoundRect(r, maxOf(0f, rad - one * 2f), maxOf(0f, rad - one * 2f), p)
            p.color = a(Color.rgb(20, 10, 30), if (light) .12f else .35f); p.strokeWidth = one
            r.set(one / 2, one / 2, w - one / 2, h - one / 2); c.drawRoundRect(r, rad, rad, p)
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

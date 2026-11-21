package pt.pine3.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Fundo "ameixa" do Pine3 View: base em gradiente + três brilhos radiais. Desenhado uma vez (a view é estática). */
class PlumBackground : Drawable() {
    private val base = Paint()
    private val glow = Paint()
    private var w = 0f
    private var h = 0f

    override fun onBoundsChange(b: android.graphics.Rect) {
        w = b.width().toFloat(); h = b.height().toFloat()
        base.shader = android.graphics.LinearGradient(0f, 0f, w * 0.34f, h, Color.parseColor("#3A1D36"), Color.parseColor("#1D0F1F"), Shader.TileMode.CLAMP)
    }

    private fun glow(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        glow.shader = RadialGradient(cx, cy, rx, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, ry / rx, cx, cy) // elipse
        canvas.drawRect(0f, cy - rx, w, cy + rx, glow)
        canvas.restore()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, base)
        glow(canvas, w * 0.55f, h * 0.35f, w * 0.54f, h * 0.42f, Color.parseColor("#7B4467"))
        glow(canvas, 0f, 0f, w * 0.42f, h * 0.36f, Color.parseColor("#4A2440"))
        glow(canvas, w, h, w * 0.48f, h * 0.42f, Color.parseColor("#2C1630"))
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/** Ferramentas de interface: dp, fontes Outfit/Figtree, ícones MDI embutidos, vidro e cartões. */
class Kit(val ctx: Context) {
    val density = ctx.resources.displayMetrics.density
    var cream = Color.parseColor("#F5EFE1")
    var accent = Color.parseColor("#F6B83F")
    val ink = Color.parseColor("#2B1C2E")
    val muted = Color.argb(166, 255, 255, 255)
    val white = Color.WHITE

    fun dp(v: Number) = (v.toFloat() * density).toInt()

    private val fonts = HashMap<String, Typeface>()
    fun font(weight: Int, display: Boolean): Typeface = fonts.getOrPut("$display$weight") {
        try {
            Typeface.Builder(ctx.assets, if (display) "fonts/outfit.ttf" else "fonts/figtree.ttf")
                .setFontVariationSettings("'wght' $weight").build() ?: Typeface.DEFAULT
        } catch (_: Exception) { Typeface.DEFAULT }
    }

    fun text(t: String, sp: Float, color: Int, weight: Int = 400, display: Boolean = false, spacing: Float = 0f) = TextView(ctx).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = font(weight, display)
        if (spacing != 0f) letterSpacing = spacing
        includeFontPadding = false
    }

    fun iconRes(name: String): Int {
        val key = "mdi_" + name.removePrefix("mdi:").replace('-', '_')
        val id = ctx.resources.getIdentifier(key, "drawable", ctx.packageName)
        return if (id != 0) id else ctx.resources.getIdentifier("mdi_circle_medium", "drawable", ctx.packageName)
    }

    fun icon(name: String, tint: Int, sizeDp: Int) = ImageView(ctx).apply {
        setImageResource(iconRes(name))
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun rounded(color: Int, rDp: Number) = GradientDrawable().apply { setColor(color); cornerRadius = dp(rDp).toFloat() }
    fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    fun glass(rDp: Number) = GradientDrawable().apply {
        setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(rDp).toFloat(); setStroke(dp(1), Color.argb(38, 255, 255, 255))
    }
    fun ripple(d: Drawable) = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), d, null)

    /** Botão redondo com um ícone. */
    fun roundButton(icon: String, sizeDp: Int, bg: Int, tint: Int, onClick: () -> Unit) = FrameLayout(ctx).apply {
        background = ripple(circle(bg))
        isClickable = true
        setOnClickListener { onClick() }
        addView(icon(icon, tint, sizeDp * 5 / 10), FrameLayout.LayoutParams(dp(sizeDp * 5 / 10), dp(sizeDp * 5 / 10), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Pílula com ícone e texto (cenas, filtros, botões Acender/Apagar). */
    fun pill(label: String, icon: String?, filled: Boolean, onClick: () -> Unit): LinearLayout {
        val fg = if (filled) ink else white
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(if (filled) rounded(cream, 40) else glass(40))
            setPadding(dp(18), dp(12), dp(20), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            if (icon != null) addView(icon(icon, fg, 18).also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8) })
            addView(text(label, 15f, fg, 500))
        }
    }
}

/** Grelha de cartões do mesmo tamanho: o número de colunas depende da largura. */
class Grid(ctx: Context, private val minW: Int, private val childH: Int, private val gap: Int) : ViewGroup(ctx) {
    private var cols = 1
    private var cw = 0

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        cols = maxOf(1, (w + gap) / (minW + gap))
        cw = (w - (cols - 1) * gap) / cols
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY))
        }
        val rows = (childCount + cols - 1) / cols
        setMeasuredDimension(w, if (rows == 0) 0 else rows * childH + (rows - 1) * gap)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val x = (i % cols) * (cw + gap)
            val y = (i / cols) * (childH + gap)
            getChildAt(i).layout(x, y, x + cw, y + childH)
        }
    }
}

/** Uma fila de bolinhas (uma por luz, âmbar se acesa). */
class Dots(ctx: Context, private val kit: Kit) : View(ctx) {
    var on: List<Boolean> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var onColor = Color.parseColor("#F6B83F")
    var offColor = Color.argb(90, 120, 110, 120)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(on.size.coerceAtLeast(1) * kit.dp(16), kit.dp(10))

    override fun onDraw(c: Canvas) {
        val r = kit.dp(5).toFloat()
        on.forEachIndexed { i, v -> p.color = if (v) onColor else offColor; c.drawCircle(r + i * kit.dp(16), r, r, p) }
    }
}

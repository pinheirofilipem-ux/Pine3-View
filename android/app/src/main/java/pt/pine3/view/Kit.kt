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

/** Fundo de um tema do Pine3 View: base em gradiente linear + brilhos radiais (como o CSS do painel web). */
class ThemeBackground(private val theme: Theme) : Drawable() {
    private val base = Paint()
    private val glow = Paint()
    private var w = 0f
    private var h = 0f

    override fun onBoundsChange(b: android.graphics.Rect) {
        w = b.width().toFloat(); h = b.height().toFloat()
        // Ângulo CSS: 0° = para cima, 90° = para a direita; a linha do gradiente atravessa a caixa pelo centro.
        val a = Math.toRadians(theme.base.angle.toDouble())
        val dx = Math.sin(a).toFloat(); val dy = (-Math.cos(a)).toFloat()
        val half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2f
        val cx = w / 2f; val cy = h / 2f
        base.shader = android.graphics.LinearGradient(
            cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, theme.base.colors, theme.base.pos, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, base)
        // No CSS a primeira camada fica por cima: desenha de trás para a frente.
        for (g in theme.glows.asReversed()) {
            val rx = g.rx * g.stop * w
            val ry = g.ry * g.stop * h
            if (rx <= 0f || ry <= 0f) continue
            val cx = g.cx * w; val cy = g.cy * h
            val clear = g.color and 0x00FFFFFF
            glow.shader = RadialGradient(cx, cy, rx, g.color, clear, Shader.TileMode.CLAMP)
            canvas.save()
            canvas.scale(1f, ry / rx, cx, cy) // elipse
            canvas.drawRect(0f, cy - rx, w, cy + rx, glow)
            canvas.restore()
        }
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
    /** Cores que dependem do tema (claro/escuro): texto, texto suave, vidro e preenchimentos suaves. */
    var fg = Color.WHITE
    var muted = Color.argb(166, 255, 255, 255)
    var soft = Color.argb(50, 255, 255, 255)
    private var glassFill = Color.argb(30, 255, 255, 255)
    private var glassStroke = Color.argb(38, 255, 255, 255)
    private var rippleColor = Color.argb(50, 255, 255, 255)
    var light = false
        private set

    fun applyTheme(theme: Theme) {
        light = theme.light
        if (light) {
            fg = Color.rgb(33, 24, 38); muted = Color.argb(158, 33, 24, 38); soft = Color.argb(28, 33, 24, 38)
            glassFill = Color.argb(140, 255, 255, 255); glassStroke = Color.argb(200, 255, 255, 255); rippleColor = Color.argb(40, 33, 24, 38)
        } else {
            fg = Color.WHITE; muted = Color.argb(166, 255, 255, 255); soft = Color.argb(50, 255, 255, 255)
            glassFill = Color.argb(30, 255, 255, 255); glassStroke = Color.argb(38, 255, 255, 255); rippleColor = Color.argb(50, 255, 255, 255)
        }
    }

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

    /** Um ícone MDI como Drawable (a cor vem do filtro de cor da ImageView). */
    fun mdi(name: String): android.graphics.drawable.Drawable = Mdi.drawable(ctx, name)

    fun icon(name: String, tint: Int, sizeDp: Int) = ImageView(ctx).apply {
        setImageDrawable(mdi(name))
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun rounded(color: Int, rDp: Number) = GradientDrawable().apply { setColor(color); cornerRadius = dp(rDp).toFloat() }
    fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    fun glass(rDp: Number) = GradientDrawable().apply {
        setColor(glassFill); cornerRadius = dp(rDp).toFloat(); setStroke(dp(1), glassStroke)
    }
    fun ripple(d: Drawable) = RippleDrawable(ColorStateList.valueOf(rippleColor), d, null)

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
        val fgc = if (filled) ink else fg
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(if (filled) rounded(cream, 40) else glass(40))
            setPadding(dp(18), dp(12), dp(20), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            if (icon != null) addView(icon(icon, fgc, 18).also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8) })
            addView(text(label, 15f, fgc, 500))
        }
    }
}

/** Grelha de cartões do mesmo tamanho: o número de colunas depende da largura. Ignora os cartões escondidos (GONE). */
class Grid(ctx: Context, private val minW: Int, private val childH: Int, private val gap: Int) : ViewGroup(ctx) {
    private var cols = 1
    private var cw = 0

    private fun visibleCount(): Int { var n = 0; for (i in 0 until childCount) if (getChildAt(i).visibility != GONE) n++; return n }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        cols = maxOf(1, (w + gap) / (minW + gap))
        cw = (w - (cols - 1) * gap) / cols
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != GONE) c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY))
        }
        val rows = (visibleCount() + cols - 1) / cols
        setMeasuredDimension(w, if (rows == 0) 0 else rows * childH + (rows - 1) * gap)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var k = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val x = (k % cols) * (cw + gap)
            val y = (k / cols) * (childH + gap)
            c.layout(x, y, x + cw, y + childH)
            k++
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

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
import android.view.MotionEvent
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
    val ink = Color.parseColor("#2A221F")
    val inkSoft = Color.argb(153, 42, 34, 31)
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

    /** O viewport em "px CSS" (= dp): o painel web usa estas medidas e as suas media queries. */
    val wdp: Float get() = ctx.resources.displayMetrics.widthPixels / density
    val hdp: Float get() = ctx.resources.displayMetrics.heightPixels / density
    fun vw(p: Float) = wdp * p / 100f
    fun vh(p: Float) = hdp * p / 100f
    fun clamp(min: Float, v: Float, max: Float) = v.coerceIn(min, max)
    /** @media (max-width:1280px),(max-height:820px): o painel web usa medidas mais compactas. */
    /** Tablets em horizontal com ecrã baixo (min-width:721px e max-height:920px): cartões mais densos, três filas acima da dock. */
    val short: Boolean get() = wdp >= 721f && hdp <= 920f
    /** Disposição em lista (definição "layout"): lista em vez de grelha. */
    var list = false
    val compact: Boolean get() = wdp <= 1280f || hdp <= 820f

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
    fun glass(rDp: Number): Drawable = if (Glass.level == 0) GradientDrawable().apply {
        setColor(glassFill); cornerRadius = dp(rDp).toFloat(); setStroke(dp(1), glassStroke)
    } else GlassDrawable(this, dp(rDp).toFloat(), 0)

    /** Cartão ligado (creme): liso no estilo atual, com vidro nos outros. */
    fun surfaceOn(rDp: Number): Drawable = if (Glass.level == 0) rounded(cream, rDp) else GlassDrawable(this, dp(rDp).toFloat(), 1, cream)

    /** Fundo das janelas por cima (folhas, câmara, campainha, previsão). */
    fun sheetBg(rDp: Number): Drawable = if (Glass.level == 0) rounded(Color.parseColor("#F0221A26"), rDp) else GlassDrawable(this, dp(rDp).toFloat(), 2)

    fun ripple(d: Drawable) = RippleDrawable(ColorStateList.valueOf(rippleColor), d, null)

    /** Botão redondo com um ícone. */
    fun roundButton(icon: String, sizeDp: Int, bg: Int, tint: Int, onClick: () -> Unit) = FrameLayout(ctx).apply {
        background = ripple(circle(bg))
        isClickable = true
        setOnClickListener { onClick() }
        Elastic.press(this, 0.88f)
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
            Elastic.press(this, 0.94f)
            if (icon != null) addView(icon(icon, fgc, 18).also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8) })
            addView(text(label, 15f, fgc, 500))
        }
    }
}

/**
 * Grelha como `repeat(auto-fill, minmax(minW, 1fr))` do CSS: colunas de largura igual conforme o espaço, linhas com a
 * altura do cartão mais alto da linha (mín. [childH]). Um filho com tag `2` ocupa duas colunas ("grid-column: span 2").
 * Ignora os cartões escondidos (GONE).
 */
class Grid(ctx: Context, private val minW: Int, private val childH: Int, private val gap: Int, private val fixedCols: Int = 0) : ViewGroup(ctx) {
    /** Disposição em lista: uma coluna, cartões baixos e 10 dp entre eles (.as-list do painel web). */
    var list = false
    /** Modo de edição: arrastar um cartão troca-o de lugar; no fim chama [onReorder] com as chaves pela nova ordem. */
    var onReorder: ((List<String>) -> Unit)? = null
    private var drag: View? = null
    private var dox = 0f; private var doy = 0f; private var fx = 0f; private var fy = 0f; private var downX = 0f; private var downY = 0f
    private var dragging = false
    private var cols = 1
    private var cw = 0
    private val pos = ArrayList<IntArray>() // x, y, w, h por filho visível (por ordem)

    private fun span(c: View) = if (c.tag == 2) minOf(2, cols) else 1

    private fun childAt(x: Float, y: Float): View? {
        for (i in childCount - 1 downTo 0) { val c = getChildAt(i); if (c.visibility == GONE) continue; if (x >= c.left && x <= c.right && y >= c.top && y <= c.bottom) return c }
        return null
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (Lay.edit == null || onReorder == null) return false
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            val c = childAt(e.x, e.y) as? Card ?: return false
            if (c.key == null || c.chromeHit(e.x - c.left, e.y - c.top)) return false
            drag = c; downX = e.x; downY = e.y; dragging = false
            return true
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val d = drag ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                fx = e.x; fy = e.y
                if (!dragging && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > 8 * resources.displayMetrics.density) {
                    dragging = true; dox = downX - d.left; doy = downY - d.top
                    d.elevation = 14 * resources.displayMetrics.density; d.scaleX = 1.04f; d.scaleY = 1.04f
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val over = childAt(e.x, e.y)?.takeIf { it !== d }
                    if (over != null) {
                        val to = indexOfChild(over)
                        detachViewFromParent(d); attachViewToParent(d, to, d.layoutParams ?: generateDefaultLayoutParams())
                        requestLayout()
                    }
                    d.translationX = e.x - dox - d.left; d.translationY = e.y - doy - d.top
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val was = dragging
                d.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(220).withEndAction { d.elevation = 0f }.start()
                drag = null; dragging = false
                if (was && e.actionMasked == MotionEvent.ACTION_UP) onReorder?.invoke((0 until childCount).mapNotNull { (getChildAt(it) as? Card)?.key })
            }
        }
        return true
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val gap = if (list) (10 * resources.displayMetrics.density).toInt() else this.gap
        val childH = if (list) 0 else this.childH
        cols = if (list) 1 else if (fixedCols > 0) fixedCols else maxOf(1, (w + gap) / (minW + gap))
        cw = (w - (cols - 1) * gap) / cols
        pos.clear()
        var col = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val sp = span(c)
            if (col + sp > cols) { y += rowH + gap; rowH = 0; col = 0 }
            val width = cw * sp + gap * (sp - 1)
            c.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val h = maxOf(childH, c.measuredHeight)
            pos.add(intArrayOf(col * (cw + gap), y, width, h))
            rowH = maxOf(rowH, h)
            col += sp
            if (col >= cols) { y += rowH + gap; rowH = 0; col = 0 }
        }
        val total = if (col == 0 && pos.isNotEmpty()) y - gap else y + rowH
        // alturas de uma linha iguais (stretch)
        var k = 0
        val rows = HashMap<Int, Int>()
        pos.forEach { rows[it[1]] = maxOf(rows[it[1]] ?: 0, it[3]) }
        pos.forEach { it[3] = rows[it[1]] ?: it[3] }
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val p = pos[k++]
            c.measure(MeasureSpec.makeMeasureSpec(p[2], MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(p[3], MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, maxOf(0, total))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var k = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val p = pos[k++]
            c.layout(p[0], p[1], p[0] + p[2], p[1] + p[3])
        }
        drag?.let { d -> if (dragging) { d.translationX = fx - dox - d.left; d.translationY = fy - doy - d.top } }
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

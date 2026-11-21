package pt.pine3.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** A "lente" de vidro debaixo do separador ativo: desliza com mola e estica como uma gota (como no painel web). */
private class LensView(private val kit: Kit) : View(kit.ctx) {
    var scaleXf = 1f
    var scaleYf = 1f
    /** 0 = creme sólido, 1 = vidro claro (a meio da animação). */
    var glass = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(1).toFloat() }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cream = kit.cream
        val glassC = Color.argb(60, 255, 255, 255)
        fill.color = ArgbEvaluator().evaluate(glass, cream, glassC) as Int
        c.save()
        c.scale(scaleXf, scaleYf, w / 2f, h / 2f)
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, h / 2f, h / 2f, fill)
        edge.color = Color.argb((60 + 150 * glass).toInt().coerceAtMost(220), 255, 255, 255)
        r.inset(kit.dp(1) / 2f, kit.dp(1) / 2f)
        c.drawRoundRect(r, h / 2f, h / 2f, edge)
        // brilho no topo
        val top = h * 0.45f
        sheen.shader = LinearGradient(0f, 0f, 0f, top, Color.argb((140 * (0.7f + 0.3f * glass)).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        r.set(w * 0.12f, h * 0.03f, w * 0.88f, h * 0.03f + top)
        c.drawRoundRect(r, top / 2f, top / 2f, sheen)
        c.restore()
    }
}

/** A dock flutuante com a lente animada. Se não cabe, só o separador ativo mantém o nome e depois ficam só os ícones. */
class DockView(private val kit: Kit, labels: List<Triple<String, String, String>>, private val animationsOn: Boolean, private val onPick: (Int) -> Unit) : FrameLayout(kit.ctx) {
    private val lens = LensView(kit)
    private val row = LinearLayout(kit.ctx)
    private val icons = ArrayList<ImageView>()
    private val texts = ArrayList<TextView>()
    private val tabs = ArrayList<LinearLayout>()
    private var active = -1
    private var lensL = 0f
    private var lensW = 0f
    private var placed = false
    private var mode = 0 // 0 completa, 1 só o ativo com nome, 2 só ícones
    private var move: ValueAnimator? = null
    private var squish: ValueAnimator? = null
    private val small = kit.compact
    private val tabH = kit.dp(if (small) 44 else 50)

    init {
        val dark = !kit.light
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = kit.dp(60).toFloat()
            setColor(if (dark) Color.argb(150, 26, 22, 30) else Color.argb(170, 255, 255, 255))
            setStroke(kit.dp(1), if (dark) Color.argb(46, 255, 255, 255) else Color.argb(210, 255, 255, 255))
        }
        elevation = kit.dp(8).toFloat()
        val pad = kit.dp(if (small) 6 else 7)
        setPadding(pad, pad, pad, pad)
        addView(lens, LayoutParams(0, 0))
        addView(row, LayoutParams(-2, -2))
        labels.forEach { (label, icon, _) ->
            val ic = kit.icon(icon, kit.muted, if (small) 20 else 22)
            val tx = kit.text(label, if (small) 16f else 18f, kit.muted, 500).apply { setSingleLine() }
            val tab = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; isClickable = true; addView(ic); addView(tx, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(if (small) 8 else 10) }) }
            icons += ic; texts += tx; tabs += tab
            row.addView(tab, LinearLayout.LayoutParams(-2, tabH).apply { marginEnd = kit.dp(if (small) 4 else 6) })
        }
        tabs.forEachIndexed { i, t -> t.setOnClickListener { onPick(i) } }
        applyMode()
    }

    private fun applyMode() {
        tabs.forEachIndexed { i, t ->
            val showText = mode == 0 || (mode == 1 && i == active)
            texts[i].visibility = if (showText) View.VISIBLE else View.GONE
            val padH = when (mode) { 2 -> 15; 1 -> 14; else -> if (small) 18 else 24 }
            t.setPadding(kit.dp(padH), 0, kit.dp(padH), 0)
            (icons[i].layoutParams as LinearLayout.LayoutParams)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        val avail = MeasureSpec.getSize(w) - kit.dp(24)
        // escolhe o modo mais largo que cabe
        for (m in 0..2) {
            mode = m; applyMode()
            var total = paddingLeft + paddingRight
            tabs.forEach { it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.makeMeasureSpec(tabH, MeasureSpec.EXACTLY)); total += it.measuredWidth + kit.dp(if (small) 4 else 6) }
            if (total <= avail || m == 2) break
        }
        super.onMeasure(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (active >= 0) place(active, animate = false)
    }

    private fun target(i: Int): Pair<Float, Float> {
        val tab = tabs[i]
        return (row.left + tab.left).toFloat() to tab.width.toFloat()
    }

    private fun applyLens(l: Float, w: Float) {
        lensL = l; lensW = w
        val lp = lens.layoutParams as LayoutParams
        lp.width = Math.max(0, w.toInt()); lp.height = tabH; lp.leftMargin = 0; lp.topMargin = paddingTop
        lens.layoutParams = lp
        lens.translationX = l
    }

    private fun colorTo(i: Int, on: Boolean, animate: Boolean) {
        val to = if (on) kit.ink else kit.muted
        val from = texts[i].currentTextColor
        if (!animate || from == to) { texts[i].setTextColor(to); icons[i].setColorFilter(to); return }
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 350
            addUpdateListener { texts[i].setTextColor(it.animatedValue as Int); icons[i].setColorFilter(it.animatedValue as Int) }
            start()
        }
    }

    /** Põe a lente no separador [i]; com [animate] desliza com mola e estica como uma gota. */
    fun setActive(i: Int, animate: Boolean) {
        val prev = active
        active = i
        tabs.indices.forEach { colorTo(it, it == i, animate && animationsOn && prev >= 0) }
        if (mode == 1) { requestLayout(); placed = false; return } // o nome muda de separador: refaz a medida
        if (width == 0 || tabs[i].width == 0) { placed = false; return }
        place(i, animate && animationsOn && prev >= 0)
    }

    private fun place(i: Int, animate: Boolean) {
        val (l1, w1) = target(i)
        if (w1 <= 0f) return
        move?.cancel(); squish?.cancel()
        if (!animate) { applyLens(l1, w1); lens.scaleXf = 1f; lens.scaleYf = 1f; lens.glass = 0f; lens.invalidate(); placed = true; return }
        val l0 = lensL; val w0 = lensW
        move = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            interpolator = PathInterpolator(0.34f, 1.36f, 0.5f, 1f)
            addUpdateListener { val f = it.animatedValue as Float; applyLens(l0 + (l1 - l0) * f, w0 + (w1 - w0) * f) }
            start()
        }
        val ease = PathInterpolator(0.3f, 0.9f, 0.4f, 1f)
        squish = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            addUpdateListener {
                val f = it.animatedValue as Float
                fun seg(a: Float, b: Float, x0: Float, x1: Float): Float { val k = ease.getInterpolation(((f - a) / (b - a)).coerceIn(0f, 1f)); return x0 + (x1 - x0) * k }
                when {
                    f < 0.35f -> { lens.scaleXf = seg(0f, 0.35f, 1f, 1.16f); lens.scaleYf = seg(0f, 0.35f, 1f, 0.86f); lens.glass = seg(0f, 0.35f, 0f, 1f) }
                    f < 0.70f -> { lens.scaleXf = seg(0.35f, 0.70f, 1.16f, 0.97f); lens.scaleYf = seg(0.35f, 0.70f, 0.86f, 1.04f); lens.glass = seg(0.35f, 0.70f, 1f, 0f) }
                    else -> { lens.scaleXf = seg(0.70f, 1f, 0.97f, 1f); lens.scaleYf = seg(0.70f, 1f, 1.04f, 1f); lens.glass = 0f }
                }
                lens.invalidate()
            }
            start()
        }
        placed = true
    }
}

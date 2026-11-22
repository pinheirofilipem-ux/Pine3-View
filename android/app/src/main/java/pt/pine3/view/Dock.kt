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
internal class LensView(private val kit: Kit) : View(kit.ctx) {
    var scaleXf = 1f
    var scaleYf = 1f
    /** 0 = creme sólido, 1 = vidro claro (a meio da animação). */
    var glass = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(1).toFloat() }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    /** Folga à volta da lente: o esticão elástico passa dos limites do separador e não pode ser cortado. */
    val over = kit.dp(14)

    override fun onDraw(c: Canvas) {
        val w = (width - 2 * over).toFloat(); val h = (height - 2 * over).toFloat()
        if (w <= 0f || h <= 0f) return
        c.save(); c.translate(over.toFloat(), over.toFloat())
        val cream = kit.cream
        val glassC = Color.argb(60, 255, 255, 255)
        fill.color = ArgbEvaluator().evaluate(glass, cream, glassC) as Int
        c.save()
        c.scale(scaleXf, scaleYf, w / 2f, h / 2f)
        r.set(0f, 0f, w, h)
        if (Glass.level >= 2) { val g = kit.dp(5).toFloat(); fill.color = Color.argb(46, 255, 232, 180); r.set(-g, -g * 0.4f, w + g, h + g); c.drawRoundRect(r, (h + g) / 2f, (h + g) / 2f, fill); fill.color = ArgbEvaluator().evaluate(glass, cream, glassC) as Int; r.set(0f, 0f, w, h) }
        c.drawRoundRect(r, h / 2f, h / 2f, fill)
        edge.color = Color.argb((60 + 150 * glass).toInt().coerceAtMost(220), 255, 255, 255)
        r.inset(kit.dp(1) / 2f, kit.dp(1) / 2f)
        c.drawRoundRect(r, h / 2f, h / 2f, edge)
        // brilho no topo
        val top = h * 0.45f
        sheen.shader = LinearGradient(0f, 0f, 0f, top, Color.argb((140 * (0.7f + 0.3f * glass)).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        r.set(w * 0.12f, h * 0.03f, w * 0.88f, h * 0.03f + top)
        c.drawRoundRect(r, top / 2f, top / 2f, sheen)
        c.restore()
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
        background = if (Glass.level == 0) android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = kit.dp(60).toFloat()
            setColor(if (dark) Color.argb(150, 26, 22, 30) else Color.argb(170, 255, 255, 255))
            setStroke(kit.dp(1), if (dark) Color.argb(46, 255, 255, 255) else Color.argb(210, 255, 255, 255))
        } else GlassDrawable(kit, kit.dp(60).toFloat(), 2)
        elevation = kit.dp(8).toFloat()
        clipChildren = false; clipToPadding = false
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
        tabs.forEachIndexed { i, t -> t.setOnClickListener { onPick(i) }; Elastic.press(t, 0.92f) }
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

    // --- arrastar a lente com o dedo (como o Liquid Glass da Apple): a lente cresce, segue o dedo e, ao largar, assenta no separador mais próximo
    private var grab = false
    private var dragging = false
    private var downX = 0f
    private var grabDx = 0f
    private var hover = -1
    private val slop = kit.dp(6)

    private fun lensRect(): android.graphics.RectF = android.graphics.RectF(lensL, paddingTop.toFloat(), lensL + lensW, (paddingTop + tabH).toFloat())

    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; grab = lensRect().let { r -> r.inset(-kit.dp(6).toFloat(), -kit.dp(8).toFloat()); r.contains(e.x, e.y) } && active >= 0; dragging = false }
            android.view.MotionEvent.ACTION_MOVE -> if (grab && !dragging && Math.abs(e.x - downX) > slop) { startDrag(e.x); return true }
        }
        return false
    }

    private fun startDrag(x: Float) {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        move?.cancel(); squish?.cancel()
        grabDx = x - (lensL + lensW / 2f)
        hover = active
        // a lente "levanta": mais larga, mais alta e em vidro claro
        lens.glass = 1f
        lens.animate().cancel()
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220; interpolator = PathInterpolator(0.34f, 1.56f, 0.5f, 1f)
            val sx0 = lens.scaleXf; val sy0 = lens.scaleYf
            addUpdateListener { val f = it.animatedValue as Float; lens.scaleXf = sx0 + (1.14f - sx0) * f; lens.scaleYf = sy0 + (1.18f - sy0) * f; lens.invalidate() }
            start()
        }
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(e)
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_MOVE -> {
                val minC = row.left + tabs.first().left + tabs.first().width / 2f
                val maxC = row.left + tabs.last().left + tabs.last().width / 2f
                val c = (e.x - grabDx).coerceIn(minC - tabs.first().width * 0.25f, maxC + tabs.last().width * 0.25f)
                // a largura da lente acompanha a do separador por baixo do centro
                val idx = tabs.indices.minByOrNull { i -> Math.abs(row.left + tabs[i].left + tabs[i].width / 2f - c) } ?: active
                val w = lensW + (tabs[idx].width - lensW) * 0.25f
                applyLens(c - w / 2f, w)
                if (idx != hover) { hover = idx; tabs.indices.forEach { colorTo(it, it == idx, true) } }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false; grab = false
                val i = if (e.actionMasked == android.view.MotionEvent.ACTION_UP) hover.coerceAtLeast(0) else active
                // assenta: volta ao tamanho normal e a mola leva-a ao separador escolhido
                lens.glass = 0f
                lens.scaleXf = 1f; lens.scaleYf = 1f; lens.invalidate()
                if (i != active) onPick(i) else { colorTo(active, true, true); place(active, animationsOn) }
            }
        }
        return true
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
        // a lente é posicionada à mão (nunca por LayoutParams): senão cada fotograma da animação pedia um layout que a cancelava
        if (dragging || move?.isRunning == true) applyLens(lensL, lensW) else if (active >= 0) place(active, animate = false)
    }

    private fun target(i: Int): Pair<Float, Float> {
        val tab = tabs[i]
        return (row.left + tab.left).toFloat() to tab.width.toFloat()
    }

    private fun applyLens(l: Float, w: Float) {
        lensL = l; lensW = w
        val o = lens.over
        lens.layout(l.toInt() - o, paddingTop - o, (l + Math.max(0f, w)).toInt() + o, paddingTop + tabH + o)
        lens.invalidate()
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

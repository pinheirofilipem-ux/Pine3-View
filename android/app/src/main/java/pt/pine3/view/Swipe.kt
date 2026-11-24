package pt.pine3.view

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Contentor das páginas: um deslize horizontal passa para a vista seguinte (para a esquerda) ou a anterior
 * (para a direita) da dock. Só apanha o gesto quando é claramente horizontal e não começa em algo que já desliza
 * de lado (filas de cenas e entradas) ou que segura o toque (casa 3D, arrastar no modo de edição).
 */
@SuppressLint("ViewConstructor")
class SwipeFrame(ctx: Context, private val allowed: () -> Boolean, private val onSwipe: (dir: Int) -> Unit) : FrameLayout(ctx) {
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val minFling = ViewConfiguration.get(ctx).scaledMinimumFlingVelocity * 6
    private var x0 = 0f; private var y0 = 0f
    private var blocked = false; private var swiping = false
    private var vt: VelocityTracker? = null

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x0 = e.x; y0 = e.y; swiping = false
                blocked = !allowed() || sideScrollerAt(this, e.x, e.y)
                vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> blocked = true
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!blocked && !swiping) {
                    val dx = e.x - x0; val dy = e.y - y0
                    if (abs(dx) > slop * 2 && abs(dx) > abs(dy) * 2) { swiping = true; return true }
                    if (abs(dy) > slop * 2) blocked = true // a vertical scroll: leave it alone
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!swiping) return false
        val page = getChildAt(0)
        vt?.addMovement(e)
        val dx = e.x - x0
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> page?.let { it.translationX = dx * 0.35f; it.alpha = 1f - (abs(dx) / width.coerceAtLeast(1)) * 0.5f }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val v = vt?.xVelocity ?: 0f
                val go = e.actionMasked == MotionEvent.ACTION_UP && (abs(dx) > width * 0.18f || (abs(v) > minFling && abs(dx) > slop * 3 && v * dx > 0))
                swiping = false
                if (go) onSwipe(if (dx < 0) 1 else -1)
                else page?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(220)?.start()
            }
        }
        return true
    }

    /** Is there something under the finger that scrolls sideways (a row of chips) or keeps the touch (a seek bar)? */
    private fun sideScrollerAt(g: ViewGroup, x: Float, y: Float): Boolean {
        for (i in g.childCount - 1 downTo 0) {
            val c = g.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val cx = x - c.left + g.scrollX - c.translationX; val cy = y - c.top + g.scrollY - c.translationY
            if (cx < 0 || cy < 0 || cx >= c.width || cy >= c.height) continue
            if (c.canScrollHorizontally(1) || c.canScrollHorizontally(-1) || c is android.widget.SeekBar) return true
            return c is ViewGroup && sideScrollerAt(c, cx, cy)
        }
        return false
    }
}

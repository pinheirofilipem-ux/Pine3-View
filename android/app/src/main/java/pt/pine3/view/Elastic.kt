package pt.pine3.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Animações elásticas (mola com ultrapassagem) usadas por toda a app, com a mesma curva da lente da dock. */
object Elastic {
    /** Desligado quando "Animações" está desligado nas definições. */
    @Volatile var on = true
    val spring = PathInterpolator(0.34f, 1.56f, 0.5f, 1f)

    /** Ao tocar encolhe um pouco; ao largar volta com ultrapassagem. Não consome o toque. */
    fun press(v: View, depth: Float = 0.94f) {
        v.setOnTouchListener { view, e ->
            if (on) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().cancel().also { view.animate().scaleX(depth).scaleY(depth).setDuration(110).setInterpolator(PathInterpolator(0.2f, 0.8f, 0.2f, 1f)).start() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(spring).start()
            }
            false
        }
    }

    /** Entrada com salto elástico (cartões, folhas), opcionalmente escalonada. */
    fun pop(v: View, delayMs: Long = 0, from: Float = 0.86f) {
        if (!on) return
        v.alpha = 0f; v.scaleX = from; v.scaleY = from
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delayMs).setDuration(560).setInterpolator(spring).start()
    }

    /** Faz o "pop" escalonado dos filhos de grelhas dentro de [root], até 24 cartões. */
    fun popGrids(root: View) {
        if (!on) return
        var n = 0
        fun walk(v: View) {
            if (v is Grid) { for (i in 0 until v.childCount) { if (n >= 24) return; pop(v.getChildAt(i), 40L + n * 28L, 0.9f); n++ } }
            else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }
}

/** Indicação de "a carregar" para imagens (câmaras): varrimento de brilho, anel a rodar e texto. */
class LoadingOverlay(private val kit: Kit, label: String) : FrameLayout(kit.ctx) {
    private val sweep = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(3).toFloat(); strokeCap = Paint.Cap.ROUND }
    private val r = RectF()
    private var t = 0f
    private var anim: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(kit.dp(38), kit.dp(38)))
        col.addView(kit.text(label, 14f, Color.argb(210, 255, 255, 255), 500), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(12) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
        setBackgroundColor(Color.argb(60, 0, 0, 0))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1300; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator(); addUpdateListener { t = it.animatedValue as Float; invalidate() }; start() }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun dispatchDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val x = -w * 0.5f + t * w * 2f
        sweep.shader = LinearGradient(x, 0f, x + w * 0.5f, 0f, intArrayOf(Color.TRANSPARENT, Color.argb(46, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, sweep)
        super.dispatchDraw(c)
        // anel por cima do espaço reservado na coluna
        val cx = w / 2f; val sz = kit.dp(38).toFloat()
        val top = (h - (sz + kit.dp(12) + kit.dp(18))) / 2f
        r.set(cx - sz / 2 + ring.strokeWidth, top + ring.strokeWidth, cx + sz / 2 - ring.strokeWidth, top + sz - ring.strokeWidth)
        ring.color = Color.argb(70, 255, 255, 255); c.drawArc(r, 0f, 360f, false, ring)
        ring.color = Color.WHITE; c.drawArc(r, t * 720f, 100f, false, ring)
    }
}

/** Janela por cima da app sem barras do sistema e sem fundo preto: o painel continua visível por trás. */
fun immersive(d: android.app.Dialog) {
    d.window?.apply {
        clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    }
}

/** Janela por cima da app: cada toque conta como atividade (reinicia o tempo para desligar o ecrã), tal como na janela principal. */
class PokeDialog(act: android.app.Activity) : android.app.Dialog(act, android.R.style.Theme_Translucent_NoTitleBar) {
    // Com uma janela destas aberta a atividade não tem foco: a cortina preta do ecrã principal tem de levantar na mesma ao acordar.
    private val onSleep: (Boolean) -> Unit = { s ->
        window?.let { w ->
            val v = w.decorView
            v.animate().cancel()
            fun brightness(off: Boolean) {
                val lp = w.attributes
                lp.screenBrightness = if (off) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
            if (s) {
                // desvanece na mesma velocidade que o ecrã principal; sem permissão de administrador o brilho desce no fim
                v.animate().alpha(0f).setDuration(PanelState.FADE_MS).withEndAction { if (PanelState.sleeping) { v.visibility = View.INVISIBLE; brightness(true) } }.start()
            } else { brightness(false); v.visibility = View.VISIBLE; v.alpha = 0f; v.animate().alpha(1f).setDuration(PanelState.FADE_MS).start() }
        }
    }
    override fun onStart() { super.onStart(); PanelState.dialogs++; PanelState.sleepListeners += onSleep; if (PanelState.sleeping) { window?.decorView?.alpha = 0f; window?.decorView?.visibility = View.INVISIBLE } }
    override fun onStop() { PanelState.sleepListeners -= onSleep; PanelState.dialogs = maxOf(0, PanelState.dialogs - 1); super.onStop() }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { MotionService.poke(); return super.dispatchTouchEvent(ev) }
}

package pt.pine3.view

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Ecrã de descanso (.saver do painel web): relógio grande e escurecido que deriva devagar. Um toque fecha. */
class SaverView(private val kit: Kit, private val h24: Boolean, private val video: Boolean = false, private val bg: Int? = null, private val blend: String = "white", private val onClose: () -> Unit) : FrameLayout(kit.ctx) {
    private var player: AerialView? = null
    private var overlay: TimeOverlay? = null
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
        if (video) {
            // vídeo por trás e, em baixo à esquerda como na Apple TV, a hora e a data com sombra
            setBackgroundColor(bg ?: Color.BLACK)
            player = AerialView(kit.ctx, bg != null, blend).also { addView(it, LayoutParams(-1, -1)) }
            val st = kit.text("", 12f, Color.argb(150, 255, 255, 255)).apply { setShadowLayer(4f, 0f, 1f, Color.BLACK) }
            addView(st, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(20), kit.dp(14), 0, 0) })
            player?.onStatus = { t -> st.text = t }
            addView(View(kit.ctx).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0))) },
                LayoutParams(-1, kit.dp(kit.clamp(220f, kit.hdp * 0.42f, 420f)), Gravity.BOTTOM))
            // hora e data desenhadas à mão numa só vista de tamanho fixo (sem medir texto): nada pode cortar
            overlay = TimeOverlay(kit, kit.clamp(64f, kit.vw(9f), 120f), h24).also {
                addView(it, LayoutParams(it.fixedW, it.fixedH, Gravity.BOTTOM or Gravity.START).apply { leftMargin = kit.dp(48); bottomMargin = kit.dp(36) })
            }
        } else {
            inner.addView(clock); inner.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(6) })
            inner.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(26) })
            addView(inner, LayoutParams(-2, -2, Gravity.CENTER))
        }
        refresh()
    }

    /** A lista de vídeos chegou (ou já estava em cache): começa a reproduzir. */
    fun startVideo() { player?.play(Aerials.shuffled()) }

    private fun refresh() {
        val now = java.util.Date()
        val tm = java.text.SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Tr.locale).format(now)
        clock.text = tm; overlay?.set(tm, java.text.SimpleDateFormat("EEEE d MMMM", Tr.locale).format(now).replaceFirstChar { it.uppercase() })
        date.text = java.text.SimpleDateFormat("EEEE d MMMM", Tr.locale).format(now).replaceFirstChar { it.uppercase() }
        meta.removeAllViews()
        if (video) return
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
        if (video) { if (Aerials.count > 0) startVideo(); return }
        // deriva lenta para não queimar o painel
        drift = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = LinearInterpolator()
            addUpdateListener { val f = it.animatedValue as Float; inner.translationX = (-0.04f + 0.07f * f) * kit.wdp * kit.density; inner.translationY = (-0.03f + 0.07f * f) * kit.hdp * kit.density }
            start()
        }
    }

    override fun onDetachedFromWindow() { removeCallbacks(tick); drift?.cancel(); player?.release(); super.onDetachedFromWindow() }
}

/** Reproduz os vídeos em sequência, sem som, ocupando o ecrã todo (corta as margens como "preencher") e com fade entre eles. */
class AerialView(ctx: Context, private val hasBg: Boolean = false, blend: String = "white") : TextureView(ctx), TextureView.SurfaceTextureListener {
    /** "white": multiplica (o branco do clipe passa à cor de fundo, as linhas pretas ficam); "black": clareia (o preto passa à cor). */
    private val lighten = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(if (blend == "black") android.graphics.PorterDuff.Mode.LIGHTEN else android.graphics.PorterDuff.Mode.MULTIPLY) }
    private var mp: MediaPlayer? = null
    private var queue: ArrayDeque<Aerials.Clip> = ArrayDeque()
    private var failures = 0
    private var wantPlay = false
    private var released = false

    /** Estado para mostrar no ecrã (nome do clipe, erros): ajuda a perceber porque fica preto. */
    var onStatus: ((String) -> Unit)? = null

    init { surfaceTextureListener = this; alpha = 0f; isOpaque = true }

    fun play(list: List<Aerials.Clip>) {
        if (list.isEmpty()) return
        queue = ArrayDeque(list); wantPlay = true
        if (isAvailable) next()
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { if (wantPlay && mp == null) next() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { fit() }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    private fun fit() {
        val p = mp ?: return
        val vw = p.videoWidth.toFloat(); val vh = p.videoHeight.toFloat()
        if (vw <= 0 || vh <= 0 || width == 0 || height == 0) return
        val sc = maxOf(width / vw, height / vh)
        val m = Matrix(); m.setScale(vw * sc / width, vh * sc / height, width / 2f, height / 2f)
        setTransform(m)
    }

    private fun next() {
        if (released) return
        if (queue.isEmpty()) { queue = ArrayDeque(Aerials.shuffled()); if (queue.isEmpty()) return }
        val clip = queue.removeFirst()
        val st = surfaceTexture ?: return
        // os clipes com transparência chegam com o fundo preto (o Android ignora o canal alfa): "clarear" deixa ver a cor de fundo nesse preto
        try { mp?.release() } catch (_: Exception) {}
        val p = MediaPlayer()
        mp = p
        try {
            p.setSurface(Surface(st))
            if (clip.url.startsWith("content:")) p.setDataSource(context, android.net.Uri.parse(clip.url)) else p.setDataSource(clip.url)
            p.setVolume(0f, 0f)
            onStatus?.invoke("A carregar " + clip.id.removePrefix("local:") + "…")
            p.setOnVideoSizeChangedListener { m, w, h -> fit(); onStatus?.invoke(clip.id.removePrefix("local:") + "  " + w + "×" + h) }
            p.setOnPreparedListener { if (mp === it) { failures = 0; fit(); it.start(); postDelayed({ if (mp === it) animate().alpha(1f).setDuration(600).start() }, 1500) } }
            p.setOnInfoListener { m, what, _ -> if (mp === m && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) animate().alpha(1f).setDuration(900).start(); false }
            p.setOnCompletionListener { m -> if (mp === m) animate().alpha(0f).setDuration(700).withEndAction { next() }.start() }
            p.setOnErrorListener { _, what, extra -> failures++; onStatus?.invoke("Erro " + what + "/" + extra + " em " + clip.id.removePrefix("local:")); if (failures < 4) postDelayed({ next() }, 1500); true }
            p.prepareAsync()
        } catch (_: Exception) { failures++; if (failures < 4) post { next() } }
    }

    fun release() {
        released = true; wantPlay = false
        try { mp?.release() } catch (_: Exception) {}
        mp = null
    }
}

/** Hora grande e data do screensaver em vídeo: uma só vista de tamanho fixo que desenha o texto à mão (sem TextView, sem medir). */
class TimeOverlay(private val kit: Kit, sizeSp: Float, h24: Boolean) : View(kit.ctx) {
    private val sd = kit.ctx.resources.displayMetrics.scaledDensity
    private val big = sizeSp * sd
    private val small = 24f * sd
    val fixedW = (big * (if (h24) 4.2f else 6.4f)).toInt().coerceAtLeast(kit.dp(300))
    val fixedH = (big * 1.55f + small * 1.2f).toInt()
    private var time = ""
    private var date = ""
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = kit.font(200, true); textSize = big }
    private val pd = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = kit.font(400, false); textSize = small }

    fun set(t: String, d: String) { if (t != time || d != date) { time = t; date = d; invalidate() } }

    private fun shadowed(c: Canvas, s: String, x: Float, y: Float, p: Paint, color: Int) {
        val off = kit.dp(2).toFloat()
        for (k in 1..3) { p.color = Color.argb(60, 0, 0, 0); c.drawText(s, x + off * k * 0.6f, y + off * k, p) }
        p.color = color; c.drawText(s, x, y, p)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(fixedW, fixedH)

    override fun onDraw(c: Canvas) {
        val y1 = big * 1.05f
        shadowed(c, time, 0f, y1, pt, Color.argb(245, 255, 255, 255))
        shadowed(c, date, 0f, y1 + small * 1.5f, pd, Color.argb(220, 255, 255, 255))
    }
}

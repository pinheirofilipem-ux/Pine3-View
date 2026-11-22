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
    private var vclock: ClockView? = null
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
            addView(View(kit.ctx).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0))) },
                LayoutParams(-1, kit.dp(kit.clamp(220f, kit.hdp * 0.42f, 420f)), Gravity.BOTTOM))
            date.setShadowLayer(kit.dp(10).toFloat(), 0f, kit.dp(2).toFloat(), Color.argb(170, 0, 0, 0))
            inner.gravity = Gravity.START
            // a hora é desenhada à mão (não por um TextView): o tipo de letra variável fazia o campo ficar curto e cortava os últimos algarismos
            vclock = ClockView(kit, kit.clamp(64f, kit.vw(9f), 120f), h24).also { inner.addView(it) }
            inner.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2) })
            addView(inner, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = kit.dp(56); bottomMargin = kit.dp(44) })
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
        clock.text = tm; vclock?.text = tm
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

    init { surfaceTextureListener = this; alpha = 0f }

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
        setLayerPaint(if (hasBg && clip.id.startsWith("local:")) lighten else null)
        try { mp?.release() } catch (_: Exception) {}
        val p = MediaPlayer()
        mp = p
        try {
            p.setSurface(Surface(st))
            if (clip.url.startsWith("content:")) p.setDataSource(context, android.net.Uri.parse(clip.url)) else p.setDataSource(clip.url)
            p.setVolume(0f, 0f)
            p.setOnVideoSizeChangedListener { _, _, _ -> fit() }
            p.setOnPreparedListener { if (mp === it) { failures = 0; fit(); it.start(); animate().alpha(1f).setDuration(900).start() } }
            p.setOnCompletionListener { if (mp === it) animate().alpha(0f).setDuration(700).withEndAction { next() }.start() }
            p.setOnErrorListener { _, _, _ -> failures++; if (failures < 4) post { next() }; true }
            p.prepareAsync()
        } catch (_: Exception) { failures++; if (failures < 4) post { next() } }
    }

    fun release() {
        released = true; wantPlay = false
        try { mp?.release() } catch (_: Exception) {}
        mp = null
    }
}

/** A hora grande do screensaver em vídeo, desenhada num canvas com largura de sobra (nunca corta). */
class ClockView(private val kit: Kit, sizeSp: Float, private val h24: Boolean) : View(kit.ctx) {
    var text = ""
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = kit.font(200, true); color = Color.argb(240, 255, 255, 255); textSize = sizeSp * kit.ctx.resources.displayMetrics.scaledDensity
        setShadowLayer(kit.dp(10).toFloat(), 0f, kit.dp(2).toFloat(), Color.argb(170, 0, 0, 0))
    }
    override fun onMeasure(w: Int, h: Int) {
        val ref = if (h24) "00:00" else "00:00 AM"
        setMeasuredDimension((p.measureText(ref) * 1.5f + kit.dp(40)).toInt(), (p.fontMetrics.descent - p.fontMetrics.ascent + kit.dp(8)).toInt())
    }
    override fun onDraw(c: Canvas) { c.drawText(text, 0f, -p.fontMetrics.ascent + kit.dp(4), p) }
}

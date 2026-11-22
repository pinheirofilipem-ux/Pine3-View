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

/** Ecrã de descanso: relógio grande e escurecido (ou, com vídeo, a hora e a data por cima). Um toque fecha. */
class SaverView(private val kit: Kit, private val h24: Boolean, private val video: Boolean = false, private val bg: Int? = null, private val blend: String = "white", private val fit: String = "height", private val onClose: () -> Unit) : FrameLayout(kit.ctx) {
    private var player: AerialView? = null
    private val overlay = TimeOverlay(kit, if (video) kit.clamp(64f, kit.vw(9f), 120f) else kit.clamp(90f, kit.vw(16f), 200f), h24, center = !video)
    private var right = false
    private var drift: ValueAnimator? = null
    private val tick = object : Runnable { override fun run() { refresh(); postDelayed(this, 15_000) } }
    private val move = object : Runnable { override fun run() { moveOverlay(); postDelayed(this, 90_000) } }

    init {
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(Color.parseColor("#22121F"), Color.parseColor("#050305"))
            gradientRadius = kit.dp(900).toFloat(); setGradientCenter(0.5f, 0.4f)
        }
        isClickable = true
        setOnClickListener { onClose() }
        if (video) {
            setBackgroundColor(bg ?: Color.BLACK)
            player = AerialView(kit.ctx, bg != null, blend).also {
                it.fitMode = fit; addView(it, LayoutParams(-1, -1))
                val cover = View(kit.ctx).apply { setBackgroundColor(Color.BLACK) }
                addView(cover, LayoutParams(-1, -1)); it.cover = cover
            }
            val st = kit.text("", 12f, Color.argb(150, 255, 255, 255)).apply { setShadowLayer(4f, 0f, 1f, Color.BLACK) }
            addView(st, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(20), kit.dp(14), 0, 0) })
            val hide = Runnable { st.animate().alpha(0f).setDuration(600).start() }
            st.alpha = 0f
            // só mostra erros (a linha de estado já não aparece quando tudo corre bem)
            player?.onStatus = { t -> if (t.startsWith("Erro")) { st.removeCallbacks(hide); st.text = t; st.animate().cancel(); st.alpha = 1f; st.postDelayed(hide, 8000) } }
            // a hora e a data trocam de lado (esquerda/direita) ao acaso a cada vídeo e de 90 em 90 s, para não ficarem sempre no mesmo sítio
            player?.onNextClip = { moveOverlay() }
            addView(View(kit.ctx).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0))) },
                LayoutParams(-1, kit.dp(kit.clamp(220f, kit.hdp * 0.42f, 420f)), Gravity.BOTTOM))
            right = kotlin.random.Random.nextBoolean()
            overlay.alignRight = right
            addView(overlay, LayoutParams(overlay.fixedW, overlay.fixedH, Gravity.BOTTOM or (if (right) Gravity.END else Gravity.START)).apply { leftMargin = kit.dp(48); rightMargin = kit.dp(48); bottomMargin = kit.dp(36) })
        } else {
            addView(overlay, LayoutParams(overlay.fixedW, overlay.fixedH, Gravity.CENTER))
        }
        refresh()
    }

    private fun moveOverlay() {
        if (!video) return
        val toRight = kotlin.random.Random.nextBoolean()
        if (toRight == right) return
        overlay.animate().cancel()
        overlay.animate().alpha(0f).setDuration(350).withEndAction {
            right = toRight; overlay.alignRight = toRight
            (overlay.layoutParams as LayoutParams).gravity = Gravity.BOTTOM or (if (toRight) Gravity.END else Gravity.START)
            overlay.requestLayout()
            overlay.animate().alpha(1f).setDuration(500).start()
        }.start()
    }

    /** A lista de vídeos chegou (ou já estava em cache): começa a reproduzir. */
    fun startVideo() { player?.play(Aerials.shuffled()) }

    private fun refresh() {
        val now = java.util.Date()
        val tm = java.text.SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Tr.locale).format(now)
        val dt = java.text.SimpleDateFormat("EEEE d MMMM", Tr.locale).format(now).replaceFirstChar { it.uppercase() }
        var info = ""
        if (!video) {
            val parts = ArrayList<String>()
            Ha.home?.weather?.let { Ha.states[it] }?.let { w -> val t = w.attrs.optDouble("temperature", Double.NaN); if (!t.isNaN()) parts += "${Math.round(t)}°" }
            val lit = Ha.home?.rooms.orEmpty().flatMap { r -> Fmt.switchables(r.devices.filter { !it.hidden }.map { it.id }) }.count { Fmt.isOn(it) }
            if (lit > 0) parts += Tr.pl(lit, "luz acesa", "luzes acesas", "light on", "lights on")
            info = parts.joinToString("   ·   ")
        }
        overlay.set(tm, dt, info)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, 15_000)
        if (video) { postDelayed(move, 90_000); if (Aerials.count > 0) startVideo(); return }
        // deriva lenta para não queimar o painel
        drift = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = LinearInterpolator()
            addUpdateListener { val f = it.animatedValue as Float; overlay.translationX = (-0.03f + 0.06f * f) * kit.wdp * kit.density; overlay.translationY = (-0.03f + 0.06f * f) * kit.hdp * kit.density }
            start()
        }
    }

    override fun onDetachedFromWindow() { removeCallbacks(tick); removeCallbacks(move); drift?.cancel(); player?.release(); super.onDetachedFromWindow() }
}

/** Reproduz os vídeos em sequência, sem som, ocupando o ecrã todo (corta as margens como "preencher") e com fade entre eles. */
class AerialView(ctx: Context, private val hasBg: Boolean = false, blend: String = "white") : TextureView(ctx), TextureView.SurfaceTextureListener {
    /** "white": multiplica (o branco do clipe passa à cor de fundo, as linhas pretas ficam); "black": clareia (o preto passa à cor). */
    private val lighten = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(if (blend == "black") android.graphics.PorterDuff.Mode.LIGHTEN else android.graphics.PorterDuff.Mode.MULTIPLY) }
    private var mp: MediaPlayer? = null
    private var cp: CodecPlayer? = null
    /** Depois de o leitor alternativo funcionar uma vez, vai-se direto a ele (o MediaPlayer já recusou estes ficheiros). */
    private var preferCodec = false
    private var vw = 0f
    private var vh = 0f
    private var queue: ArrayDeque<Aerials.Clip> = ArrayDeque()
    private var failures = 0
    private var wantPlay = false
    private var released = false

    /** Estado para mostrar no ecrã (nome do clipe, erros): ajuda a perceber porque fica preto. */
    var onStatus: ((String) -> Unit)? = null
    /** Chamado quando começa a carregar o clipe seguinte (a cortina está preta). */
    var onNextClip: (() -> Unit)? = null

    /** Cortina preta por cima do vídeo: os fades entre vídeos animam-na (a vista de vídeo fica sempre opaca). */
    var cover: View? = null
    private var covered = true
    init { surfaceTextureListener = this; isOpaque = true }

    private fun reveal(ms: Long) { covered = false; cover?.animate()?.cancel(); cover?.animate()?.alpha(0f)?.setDuration(ms)?.start() }
    private fun hide(ms: Long, end: () -> Unit) {
        covered = true
        val cv = cover
        if (cv == null || cv.alpha >= 0.99f) { end(); return }
        cv.animate().cancel(); cv.animate().alpha(1f).setDuration(ms).withEndAction(end).start()
    }

    fun play(list: List<Aerials.Clip>) {
        if (list.isEmpty()) return
        queue = ArrayDeque(list); wantPlay = true
        if (isAvailable) next()
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { if (wantPlay && mp == null) next() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { fit() }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    /** Como o vídeo ocupa o ecrã: "height" = pela altura do ecrã (não corta em cima nem em baixo), "fill" = preenche (corta), "all" = vê-se inteiro (barras). */
    var fitMode = "height"

    private fun fit() {
        if (vw <= 0 || vh <= 0 || width == 0 || height == 0) return
        val sc = when (fitMode) {
            "fill" -> maxOf(width / vw, height / vh)
            "all" -> minOf(width / vw, height / vh)
            else -> height / vh
        }
        val m = Matrix(); m.setScale(vw * sc / width, vh * sc / height, width / 2f, height / 2f)
        setTransform(m)
    }

    /** O MediaPlayer recusou o ficheiro: tenta com o MediaCodec (descodificador de software incluído). */
    private fun fallback(clip: Aerials.Clip, why: String) {
        val st = surfaceTexture ?: return
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        val name = clip.id.removePrefix("local:")
        onStatus?.invoke(if (why == "direto") "A carregar $name…" else "MediaPlayer falhou ($why); a tentar o descodificador alternativo…")
        val me = object : CodecPlayer(context, clip.url, Surface(st),
            { w, h -> post { vw = w.toFloat(); vh = h.toFloat(); fit() } },
            { dec -> post { failures = 0; preferCodec = true; onStatus?.invoke("$name  ${vw.toInt()}×${vh.toInt()}  [$dec]"); reveal(1000) } },
            { post { if (cp != null) hide(900) { next() } } },
            { err -> post { failures++; onStatus?.invoke("Erro em $name: $err"); if (failures < 4) postDelayed({ next() }, 1500) } },
            { post { if (cp != null && !covered) hide(1100) {} } },
        ) {}
        cp = me
        me.start()
    }

    private fun next() {
        if (released) return
        if (queue.isEmpty()) { queue = ArrayDeque(Aerials.shuffled()); if (queue.isEmpty()) return }
        val clip = queue.removeFirst()
        val st = surfaceTexture ?: return
        cp?.stop(); cp = null
        onNextClip?.invoke()
        if (preferCodec) { fallback(clip, "direto"); return }
        // os clipes com transparência chegam com o fundo preto (o Android ignora o canal alfa): "clarear" deixa ver a cor de fundo nesse preto
        try { mp?.release() } catch (_: Exception) {}
        val p = MediaPlayer()
        mp = p
        try {
            p.setSurface(Surface(st))
            if (clip.url.startsWith("content:")) p.setDataSource(context, android.net.Uri.parse(clip.url)) else p.setDataSource(clip.url)
            p.setVolume(0f, 0f)
            onStatus?.invoke("A carregar " + clip.id.removePrefix("local:") + "…")
            p.setOnVideoSizeChangedListener { m, w, h -> vw = w.toFloat(); vh = h.toFloat(); fit(); onStatus?.invoke(clip.id.removePrefix("local:") + "  " + w + "×" + h) }
            p.setOnPreparedListener { if (mp === it) { failures = 0; fit(); it.start(); postDelayed({ if (mp === it) reveal(700) }, 1500) } }
            p.setOnInfoListener { m, what, _ -> if (mp === m && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) reveal(1000); false }
            p.setOnCompletionListener { m -> if (mp === m) hide(900) { next() } }
            p.setOnErrorListener { _, what, extra -> post { fallback(clip, "$what/$extra") }; true }
            p.prepareAsync()
        } catch (e: Exception) { post { fallback(clip, e.javaClass.simpleName + " " + (e.message ?: "")) } }
    }

    fun release() {
        released = true; wantPlay = false
        try { mp?.release() } catch (_: Exception) {}
        mp = null
        cp?.stop(); cp = null
    }
}

/** Hora grande, data e (opcional) uma linha de informação: uma só vista de tamanho fixo que desenha o texto à mão (sem TextView, sem medir). */
class TimeOverlay(private val kit: Kit, sizeSp: Float, h24: Boolean, private val center: Boolean = false) : View(kit.ctx) {
    private val sd = kit.ctx.resources.displayMetrics.scaledDensity
    private val big = sizeSp * sd
    private val small = (if (center) 26f else 24f) * sd
    val fixedW = if (center) (kit.wdp * kit.density * 0.92f).toInt() else (big * (if (h24) 4.2f else 6.4f)).toInt().coerceAtLeast(kit.dp(300))
    val fixedH = (big * 1.55f + small * (if (center) 3.4f else 1.2f)).toInt()
    var alignRight = false
        set(v) { field = v; invalidate() }
    private var time = ""
    private var date = ""
    private var info = ""
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = kit.font(200, true); textSize = big }
    private val pd = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = kit.font(400, false); textSize = small }

    fun set(t: String, d: String, i: String = "") { if (t != time || d != date || i != info) { time = t; date = d; info = i; invalidate() } }

    private fun shadowed(c: Canvas, s: String, x: Float, y: Float, p: Paint, color: Int) {
        val off = kit.dp(2).toFloat()
        for (k in 1..3) { p.color = Color.argb(60, 0, 0, 0); c.drawText(s, x + off * k * 0.6f, y + off * k, p) }
        p.color = color; c.drawText(s, x, y, p)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(fixedW, fixedH)

    override fun onDraw(c: Canvas) {
        val align = if (center) Paint.Align.CENTER else if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        val x = when (align) { Paint.Align.CENTER -> fixedW / 2f; Paint.Align.RIGHT -> fixedW.toFloat(); else -> 0f }
        pt.textAlign = align; pd.textAlign = align
        val y1 = big * 1.05f
        shadowed(c, time, x, y1, pt, Color.argb(245, 255, 255, 255))
        shadowed(c, date, x, y1 + small * 1.5f, pd, Color.argb(200, 255, 255, 255))
        if (info.isNotBlank()) shadowed(c, info, x, y1 + small * 3.1f, pd, Color.argb(140, 255, 255, 255))
    }
}

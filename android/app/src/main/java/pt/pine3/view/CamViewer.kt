package pt.pine3.view

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Câmara ao vivo (imagem atualizada cerca de 1 vez por segundo), com botão para abrir a porta e "+2 min". */
class CamViewer(private val act: Activity, private val kit: Kit, private val camId: String, private val name: String, private val doors: List<String>) {
    private val dialog = PokeDialog(act).apply { closeOnIdle = false }
    private val h = Handler(Looper.getMainLooper())
    private val img = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.argb(70, 0, 0, 0)) }
    private val timer = kit.text("", 13f, Color.WHITE, 600).apply { background = kit.rounded(Color.argb(170, 20, 16, 24), 20); setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(6)) }
    private val elapsed = kit.text("0:00", 15f, Color.argb(190, 255, 255, 255))
    private var started = 0L
    private var until = 0L
    private var armed: String? = null
    private var armUntil = 0L
    private val loading = LoadingOverlay(kit, Tr.t("A carregar a câmara…"))
    private var last: android.graphics.Bitmap? = null
    private var open = true

    private val tick = object : Runnable {
        override fun run() {
            if (!open) return
            val now = System.currentTimeMillis()
            if (now >= until) { dismiss(); return }
            MotionService.poke() // a ver o vídeo conta como atividade: o ecrã não desliga
            val s = ((now - started) / 1000).toInt(); elapsed.text = "${s / 60}:${"%02d".format(s % 60)}"
            val left = ((until - now) / 1000).toInt(); timer.text = Tr.t("fecha em ") + "${left / 60}:${"%02d".format(left % 60)}"
            val pic = Fmt.state(camId)?.attrs?.optString("entity_picture").orEmpty()
            Ha.fetchImage(pic.ifBlank { "/api/camera_proxy/$camId" }) { b -> if (b != null && open) { img.setImageBitmap(b); last = b; loading.visibility = View.GONE } }
            h.postDelayed(this, 1000)
        }
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        started = System.currentTimeMillis(); until = started + 120_000
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = kit.sheetBg(32)
            setPadding(kit.dp(22), kit.dp(20), kit.dp(22), kit.dp(22)); isClickable = true
        }
        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(FrameLayout(act).apply {
            background = kit.circle(Color.parseColor("#64D2FF"))
            addView(kit.icon("mdi:cctv", kit.ink, 26), FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(14) })
        val t = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        t.addView(kit.text(name, 24f, Color.WHITE, 400, display = true))
        val live = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        live.addView(kit.text("● " + Tr.t("Em direto"), 12f, Color.WHITE, 700).apply { background = kit.rounded(Color.parseColor("#E5382B"), 20); setPadding(kit.dp(10), kit.dp(3), kit.dp(10), kit.dp(3)) })
        live.addView(elapsed, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
        t.addView(live, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
        hd.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(kit.roundButton("mdi:close", 44, Color.argb(40, 255, 255, 255), Color.WHITE) { dismiss() })
        card.addView(hd)

        val body = LinearLayout(act).apply { setPadding(0, kit.dp(14), 0, 0) }
        val pic = FrameLayout(act)
        pic.addView(img, FrameLayout.LayoutParams(-1, -1))
        pic.addView(loading, FrameLayout.LayoutParams(-1, -1))
        pic.addView(timer, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, kit.dp(14), kit.dp(14)) })
        pic.clipToOutline = true
        pic.outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, kit.dp(20).toFloat()) } }
        body.addView(pic, LinearLayout.LayoutParams(0, kit.dp(kit.clamp(220f, kit.hdp - 230f, 560f)), 1f))
        val side = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val doorsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        side.addView(doorsBox)
        fun fillDoors() {
            doorsBox.removeAllViews()
            Doors.forCamera(camId).ifEmpty { doors }.forEach { d ->
                val label = TextView(act)
                val b = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = kit.ripple(kit.rounded(kit.accent, 24)); setPadding(kit.dp(14), kit.dp(18), kit.dp(14), kit.dp(18)); isClickable = true
                    addView(kit.icon("mdi:door-open", kit.ink, 30), LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)))
                    addView(kit.text(Tr.t("Abrir"), 19f, kit.ink, 600, display = true))
                    addView(label.apply { text = Fmt.name(d) + " · " + Tr.t("toca duas vezes"); setTextColor(Color.argb(170, 42, 34, 31)); textSize = 12f })
                }
                b.setOnClickListener {
                    val now = System.currentTimeMillis()
                    if (armed == d && now < armUntil) {
                        armed = null
                        if (Fmt.domain(d) == "button") Ha.call("button", "press", listOf(d)) else Ha.call("lock", "open", listOf(d))
                        label.text = Tr.t("Abrir") + " ✓"
                    } else { armed = d; armUntil = now + 4000 }
                }
                doorsBox.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(12) })
            }
        }
        fillDoors()
        side.addView(LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = kit.ripple(kit.rounded(Color.argb(36, 255, 255, 255), 24)); setPadding(kit.dp(14), kit.dp(18), kit.dp(14), kit.dp(18)); isClickable = true
            setOnClickListener { until += 120_000 }
            addView(kit.icon("mdi:timer-outline", Color.WHITE, 30), LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)))
            addView(kit.text("+2 min", 19f, Color.WHITE, 600, display = true))
            addView(kit.text(Tr.t("mantém ao vivo"), 12f, Color.argb(166, 255, 255, 255)))
        }, LinearLayout.LayoutParams(-1, -2))
        side.addView(kit.text("✎  " + Tr.t("Escolher portas"), 13f, Color.argb(190, 255, 255, 255), 600).apply {
            setPadding(kit.dp(4), kit.dp(12), kit.dp(4), kit.dp(8)); isClickable = true
            setOnClickListener { DoorPicker(act, kit, camId) { fillDoors() }.show() }
        })
        side.addView(View(act), LinearLayout.LayoutParams(-1, 0, 1f))
        side.addView(kit.text(Tr.t("Fechar para o vídeo."), 12f, Color.argb(166, 255, 255, 255)).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        body.addView(side, LinearLayout.LayoutParams(kit.dp(200), -1).apply { marginStart = kit.dp(16) })
        card.addView(body)

        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(160, 10, 4, 12)); isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(360f, kit.wdp - 60f, 1180f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        dialog.setOnDismissListener { open = false; h.removeCallbacks(tick); last?.let { CamCache.save(act, camId, it) } }
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.9f)
        h.post(tick)
    }

    fun dismiss() { open = false; if (dialog.isShowing) dialog.dismiss() }
}

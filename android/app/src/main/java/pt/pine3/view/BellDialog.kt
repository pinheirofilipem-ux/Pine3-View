package pt.pine3.view

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
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
import org.json.JSONObject

/**
 * Janela da campainha: abre logo com a última imagem da câmara da porta (ou a foto do toque, quando chega).
 * O vídeo só começa em "Ver câmara". Fecha sozinha ao fim de 90 s, ou 6 s depois de atendida.
 */
class BellDialog(
    private val act: Activity, private val kit: Kit, private val id: String, private val type: String, private val attrs: JSONObject,
    private val doors: List<String>, private val onWatch: (cam: String, name: String) -> Unit,
) {
    private val dialog = PokeDialog(act)
    private val h = Handler(Looper.getMainLooper())
    private val cam = attrs.optString("camera_entity").takeIf { type == "ring" && Ha.states.containsKey(it) } ?: ""
    private val call = attrs.optString("call_id")
    private val snap: String = if (type != "ring") "" else (listOf(attrs.optString("snapshot_entity")) + Ha.states.keys.filter { it.startsWith("image.") && it.contains("doorbell_snapshot") })
        .firstOrNull { it.isNotBlank() && Ha.states.containsKey(it) } ?: ""
    private val started = System.currentTimeMillis()
    private var end = started + 90_000
    private var status = ""
    private var picKey: String? = null
    private var open = true

    private val img = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.argb(70, 0, 0, 0)) }
    private val loading = LoadingOverlay(kit, Tr.t("À espera da foto do toque…"))
    private val when_ = kit.text("", 13f, Color.WHITE, 600).apply { background = kit.rounded(Color.argb(170, 20, 16, 24), 20); setPadding(kit.dp(12), kit.dp(6), kit.dp(12), kit.dp(6)); visibility = View.GONE }
    private val st = kit.text("", 14f, Color.argb(200, 255, 255, 255))
    private var armed: String? = null
    private var armUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!open) return
            if (System.currentTimeMillis() >= end) { dismiss(); return }
            MotionService.poke()
            val s = ((System.currentTimeMillis() - started) / 1000).toInt()
            st.text = when (status) { "answered" -> Tr.t("Atendida"); "missed" -> Tr.t("Chamada perdida"); else -> Tr.t("A tocar") + " · ${s / 60}:${"%02d".format(s % 60)}" }
            pic()
            h.postDelayed(this, 1000)
        }
    }

    /** Chamado pelo ecrã principal quando chega "answered"/"missed_call" desta chamada. */
    fun onEvent(evId: String, evType: String, a: JSONObject) {
        if (evId != id || (a.optString("call_id").isNotBlank() && call.isNotBlank() && a.optString("call_id") != call)) return
        if (evType == "answered") { status = "answered"; end = minOf(end, System.currentTimeMillis() + 6000) }
        if (evType == "missed_call") status = "missed"
    }

    private fun showFrame(b: Bitmap, label: String) {
        img.setImageBitmap(b); loading.visibility = View.GONE
        when_.text = label; when_.visibility = View.VISIBLE
    }

    /** A foto desta chamada substitui a última imagem da câmara assim que a entidade image.* tem o call_id certo. */
    private fun pic() {
        if (snap.isBlank()) return
        val s = Ha.states[snap] ?: return
        val url = s.attrs.optString("entity_picture")
        val mine = url.isNotBlank() && s.state != "unavailable" && (call.isBlank() || s.attrs.opt("call_id")?.toString() == call)
        if (mine && picKey != s.state) {
            picKey = s.state
            Ha.fetchImage(url) { b -> if (b != null && open) showFrame(b, Tr.t("Foto do toque")) }
        }
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val title = if (type == "floor_call") Tr.t("Tocaram à porta de casa") else Tr.t("Tocaram à porta")
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = kit.sheetBg(32)
            setPadding(kit.dp(22), kit.dp(20), kit.dp(22), kit.dp(22)); isClickable = true
        }
        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(FrameLayout(act).apply {
            background = kit.circle(kit.accent)
            addView(kit.icon("mdi:bell-ring", kit.ink, 26), FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(14) })
        val t = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        t.addView(kit.text(title, 24f, Color.WHITE, 400, display = true)); t.addView(st)
        hd.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(kit.roundButton("mdi:close", 44, Color.argb(40, 255, 255, 255), Color.WHITE) { dismiss() })
        card.addView(hd)

        val body = LinearLayout(act).apply { setPadding(0, kit.dp(14), 0, 0) }
        if (cam.isNotBlank() || snap.isNotBlank()) {
            val pic = FrameLayout(act)
            pic.addView(img, FrameLayout.LayoutParams(-1, -1))
            val saved = if (cam.isNotBlank()) CamCache.load(act, cam) else null
            if (saved != null) {
                val stamp = CamCache.stamp(act, cam)
                showFrame(saved, Tr.t("Última imagem") + " · " + java.text.SimpleDateFormat("d MMM HH:mm", Tr.locale).format(java.util.Date(stamp)))
            } else pic.addView(loading, FrameLayout.LayoutParams(-1, -1))
            if (cam.isNotBlank()) {
                val play = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(Color.argb(190, 20, 16, 24), 40)); setPadding(kit.dp(10), kit.dp(10), kit.dp(22), kit.dp(10)); isClickable = true
                    addView(FrameLayout(act).apply { background = kit.circle(Color.WHITE); addView(kit.icon("mdi:play", kit.ink, 24), FrameLayout.LayoutParams(kit.dp(24), kit.dp(24), Gravity.CENTER)) }, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)).apply { marginEnd = kit.dp(12) })
                    addView(kit.text(Tr.t("Ver câmara"), 18f, Color.WHITE, 600, display = true))
                    Elastic.press(this, 0.95f)
                    setOnClickListener { dismiss(); onWatch(cam, Fmt.name(cam)) }
                }
                pic.addView(play, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            }
            pic.addView(when_, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(kit.dp(14), 0, 0, kit.dp(14)) })
            pic.clipToOutline = true
            pic.outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, kit.dp(20).toFloat()) } }
            body.addView(pic, LinearLayout.LayoutParams(0, kit.dp(kit.clamp(220f, kit.hdp - 260f, 520f)), 1f))
        }
        val side = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val doorsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        side.addView(doorsBox)
        fun fillDoors() {
            doorsBox.removeAllViews()
            // a abertura da própria campainha, senão as portas da câmara (as que escolheres valem também no painel web); nada na chamada ao andar
            val rel = attrs.optString("release_entity").takeIf { Ha.states.containsKey(it) }
            val list = if (type == "floor_call") emptyList() else if (rel != null) listOf(rel) else if (cam.isNotBlank()) Doors.forCamera(cam) else doors
            list.forEach { d ->
                val label = TextView(act)
                val b = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = kit.ripple(kit.rounded(kit.accent, 24)); setPadding(kit.dp(14), kit.dp(18), kit.dp(14), kit.dp(18)); isClickable = true
                    addView(kit.icon("mdi:door-open", kit.ink, 30), LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)))
                    addView(kit.text(Tr.t("Abrir"), 19f, kit.ink, 600, display = true))
                    addView(label.apply { text = Fmt.name(d) + " · " + Tr.t("toca duas vezes"); setTextColor(Color.argb(170, 42, 34, 31)); textSize = 12f })
                    Elastic.press(this, 0.95f)
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
        if (cam.isNotBlank() && type != "floor_call") side.addView(kit.text("✎  " + Tr.t("Escolher portas"), 13f, Color.argb(190, 255, 255, 255), 600).apply {
            setPadding(kit.dp(4), kit.dp(8), kit.dp(4), kit.dp(8)); isClickable = true
            setOnClickListener { DoorPicker(act, kit, cam) { fillDoors() }.show() }
        })
        body.addView(side, LinearLayout.LayoutParams(if (cam.isBlank() && snap.isBlank()) kit.dp(320) else kit.dp(200), -2).apply { if (cam.isNotBlank() || snap.isNotBlank()) marginStart = kit.dp(16) })
        card.addView(body)

        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(160, 10, 4, 12)); isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(360f, kit.wdp - 60f, 1100f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        dialog.setOnDismissListener { open = false; h.removeCallbacks(tick) }
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.9f)
        h.post(tick)
    }

    val isShowing get() = open && dialog.isShowing
    fun dismiss() { open = false; if (dialog.isShowing) dialog.dismiss() }
}

/** Portas ao lado de uma câmara (mesmas regras do painel web). */
fun doorsForCamera(camId: String): List<String> = Doors.forCamera(camId)

/** Mostra o pop-up da campainha como se tivessem tocado agora (a primeira campainha do HA, ou a entidade das regras). Devolve false se não houver nenhuma. */
fun testBell(act: Activity, kit: Kit): Boolean {
    val id = Ha.states.keys.filter { it.startsWith("event.") && Ha.states[it]?.attrs?.optString("device_class") == "doorbell" }.sorted().firstOrNull()
        ?: Prefs(act).wakeRules.firstOrNull()?.entity?.takeIf { Ha.states.containsKey(it) } ?: return false
    val attrs = JSONObject(Ha.states[id]?.attrs?.toString() ?: "{}").put("event_type", "ring")
    val rel = attrs.optString("release_entity").takeIf { Ha.states.containsKey(it) }
    val doors = if (rel != null) listOf(rel) else doorsForCamera(attrs.optString("camera_entity"))
    BellDialog(act, kit, id, "ring", attrs, doors) { c, n -> CamViewer(act, kit, c, n, doorsForCamera(c)).show() }.show()
    return true
}

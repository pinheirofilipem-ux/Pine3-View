package pt.pine3.view

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONObject

/** Painel de detalhes de um dispositivo (o "sheet" do painel web): controlos conforme o tipo e as outras entidades do dispositivo. */
class DeviceSheet(private val act: Activity, private val kit: Kit, private val id: String, private val dev: Dev?) {
    private val dialog = Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar)
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val listener = object : Ha.Listener {
        override fun onStates(changed: Set<String>) { if (changed.isEmpty() || changed.any { it == id || dev?.members?.contains(it) == true }) schedule() }
    }
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val redraw = Runnable { render() }
    private var dragging = false

    private val sheetBg = Color.parseColor("#F0221A26")

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.rounded(sheetBg, 32)
            setPadding(kit.dp(24), kit.dp(22), kit.dp(24), kit.dp(24))
            addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(body) }, LinearLayout.LayoutParams(-1, -2))
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(150, 10, 4, 12))
            isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(320f, kit.wdp - 48f, 560f)), -2, Gravity.CENTER))
        }
        card.isClickable = true
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        dialog.setOnDismissListener { Ha.listeners -= listener; h.removeCallbacks(redraw) }
        Ha.listeners += listener
        render()
        dialog.show()
    }

    fun dismiss() = dialog.dismiss()
    private fun schedule() { if (!dragging) { h.removeCallbacks(redraw); h.postDelayed(redraw, 80) } }

    private fun call(domain: String, service: String, data: JSONObject? = null) = Ha.call(domain, service, listOf(id), data)

    private fun pill(label: String, active: Boolean, onClick: () -> Unit) =
        kit.text(label, 15f, if (active) kit.ink else Color.WHITE, 600).apply {
            gravity = Gravity.CENTER; background = kit.ripple(kit.rounded(if (active) kit.cream else Color.argb(36, 255, 255, 255), 40))
            setPadding(kit.dp(18), kit.dp(11), kit.dp(18), kit.dp(11)); isClickable = true; setOnClickListener { onClick() }
        }

    private fun row(vararg v: View) = LinearLayout(act).apply {
        gravity = Gravity.CENTER; v.forEachIndexed { i, x -> addView(x, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = kit.dp(10) }) }
    }

    private fun sub(t: String) = kit.text(t.uppercase(), 12f, Color.argb(166, 255, 255, 255), 600, spacing = 0.14f)

    private fun chips(label: String, list: List<String>, cur: String?, onPick: (String) -> Unit) {
        if (list.size < 2) return
        body.addView(sub(label), lp(top = 16))
        val flow = Grid(act, kit.dp(110), kit.dp(44), kit.dp(8))
        list.forEach { m -> flow.addView(pill(Tr.t(Fmt.STATE_PT[m] ?: m), m == cur) { onPick(m) }) }
        body.addView(flow, lp(top = 8))
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(top) }

    private fun slider(pct: Int, label: String, onSet: (Int) -> Unit) {
        val sb = SeekBar(act).apply {
            max = 100; progress = pct
            progressTintList = ColorStateList.valueOf(kit.accent); thumbTintList = ColorStateList.valueOf(kit.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(60, 255, 255, 255))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
                override fun onStopTrackingTouch(s: SeekBar) { dragging = false; onSet(s.progress) }
            })
        }
        body.addView(sub(label), lp(top = 18))
        body.addView(sb, lp(top = 6))
    }

    private fun render() {
        body.removeAllViews()
        val s = Fmt.state(id) ?: run { dismiss(); return }
        val a = s.attrs; val d = Fmt.domain(id); val on = Fmt.isOn(id)
        // cabeçalho
        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(FrameLayout(act).apply {
            background = kit.circle(if (on) Fmt.accent(this@DeviceSheet.id, kit.accent) else Color.argb(40, 255, 255, 255))
            addView(kit.icon(Fmt.icon(this@DeviceSheet.id, dev?.kind ?: d), if (on) kit.ink else Color.WHITE, 26), FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(14) })
        val t = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        t.addView(kit.text(dev?.name ?: Fmt.name(id), 24f, Color.WHITE, 400, display = true))
        t.addView(kit.text(Fmt.sub(id), 15f, Color.argb(166, 255, 255, 255)))
        hd.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(kit.roundButton("mdi:close", 40, Color.argb(40, 255, 255, 255), Color.WHITE) { dismiss() })
        body.addView(hd)

        when (d) {
            "light" -> {
                val modes = (0 until (a.optJSONArray("supported_color_modes")?.length() ?: 0)).map { a.optJSONArray("supported_color_modes")!!.getString(it) }
                if (modes.any { it != "onoff" }) {
                    slider(if (on) Math.round(a.optDouble("brightness", 255.0) / 2.55).toInt() else 0, Tr.t("Luminosidade")) { p ->
                        if (p == 0) call("light", "turn_off") else call("light", "turn_on", JSONObject().put("brightness_pct", p))
                    }
                }
                if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
                    body.addView(sub(Tr.t("Cor")), lp(top = 18))
                    val sw = LinearLayout(act)
                    listOf("#ffffff", "#ffd28a", "#ff8a5c", "#ff5ca8", "#8a7cff", "#5cc8ff", "#5cff9d").forEach { c ->
                        sw.addView(View(act).apply {
                            background = kit.circle(Color.parseColor(c)); isClickable = true
                            setOnClickListener {
                                val col = Color.parseColor(c)
                                call("light", "turn_on", JSONObject().put("rgb_color", org.json.JSONArray(listOf(Color.red(col), Color.green(col), Color.blue(col)))))
                            }
                        }, LinearLayout.LayoutParams(kit.dp(38), kit.dp(38)).apply { marginEnd = kit.dp(10) })
                    }
                    body.addView(sw, lp(top = 8))
                }
                body.addView(row(pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("light", if (on) "turn_off" else "turn_on") }), lp(top = 20))
            }
            "cover" -> {
                if (a.has("current_position") && !a.isNull("current_position")) slider(a.optInt("current_position"), Tr.t("Posição")) { p -> call("cover", "set_cover_position", JSONObject().put("position", p)) }
                body.addView(row(pill(Tr.t("Abrir"), false) { call("cover", "open_cover") }, pill(Tr.t("Parar"), false) { call("cover", "stop_cover") }, pill(Tr.t("Fechar"), false) { call("cover", "close_cover") }), lp(top = 20))
            }
            "climate" -> {
                val step = a.optDouble("target_temp_step", 0.5).takeIf { it > 0 } ?: 0.5
                val min = a.optDouble("min_temp", 7.0); val max = a.optDouble("max_temp", 35.0)
                val tgt = a.optDouble("temperature", Double.NaN)
                val cur = a.optDouble("current_temperature", Double.NaN)
                val big = kit.text(if (tgt.isNaN()) "–" else Fmt.num(tgt) + "°", 64f, Color.WHITE, 300, display = true).apply { gravity = Gravity.CENTER }
                body.addView(big, lp(top = 16))
                if (!cur.isNaN()) body.addView(kit.text(Tr.t("Atual") + " " + Fmt.num(cur) + "°", 15f, Color.argb(166, 255, 255, 255)).apply { gravity = Gravity.CENTER }, lp(top = 2))
                if (!tgt.isNaN()) body.addView(row(
                    kit.roundButton("mdi:minus", 56, Color.argb(36, 255, 255, 255), Color.WHITE) { call("climate", "set_temperature", JSONObject().put("temperature", (tgt - step).coerceIn(min, max))) },
                    kit.roundButton("mdi:plus", 56, Color.argb(36, 255, 255, 255), Color.WHITE) { call("climate", "set_temperature", JSONObject().put("temperature", (tgt + step).coerceIn(min, max))) },
                ), lp(top = 16))
                fun list(k: String) = (0 until (a.optJSONArray(k)?.length() ?: 0)).map { a.optJSONArray(k)!!.getString(it) }
                chips(Tr.t("Modo"), list("hvac_modes"), s.state) { call("climate", "set_hvac_mode", JSONObject().put("hvac_mode", it)) }
                chips(Tr.t("Velocidade"), list("fan_modes"), a.optString("fan_mode")) { call("climate", "set_fan_mode", JSONObject().put("fan_mode", it)) }
                chips(Tr.t("Oscilação"), list("swing_modes"), a.optString("swing_mode")) { call("climate", "set_swing_mode", JSONObject().put("swing_mode", it)) }
                chips(Tr.t("Perfil"), list("preset_modes"), a.optString("preset_mode")) { call("climate", "set_preset_mode", JSONObject().put("preset_mode", it)) }
            }
            "media_player" -> {
                val f = a.optInt("supported_features", 0)
                fun has(b: Int) = (f and b) != 0
                if (a.optString("media_title").isNotEmpty()) {
                    body.addView(kit.text(a.optString("media_title"), 18f, Color.WHITE, 600).apply { gravity = Gravity.CENTER }, lp(top = 16))
                    body.addView(kit.text(a.optString("media_artist").ifBlank { a.optString("app_name") }, 14f, Color.argb(166, 255, 255, 255)).apply { gravity = Gravity.CENTER }, lp(top = 2))
                }
                val tr = ArrayList<View>()
                if (has(16)) tr += kit.roundButton("mdi:skip-previous", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("media_player", "media_previous_track") }
                if (has(1) || has(16384)) tr += kit.roundButton(if (s.state == "playing") "mdi:pause" else "mdi:play", 60, kit.cream, kit.ink) { call("media_player", "media_play_pause") }
                if (has(32)) tr += kit.roundButton("mdi:skip-next", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("media_player", "media_next_track") }
                if (on && tr.isNotEmpty()) body.addView(row(*tr.toTypedArray()), lp(top = 18))
                if (on && has(4) && a.has("volume_level") && !a.isNull("volume_level")) slider(Math.round(a.optDouble("volume_level") * 100).toInt(), Tr.t("Volume")) { p -> call("media_player", "volume_set", JSONObject().put("volume_level", p / 100.0)) }
                val src = (0 until (a.optJSONArray("source_list")?.length() ?: 0)).map { a.optJSONArray("source_list")!!.getString(it) }
                if (on) chips(Tr.t("Entrada"), src, a.optString("source")) { call("media_player", "select_source", JSONObject().put("source", it)) }
                if (has(128) || has(256) || Fmt.isTv(id)) body.addView(row(pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("media_player", if (on) "turn_off" else "turn_on") }), lp(top = 20))
            }
            "fan" -> {
                if (a.has("percentage") && !a.isNull("percentage")) slider(if (on) a.optInt("percentage") else 0, Tr.t("Velocidade")) { p -> if (p == 0) call("fan", "turn_off") else call("fan", "set_percentage", JSONObject().put("percentage", p)) }
                val pm = (0 until (a.optJSONArray("preset_modes")?.length() ?: 0)).map { a.optJSONArray("preset_modes")!!.getString(it) }
                chips(Tr.t("Modo"), pm, if (on) a.optString("preset_mode") else null) { call("fan", "set_preset_mode", JSONObject().put("preset_mode", it)) }
                body.addView(row(pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("fan", if (on) "turn_off" else "turn_on") }), lp(top = 20))
            }
            "vacuum" -> body.addView(row(
                pill(Tr.t("Iniciar"), false) { call("vacuum", "start") }, pill(Tr.t("Pausar"), false) { call("vacuum", "pause") }, pill(Tr.t("Base"), false) { call("vacuum", "return_to_base") },
            ), lp(top = 20))
            "lock" -> body.addView(row(pill(Tr.t(if (s.state == "locked") "Destrancar" else "Trancar"), false) { call("lock", if (s.state == "locked") "unlock" else "lock") }), lp(top = 20))
            else -> if (Fmt.isToggle(id)) body.addView(row(pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { Fmt.tap(id) }), lp(top = 20))
        }

        // outras entidades do dispositivo
        val members = dev?.members.orEmpty().filter { Fmt.state(it) != null }
        if (members.isNotEmpty()) {
            body.addView(sub(Tr.t("Dispositivo")), lp(top = 22))
            members.forEach { m ->
                val r = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL; background = kit.rounded(Color.argb(24, 255, 255, 255), 16); setPadding(kit.dp(14), kit.dp(10), kit.dp(14), kit.dp(10)) }
                r.addView(kit.text(Fmt.name(m).removePrefix(dev?.name ?: "").trim().ifBlank { Fmt.name(m) }, 15f, Color.WHITE).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(kit.text(Fmt.sub(m), 15f, if (Fmt.isOn(m)) kit.accent else Color.argb(166, 255, 255, 255), 600))
                if (Fmt.isToggle(m) && Fmt.domain(m) != "media_player") { r.isClickable = true; r.setOnClickListener { Fmt.tap(m) } }
                body.addView(r, lp(top = 8))
            }
        }
    }
}

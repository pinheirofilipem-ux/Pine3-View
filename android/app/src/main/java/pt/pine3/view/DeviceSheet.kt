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
import org.json.JSONObject

/**
 * Painel de detalhes de um dispositivo (o "sheet" do painel web): à esquerda o controlo principal (anel, deslizador,
 * transporte), à direita os modos, as leituras e os controlos das outras entidades do dispositivo.
 */
class DeviceSheet(private val act: Activity, private val kit: Kit, private val id: String, private val dev: Dev?) {
    private val dialog = PokeDialog(act)
    private val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var main = LinearLayout(act)
    private var side = LinearLayout(act)
    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private val redraw = Runnable { render() }
    private var dragging = false
    private var openSel: String? = null
    private val wide = kit.wdp >= 820f
    private val listener = object : Ha.Listener {
        override fun onStates(changed: Set<String>) { if (changed.isEmpty() || changed.any { it == id || dev?.members?.contains(it) == true }) schedule() }
    }
    private val muted = Color.argb(166, 255, 255, 255)

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.rounded(Color.parseColor("#F0221A26"), 32)
            setPadding(kit.dp(26), kit.dp(22), kit.dp(26), kit.dp(26)); isClickable = true
            addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(content) }, LinearLayout.LayoutParams(-1, -2))
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(150, 10, 4, 12)); isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(320f, kit.wdp - 48f, 1350f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        dialog.setOnDismissListener { Ha.listeners -= listener; h.removeCallbacks(redraw) }
        Ha.listeners += listener
        render()
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.9f)
    }

    fun dismiss() = dialog.dismiss()
    private fun schedule() { if (!dragging) { h.removeCallbacks(redraw); h.postDelayed(redraw, 80) } }
    private fun call(domain: String, service: String, data: JSONObject? = null) = Ha.call(domain, service, listOf(id), data)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(top) }

    private fun pill(label: String, active: Boolean, onClick: () -> Unit) =
        kit.text(label, 15f, if (active) kit.ink else Color.WHITE, 600).apply {
            gravity = Gravity.CENTER; background = kit.ripple(kit.rounded(if (active) kit.cream else Color.argb(36, 255, 255, 255), 40))
            setPadding(kit.dp(18), kit.dp(10), kit.dp(18), kit.dp(10)); isClickable = true; setOnClickListener { onClick() }; Elastic.press(this, 0.94f)
        }

    private fun row(into: LinearLayout, vararg v: View, top: Int = 16) {
        val r = LinearLayout(act).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        v.forEachIndexed { i, x -> r.addView(x, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = kit.dp(10) }) }
        into.addView(r, lp(top))
    }

    private fun sub(into: LinearLayout, t: String, top: Int = 18) = into.addView(kit.text(t.uppercase(), 12f, muted, 600, spacing = 0.14f), lp(top))

    private fun chips(into: LinearLayout, label: String, list: List<String>, cur: String?, onPick: (String) -> Unit) {
        if (list.size < 2) return
        sub(into, label)
        val g = Grid(act, kit.dp(96), kit.dp(42), kit.dp(8))
        list.forEach { m -> g.addView(pill(Tr.t(Fmt.STATE_PT[m] ?: m), m == cur) { onPick(m) }) }
        into.addView(g, lp(8))
    }

    private fun slider(into: LinearLayout, pct: Int, label: String, onSet: (Int) -> Unit) {
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
        sub(into, label); into.addView(sb, lp(6))
    }

    private fun list(a: JSONObject, k: String) = (0 until (a.optJSONArray(k)?.length() ?: 0)).map { a.optJSONArray(k)!!.getString(it) }

    private fun render() {
        content.removeAllViews()
        val s = Fmt.state(id) ?: run { dismiss(); return }
        val a = s.attrs; val d = Fmt.domain(id); val on = Fmt.isOn(id)
        main = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        side = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

        // cabeçalho
        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(FrameLayout(act).apply {
            background = kit.circle(if (on) Fmt.accent(this@DeviceSheet.id, kit.accent) else Color.argb(40, 255, 255, 255))
            addView(kit.icon(Fmt.icon(this@DeviceSheet.id, dev?.kind ?: d), if (on) kit.ink else Color.WHITE, 26), FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(14) })
        val t = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        t.addView(kit.text(dev?.name ?: Fmt.name(id), 24f, Color.WHITE, 400, display = true))
        t.addView(kit.text(Fmt.sub(id), 15f, muted))
        hd.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(kit.roundButton("mdi:close", 44, Color.argb(40, 255, 255, 255), Color.WHITE) { dismiss() })
        content.addView(hd)

        when (d) {
            "light" -> {
                val modes = list(a, "supported_color_modes")
                if (modes.any { it != "onoff" }) slider(main, if (on) Math.round(a.optDouble("brightness", 255.0) / 2.55).toInt() else 0, Tr.t("Luminosidade")) { p ->
                    if (p == 0) call("light", "turn_off") else call("light", "turn_on", JSONObject().put("brightness_pct", p))
                }
                if (modes.any { it in setOf("hs", "rgb", "rgbw", "rgbww", "xy") }) {
                    sub(side, Tr.t("Cor"), 4)
                    val sw = LinearLayout(act)
                    listOf("#ffffff", "#ffd28a", "#ff8a5c", "#ff5ca8", "#8a7cff", "#5cc8ff", "#5cff9d").forEach { c ->
                        sw.addView(View(act).apply {
                            background = kit.circle(Color.parseColor(c)); isClickable = true
                            setOnClickListener { val col = Color.parseColor(c); call("light", "turn_on", JSONObject().put("rgb_color", org.json.JSONArray(listOf(Color.red(col), Color.green(col), Color.blue(col))))) }
                        }, LinearLayout.LayoutParams(kit.dp(38), kit.dp(38)).apply { marginEnd = kit.dp(10) })
                    }
                    side.addView(sw, lp(8))
                }
                row(main, pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("light", if (on) "turn_off" else "turn_on") }, top = 20)
            }
            "cover" -> {
                if (a.has("current_position") && !a.isNull("current_position")) slider(main, a.optInt("current_position"), Tr.t("Posição")) { p -> call("cover", "set_cover_position", JSONObject().put("position", p)) }
                row(side, pill(Tr.t("Abrir"), false) { call("cover", "open_cover") }, pill(Tr.t("Parar"), false) { call("cover", "stop_cover") }, pill(Tr.t("Fechar"), false) { call("cover", "close_cover") }, top = 4)
            }
            "climate" -> {
                val step = a.optDouble("target_temp_step", 0.5).takeIf { it > 0 } ?: 0.5
                val min = a.optDouble("min_temp", 7.0); val max = a.optDouble("max_temp", 35.0)
                val tgt = a.optDouble("temperature", Double.NaN); val cur = a.optDouble("current_temperature", Double.NaN)
                val off = s.state == "off" || s.state == "unavailable"
                val act2 = if (off) "" else a.optString("hvac_action")
                val mode = mapOf("heating" to "heat", "preheating" to "heat", "cooling" to "cool", "defrosting" to "cool", "drying" to "dry", "fan" to "fan_only", "idle" to "idle")[act2] ?: s.state
                val col = if (off) Color.argb(77, 255, 255, 255) else when (mode) { "cool" -> 0xFF5AC8FA.toInt(); "dry" -> 0xFF64D2FF.toInt(); "fan_only" -> 0xFFA8B0BD.toInt(); "idle" -> Color.argb(102, 255, 255, 255); else -> 0xFFFF9F0A.toInt() }
                val ring = RingView(kit).apply {
                    frac = if (!tgt.isNaN() && !off) ((tgt - min) / (max - min).coerceAtLeast(1.0)).coerceIn(0.04, 1.0).toFloat() else 0f
                    color = col; track = Color.argb(31, 255, 255, 255); textColor = Color.WHITE; smallColor = muted
                    bigText = if (tgt.isNaN()) "–" else Fmt.num(tgt); bigSp = 54f
                    smallText = listOfNotNull(if (!cur.isNaN()) Tr.t("Atual") + " " + Fmt.num(cur) + "°" else null, if (a.has("current_humidity") && !a.isNull("current_humidity")) Math.round(a.optDouble("current_humidity")).toString() + "%" else null).joinToString(" · ")
                }
                main.addView(ring, LinearLayout.LayoutParams(kit.dp(220), kit.dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = kit.dp(10) })
                if (!tgt.isNaN()) row(main,
                    kit.roundButton("mdi:minus", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("climate", "set_temperature", JSONObject().put("temperature", (tgt - step).coerceIn(min, max))) },
                    kit.roundButton("mdi:plus", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("climate", "set_temperature", JSONObject().put("temperature", (tgt + step).coerceIn(min, max))) }, top = 8)
                chips(side, Tr.t("Modo"), list(a, "hvac_modes"), s.state) { call("climate", "set_hvac_mode", JSONObject().put("hvac_mode", it)) }
                chips(side, Tr.t("Velocidade"), list(a, "fan_modes"), a.optString("fan_mode")) { call("climate", "set_fan_mode", JSONObject().put("fan_mode", it)) }
                chips(side, Tr.t("Oscilação"), list(a, "swing_modes"), a.optString("swing_mode")) { call("climate", "set_swing_mode", JSONObject().put("swing_mode", it)) }
                chips(side, Tr.t("Perfil"), list(a, "preset_modes"), a.optString("preset_mode")) { call("climate", "set_preset_mode", JSONObject().put("preset_mode", it)) }
            }
            "media_player" -> {
                val f = a.optInt("supported_features", 0)
                fun has(b: Int) = (f and b) != 0
                if (a.optString("media_title").isNotEmpty()) {
                    main.addView(kit.text(a.optString("media_title"), 18f, Color.WHITE, 600).apply { gravity = Gravity.CENTER }, lp(16))
                    main.addView(kit.text(a.optString("media_artist").ifBlank { a.optString("app_name") }, 14f, muted).apply { gravity = Gravity.CENTER }, lp(2))
                }
                val tr = ArrayList<View>()
                if (has(16)) tr += kit.roundButton("mdi:skip-previous", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("media_player", "media_previous_track") }
                if (has(1) || has(16384)) tr += kit.roundButton(if (s.state == "playing") "mdi:pause" else "mdi:play", 60, kit.cream, kit.ink) { call("media_player", "media_play_pause") }
                if (has(32)) tr += kit.roundButton("mdi:skip-next", 52, Color.argb(36, 255, 255, 255), Color.WHITE) { call("media_player", "media_next_track") }
                if (on && tr.isNotEmpty()) row(main, *tr.toTypedArray(), top = 18)
                if (on && has(4) && a.has("volume_level") && !a.isNull("volume_level")) slider(side, Math.round(a.optDouble("volume_level") * 100).toInt(), Tr.t("Volume")) { p -> call("media_player", "volume_set", JSONObject().put("volume_level", p / 100.0)) }
                if (on) chips(side, Tr.t("Entrada"), list(a, "source_list"), a.optString("source")) { call("media_player", "select_source", JSONObject().put("source", it)) }
                if (has(128) || has(256) || Fmt.isTv(id)) row(side, pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("media_player", if (on) "turn_off" else "turn_on") }, top = 20)
            }
            "fan" -> {
                if (a.has("percentage") && !a.isNull("percentage")) slider(main, if (on) a.optInt("percentage") else 0, Tr.t("Velocidade")) { p -> if (p == 0) call("fan", "turn_off") else call("fan", "set_percentage", JSONObject().put("percentage", p)) }
                chips(side, Tr.t("Modo"), list(a, "preset_modes"), if (on) a.optString("preset_mode") else null) { call("fan", "set_preset_mode", JSONObject().put("preset_mode", it)) }
                row(main, pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { call("fan", if (on) "turn_off" else "turn_on") }, top = 20)
            }
            "vacuum" -> vacuumContent(s, a)
            "lock" -> row(main, pill(Tr.t(if (s.state == "locked") "Destrancar" else "Trancar"), false) { call("lock", if (s.state == "locked") "unlock" else "lock") }, top = 20)
            else -> if (Fmt.isToggle(id)) row(main, pill(Tr.t(if (on) "Desligado" else "Ligado"), false) { Fmt.tap(id) }, top = 20)
        }
        if (d != "vacuum") deviceSection()

        if (wide) {
            val cols = LinearLayout(act).apply { setPadding(0, kit.dp(6), 0, 0) }
            cols.addView(main, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = kit.dp(26) })
            cols.addView(side, LinearLayout.LayoutParams(0, -2, 1.2f))
            content.addView(cols, lp(8))
        } else { content.addView(main, lp(4)); content.addView(side, lp(0)) }
    }

    private val vacOpen = HashSet<String>()

    /** Aspirador: estado, rotinas, comandos, potência, divisões a limpar e o mapa; o resto das entidades arrumado em secções. */
    private fun vacuumContent(s: Ha.State, a: JSONObject) {
        Vac.load(id) { schedule() }
        val state = s.state
        val on = Fmt.isOn(id)
        val level = Vac.battery(id, dev)
        val status = Vac.member(dev, "sensor", "status")
        val errE = Vac.error(dev)
        val now = if (on) listOfNotNull(Vac.member(dev, "sensor", "cleaning_area"), Vac.member(dev, "sensor", "cleaning_time"), Vac.member(dev, "sensor", "clean_percent", "cleaning_progress")).joinToString(" · ") { Fmt.sub(it) } else ""
        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        if (level != null) {
            hd.addView(kit.icon(Vac.batteryIcon(level), kit.accent, 26), LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)).apply { marginEnd = kit.dp(6) })
            hd.addView(kit.text("$level%", 22f, Color.WHITE, 600, display = true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(18) })
        }
        val tv = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        tv.addView(kit.text(Fmt.sub(status ?: id), 18f, Color.WHITE, 600))
        if (now.isNotBlank()) tv.addView(kit.text(now, 13f, muted))
        if (errE != null) tv.addView(kit.text(Fmt.sub(errE), 13f, Color.parseColor("#FF6B5C"), 600))
        hd.addView(tv)
        main.addView(hd, lp(14))

        val routines = Vac.routines(dev)
        if (routines.isNotEmpty()) {
            sub(main, Tr.t("Rotinas"))
            val g = Grid(act, kit.dp(150), kit.dp(42), kit.dp(8))
            routines.forEach { e -> g.addView(pill(Fmt.name(e).removePrefix(dev?.name ?: "").trim().ifBlank { Fmt.name(e) }, firedRoutine == e) { firedRoutine = e; Ha.call("button", "press", listOf(e)); h.postDelayed({ firedRoutine = null; schedule() }, 1200); schedule() }) }
            main.addView(g, lp(8))
        }
        val btns = ArrayList<View>()
        btns += if (state == "cleaning") pill(Tr.t("Pausar"), true) { call("vacuum", "pause") } else pill(Tr.t(if (state == "paused") "Retomar" else "Iniciar"), false) { call("vacuum", "start") }
        btns += pill(Tr.t("Voltar à base"), state == "returning") { call("vacuum", "return_to_base") }
        btns += pill(Tr.t("Localizar"), false) { call("vacuum", "locate") }
        row(main, *btns.toTypedArray(), top = 18)

        val fans = list(a, "fan_speed_list")
        if (fans.size > 1) {
            sub(main, Tr.t("Potência"))
            val g = Grid(act, kit.dp(110), kit.dp(42), kit.dp(8))
            fans.forEach { f -> g.addView(pill(Vac.fanLabel(f), f == a.optString("fan_speed")) { call("vacuum", "set_fan_speed", JSONObject().put("fan_speed", f)) }) }
            main.addView(g, lp(8))
        }
        val maps = Vac.maps[id]
        val rooms = Vac.map(id, dev)?.rooms?.entries?.toList().orEmpty()
        val pick = Vac.pick.getOrPut(id) { HashSet() }
        if (maps == "loading") { sub(main, Tr.t("Divisões")); main.addView(kit.text(Tr.t("A carregar as divisões…"), 14f, muted), lp(8)) }
        else if (rooms.isNotEmpty()) {
            sub(main, Tr.t("Limpar divisões"))
            val g = Grid(act, kit.dp(110), kit.dp(42), kit.dp(8))
            rooms.forEach { (sid, name) -> g.addView(pill(name, sid in pick) { if (sid in pick) pick.remove(sid) else pick.add(sid); render() }) }
            main.addView(g, lp(8))
            val go = pill(if (pick.isEmpty()) Tr.t("Escolhe as divisões") else Tr.t("Limpar") + " " + Tr.pl(pick.size, "divisão", "divisões", "room", "rooms"), pick.isNotEmpty()) {
                if (pick.isNotEmpty()) { Vac.cleanRooms(id, pick.toList()); pick.clear(); render() }
            }
            go.alpha = if (pick.isEmpty()) 0.5f else 1f
            main.addView(go, lp(12))
        }

        // o mapa desenhado pelo robô, na coluna do lado
        val mapImg = android.widget.ImageView(act).apply { adjustViewBounds = true; scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        side.addView(mapImg, lp(4))
        Vac.mapImage(id, dev) { b -> mapImg.setImageBitmap(b); mapImg.visibility = View.VISIBLE }

        // resto das entidades: o que se usa já (opções, comandos) e, dobrado, manutenção, definições e diagnóstico
        val cats = Ha.home?.rooms.orEmpty().flatMap { it.entities }.filter { it.category }.map { it.id }.toSet()
        val skip = listOf("battery", "status", "cleaning_area", "cleaning_time", "clean_percent", "cleaning_progress", "vacuum_error", "dock_error").mapNotNull { Vac.member(dev, "sensor", it) }.toSet() + routines.toSet()
        val mem = dev?.members.orEmpty().filter { val st = Fmt.state(it); st != null && st.state != "unavailable" && Fmt.domain(it) != "image" && it !in skip }
        fun isReading(e: String) = Fmt.domain(e) in setOf("sensor", "binary_sensor")
        val maint = mem.filter { (isReading(it) && Regex("_(time_)?left$|_remaining$|consumable").containsMatchIn(it)) || (Fmt.domain(it) == "button" && it.contains("reset")) }
        val mainL = mem.filter { it !in maint && !isReading(it) && (Fmt.domain(it) == "select" || it !in cats) }
        val settings = mem.filter { it !in maint && it !in mainL && !isReading(it) }
        val diag = mem.filter { it !in maint && isReading(it) }
        if (mainL.isNotEmpty()) deviceSection(mainL, side)
        fun fold(key: String, title: String, icon: String, list: List<String>) {
            if (list.isEmpty()) return
            val open = key in vacOpen
            val hdr = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(Color.argb(24, 255, 255, 255), 16)); setPadding(kit.dp(14), kit.dp(12), kit.dp(14), kit.dp(12)); isClickable = true
                setOnClickListener { if (open) vacOpen.remove(key) else vacOpen.add(key); render() }
                addView(kit.icon(icon, kit.accent, 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(10) })
                addView(kit.text(title, 15f, Color.WHITE, 500), LinearLayout.LayoutParams(0, -2, 1f))
                addView(kit.text("${list.size}", 13f, muted), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(8) })
                addView(kit.icon(if (open) "mdi:chevron-up" else "mdi:chevron-down", muted, 18), LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)))
            }
            side.addView(hdr, lp(10))
            if (open) { val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }; deviceSection(list, box); side.addView(box, lp(0)) }
        }
        fold("maint", Tr.t("Manutenção"), "mdi:wrench-outline", maint)
        fold("set", Tr.t("Definições do robô"), "mdi:tune-variant", settings)
        fold("diag", Tr.t("Diagnóstico"), "mdi:chart-box-outline", diag)
    }

    private var firedRoutine: String? = null

    /** As outras entidades do dispositivo: leituras (sensores), alternadores, opções e controlos. */
    private fun deviceSection(only: List<String>? = null, into: LinearLayout = side) {
        // Como a secção "Entidades" do painel: configuração e diagnóstico (reiniciar, velocidade do efeito, tipo de fita...) ficam escondidos, a não ser que os tenhas ativado.
        val allEnts = Ha.home?.rooms.orEmpty().flatMap { it.entities }.associateBy { it.id }
        val shownIds = Ha.home?.settings?.optJSONArray("shown")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        val all = (only ?: dev?.members.orEmpty()).filter {
            val ent = allEnts[it]; if (only == null && ent != null && (ent.category || ent.haHidden) && it !in shownIds) return@filter false
            val st = Fmt.state(it); st != null && st.state != "unavailable" && Fmt.domain(it) != "image" &&
                !(Fmt.domain(it) == "button" && st.attrs.optString("device_class") == "identify") &&
                !Regex("power[_ ]?on[_ ]?(behaviou?r|state)|startup[_ ]?on[_ ]?off", RegexOption.IGNORE_CASE).containsMatchIn((it + " " + st.attrs.optString("friendly_name")).replace("-", "_"))
        }
        if (all.isEmpty()) return
        val readings = all.filter { Fmt.domain(it) in setOf("sensor", "binary_sensor") }
        val toggles = all.filter { Fmt.domain(it) in setOf("switch", "input_boolean", "light", "fan") }
        val selects = all.filter { Fmt.domain(it) in setOf("select", "input_select") }
        val other = all.filter { it !in readings && it !in toggles && it !in selects }
        fun nm(e: String) = Fmt.name(e).removePrefix(dev?.name ?: "").trim().ifBlank { Fmt.name(e) }

        if (readings.isNotEmpty()) {
            sub(into, Tr.t("Leituras"))
            val g = Grid(act, kit.dp(130), kit.dp(64), kit.dp(10))
            readings.forEach { e ->
                g.addView(LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; background = kit.rounded(Color.argb(24, 255, 255, 255), 16); setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(10))
                    val v = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
                    v.addView(kit.icon(Fmt.icon(e, Fmt.domain(e)), kit.accent, 18), LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)).apply { marginEnd = kit.dp(6) })
                    v.addView(kit.text(Fmt.sub(e), 17f, Color.WHITE, 600).apply { setSingleLine() })
                    addView(v); addView(kit.text(nm(e), 12f, muted).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END })
                })
            }
            into.addView(g, lp(8))
        }
        if (toggles.isNotEmpty() || selects.isNotEmpty() || other.isNotEmpty()) sub(into, Tr.t("Controlos"))
        if (toggles.isNotEmpty() || selects.isNotEmpty()) {
            val g = Grid(act, kit.dp(180), kit.dp(56), kit.dp(10))
            toggles.forEach { e ->
                val on = Fmt.isOn(e)
                g.addView(LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(if (on) kit.cream else Color.argb(24, 255, 255, 255), 16)); setPadding(kit.dp(14), 0, kit.dp(14), 0)
                    isClickable = true; setOnClickListener { Fmt.tap(e) }
                    addView(kit.icon(Fmt.icon(e, Fmt.domain(e)), if (on) kit.ink else Color.WHITE, 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(10) })
                    addView(kit.text(nm(e), 14f, if (on) kit.ink else Color.WHITE, 500).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END })
                })
            }
            selects.forEach { e ->
                val st = Fmt.state(e)!!
                g.addView(LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(Color.argb(24, 255, 255, 255), 16)); setPadding(kit.dp(14), 0, kit.dp(10), 0)
                    isClickable = true; setOnClickListener { openSel = if (openSel == e) null else e; render() }
                    addView(kit.icon(Fmt.icon(e, Fmt.domain(e)), kit.accent, 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(10) })
                    val c = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    c.addView(kit.text(nm(e), 11f, muted).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END })
                    c.addView(kit.text(Tr.t(Fmt.STATE_PT[st.state] ?: st.state), 14f, Color.WHITE, 600).apply { setSingleLine() })
                    addView(c, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(kit.icon("mdi:chevron-down", muted, 18), LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)))
                })
            }
            into.addView(g, lp(8))
        }
        openSel?.let { e ->
            val st = Fmt.state(e) ?: return@let
            val g = Grid(act, kit.dp(110), kit.dp(42), kit.dp(8))
            list(st.attrs, "options").forEach { o -> g.addView(pill(Tr.t(Fmt.STATE_PT[o] ?: o), o == st.state) { Ha.call(Fmt.domain(e), "select_option", listOf(e), JSONObject().put("option", o)) }) }
            into.addView(g, lp(10))
        }
        other.forEach { e ->
            val r = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL; background = kit.rounded(Color.argb(24, 255, 255, 255), 16); setPadding(kit.dp(14), kit.dp(10), kit.dp(14), kit.dp(10)) }
            r.addView(kit.text(nm(e), 15f, Color.WHITE).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            if (Fmt.domain(e) == "button") { r.addView(pill(Tr.t("Executar"), false) { Ha.call("button", "press", listOf(e)) }) }
            else r.addView(kit.text(Fmt.sub(e), 15f, muted, 600))
            into.addView(r, lp(8))
        }
    }
}

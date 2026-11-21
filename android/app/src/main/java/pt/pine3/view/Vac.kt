package pt.pine3.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import org.json.JSONObject

/** Aspirador (Roborock e outros): entidades do dispositivo, mapas/divisões e escolha de divisões para limpar. */
object Vac {
    class VMap(val name: String, val rooms: Map<Int, String>)

    /** Por aspirador: null = sem mapas, "loading", ou lista de mapas. */
    val maps = HashMap<String, Any?>()
    val pick = HashMap<String, MutableSet<Int>>()
    private val NO_ERR = setOf("none", "ok", "unknown", "no_error")

    fun member(dev: Dev?, domain: String, vararg suffixes: String): String? =
        dev?.members?.firstOrNull { Fmt.domain(it) == domain && Fmt.state(it) != null && suffixes.any { s -> it.endsWith("_$s") } }

    fun battery(id: String, dev: Dev?): Int? {
        val a = Fmt.state(id)?.attrs
        val v = a?.takeIf { it.has("battery_level") && !it.isNull("battery_level") }?.optDouble("battery_level")
            ?: member(dev, "sensor", "battery")?.let { Fmt.state(it)?.state?.toDoubleOrNull() }
        return v?.takeIf { !it.isNaN() }?.let { Math.round(it).toInt() }
    }

    fun batteryIcon(l: Int) = "mdi:battery" + if (l >= 95) "" else "-" + Math.max(10, Math.round(l / 10f) * 10)

    fun error(dev: Dev?): String? = member(dev, "sensor", "vacuum_error", "dock_error")?.takeIf { Fmt.state(it)?.state?.lowercase() !in NO_ERR }

    fun fanLabel(f: String) = Tr.t(Fmt.STATE_PT[f] ?: f.replace('_', ' ').replaceFirstChar { it.uppercase() })

    /** Mapas e divisões (roborock.get_maps), pedidos uma vez por aspirador. */
    fun load(id: String, done: () -> Unit) {
        if (maps.containsKey(id)) return
        maps[id] = "loading"
        Ha.request(JSONObject().put("type", "call_service").put("domain", "roborock").put("service", "get_maps")
            .put("target", JSONObject().put("entity_id", id)).put("return_response", true)) { res ->
            val resp = res?.optJSONObject("response")
            val arr = (resp?.optJSONObject(id) ?: resp)?.optJSONArray("maps")
            maps[id] = if (arr == null) null else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { m ->
                val r = m.optJSONObject("rooms")
                VMap(m.optString("name"), r?.keys()?.asSequence()?.associate { k -> k.toInt() to r.optString(k) } ?: emptyMap())
            }
            done()
        }
    }

    /** O mapa em uso: o que o seletor "mapa selecionado" indica, ou o primeiro. */
    fun map(id: String, dev: Dev?): VMap? {
        @Suppress("UNCHECKED_CAST") val list = (maps[id] as? List<VMap>) ?: return null
        if (list.isEmpty()) return null
        val names = list.map { it.name }
        val sel = dev?.members?.firstOrNull { e -> Fmt.domain(e) == "select" && (Fmt.state(e)?.attrs?.optJSONArray("options")?.let { a -> (0 until a.length()).any { a.getString(it) in names } } == true) }
        return list.firstOrNull { it.name == Fmt.state(sel ?: "")?.state } ?: list[0]
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()

    /** O segmento do mapa com o mesmo nome da divisão do HA ("Sala" ↔ "sala"). */
    fun segment(id: String, dev: Dev?, room: Room?): Int? {
        if (room == null) return null
        val want = norm(room.name)
        return map(id, dev)?.rooms?.entries?.firstOrNull { norm(it.value) == want }?.key
    }

    /** Rotinas da app do robô: botões sem categoria e que não são os próprios do robô (esvaziar, repor consumíveis...). */
    fun routines(dev: Dev?): List<String> {
        val cats = Ha.home?.rooms.orEmpty().flatMap { it.entities }.filter { it.category }.map { it.id }.toSet()
        return dev?.members.orEmpty().filter { Fmt.domain(it) == "button" && Fmt.state(it)?.state != "unavailable" && Fmt.state(it) != null && it !in cats && !Regex("reset|consumable|empty_dustbin|dock_error").containsMatchIn(it) }
    }

    fun cleanRooms(id: String, segs: Collection<Int>) {
        if (segs.isEmpty()) return
        Ha.call("vacuum", "send_command", listOf(id), JSONObject().put("command", "app_segment_clean").put("params", org.json.JSONArray().put(JSONObject().put("segments", org.json.JSONArray(segs.toList())))))
    }

    private val imgCache = HashMap<String, Pair<String, android.graphics.Bitmap>>()

    /** O mapa desenhado pelo robô (uma entidade image.* por mapa), com cache enquanto a imagem não muda. */
    fun mapImage(id: String, dev: Dev?, cb: (android.graphics.Bitmap) -> Unit) {
        val m = map(id, dev)
        val imgs = dev?.members.orEmpty().filter { Fmt.domain(it) == "image" && Fmt.state(it)?.attrs?.optString("entity_picture").orEmpty().isNotBlank() }
        val img = imgs.firstOrNull { m != null && Fmt.state(it)?.attrs?.optString("friendly_name").orEmpty().lowercase().contains(m.name.lowercase()) } ?: imgs.firstOrNull() ?: return
        val st = Fmt.state(img)!!
        imgCache[img]?.let { if (it.first == st.state) { cb(it.second); return } }
        Ha.fetchImage(st.attrs.optString("entity_picture")) { b -> if (b != null) { imgCache[img] = st.state to b; cb(b) } }
    }
}

/** Cartão largo do aspirador numa divisão: estado, bateria, progresso e quatro comandos rápidos (.vacw do painel web). */
class VacCard(private val kit: Kit, private val dev: Dev, private val room: Room?, private val onOpen: (String) -> Unit) : Card(kit.ctx) {
    private val ic = FrameLayout(kit.ctx)
    private val icon = kit.icon("mdi:robot-vacuum", kit.ink, 26)
    private val nm = kit.text(dev.name, 20f, kit.ink, 500, display = true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
    private val st = kit.text("", 15f, kit.ink).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
    private val err = kit.text("", 14f, Color.parseColor("#E5382B"), 600)
    private val meta = kit.text("", 14f, kit.ink)
    private val bar = View(kit.ctx)
    private val barTrack = FrameLayout(kit.ctx)
    private val btns = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private var lastSig = ""

    init {
        tag = 2
        isClickable = true
        setOnClickListener { onOpen(dev.id) }
        val pad = kit.dp(18)
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val top = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        ic.addView(icon, FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        top.addView(ic, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(14) })
        val tx = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        tx.addView(nm); tx.addView(st); tx.addView(err); tx.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2) })
        top.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(top)
        barTrack.addView(bar, FrameLayout.LayoutParams(0, -1))
        col.addView(barTrack, LinearLayout.LayoutParams(-1, kit.dp(5)).apply { topMargin = kit.dp(12) })
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(12) })
        addView(col, LayoutParams(-1, -1))
        Vac.load(dev.id) { update() }
        update()
    }

    override fun update() {
        val s = Fmt.state(dev.id)
        val state = s?.state ?: "unavailable"
        val on = Fmt.isOn(dev.id); val na = state == "unavailable"
        val errE = Vac.error(dev); val errOn = errE != null
        val active = on
        val fg = if (active) kit.ink else kit.fg
        background = kit.ripple(if (active) kit.rounded(kit.cream, 26) else kit.glass(26))
        alpha = if (na) 0.5f else 1f
        ic.background = kit.circle(if (active) kit.accent else kit.soft)
        icon.setImageDrawable(kit.mdi(if (errOn) "mdi:robot-vacuum-alert" else "mdi:robot-vacuum")); icon.setColorFilter(if (active) kit.ink else kit.fg)
        nm.setTextColor(fg); st.setTextColor(if (active) kit.inkSoft else kit.muted); meta.setTextColor(if (active) kit.inkSoft else kit.muted)
        st.text = Vac.member(dev, "sensor", "status")?.let { Fmt.sub(it) } ?: Fmt.sub(dev.id)
        err.visibility = if (errOn) View.VISIBLE else View.GONE
        if (errE != null) err.text = "⚠ " + Fmt.sub(errE)
        val parts = ArrayList<String>()
        Vac.battery(dev.id, dev)?.let { parts += "🔋 $it%" }
        if (on) listOfNotNull(Vac.member(dev, "sensor", "cleaning_area"), Vac.member(dev, "sensor", "cleaning_time")).forEach { parts += Fmt.sub(it) }
        else s?.attrs?.optString("fan_speed")?.takeIf { it.isNotBlank() }?.let { parts += Vac.fanLabel(it) }
        meta.text = parts.joinToString("  ·  ")
        val pe = Vac.member(dev, "sensor", "clean_percent", "cleaning_progress")
        val pct = if (on && pe != null) Fmt.state(pe)?.state?.toDoubleOrNull() else null
        barTrack.visibility = if (pct != null) View.VISIBLE else View.GONE
        barTrack.background = kit.rounded(if (active) Color.argb(30, 42, 34, 31) else Color.argb(50, 255, 255, 255), 4)
        bar.background = kit.rounded(kit.accent, 4)
        if (pct != null) barTrack.post { (bar.layoutParams as FrameLayout.LayoutParams).width = (barTrack.width * pct.coerceIn(2.0, 100.0) / 100.0).toInt(); bar.requestLayout() }

        val seg = Vac.segment(dev.id, dev, room)
        val fans = (0 until (s?.attrs?.optJSONArray("fan_speed_list")?.length() ?: 0)).map { s!!.attrs.optJSONArray("fan_speed_list")!!.getString(it) }
        val sig = "$state|$errOn|$seg|${fans.size}|$na|$active"
        if (sig == lastSig) return
        lastSig = sig
        btns.removeAllViews()
        if (na) return
        fun q(label: String, i: String, main: Boolean = false, fn: (View) -> Unit): View {
            val v = kit.pill(label, i, main) { }
            v.setOnClickListener { fn(v) }
            btns.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = kit.dp(8) })
            (v as LinearLayout).gravity = Gravity.CENTER
            v.setPadding(kit.dp(8), kit.dp(12), kit.dp(8), kit.dp(12))
            return v
        }
        if (state == "cleaning") q(Tr.t("Pausar"), "mdi:pause", true) { Ha.call("vacuum", "pause", listOf(dev.id)) }
        else q(Tr.t(if (state == "paused" || (errOn && state != "docked")) "Retomar" else "Iniciar"), "mdi:play", true) { Ha.call("vacuum", "start", listOf(dev.id)) }
        q(Tr.t("Base"), "mdi:home-import-outline") { Ha.call("vacuum", "return_to_base", listOf(dev.id)) }
        if (seg != null) q(Tr.t("Esta divisão"), "mdi:broom") { Vac.cleanRooms(dev.id, listOf(seg)) }
        else if (fans.size > 1) q(Tr.t("Potência"), "mdi:fan") { v ->
            PopupMenu(kit.ctx, v).apply { fans.forEach { f -> menu.add(Vac.fanLabel(f)).setOnMenuItemClickListener { Ha.call("vacuum", "set_fan_speed", listOf(dev.id), JSONObject().put("fan_speed", f)); true } } }.show()
        }
        q(Tr.t("Localizar"), "mdi:map-marker-radius-outline") { Ha.call("vacuum", "locate", listOf(dev.id)) }
    }
}

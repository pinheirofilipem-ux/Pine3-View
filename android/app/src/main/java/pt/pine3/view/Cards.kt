package pt.pine3.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Um cartão que se atualiza sozinho quando os estados mudam (sem reconstruir a página). */
abstract class Card(ctx: Context) : FrameLayout(ctx) {
    init { Elastic.press(this, 0.96f) }
    abstract fun update()

    /** Chave para ordenar no modo de edição (id da divisão, do dispositivo ou "@climate"...). */
    var key: String? = null
    private var chromeView: View? = null

    /** Os botões do modo de edição: − (esconder) e duplicar/repor o tamanho, no canto de baixo à direita. */
    fun setupEdit(kit: Kit, kind: String, id: String, big: Boolean, canHide: Boolean = true) {
        if (Lay.edit == null) return
        val bar = LinearLayout(context)
        fun b(icon: String, onClick: () -> Unit) = FrameLayout(context).apply {
            background = kit.circle(Color.argb(210, 20, 12, 24)); isClickable = true; setOnClickListener { onClick() }
            addView(kit.icon(icon, Color.WHITE, 18), LayoutParams(kit.dp(18), kit.dp(18), Gravity.CENTER))
            bar.addView(this, LinearLayout.LayoutParams(kit.dp(38), kit.dp(38)).apply { marginStart = kit.dp(8) })
        }
        if (canHide) b("mdi:minus") { Lay.hide(kind, id) }
        b(if (big) "mdi:arrow-collapse" else "mdi:arrow-expand") { Lay.toggleBig(kind, id) }
        addView(bar, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, kit.dp(10), kit.dp(10)) })
        chromeView = bar
        foreground = android.graphics.drawable.GradientDrawable().apply { cornerRadius = kit.dp(26).toFloat(); setStroke(kit.dp(2), Color.argb(90, 255, 255, 255)); setColor(Color.TRANSPARENT) }
    }

    /** Toque (em coordenadas do cartão) num dos botões de edição? Então a grelha não pega nele para arrastar. */
    fun chromeHit(x: Float, y: Float): Boolean {
        val v = chromeView ?: return false
        return x >= v.left && x <= v.right && y >= v.top && y <= v.bottom
    }
}

/** Círculo com um ícone (as bolinhas `.ri`, `.ic` e `.pw` do painel web). */
private class IconCircle(val kit: Kit, sizeDp: Int, iconDp: Int, icon: String) : FrameLayout(kit.ctx) {
    val img = kit.icon(icon, Color.WHITE, iconDp)
    init {
        addView(img, LayoutParams(kit.dp(iconDp), kit.dp(iconDp), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(kit.dp(sizeDp), kit.dp(sizeDp))
    }
    fun paint(bg: Int, fg: Int) { background = kit.ripple(kit.circle(bg)); img.setColorFilter(fg) }
    fun setIcon(name: String) = img.setImageDrawable(kit.mdi(name))
}

private fun TextView.oneLine() = apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }

/** Cartão de uma divisão (.rc): ícone, botão de ligar/desligar, nome, "x de y ligados", bolinhas e temperatura. */
class RoomCard(private val kit: Kit, private val room: Room, dense: Boolean, private val short: Boolean = false, private val onOpen: () -> Unit) : Card(kit.ctx) {
    private val ids = room.devices.filter { !it.hidden }.map { it.id }
    private val ri = IconCircle(kit, if (short) 42 else if (dense) 38 else 52, if (short || dense) 22 else 26, room.icon)
    private val pw = IconCircle(kit, if (short) 50 else if (dense) 44 else 64, if (short || dense) 24 else 28, "mdi:power")
    private val nm = kit.text(room.name, if (short) 19f else 22f, kit.ink, 500, display = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val meta = kit.text("", if (short) 14f else 16f, kit.ink).oneLine()
    private val dots = Dots(kit.ctx, kit)
    private val tmp = kit.text("", if (short) 14f else 16f, kit.ink)
    private val big = !kit.list && Ha.home?.let { Lay.isBigRoom(it, room.id) } == true
    private val quick = Grid(kit.ctx, kit.dp(120), kit.dp(38), kit.dp(8))

    init {
        val pad = kit.dp(if (short || dense) 16 else 22)
        isClickable = true
        setOnClickListener { onOpen() }
        if (kit.list) {
            val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(kit.dp(16), kit.dp(12), kit.dp(16), kit.dp(12)) }
            row.addView(ri, LinearLayout.LayoutParams(kit.dp(42), kit.dp(42)).apply { marginEnd = kit.dp(14) })
            val tx = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
            nm.maxLines = 1; tx.addView(nm); tx.addView(meta)
            row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tmp, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(12) })
            row.addView(pw, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)))
            addView(row, LayoutParams(-1, -2))
            pw.setOnClickListener {
                val sw = Fmt.switchables(ids)
                Ha.call("homeassistant", if (sw.any { Fmt.isOn(it) }) "turn_off" else "turn_on", sw)
            }
            update()
        } else {
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row1 = LinearLayout(kit.ctx).apply { gravity = Gravity.TOP }
        row1.addView(ri)
        row1.addView(View(kit.ctx), LinearLayout.LayoutParams(0, 1, 1f))
        row1.addView(pw)
        col.addView(row1, LinearLayout.LayoutParams(-1, -2))
        if (big) { tag = 2; col.addView(quick, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(12) }) }
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(nm, LinearLayout.LayoutParams(-1, -2))
        col.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(if (short) 2 else 4) })
        val drow = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = kit.dp(22) }
        drow.addView(dots, LinearLayout.LayoutParams(0, -2, 1f))
        drow.addView(tmp)
        col.addView(drow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(if (short) 8 else 12) })
        addView(col, LayoutParams(-1, -1))
        pw.setOnClickListener {
            val sw = Fmt.switchables(ids)
            Ha.call("homeassistant", if (sw.any { Fmt.isOn(it) }) "turn_off" else "turn_on", sw)
        }
        update()
        }
    }

    override fun update() {
        val sw = Fmt.switchables(ids)
        val on = sw.filter { Fmt.isOn(it) }
        val active = on.isNotEmpty()
        val fg = if (active) kit.ink else kit.fg
        val rad = if (short) 24 else 28
        background = kit.ripple(if (active) kit.surfaceOn(rad) else kit.glass(rad))
        ri.paint(if (active) kit.accent else kit.soft, if (active) kit.ink else kit.fg)
        pw.paint(if (active) Color.argb(20, 42, 34, 31) else kit.soft, fg)
        pw.visibility = if (sw.isEmpty()) View.GONE else View.VISIBLE
        nm.setTextColor(fg); tmp.setTextColor(fg)
        meta.setTextColor(if (active) kit.inkSoft else kit.muted)
        meta.text = when {
            sw.isNotEmpty() && active -> "${on.size}" + Tr.t(" de ") + "${sw.size}" + Tr.t(" ligados")
            sw.isNotEmpty() -> Tr.t("Tudo desligado")
            else -> Tr.pl(ids.size, "dispositivo", "dispositivos", "device", "devices")
        }
        dots.onColor = kit.accent
        dots.offColor = if (active) Color.argb(41, 42, 34, 31) else Color.argb(71, 255, 255, 255)
        dots.on = sw.take(8).map { Fmt.isOn(it) }
        tmp.text = Fmt.roomClimate(room).first?.replace(Regex("[,.]\\d"), "")?.replace("°C", "°") ?: ""
        if (big) {
            quick.removeAllViews()
            sw.take(6).forEach { id ->
                val isOn = Fmt.isOn(id)
                quick.addView(LinearLayout(kit.ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(if (isOn) kit.accent else if (active) Color.argb(20, 42, 34, 31) else kit.soft, 40)); setPadding(kit.dp(12), 0, kit.dp(12), 0)
                    isClickable = true; setOnClickListener { Fmt.tap(id) }
                    addView(kit.icon(Fmt.icon(id, Fmt.domain(id)), if (isOn || active) kit.ink else kit.fg, 18), LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)).apply { marginEnd = kit.dp(8) })
                    addView(kit.text(Fmt.name(id), 14f, if (isOn || active) kit.ink else kit.fg, 500).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
                })
            }
        }
    }
}

/** Cartão de um dispositivo (.tile): creme se ligado, vidro se desligado. */
class Tile(private val kit: Kit, private val dev: Dev, private val secWatch: Boolean = false, private val onOpen: (String) -> Unit) : Card(kit.ctx) {
    private val domain = Fmt.domain(dev.id)
    private val ic = IconCircle(kit, 48, 24, "mdi:circle-medium")
    private val pw = IconCircle(kit, 54, 24, "mdi:power")
    private val nm = kit.text(dev.name, 19f, kit.ink, 500, display = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val st = kit.text("", 15f, kit.ink).oneLine()
    private val value = kit.text("", 34f, kit.ink, 300, display = true).oneLine()
    private val ex = kit.text("", 13f, kit.ink).oneLine()
    private val hasPower = domain in setOf("light", "switch", "fan", "input_boolean") ||
        (Fmt.isTv(dev.id, dev.name) && ((Fmt.state(dev.id)?.attrs?.optInt("supported_features", 0) ?: 0) and 384) != 0)

    init {
        if (!kit.list && Ha.home?.let { Lay.isBigDev(it, dev.id) } == true) tag = 2
        val pad = kit.dp(18)
        isClickable = domain != "sensor"
        setOnClickListener { if (domain in setOf("light", "cover", "climate", "media_player", "fan", "vacuum")) onOpen(dev.id) else Fmt.tap(dev.id) }
        if (kit.list) {
            val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(kit.dp(16), kit.dp(12), kit.dp(16), kit.dp(12)) }
            row.addView(ic, LinearLayout.LayoutParams(kit.dp(44), kit.dp(44)).apply { marginEnd = kit.dp(14) })
            val tx = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
            nm.maxLines = 1; tx.addView(nm); tx.addView(st); tx.addView(ex)
            row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
            if (domain == "sensor") row.addView(value.apply { textSize = 24f }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(12) })
            if (hasPower) row.addView(pw, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)).apply { marginStart = kit.dp(12) })
            addView(row, LayoutParams(-1, -2))
            pw.setOnClickListener { Fmt.tap(dev.id) }
            update()
        } else {
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row1 = LinearLayout(kit.ctx).apply { gravity = Gravity.TOP }
        row1.addView(ic)
        row1.addView(View(kit.ctx), LinearLayout.LayoutParams(0, 1, 1f))
        if (hasPower) row1.addView(pw)
        col.addView(row1, LinearLayout.LayoutParams(-1, -2))
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(-1, 0, 1f))
        if (domain == "sensor") { col.addView(nm, LinearLayout.LayoutParams(-1, -2)); col.addView(value, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(6) }) }
        else { col.addView(nm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(14) }); col.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(3) }) }
        col.addView(ex, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(4) })
        addView(col, LayoutParams(-1, -1))
        pw.setOnClickListener { Fmt.tap(dev.id) }
        update()
        }
    }

    override fun update() {
        val on = Fmt.isOn(dev.id)
        val fg = if (on) kit.ink else kit.fg
        background = kit.ripple(if (on) kit.surfaceOn(26) else kit.glass(26))
        val tint = Fmt.accent(dev.id, kit.accent)
        ic.setIcon(Fmt.icon(dev.id, dev.kind))
        ic.paint(if (on) tint else kit.soft, if (on) kit.ink else kit.fg)
        if (secWatch && Fmt.state(dev.id)?.state != "unavailable") {
            val bad = if (domain == "lock") Fmt.state(dev.id)?.state != "locked" else Fmt.state(dev.id)?.state == "on"
            if (!bad && !on) ic.paint(Color.parseColor("#30D158"), Color.parseColor("#06210F"))
        }
        pw.paint(if (on) Color.argb(20, 42, 34, 31) else kit.soft, fg)
        nm.setTextColor(fg); value.setTextColor(fg)
        st.setTextColor(if (on) kit.inkSoft else kit.muted); ex.setTextColor(if (on) kit.inkSoft else kit.muted)
        alpha = if (Fmt.state(dev.id)?.state == "unavailable") 0.5f else 1f
        val extra = dev.extras.mapNotNull { Fmt.reading(it) }.joinToString(" · ")
        if (domain == "sensor") value.text = Fmt.reading(dev.id) ?: "" else st.text = Fmt.sub(dev.id)
        ex.text = extra; ex.visibility = if (extra.isEmpty()) View.GONE else View.VISIBLE
        if (domain != "sensor") value.visibility = View.GONE
    }
}

/** O anel do cartão de clima: 270° de arco com a abertura em baixo, o número ao centro com o "°" e a legenda por baixo. */
internal class RingView(private val kit: Kit) : View(kit.ctx) {
    var frac = 0f; var color = Color.WHITE; var track = Color.argb(31, 255, 255, 255)
    var bigText = ""; var bigSp = 40f; var smallText = ""; var textColor = Color.WHITE; var smallColor = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val s = width.toFloat(); val k = s / 100f
        p.strokeWidth = 7f * k
        r.set(6f * k, 6f * k, 94f * k, 94f * k)
        p.color = track; c.drawArc(r, 135f, 270f, false, p)
        if (frac > 0f) { p.color = color; c.drawArc(r, 135f, 270f * frac, false, p) }
        val sp = resources.displayMetrics.scaledDensity
        tp.typeface = kit.font(300, true); tp.color = textColor; tp.textSize = bigSp * sp
        val degW = 18f * sp * 0.45f
        val w = tp.measureText(bigText) + degW
        val cy = s / 2f + (if (smallText.isEmpty()) 0f else -6f * kit.density)
        val x = (s - w) / 2f
        val base = cy + tp.textSize * 0.35f
        c.drawText(bigText, x, base, tp)
        tp.textSize = 18f * sp; c.drawText("°", x + w - degW * 1.6f + degW * 0.35f, base - bigSp * sp * 0.55f + 18f * sp * 0.55f, tp)
        if (smallText.isNotEmpty()) {
            tp.typeface = kit.font(400, false); tp.color = smallColor; tp.textSize = 12f * sp
            c.drawText(smallText, (s - tp.measureText(smallText)) / 2f, base + 16f * kit.density, tp)
        }
    }
}

/** Cartão de clima com controlo direto (.tile.clx): anel com o alvo, temperatura atual, modos e − / +. */
class ClimateCard(private val kit: Kit, private val dev: Dev, private val roomName: String?, private val onOpen: (String) -> Unit) : Card(kit.ctx) {
    private val ring = RingView(kit)
    private val nm = kit.text(dev.name, 19f, kit.ink, 500, display = true).oneLine()
    private val st = kit.text("", 15f, kit.ink).oneLine()
    private val seg = LinearLayout(kit.ctx)
    private val pm = LinearLayout(kit.ctx)
    private var want: Pair<Double, Long>? = null

    init {
        tag = 2 // ocupa duas colunas
        isClickable = true
        setOnClickListener { onOpen(dev.id) }
        val pad = kit.dp(18)
        val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(pad, pad, pad, pad) }
        val rs = kit.dp(kit.clamp(96f, kit.vw(10f), 128f))
        row.addView(ring, LinearLayout.LayoutParams(rs, rs).apply { marginEnd = kit.dp(18) })
        val right = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        right.addView(nm); right.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(2) })
        right.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(10) })
        right.addView(pm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(10) })
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        addView(row, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        update()
    }

    private fun target(a: JSONObject): Double? {
        val w = want
        if (w != null && System.currentTimeMillis() - w.second < 15000 && a.optDouble("temperature", Double.NaN) != w.first) return w.first
        return a.optDouble("temperature", Double.NaN).takeIf { !it.isNaN() }
    }

    override fun update() {
        val s = Fmt.state(dev.id) ?: return
        val a = s.attrs
        val on = Fmt.isOn(dev.id)
        val fg = if (on) kit.ink else kit.fg
        val action = if (on) a.optString("hvac_action").takeIf { it.isNotEmpty() && it !in setOf("off", "idle") } else null
        val tgt = target(a)
        val lo = a.optDouble("target_temp_low", Double.NaN); val hi = a.optDouble("target_temp_high", Double.NaN)
        val range = tgt == null && !lo.isNaN() && !hi.isNaN()
        val all = (0 until (a.optJSONArray("hvac_modes")?.length() ?: 0)).map { a.optJSONArray("hvac_modes")!!.getString(it) }
        val shortName = mapOf("cool" to "Frio", "heat" to "Calor", "heat_cool" to "Auto", "auto" to "Auto", "dry" to "Seco", "fan_only" to "Ventilar", "off" to "Desligar")
        var modes = listOf("cool", "heat").filter { it in all }.toMutableList()
        if (modes.size < 2) modes = (modes + all.filter { it != "off" && it !in modes }).take(2).toMutableList()
        if ("off" in all) modes.add("off")
        if (s.state != "off" && s.state in all && s.state !in modes) modes.add(modes.size - (if ("off" in all) 1 else 0), s.state)
        val mode = mapOf("heating" to "heat", "preheating" to "heat", "cooling" to "cool", "drying" to "dry", "fan" to "fan_only")[action] ?: s.state
        val col = if (!on) Color.argb(77, 255, 255, 255) else when (mode) {
            "cool" -> 0xFF4AA8FF.toInt(); "dry" -> 0xFF64D2FF.toInt(); "fan_only" -> 0xFFA8B0BD.toInt(); "heat_cool", "auto" -> 0xFF4ADE80.toInt(); else -> 0xFFFF9F43.toInt()
        }
        val min = a.optDouble("min_temp", 7.0); val max = a.optDouble("max_temp", 35.0)
        val cur = a.optDouble("current_temperature", Double.NaN)
        val value = if (range) hi else tgt ?: cur
        ring.frac = if (on && !value.isNaN()) ((value - min) / (max - min).coerceAtLeast(1.0)).coerceIn(0.04, 1.0).toFloat() else 0f
        ring.color = col
        ring.track = if (on) Color.argb(31, 42, 34, 31) else Color.argb(31, 255, 255, 255)
        val bigText = when {
            on && tgt != null -> Fmt.num(tgt)
            range && on -> Fmt.num(lo) + "–" + Fmt.num(hi)
            !cur.isNaN() -> Fmt.num(Math.round(cur).toDouble(), 0)
            else -> "–"
        }
        ring.bigText = bigText
        ring.bigSp = if (range && on) 22f else if (bigText.length > 3) kit.clamp(26f, kit.vw(2.6f), 34f) else kit.clamp(34f, kit.vw(3.4f), 44f)
        ring.textColor = fg; ring.smallColor = if (on) kit.inkSoft else kit.muted
        ring.smallText = if (!on && !cur.isNaN()) Tr.t("Atual") else ""
        ring.invalidate()
        nm.setTextColor(fg)
        st.setTextColor(if (on) kit.inkSoft else kit.muted)
        st.text = listOfNotNull(
            roomName?.takeIf { it.isNotBlank() }, if (on && tgt != null && !cur.isNaN()) Fmt.num(cur) + "°" else null,
            Tr.t(Fmt.STATE_PT[action ?: s.state] ?: (action ?: s.state)),
        ).joinToString(" · ")
        background = kit.ripple(if (on) kit.surfaceOn(26) else kit.glass(26))
        alpha = if (s.state == "unavailable") 0.5f else 1f

        seg.removeAllViews()
        seg.background = kit.rounded(if (on) Color.argb(20, 42, 34, 31) else Color.argb(26, 255, 255, 255), 40)
        seg.setPadding(kit.dp(3), kit.dp(3), kit.dp(3), kit.dp(3))
        seg.visibility = if (s.state != "unavailable" && modes.size > 1) View.VISIBLE else View.GONE
        modes.forEach { m ->
            val act = m == s.state
            seg.addView(kit.text(Tr.t(shortName[m] ?: (Fmt.STATE_PT[m] ?: m)), 14f, if (act) (if (on) kit.cream else kit.ink) else (if (on) kit.inkSoft else kit.muted), 600).apply {
                gravity = Gravity.CENTER; setSingleLine(); ellipsize = TextUtils.TruncateAt.END
                background = if (act) kit.rounded(if (on) kit.ink else Color.WHITE, 40) else null
                isClickable = true; setOnClickListener { Ha.call("climate", "set_hvac_mode", listOf(dev.id), JSONObject().put("hvac_mode", m)) }
            }, LinearLayout.LayoutParams(0, kit.dp(34), 1f))
        }
        pm.removeAllViews()
        pm.visibility = if (on && tgt != null) View.VISIBLE else View.GONE
        val step = a.optDouble("target_temp_step", 0.5).takeIf { it > 0 } ?: 0.5
        listOf("−" to -1, "+" to 1).forEach { (label, d) ->
            pm.addView(kit.text(label, 22f, fg, 500).apply {
                gravity = Gravity.CENTER
                background = kit.ripple(kit.rounded(if (on) Color.argb(20, 42, 34, 31) else kit.soft, 40))
                isClickable = true
                setOnClickListener {
                    val now = target(a) ?: return@setOnClickListener
                    val nv = (now + d * step).coerceIn(min, max)
                    want = nv to System.currentTimeMillis()
                    Ha.call("climate", "set_temperature", listOf(dev.id), JSONObject().put("temperature", nv))
                    update()
                }
            }, LinearLayout.LayoutParams(0, kit.dp(44), 1f).apply { marginEnd = if (d < 0) kit.dp(8) else 0 })
        }
    }
}

/** Cartões de resumo do início (Clima, Segurança, Aspirador, A tocar), com as cores e textos do painel web. */
class SummaryCard(private val kit: Kit, private val kind: String, private val id: String?, private val home: Home, private val nav: Nav, private val dense: Boolean = false) : Card(kit.ctx) {
    private val ri = IconCircle(kit, if (dense) 38 else 52, if (dense) 22 else 26, "mdi:thermometer")
    private val eyebrow = kit.text("", 13f, kit.fg, 600, spacing = if (dense) 0.12f else 0.2f).apply { setSingleLine() }
    private val numSp = if (dense) 32f else 46f
    private val txtSp = if (dense) 20f else 30f
    private val big = kit.text("", numSp, kit.fg, 300, display = true).oneLine()
    private val meta = kit.text("", 16f, kit.fg).oneLine()
    private val bar = View(kit.ctx)
    private val barBox = FrameLayout(kit.ctx)
    private val controls = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val devices get() = home.rooms.filter { !it.hidden }.flatMap { it.devices }.filter { !it.hidden }

    init {
        val pad = kit.dp(if (dense) 16 else 22)
        isClickable = true
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row1 = LinearLayout(kit.ctx).apply { gravity = Gravity.TOP }
        if (kind != "media") row1.addView(ri)
        row1.addView(eyebrow, LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = kit.dp(if (dense) 10 else 14); marginStart = if (kind != "media") kit.dp(if (dense) 10 else 14) else 0 })
        col.addView(row1, LinearLayout.LayoutParams(-1, -2))
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(big, LinearLayout.LayoutParams(-1, -2))
        col.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(4) })
        if (kind == "media") {
            barBox.addView(bar, LayoutParams(0, kit.dp(4)))
            col.addView(barBox, LinearLayout.LayoutParams(-1, kit.dp(4)).apply { topMargin = kit.dp(10) })
            col.addView(controls, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(8) })
        }
        addView(col, LayoutParams(-1, -1))
        update()
    }

    private fun ok(bg: Int, fg: Int) = ri.paint(bg, fg)

    override fun update() {
        eyebrow.setTextColor(kit.fg); big.setTextColor(kit.fg); meta.setTextColor(kit.muted)
        var on = false
        when (kind) {
            "climate" -> {
                val cl = devices.filter { it.kind == "climate" && Fmt.state(it.id) != null }
                val temps = devices.filter { Fmt.domain(it.id) == "sensor" && Fmt.state(it.id)?.attrs?.optString("device_class") == "temperature" }
                    .mapNotNull { Fmt.state(it.id)?.state?.toDoubleOrNull() } + home.rooms.flatMap { it.temperature }.mapNotNull { Fmt.state(it)?.state?.toDoubleOrNull() }
                val hums = home.rooms.flatMap { it.humidity }.mapNotNull { Fmt.state(it)?.state?.toDoubleOrNull() }
                val climate = cl.firstOrNull { Fmt.isOn(it.id) } ?: cl.firstOrNull()
                visibility = if (temps.isEmpty() && climate == null) View.GONE else View.VISIBLE
                on = climate != null && Fmt.isOn(climate.id)
                eyebrow.text = Tr.t("Clima").uppercase(); ri.setIcon("mdi:thermometer"); ok(Color.parseColor("#34C759"), Color.parseColor("#0D2A14"))
                val tt = if (temps.isNotEmpty()) temps.average() else climate?.let { Fmt.state(it.id)?.attrs?.optDouble("current_temperature", Double.NaN) }
                big.text = if (tt == null || tt.isNaN()) "–°" else Fmt.num(Math.round(tt).toDouble(), 0) + "°"
                meta.text = buildString {
                    if (cl.size > 1) append("${cl.count { Fmt.isOn(it.id) }}").append(Tr.t(" de ")).append("${cl.size}").append(Tr.t(" ligados")).append(" · ")
                    if (hums.isNotEmpty()) append(Tr.t("Humidade ")).append(Fmt.num(hums.average(), 0)).append("%")
                    else if (climate != null) append(Tr.t(Fmt.STATE_PT[Fmt.state(climate.id)?.state] ?: Fmt.state(climate.id)?.state.orEmpty()))
                }.trimEnd(' ', '·')
                setOnClickListener { if (cl.size > 1) nav.openScreen("climate") else if (climate != null) nav.openDevice(climate.id) }
            }
            "security" -> {
                val sec = devices.filter { it.kind == "lock" || (it.kind == "binary_sensor" && Fmt.state(it.id)?.attrs?.optString("device_class") in setOf("door", "window", "garage_door", "opening")) }
                visibility = if (sec.isEmpty()) View.GONE else View.VISIBLE
                val bad = sec.count { if (it.kind == "lock") Fmt.state(it.id)?.state == "unlocked" else Fmt.state(it.id)?.state == "on" }
                eyebrow.text = Tr.t("Segurança").uppercase()
                ri.setIcon(if (bad > 0) "mdi:shield-alert-outline" else "mdi:shield-check-outline")
                if (bad > 0) ok(Color.parseColor("#FF9F0A"), Color.parseColor("#2A1500")) else ok(Color.parseColor("#30D158"), Color.parseColor("#06210F"))
                big.textSize = txtSp; big.typeface = kit.font(300, true)
                big.text = if (bad > 0) Tr.pl(bad, "item aberto", "itens abertos", "item open", "items open") else Tr.t("Tudo seguro")
                meta.text = Tr.pl(sec.size, "item monitorizado", "itens monitorizados", "item monitored", "items monitored")
                setOnClickListener { nav.openScreen("security") }
            }
            "vacuum" -> {
                val st = id?.let { Fmt.state(it) }
                visibility = if (st == null) View.GONE else View.VISIBLE
                if (st != null && id != null) {
                    on = Fmt.isOn(id)
                    eyebrow.text = Tr.t("Aspirador").uppercase(); ri.setIcon("mdi:robot-vacuum"); ok(Color.parseColor("#30D158"), Color.parseColor("#06210F"))
                    big.textSize = txtSp; big.typeface = kit.font(300, true)
                    big.text = Tr.t(Fmt.STATE_PT[st.state] ?: st.state)
                    val bat = st.attrs.optInt("battery_level", -1)
                    meta.text = listOfNotNull(if (bat >= 0) "🔋 $bat%" else null, Fmt.name(id)).joinToString(" · ")
                    setOnClickListener { nav.openDevice(id) }
                }
            }
            "media" -> {
                val p = devices.firstOrNull { it.kind == "media_player" && Fmt.state(it.id)?.state == "playing" }
                    ?: devices.firstOrNull { it.kind == "media_player" && Fmt.state(it.id)?.state == "paused" }
                visibility = if (p == null) View.GONE else View.VISIBLE
                if (p != null) {
                    val a = Fmt.state(p.id)!!.attrs
                    val room = home.rooms.firstOrNull { r -> r.devices.any { it.id == p.id } }?.name
                    eyebrow.setTextColor(Color.parseColor("#5EE08A"))
                    eyebrow.text = listOf(Tr.t("A tocar"), room.orEmpty(), a.optString("app_name")).filter { it.isNotBlank() }.joinToString(" · ").uppercase()
                    big.textSize = if (dense) 16f else 24f; big.typeface = kit.font(400, true)
                    big.text = a.optString("media_title").ifBlank { p.name }
                    meta.text = a.optString("media_artist")
                    val dur = a.optDouble("media_duration", 0.0); val pos = a.optDouble("media_position", 0.0)
                    val pct = if (dur > 0) (pos / dur).coerceIn(0.0, 1.0) else 0.0
                    barBox.visibility = if (pct > 0) View.VISIBLE else View.GONE
                    barBox.background = kit.rounded(kit.soft, 4); bar.background = kit.rounded(kit.fg, 4)
                    barBox.post { bar.layoutParams = (bar.layoutParams as LayoutParams).apply { width = (barBox.width * pct).toInt() }; bar.requestLayout() }
                    controls.removeAllViews()
                    controls.addView(kit.roundButton("mdi:skip-previous", 38, Color.TRANSPARENT, kit.fg) { Ha.call("media_player", "media_previous_track", listOf(p.id)) })
                    controls.addView(kit.roundButton(if (Fmt.state(p.id)?.state == "playing") "mdi:pause" else "mdi:play", 48, kit.cream, kit.ink) { Ha.call("media_player", "media_play_pause", listOf(p.id)) },
                        LinearLayout.LayoutParams(kit.dp(48), kit.dp(48)).apply { marginStart = kit.dp(12); marginEnd = kit.dp(12) })
                    controls.addView(kit.roundButton("mdi:skip-next", 38, Color.TRANSPARENT, kit.fg) { Ha.call("media_player", "media_next_track", listOf(p.id)) })
                    setOnClickListener { nav.openDevice(p.id) }
                }
            }
        }
        if (kind == "climate" || kind == "vacuum") {
            val fg = if (on) kit.ink else kit.fg
            eyebrow.setTextColor(fg); big.setTextColor(fg); meta.setTextColor(if (on) kit.inkSoft else kit.muted)
        }
        background = kit.ripple(if (on) kit.surfaceOn(28) else kit.glass(28))
    }
}

/** Cartão de câmara (.camt): imagem da última captura, botão de reproduzir e "Toca para ver ao vivo". Ocupa duas colunas. */
class CamTile(private val kit: Kit, private val dev: Dev, private val onOpen: (String) -> Unit) : Card(kit.ctx) {
    /** A imagem nunca define a altura do cartão (senão o cartão mudava de tamanho quando chegava a miniatura). */
    private val shot = object : ImageView(kit.ctx) {
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(getDefaultSize(0, w), getDefaultSize(0, h))
    }.apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
    private val ic = IconCircle(kit, 48, 24, "mdi:cctv")
    private val nm = kit.text(dev.name, 19f, Color.WHITE, 500, display = true).oneLine()
    private val st = kit.text("", 15f, Color.argb(210, 255, 255, 255)).oneLine()
    private var shownStamp = -1L
    private lateinit var play: View

    init {
        tag = 2
        isClickable = true
        setOnClickListener { onOpen(dev.id) }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, kit.dp(26).toFloat()) }
        }
        addView(shot, LayoutParams(-1, -1))
        addView(View(kit.ctx).apply { background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(20, 0, 0, 0), Color.argb(150, 0, 0, 0))) }, LayoutParams(-1, -1))
        val pad = kit.dp(18)
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        col.addView(ic)
        col.addView(View(kit.ctx), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(nm); col.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(3) })
        addView(col, LayoutParams(-1, -1))
        play = FrameLayout(kit.ctx).apply {
            background = kit.circle(Color.argb(150, 20, 16, 24))
            addView(kit.icon("mdi:play", Color.WHITE, 28), LayoutParams(kit.dp(28), kit.dp(28), Gravity.CENTER))
        }
        addView(play, LayoutParams(kit.dp(64), kit.dp(64), Gravity.CENTER))
        update()
    }

    override fun update() {
        background = kit.glass(26)
        ic.paint(Color.parseColor("#64D2FF"), kit.ink)
        alpha = if (Fmt.state(dev.id)?.state == "unavailable") 0.5f else 1f
        // O cartão nunca liga a câmara: mostra só a última imagem guardada ao fechar a vista ao vivo.
        val saved = CamCache.stamp(kit.ctx, dev.id)
        if (saved != shownStamp) {
            shownStamp = saved
            val bmp = CamCache.load(kit.ctx, dev.id)
            if (bmp != null) { shot.setImageBitmap(bmp); shot.visibility = View.VISIBLE; play.visibility = View.GONE } else { shot.visibility = View.GONE; play.visibility = View.VISIBLE }
        }
        st.text = if (saved > 0) Tr.t("Última imagem") + " · " + java.text.SimpleDateFormat("d MMM HH:mm", Tr.locale).format(java.util.Date(saved)) else Tr.t("Toca para ver ao vivo")
    }
}

/** "Mostrar N escondidas": cartão tracejado do modo de edição que volta a mostrar o que foi escondido. */
class RestoreCard(private val kit: Kit, text: String, onClick: () -> Unit) : Card(kit.ctx) {
    init {
        isClickable = true; setOnClickListener { onClick() }
        background = kit.ripple(android.graphics.drawable.GradientDrawable().apply { cornerRadius = kit.dp(26).toFloat(); setColor(Color.TRANSPARENT); setStroke(kit.dp(2), Color.argb(80, 255, 255, 255), kit.dp(8).toFloat(), kit.dp(6).toFloat()) })
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(FrameLayout(kit.ctx).apply {
            background = kit.circle(kit.soft); addView(kit.icon("mdi:plus", kit.fg, 28), LayoutParams(kit.dp(28), kit.dp(28), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(56), kit.dp(56)))
        col.addView(kit.text(text, 15f, kit.muted, 600).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) })
        addView(col, LayoutParams(-1, -1, Gravity.CENTER))
    }
    override fun update() {}
}

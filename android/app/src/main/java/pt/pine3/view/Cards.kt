package pt.pine3.view

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Um cartão que se atualiza sozinho quando os estados mudam (sem reconstruir a página). */
abstract class Card(ctx: Context) : FrameLayout(ctx) {
    abstract fun update()
}

private fun lightsOf(room: Room) = room.devices.filter { !it.hidden && it.kind == "light" }.map { it.id }

/** Cartão de uma divisão: ícone, botão de ligar/desligar luzes, nome, "x de y ligados", temperatura e bolinhas. */
class RoomCard(private val kit: Kit, private val room: Room, private val onOpen: () -> Unit) : Card(kit.ctx) {
    private val iconBox = FrameLayout(kit.ctx)
    private val icon = kit.icon(room.icon, kit.ink, 26)
    private val power = kit.roundButton("mdi:power", 44, Color.argb(40, 0, 0, 0), kit.ink) { toggleLights() }
    private val name = kit.text(room.name, 22f, kit.ink, 400, display = true)
    private val sub = kit.text("", 14f, kit.ink)
    private val temp = kit.text("", 15f, kit.ink)
    private val dots = Dots(kit.ctx, kit)

    init {
        isClickable = true
        setOnClickListener { onOpen() }
        iconBox.addView(icon, FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.CENTER))
        addView(iconBox, LayoutParams(kit.dp(44), kit.dp(44), Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(18), kit.dp(18), 0, 0) })
        addView(power, LayoutParams(kit.dp(44), kit.dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, kit.dp(18), kit.dp(18), 0) })
        val bottom = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(name)
        bottom.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
        val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(dots, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(temp)
        bottom.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(10) })
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(kit.dp(18), 0, kit.dp(18), kit.dp(16)) })
        update()
    }

    private fun toggleLights() {
        val ids = lightsOf(room)
        val anyOn = ids.any { Fmt.isOn(it) }
        Ha.call("light", if (anyOn) "turn_off" else "turn_on", ids)
    }

    override fun update() {
        val ids = lightsOf(room)
        val on = ids.map { Fmt.isOn(it) }
        val any = on.any { it }
        val fg = if (any) kit.ink else kit.fg
        background = kit.ripple(if (any) kit.rounded(kit.cream, 28) else kit.glass(28))
        iconBox.background = kit.circle(if (any) kit.accent else kit.soft)
        icon.setColorFilter(if (any) kit.ink else kit.fg)
        name.setTextColor(fg); sub.setTextColor(if (any) Color.argb(160, 43, 28, 46) else kit.muted); temp.setTextColor(fg)
        power.background = kit.ripple(kit.circle(if (any) Color.argb(36, 0, 0, 0) else kit.soft))
        (power.getChildAt(0) as android.widget.ImageView).setColorFilter(fg)
        power.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
        sub.text = if (ids.isEmpty()) "${room.devices.count { !it.hidden }}" + Tr.t(" dispositivos") else "${on.count { it }}" + Tr.t(" de ") + "${ids.size}" + Tr.t(" ligados")
        dots.onColor = kit.accent
        dots.offColor = if (any) Color.argb(60, 43, 28, 46) else kit.soft
        dots.on = on
        temp.text = room.temperature.firstNotNullOfOrNull { id -> Fmt.state(id)?.state?.toDoubleOrNull() }?.let { Fmt.num(it, 0) + "°" } ?: ""
    }
}

/** Cartão de um dispositivo numa divisão: creme se ligado, vidro se desligado. Tocar liga/desliga. */
class Tile(private val kit: Kit, private val dev: Dev) : Card(kit.ctx) {
    private val domain = Fmt.domain(dev.id)
    private val iconBox = FrameLayout(kit.ctx)
    private val icon = kit.icon(Fmt.icon(dev.id, dev.kind), kit.ink, 24)
    private val power = kit.roundButton("mdi:power", 40, Color.argb(40, 0, 0, 0), kit.ink) { Fmt.tap(dev.id) }
    private val name = kit.text(dev.name, 20f, kit.ink, 400, display = true)
    private val sub = kit.text("", 14f, kit.ink)
    private val value = kit.text("", 30f, kit.ink, 300, display = true)

    init {
        isClickable = true
        setOnClickListener { Fmt.tap(dev.id) }
        iconBox.addView(icon, FrameLayout.LayoutParams(kit.dp(24), kit.dp(24), Gravity.CENTER))
        addView(iconBox, LayoutParams(kit.dp(40), kit.dp(40), Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(16), kit.dp(16), 0, 0) })
        if (Fmt.isToggle(dev.id) && domain in setOf("light", "switch", "fan", "input_boolean")) {
            addView(power, LayoutParams(kit.dp(40), kit.dp(40), Gravity.TOP or Gravity.END).apply { setMargins(0, kit.dp(16), kit.dp(16), 0) })
        }
        val bottom = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        if (domain == "sensor") bottom.addView(name.also { it.textSize = 16f })
        bottom.addView(value, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2) })
        if (domain != "sensor") bottom.addView(name)
        bottom.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2) })
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(kit.dp(16), 0, kit.dp(16), kit.dp(14)) })
        update()
    }

    override fun update() {
        val on = Fmt.isOn(dev.id)
        val fg = if (on) kit.ink else kit.fg
        background = kit.ripple(if (on) kit.rounded(kit.cream, 26) else kit.glass(26))
        val tint = Fmt.ACCENT[domain] ?: kit.accent
        iconBox.background = kit.circle(if (on) tint else kit.soft)
        icon.setColorFilter(if (on) kit.ink else kit.fg)
        name.setTextColor(fg); sub.setTextColor(if (on) Color.argb(160, 43, 28, 46) else kit.muted); value.setTextColor(fg)
        power.background = kit.ripple(kit.circle(if (on) Color.argb(36, 0, 0, 0) else kit.soft))
        (power.getChildAt(0) as android.widget.ImageView).setColorFilter(fg)
        if (domain == "sensor") {
            value.visibility = View.VISIBLE
            value.text = Fmt.reading(dev.id) ?: ""
            val extra = dev.extras.mapNotNull { Fmt.reading(it) }.joinToString(" · ")
            sub.text = extra; sub.visibility = if (extra.isEmpty()) View.GONE else View.VISIBLE
        } else {
            value.visibility = View.GONE
            val extra = dev.extras.mapNotNull { Fmt.reading(it) }.joinToString(" · ")
            sub.text = Fmt.sub(dev.id) + if (extra.isNotEmpty() && on) " · $extra" else ""
            sub.visibility = View.VISIBLE
        }
    }
}

/** Cartão de resumo do início: Clima, Segurança, Aspirador e A tocar. Esconde-se se não houver nada a mostrar. */
class SummaryCard(private val kit: Kit, private val kind: String, private val home: Home) : Card(kit.ctx) {
    private val iconBox = FrameLayout(kit.ctx)
    private val icon = kit.icon("mdi:thermometer", kit.ink, 24)
    private val label = kit.text("", 13f, kit.muted, 600, spacing = 0.18f)
    private val big = kit.text("", 28f, kit.fg, 300, display = true)
    private val sub = kit.text("", 14f, kit.muted)
    private val controls = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private var mediaId: String? = null

    private val devices get() = home.rooms.filter { !it.hidden }.flatMap { it.devices }.filter { !it.hidden }

    init {
        background = kit.glass(26)
        iconBox.addView(icon, FrameLayout.LayoutParams(kit.dp(24), kit.dp(24), Gravity.CENTER))
        val head = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        if (kind != "media") head.addView(iconBox, LinearLayout.LayoutParams(kit.dp(44), kit.dp(44)).apply { marginEnd = kit.dp(12) })
        head.addView(label)
        addView(head, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(18), kit.dp(18), kit.dp(18), 0) })
        val bottom = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(big)
        bottom.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
        if (kind == "media") bottom.addView(controls, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(8) })
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(kit.dp(18), 0, kit.dp(18), kit.dp(16)) })
        sub.setSingleLine(); sub.ellipsize = android.text.TextUtils.TruncateAt.END
        big.setSingleLine(); big.ellipsize = android.text.TextUtils.TruncateAt.END
        update()
    }

    private fun tint(c: Int) { iconBox.background = kit.circle(c) }

    override fun update() {
        big.setTextColor(kit.fg); sub.setTextColor(kit.muted); label.setTextColor(kit.muted)
        background = kit.glass(26)
        when (kind) {
            "climate" -> {
                val cl = devices.filter { it.kind == "climate" }
                val temps = home.rooms.flatMap { it.temperature }.mapNotNull { Fmt.state(it)?.state?.toDoubleOrNull() }
                val hums = home.rooms.flatMap { it.humidity }.mapNotNull { Fmt.state(it)?.state?.toDoubleOrNull() }
                visibility = if (cl.isEmpty() && temps.isEmpty()) View.GONE else View.VISIBLE
                label.text = Tr.t("Clima").uppercase(); icon.setImageDrawable(kit.mdi("mdi:thermometer")); tint(Color.parseColor("#30D158")); icon.setColorFilter(kit.ink)
                val t = cl.firstNotNullOfOrNull { Fmt.state(it.id)?.attrs?.optDouble("current_temperature", Double.NaN)?.takeIf { v -> !v.isNaN() } }
                    ?: temps.takeIf { it.isNotEmpty() }?.average()
                big.text = t?.let { Fmt.num(it, 0) + "°" } ?: "–"
                val on = cl.count { Fmt.isOn(it.id) }
                sub.text = buildString {
                    if (cl.isNotEmpty()) append("$on").append(Tr.t(" de ")).append("${cl.size}").append(Tr.t(" ligados"))
                    if (hums.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(Tr.t("Humidade ")).append(Fmt.num(hums.average(), 0)).append("%") }
                }
            }
            "security" -> {
                val locks = devices.filter { it.kind == "lock" }
                val opens = devices.filter { it.kind == "binary_sensor" && Fmt.state(it.id)?.attrs?.optString("device_class") in setOf("door", "window", "garage_door", "opening") }
                val items = locks.size + opens.size
                visibility = if (items == 0) View.GONE else View.VISIBLE
                label.text = Tr.t("Segurança").uppercase(); icon.setImageDrawable(kit.mdi("mdi:shield-outline")); tint(kit.soft); icon.setColorFilter(kit.fg)
                val unsafe = locks.count { Fmt.state(it.id)?.state == "unlocked" } + opens.count { Fmt.isOn(it.id) }
                big.text = if (unsafe == 0) Tr.t("Tudo seguro") else "$unsafe" + Tr.t(if (locks.any { Fmt.state(it.id)?.state == "unlocked" }) " destrancados" else " abertos")
                sub.text = "$items " + Tr.t("itens monitorizados")
            }
            "vacuum" -> {
                val v = devices.firstOrNull { it.kind == "vacuum" }
                visibility = if (v == null) View.GONE else View.VISIBLE
                if (v != null) {
                    label.text = Tr.t("Aspirador").uppercase(); icon.setImageDrawable(kit.mdi("mdi:robot-vacuum")); tint(Color.parseColor("#30D158")); icon.setColorFilter(kit.ink)
                    val st = Fmt.state(v.id)
                    big.text = Tr.t(Fmt.STATE_PT[st?.state] ?: st?.state.orEmpty())
                    val bat = st?.attrs?.optInt("battery_level", -1) ?: -1
                    sub.text = (if (bat >= 0) "$bat% · " else "") + v.name
                    setOnClickListener { Fmt.tap(v.id) }
                }
            }
            "media" -> {
                val p = devices.firstOrNull { it.kind == "media_player" && Fmt.state(it.id)?.state == "playing" }
                visibility = if (p == null) View.GONE else View.VISIBLE
                if (p != null) {
                    mediaId = p.id
                    val a = Fmt.state(p.id)?.attrs
                    label.text = Tr.t("A TOCAR") + " · " + p.name.uppercase()
                    big.text = a?.optString("media_title").orEmpty().ifBlank { p.name }
                    sub.text = a?.optString("media_artist").orEmpty()
                    controls.removeAllViews()
                    val id = p.id
                    controls.addView(kit.roundButton("mdi:skip-previous", 40, kit.soft, kit.fg) { Ha.call("media_player", "media_previous_track", listOf(id)) })
                    controls.addView(kit.roundButton("mdi:pause", 48, kit.cream, kit.ink) { Ha.call("media_player", "media_play_pause", listOf(id)) },
                        LinearLayout.LayoutParams(kit.dp(48), kit.dp(48)).apply { marginStart = kit.dp(10); marginEnd = kit.dp(10) })
                    controls.addView(kit.roundButton("mdi:skip-next", 40, kit.soft, kit.fg) { Ha.call("media_player", "media_next_track", listOf(id)) })
                }
            }
        }
    }
}

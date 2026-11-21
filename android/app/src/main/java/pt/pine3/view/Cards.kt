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
        val fg = if (any) kit.ink else kit.white
        background = kit.ripple(if (any) kit.rounded(kit.cream, 28) else kit.glass(28))
        iconBox.background = kit.circle(if (any) kit.accent else Color.argb(50, 255, 255, 255))
        icon.setColorFilter(if (any) kit.ink else kit.white)
        name.setTextColor(fg); sub.setTextColor(if (any) Color.argb(160, 43, 28, 46) else kit.muted); temp.setTextColor(fg)
        power.background = kit.ripple(kit.circle(if (any) Color.argb(36, 0, 0, 0) else Color.argb(50, 255, 255, 255)))
        (power.getChildAt(0) as android.widget.ImageView).setColorFilter(fg)
        power.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
        sub.text = if (ids.isEmpty()) "${room.devices.count { !it.hidden }} dispositivos" else "${on.count { it }} de ${ids.size} ligados"
        dots.onColor = kit.accent
        dots.offColor = if (any) Color.argb(60, 43, 28, 46) else Color.argb(90, 255, 255, 255)
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
        val fg = if (on) kit.ink else kit.white
        background = kit.ripple(if (on) kit.rounded(kit.cream, 26) else kit.glass(26))
        val tint = Fmt.ACCENT[domain] ?: kit.accent
        iconBox.background = kit.circle(if (on) tint else Color.argb(50, 255, 255, 255))
        icon.setColorFilter(if (on) kit.ink else kit.white)
        name.setTextColor(fg); sub.setTextColor(if (on) Color.argb(160, 43, 28, 46) else kit.muted); value.setTextColor(fg)
        power.background = kit.ripple(kit.circle(if (on) Color.argb(36, 0, 0, 0) else Color.argb(50, 255, 255, 255)))
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

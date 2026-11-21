package pt.pine3.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Navegação pedida pelas páginas. */
interface Nav {
    fun openRoom(room: Room)
    fun openSettings()
    fun back()
}

abstract class Page(val kit: Kit) {
    abstract val view: View
    /** Os estados mudaram: atualiza os cartões. */
    open fun update() {}
}

private const val DOCK_SPACE = 120

private fun scroll(kit: Kit, content: View): ScrollView = ScrollView(kit.ctx).apply {
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    addView(content, ViewGroup.LayoutParams(-1, -2))
}

private fun column(kit: Kit): LinearLayout = LinearLayout(kit.ctx).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(kit.dp(28), kit.dp(24), kit.dp(28), kit.dp(DOCK_SPACE))
}

private fun label(kit: Kit, t: String) = kit.text(t.uppercase(), 13f, kit.muted, 600, spacing = 0.18f)

private fun lp(kit: Kit, top: Int = 0, bottom: Int = 0) =
    LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(top); bottomMargin = kit.dp(bottom) }

/** Todas as luzes visíveis da casa. */
fun allLights(home: Home): List<String> =
    home.rooms.filter { !it.hidden }.flatMap { r -> r.devices.filter { !it.hidden && it.kind == "light" }.map { it.id } }.distinct()

/** Início: data e relógio, estado da casa, tempo, cenas e divisões. */
class HomePage(kit: Kit, private val home: Home, private val nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    private val grid = Grid(kit.ctx, kit.dp(250), kit.dp(180), kit.dp(16))
    private val status = kit.text("", 34f, kit.fg, 400, display = true)
    private val others = kit.text("", 15f, kit.muted).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
    private val persons = LinearLayout(kit.ctx)
    private val wIcon = kit.icon("mdi:weather-partly-cloudy", kit.accent, 44)
    private val wTemp = kit.text("", 34f, kit.fg, 300, display = true)
    private val wCond = kit.text("", 15f, kit.fg)
    private val wExtra = kit.text("", 13f, kit.muted)
    private val weatherCard = LinearLayout(kit.ctx)

    override val view: View

    init {
        val col = column(kit)

        // Linha de topo
        val top = LinearLayout(kit.ctx)
        val date = SimpleDateFormat(if (Tr.en) "EEEE, MMMM d" else "EEEE, d 'DE' MMMM", Tr.locale).format(Date()).uppercase()
        val left = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        left.addView(kit.text(date, 16f, kit.fg, 500, spacing = 0.12f))
        left.addView(TextClock(kit.ctx).apply {
            format24Hour = if (home.settings.optBoolean("clock_24h", true)) "HH:mm" else "h:mm"
            format12Hour = if (home.settings.optBoolean("clock_24h", true)) "HH:mm" else "h:mm"
            setTextColor(kit.fg); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 112f)
            typeface = kit.font(200, true); includeFontPadding = false
        }, lp(kit, top = 6))
        top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val mid = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(kit.dp(28), kit.dp(4), kit.dp(16), 0) }
        if (home.settings.optBoolean("show_status", true)) {
            mid.addView(label(kit, Tr.t("Estado da casa")))
            mid.addView(status, lp(kit, top = 8))
            mid.addView(others, lp(kit, top = 6))
            val btns = LinearLayout(kit.ctx)
            btns.addView(kit.pill(Tr.t("Acender luzes"), "mdi:lightbulb-outline", false) { Ha.call("light", "turn_on", allLights(home)) })
            btns.addView(kit.pill(Tr.t("Apagar luzes"), "mdi:power", true) { Ha.call("light", "turn_off", allLights(home).filter { Fmt.isOn(it) }) },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
            mid.addView(btns, lp(kit, top = 14))
        }
        top.addView(mid, LinearLayout.LayoutParams(0, -2, 1.1f))

        val right = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val rrow = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        if (home.settings.optBoolean("home_people", false) || home.persons.isNotEmpty()) rrow.addView(persons)
        rrow.addView(kit.roundButton("mdi:cog", 52, kit.soft, kit.fg) { nav.openSettings() },
            LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginStart = kit.dp(12) })
        right.addView(rrow, LinearLayout.LayoutParams(-2, -2))
        if (home.settings.optBoolean("show_weather", true) && home.weather != null) {
            weatherCard.apply {
                gravity = Gravity.CENTER_VERTICAL
                background = kit.glass(28)
                setPadding(kit.dp(22), kit.dp(18), kit.dp(26), kit.dp(18))
                addView(wIcon, LinearLayout.LayoutParams(kit.dp(44), kit.dp(44)).apply { marginEnd = kit.dp(16) })
                val tx = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                tx.addView(wTemp); tx.addView(wCond); tx.addView(wExtra)
                addView(tx)
            }
            right.addView(weatherCard, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(14) })
        }
        top.addView(right, LinearLayout.LayoutParams(0, -2, 1.1f))
        col.addView(top, lp(kit))

        // Cenas
        if (home.settings.optBoolean("show_scenes", true) && home.scenes.isNotEmpty()) {
            col.addView(label(kit, Tr.t("Cenas") + "  ·  ${home.scenes.size}"), lp(kit, top = 26))
            val row = LinearLayout(kit.ctx)
            home.scenes.forEach { id ->
                row.addView(kit.pill(Fmt.name(id), "mdi:creation", false) { Fmt.tap(id) },
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(10) })
            }
            col.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }, lp(kit, top = 12))
        }

        // Divisões
        home.rooms.filter { !it.hidden }.forEach { r ->
            val c = RoomCard(kit, r) { nav.openRoom(r) }
            cards += c; grid.addView(c)
        }
        if (home.settings.optBoolean("show_summary", true)) {
            listOf("climate", "security", "vacuum", "media").forEach { k ->
                val c = SummaryCard(kit, k, home); cards += c; grid.addView(c)
            }
        }
        col.addView(grid, lp(kit, top = 22))
        view = scroll(kit, col)
        update()
    }

    override fun update() {
        cards.forEach { it.update() }
        grid.requestLayout()
        val lights = allLights(home)
        val n = lights.count { Fmt.isOn(it) }
        status.text = when (n) { 0 -> Tr.t("Nenhuma luz acesa"); 1 -> Tr.t("1 luz acesa"); else -> "$n" + Tr.t(" luzes acesas") }
        val onOthers = home.rooms.filter { !it.hidden }.flatMap { it.devices }.filter {
            !it.hidden && it.kind in setOf("switch", "outlet", "fan", "media_player", "input_boolean") && Fmt.isOn(it.id)
        }.map { it.name }.distinct()
        others.text = if (onOthers.isEmpty()) "" else Tr.t("Também ligados: ") + onOthers.joinToString(", ")
        others.visibility = if (onOthers.isEmpty()) View.GONE else View.VISIBLE

        home.weather?.let { w ->
            val st = Fmt.state(w)
            if (st != null) {
                wIcon.setImageDrawable(kit.mdi(Fmt.WEATHER_ICON[st.state] ?: "mdi:weather-partly-cloudy"))
                val t = st.attrs.optDouble("temperature", Double.NaN)
                wTemp.text = if (t.isNaN()) "" else Fmt.num(t, 0) + "°"
                wCond.text = Tr.t(Fmt.WEATHER_PT[st.state] ?: st.state)
                val h = st.attrs.optDouble("humidity", Double.NaN)
                wExtra.text = if (h.isNaN()) "" else Tr.t("Humidade ") + "${Fmt.num(h, 0)}%"
            }
        }

        persons.removeAllViews()
        home.persons.forEach { id ->
            val st = Fmt.state(id) ?: return@forEach
            val box = FrameLayout(kit.ctx)
            val letter = Fmt.name(id).trim().take(1).uppercase()
            box.addView(kit.text(letter, 19f, kit.ink, 600, display = true).apply {
                gravity = Gravity.CENTER; background = kit.circle(if (st.state == "home") kit.accent else kit.soft)
            }, FrameLayout.LayoutParams(kit.dp(46), kit.dp(46)))
            if (st.state == "home") {
                box.addView(View(kit.ctx).apply { background = kit.circle(Color.parseColor("#30D158")) },
                    FrameLayout.LayoutParams(kit.dp(12), kit.dp(12), Gravity.BOTTOM or Gravity.END))
            }
            persons.addView(box, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)).apply { marginStart = kit.dp(6) })
        }
    }
}

/** Lista de todas as divisões. */
class RoomsPage(kit: Kit, home: Home, nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    override val view: View

    init {
        val col = column(kit)
        col.addView(kit.text(Tr.t("Divisões"), 40f, kit.fg, 400, display = true))
        val grid = Grid(kit.ctx, kit.dp(250), kit.dp(180), kit.dp(16))
        home.rooms.filter { !it.hidden }.forEach { r -> val c = RoomCard(kit, r) { nav.openRoom(r) }; cards += c; grid.addView(c) }
        col.addView(grid, lp(kit, top = 22))
        view = scroll(kit, col)
    }

    override fun update() = cards.forEach { it.update() }
}

/** Uma divisão: filtros por tipo e os dispositivos como cartões. */
class RoomPage(kit: Kit, private val room: Room, private val nav: Nav) : Page(kit) {
    private val tiles = ArrayList<Pair<Dev, Tile>>()
    private val chips = LinearLayout(kit.ctx)
    private val grid = Grid(kit.ctx, kit.dp(240), kit.dp(170), kit.dp(14))
    private val meta = label(kit, "")
    private var filter: Set<String>? = null
    override val view: View

    private val devices = room.devices.filter { !it.hidden }

    init {
        val col = column(kit)
        val head = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(kit.roundButton("mdi:chevron-left", 52, kit.soft, kit.fg) { nav.back() },
            LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { marginEnd = kit.dp(18) })
        val titles = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(meta)
        titles.addView(kit.text(room.name, 44f, kit.fg, 400, display = true), lp(kit, top = 4))
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val lights = devices.filter { it.kind == "light" }.map { it.id }
        if (lights.isNotEmpty()) {
            head.addView(kit.pill(Tr.t("Acender"), "mdi:lightbulb-outline", false) { Ha.call("light", "turn_on", lights) })
            head.addView(kit.pill(Tr.t("Apagar"), "mdi:power", true) { Ha.call("light", "turn_off", lights) },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
        }
        col.addView(head, lp(kit))
        col.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(kit, top = 18))
        devices.forEach { d -> val t = Tile(kit, d); tiles += d to t; grid.addView(t) }
        col.addView(grid, lp(kit, top = 16))
        view = scroll(kit, col)
        buildChips()
        update()
    }

    private fun domainOf(d: Dev) = if (d.kind == "outlet") "outlet" else Fmt.domain(d.id)

    private fun buildChips() {
        chips.removeAllViews()
        fun chip(label: String, icon: String?, count: Int, set: Set<String>?) {
            val sel = filter == set
            chips.addView(kit.pill("$label  $count", icon, sel) { filter = set; buildChips(); applyFilter() },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(10) })
        }
        chip(Tr.t("Tudo"), null, devices.size, null)
        Fmt.SECTIONS.forEach { (label, icon, set) ->
            val n = devices.count { domainOf(it) in set }
            if (n > 0) chip(Tr.t(label), icon, n, set)
        }
    }

    private fun applyFilter() {
        tiles.forEach { (d, t) -> t.visibility = if (filter == null || domainOf(d) in filter!!) View.VISIBLE else View.GONE }
        grid.requestLayout()
    }

    override fun update() {
        tiles.forEach { it.second.update() }
        val temp = room.temperature.firstNotNullOfOrNull { Fmt.state(it)?.state?.toDoubleOrNull() }
        val hum = room.humidity.firstNotNullOfOrNull { Fmt.state(it)?.state?.toDoubleOrNull() }
        meta.text = buildString {
            append("${devices.size}").append(Tr.t(" DISPOSITIVOS"))
            if (temp != null) append(" · ").append(Fmt.num(temp)).append("°C")
            if (hum != null) append(" · ").append(Fmt.num(hum, 0)).append("%")
        }
    }
}

/** Cenas e scripts. */
class ScenesPage(kit: Kit, home: Home) : Page(kit) {
    override val view: View

    init {
        val col = column(kit)
        col.addView(kit.text(Tr.t("Cenas"), 40f, kit.fg, 400, display = true))
        val grid = Grid(kit.ctx, kit.dp(240), kit.dp(110), kit.dp(14))
        (home.scenes + home.scripts).forEach { id ->
            val card = FrameLayout(kit.ctx).apply {
                background = kit.ripple(kit.glass(26)); isClickable = true; setOnClickListener { Fmt.tap(id) }
                addView(kit.icon(if (id.startsWith("scene.")) "mdi:creation" else "mdi:script-text-play", kit.accent, 26),
                    FrameLayout.LayoutParams(kit.dp(26), kit.dp(26), Gravity.TOP or Gravity.START).apply { setMargins(kit.dp(18), kit.dp(16), 0, 0) })
                addView(kit.text(Fmt.name(id), 19f, kit.fg, 400, display = true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END },
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(kit.dp(18), 0, kit.dp(18), kit.dp(16)) })
            }
            grid.addView(card)
        }
        col.addView(grid, lp(kit, top = 22))
        view = scroll(kit, col)
    }
}

/** Marcador: a casa 3D (Canvas) vem na fase 2. */
class Placeholder(kit: Kit, title: String, msg: String) : Page(kit) {
    override val view: View = LinearLayout(kit.ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(kit.text(title, 40f, kit.fg, 400, display = true))
        addView(kit.text(msg, 16f, kit.muted), lp(kit, top = 10))
    }
}

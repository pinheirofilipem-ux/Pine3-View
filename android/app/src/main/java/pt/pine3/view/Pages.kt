package pt.pine3.view

import android.graphics.Color
import android.text.TextUtils
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

/** Navegação pedida pelas páginas. */
interface Nav {
    fun openRoom(room: Room)
    fun openScreen(key: String)
    fun openDevice(id: String)
    fun openSettings()
    fun back()
}

abstract class Page(val kit: Kit) {
    abstract val view: View
    /** Os estados mudaram: atualiza os cartões. */
    open fun update() {}
}

/** Coluna centrada com largura máxima de 1500 (como .wrap do painel web): padding 0 40 e espaço para a dock em baixo. */
private class Wrap(val kit: Kit) : LinearLayout(kit.ctx) {
    init { orientation = VERTICAL }
    override fun onMeasure(w: Int, h: Int) {
        val max = kit.dp(1500)
        val size = MeasureSpec.getSize(w)
        super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(size, max), MeasureSpec.EXACTLY), h)
    }
}

fun dockSpace(kit: Kit) = kit.dp(if (kit.compact) 90 else 108) + kit.dp(14)

private fun pageOf(kit: Kit, build: (LinearLayout) -> Unit): ScrollView {
    val wrap = Wrap(kit)
    wrap.setPadding(kit.dp(40), 0, kit.dp(40), dockSpace(kit))
    build(wrap)
    val box = FrameLayout(kit.ctx)
    box.addView(wrap, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
    return ScrollView(kit.ctx).apply {
        isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
        addView(box, ViewGroup.LayoutParams(-1, -2))
    }
}

private fun lp(kit: Kit, top: Int = 0, bottom: Int = 0, w: Int = -1) =
    LinearLayout.LayoutParams(w, -2).apply { topMargin = kit.dp(top); bottomMargin = kit.dp(bottom) }

/** .eyebrow: 13px, 600, espaçamento .2em, maiúsculas, cor suave. */
private fun eyebrow(kit: Kit, t: String) = kit.text(t.uppercase(), 13f, kit.muted, 600, spacing = 0.2f)

/** .hero: eyebrow, título grande e, à direita, o que for preciso. */
private fun hero(kit: Kit, eyebrowText: String, title: String, right: View? = null): LinearLayout {
    val row = LinearLayout(kit.ctx).apply { gravity = Gravity.BOTTOM; setPadding(kit.dp(2), kit.dp(30), kit.dp(2), kit.dp(4)) }
    val l = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
    l.addView(eyebrow(kit, eyebrowText))
    l.addView(kit.text(title, kit.clamp(36f, kit.vw(5f), 52f), kit.fg, 400, display = true), lp(kit, top = 8))
    row.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
    if (right != null) row.addView(right)
    return row
}

fun devOf(id: String) = Dev(id, Fmt.name(id), Fmt.domain(id), emptyList(), emptyList(), false)

private fun devices(home: Home) = home.rooms.filter { !it.hidden }.flatMap { it.devices }.filter { !it.hidden }

/** Todas as luzes visíveis da casa. */
fun allLights(home: Home): List<String> = devices(home).filter { it.kind == "light" }.map { it.id }.distinct()

/** Cartão de tempo (.wxc): agora, próximas horas e amanhã (ou os próximos dias no formato alto). */
class WxCard(kit: Kit, private val home: Home, private val kind: String, private val onClick: () -> Unit) : Card(kit.ctx) {
    private val k = kit
    private val pref = home.settings.optString("weather_entity", "")
    init {
        isClickable = true; setOnClickListener { onClick() }
        Weather.load(pref) { post { update() } }
        update()
    }

    private fun deg(v: Double?) = if (v == null) "–" else Fmt.num(Math.round(v).toDouble(), 0) + "°"
    private fun icon(c: String) = Fmt.WEATHER_ICON[c] ?: "mdi:weather-cloudy"
    private fun hh(t: Long) = SimpleDateFormat("HH:mm", Tr.locale).format(Date(t))
    private fun tempColor(t: Double): Int = when {
        t <= 0 -> Color.parseColor("#8ECBFF"); t <= 10 -> Color.parseColor("#64D2FF"); t <= 15 -> Color.parseColor("#5EE0B0")
        t <= 20 -> Color.parseColor("#E8E04A"); t <= 25 -> Color.parseColor("#FFB340"); else -> Color.parseColor("#FF7A45")
    }

    override fun update() {
        removeAllViews()
        val tall = kind.contains("tall")
        val wide = kind == "wide"
        background = k.glass(24)
        Weather.load(pref) { post { update() } }
        val st = Weather.current(pref) { post { update() } }
        val als = Ipma.alerts { post { update() } }
        val col = LinearLayout(k.ctx).apply { orientation = if (tall) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(if (wide) 16 else 18), k.dp(if (wide) 12 else 14), k.dp(if (wide) 16 else 18), k.dp(if (wide) 12 else 14)) }
        val daily = Weather.daily
        val today = daily.firstOrNull()?.takeIf { java.text.SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date(it.time)) == java.text.SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date()) }

        // agora
        val now = LinearLayout(k.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        if (st != null) {
            val t = st.temp ?: Double.NaN
            now.addView(k.icon(icon(st.cond), k.accent, if (wide) 36 else 44), LinearLayout.LayoutParams(k.dp(if (wide) 36 else 44), k.dp(if (wide) 36 else 44)).apply { marginEnd = k.dp(12) })
            val tx = LinearLayout(k.ctx).apply { orientation = LinearLayout.VERTICAL }
            tx.addView(k.text(if (t.isNaN()) "–" else Fmt.num(Math.round(t).toDouble(), 0) + "°", if (wide) 30f else 36f, k.fg, 300, display = true))
            tx.addView(k.text(Tr.t(Fmt.WEATHER_PT[st.cond] ?: st.cond), 15f, k.fg).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
            if (today != null) tx.addView(k.text(Tr.t("Máx. ") + deg(today.temp) + " · " + Tr.t("Mín. ") + deg(today.low), 13f, k.muted))
            now.addView(tx)
        } else now.addView(k.text(Tr.t("A carregar o tempo…"), 14f, k.muted))
        col.addView(now)

        // próximas horas: fila que desliza para a esquerda (até 24 horas), como no iPhone
        val hours = Weather.hourly.filter { it.time >= System.currentTimeMillis() - 30 * 60_000 }.take(24)
        if (hours.isNotEmpty()) {
            val hr = LinearLayout(k.ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(k.dp(4), if (tall) k.dp(10) else 0, k.dp(4), if (tall) k.dp(10) else 0) }
            hours.forEachIndexed { i, h ->
                val c = LinearLayout(k.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                fun cell(v: View, lp: LinearLayout.LayoutParams) = c.addView(v, lp.apply { gravity = Gravity.CENTER_HORIZONTAL })
                cell(k.text(if (i == 0 && h.time - System.currentTimeMillis() < 3600_000) Tr.t("Agora") else hh(h.time), 12f, k.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                cell(k.icon(icon(h.cond), k.fg, 22), LinearLayout.LayoutParams(k.dp(22), k.dp(22)).apply { topMargin = k.dp(3); bottomMargin = k.dp(3) })
                cell(k.text(deg(h.temp), 15f, k.fg, 600).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                cell(k.text(if (h.precip != null && h.precip >= 10) "💧" + Math.round(h.precip) + "%" else " ", 11f, Color.parseColor("#64D2FF")).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                hr.addView(c, LinearLayout.LayoutParams(k.dp(58), -2))
            }
            val hsv = android.widget.HorizontalScrollView(k.ctx).apply {
                isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(hr)
                setOnClickListener { onClick() } // um toque abre a previsão; deslizar mostra mais horas
            }
            col.addView(hsv, if (tall) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = k.dp(12); marginEnd = k.dp(12) })
        }

        // amanhã / próximos dias
        val days = daily.drop(if (today != null) 1 else 0).take(if (tall) 6 else 1)
        if (days.isNotEmpty()) {
            val box = LinearLayout(k.ctx).apply { orientation = LinearLayout.VERTICAL }
            val lo = days.minOf { it.low ?: it.temp ?: 0.0 }; val hi = days.maxOf { it.temp ?: 0.0 }; val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun pos(v: Double) = ((v - lo) / span * 100).coerceIn(0.0, 100.0)
            days.forEachIndexed { i, d ->
                if (tall) {
                    val nm = if (i == 0 && today != null) Tr.t("Amanhã") else SimpleDateFormat("EEE", Tr.locale).format(Date(d.time)).replace(".", "").take(3).replaceFirstChar { it.uppercase() }
                    val r = LinearLayout(k.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                    r.addView(k.text(nm.uppercase(), 12f, k.fg, 600, spacing = 0.06f).apply { setSingleLine() }, LinearLayout.LayoutParams(k.dp(78), -2))
                    r.addView(k.icon(icon(d.cond), k.fg, 20), LinearLayout.LayoutParams(k.dp(20), k.dp(20)).apply { marginEnd = k.dp(6) })
                    r.addView(k.text(if (d.precip != null && d.precip >= 10) Math.round(d.precip).toString() + "%" else "", 11f, Color.parseColor("#64D2FF")), LinearLayout.LayoutParams(k.dp(34), -2))
                    r.addView(k.text(deg(d.low ?: d.temp), 12f, k.muted), LinearLayout.LayoutParams(k.dp(30), -2))
                    val bar = LinearLayout(k.ctx).apply { background = k.rounded(k.soft, 4) }
                    val l = d.low ?: d.temp ?: lo; val h2 = d.temp ?: hi
                    bar.addView(View(k.ctx), LinearLayout.LayoutParams(0, k.dp(6), pos(l).toFloat().coerceAtLeast(0.01f)))
                    bar.addView(View(k.ctx).apply {
                        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(tempColor(l), tempColor(h2))).apply { cornerRadius = k.dp(4).toFloat() }
                    }, LinearLayout.LayoutParams(0, k.dp(6), maxOf(4.0, pos(h2) - pos(l)).toFloat()))
                    bar.addView(View(k.ctx), LinearLayout.LayoutParams(0, k.dp(6), (100 - pos(h2)).toFloat().coerceAtLeast(0.01f)))
                    r.addView(bar, LinearLayout.LayoutParams(0, k.dp(6), 1f).apply { marginStart = k.dp(6); marginEnd = k.dp(8) })
                    r.addView(k.text(deg(d.temp), 13f, k.fg, 600), LinearLayout.LayoutParams(k.dp(34), -2))
                    box.addView(r, lp(k, top = 7))
                    return@forEachIndexed
                }
                val name = if (i == 0 && today != null) Tr.t("Amanhã") else SimpleDateFormat("EEE", Tr.locale).format(Date(d.time)).replace(".", "").take(3).replaceFirstChar { it.uppercase() }
                if (!tall) box.addView(k.text(name.uppercase(), 12f, k.muted, 500, spacing = 0.12f))
                val row = LinearLayout(k.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                if (tall) row.addView(k.text(name, 14f, k.fg), LinearLayout.LayoutParams(k.dp(52), -2))
                row.addView(k.icon(icon(d.cond), k.fg, 20), LinearLayout.LayoutParams(k.dp(20), k.dp(20)).apply { marginEnd = k.dp(8) })
                row.addView(k.text(deg(d.temp), 17f, k.fg, 600))
                row.addView(k.text(deg(d.low ?: d.temp), 15f, k.muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = k.dp(8) })
                if (d.precip != null && d.precip >= 10) row.addView(k.text("💧" + Math.round(d.precip) + "%", 11f, Color.parseColor("#64D2FF")), LinearLayout.LayoutParams(-2, -2).apply { marginStart = k.dp(8) })
                box.addView(row, lp(k, top = if (tall) 6 else 3))
            }
            col.addView(box, if (tall) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(-2, -2))
        }
        if (als.isNotEmpty()) {
            val wrap = LinearLayout(k.ctx).apply { orientation = LinearLayout.VERTICAL }
            wrap.addView(col)
            wrap.addView(Ipma.banner(k, als[0], false, als.size - 1), LinearLayout.LayoutParams(-1, -2).apply { setMargins(k.dp(12), 0, k.dp(12), k.dp(12)) })
            addView(wrap, LayoutParams(-1, -2))
        } else addView(col, LayoutParams(-1, -2))
    }
}

/** A "pílula" do tempo da disposição clássica (.wpill). */
private fun wxPill(kit: Kit, home: Home, onClick: () -> Unit): View? {
    val pref = home.settings.optString("weather_entity", "")
    val cur = Weather.current(pref)
    val al = Ipma.alerts().firstOrNull()
    return LinearLayout(kit.ctx).apply {
        gravity = Gravity.CENTER_VERTICAL; background = kit.glass(40); isClickable = true; setOnClickListener { onClick() }
        setPadding(kit.dp(18), 0, kit.dp(22), 0); minimumHeight = kit.dp(56)
        if (cur == null) {
            addView(kit.icon("mdi:weather-cloudy-clock", kit.accent, 26), LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)).apply { marginEnd = kit.dp(12) })
            addView(kit.text(Tr.t("A carregar o tempo…"), 16f, kit.muted))
            return@apply
        }
        addView(kit.icon(Fmt.WEATHER_ICON[cur.cond] ?: "mdi:weather-cloudy", kit.accent, 26), LinearLayout.LayoutParams(kit.dp(26), kit.dp(26)).apply { marginEnd = kit.dp(12) })
        addView(kit.text(if (cur.temp == null) "–" else Fmt.num(Math.round(cur.temp).toDouble(), 0) + "°", 26f, kit.fg, 400, display = true))
        addView(kit.text(Tr.t(Fmt.WEATHER_PT[cur.cond] ?: cur.cond), 16f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(12) })
        if (al != null) addView(kit.icon("mdi:alert", Ipma.color(al.level), 22), LinearLayout.LayoutParams(kit.dp(22), kit.dp(22)).apply { marginStart = kit.dp(12) })
    }
}

/** Início: 4 disposições (clássica, topo, colunas e barra lateral), como no painel web. */
class HomePage(kit: Kit, private val home: Home, private val nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    private val s = home.settings
    private val layout = s.optString("home_layout", "top")
    private val dense = layout == "columns" || layout == "sidebar"
    private val status = kit.text("", kit.clamp(24f, kit.vw(2.5f), 32f), kit.fg, 300, display = true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
    private val chips = LinearLayout(kit.ctx)
    private val peopleBox = LinearLayout(kit.ctx)
    private val clockSize = when (layout) {
        "columns" -> kit.clamp(80f, kit.vw(8.6f), 128f); "sidebar" -> kit.clamp(64f, kit.vw(6.4f), 88f)
        "top" -> kit.clamp(70f, kit.vw(7.8f), 116f); else -> kit.clamp(52f, kit.vw(7f), 76f)
    }
    override val view: View

    private fun date(): View {
        val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val fmt = SimpleDateFormat(if (Tr.en) "EEEE d MMMM" else "EEEE, d 'de' MMMM", if (Tr.en) java.util.Locale.UK else Tr.locale)
        row.addView(eyebrow(kit, fmt.format(Date())))
        if (s.optBoolean("home_people", false)) row.addView(peopleBox, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(16) })
        return row
    }

    private fun clock(): View = TextClock(kit.ctx).apply {
        val f = if (s.optBoolean("clock_24h", true)) "HH:mm" else "h:mm"
        format24Hour = f; format12Hour = f
        setTextColor(kit.fg); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, clockSize)
        typeface = kit.font(300, true); includeFontPadding = false; letterSpacing = -0.02f
    }

    private fun stat(): View? {
        if (!s.optBoolean("show_status", true) || Lay.edit != null) return null
        val c = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(eyebrow(kit, Tr.t("Estado da casa")))
        c.addView(status, lp(kit, top = 6))
        c.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(kit, top = 6))
        return c
    }

    private fun scenes(): View? {
        if (!s.optBoolean("show_scenes", true) || home.scenes.isEmpty() || Lay.edit != null) return null
        val c = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(eyebrow(kit, Tr.t("Cenas")))
        head.addView(kit.text("${home.scenes.size}", 12f, kit.fg, 600).apply {
            background = kit.rounded(kit.soft, 20); setPadding(kit.dp(8), kit.dp(2), kit.dp(8), kit.dp(2))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
        c.addView(head)
        val row = LinearLayout(kit.ctx)
        home.scenes.take(12).forEach { id ->
            row.addView(LinearLayout(kit.ctx).apply {
                gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.glass(40)); isClickable = true
                setPadding(kit.dp(20), 0, kit.dp(24), 0); minimumHeight = kit.dp(52)
                setOnClickListener { Fmt.tap(id) }
                addView(kit.icon(Fmt.state(id)?.attrs?.optString("icon").orEmpty().ifBlank { "mdi:creation-outline" }, kit.fg, 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(10) })
                addView(kit.text(Fmt.name(id), 17f, kit.fg, 600))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(12) })
        }
        c.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }, lp(kit, top = 14))
        return c
    }

    private fun grid(): View {
        val g = if (dense) Grid(kit.ctx, 0, kit.dp(kotlin.math.max(120f, kotlin.math.min(230f, (kit.hdp - 316f) / 3f)).toInt()), kit.dp(14), 4).also { it.list = kit.list }
        else Grid(kit.ctx, kit.dp(230), kit.dp(250), kit.dp(20)).also { it.list = kit.list }
        val order = Lay.roomOrder(home)
        val items = ArrayList<Pair<String, Card>>()
        Lay.rooms(home).forEach { r -> items += r.id to RoomCard(kit, r, dense) { nav.openRoom(r) }.also { c -> c.key = r.id; c.setupEdit(kit, "room", r.id, Lay.isBigRoom(home, r.id)) } }
        if (s.optBoolean("show_summary", true)) {
            items += "@climate" to SummaryCard(kit, "climate", null, home, nav, dense)
            items += "@security" to SummaryCard(kit, "security", null, home, nav, dense)
            devices(home).filter { Fmt.domain(it.id) == "vacuum" }.forEach { items += "@vacuum:${it.id}" to SummaryCard(kit, "vacuum", it.id, home, nav, dense) }
            items += "@media" to SummaryCard(kit, "media", null, home, nav, dense)
        }
        fun pos(k: String, i: Int) = order.indexOf(k).let { if (it < 0) 10000 + i else it }
        items.forEach { (k, c) -> if (c.key == null && Lay.edit != null) c.key = k }
        items.mapIndexed { i, p -> Triple(pos(p.first, i), p.first, p.second) }.sortedBy { it.first }.forEach { cards += it.third; g.addView(it.third) }
        if (Lay.edit != null) {
            Lay.hiddenRoomCount(home).takeIf { it > 0 }?.let { n -> g.addView(RestoreCard(kit, Tr.pl(n, "escondida", "escondidas", "hidden", "hidden")) { Lay.restoreRooms() }) }
            g.onReorder = { keys -> Lay.reorderRooms(keys) }
        }
        return g
    }

    init {
        val scenesView = scenes()
        view = pageOf(kit) { w ->
            when (layout) {
                "columns" -> {
                    val row = LinearLayout(kit.ctx).apply { setPadding(0, kit.dp(26), 0, 0) }
                    val left = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    left.addView(date()); left.addView(clock(), lp(kit, top = 8))
                    stat()?.let { left.addView(it, lp(kit, top = 24)) }
                    if (s.optBoolean("show_weather", true)) left.addView(WxCard(kit, home, "tall") { nav.openScreen("weather") }.also { cards += it }, lp(kit, top = 20))
                    row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = kit.dp(36) })
                    val main = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    scenesView?.let { main.addView(it, lp(kit, top = 0)) }
                    main.addView(grid(), lp(kit, top = 16))
                    row.addView(main, LinearLayout.LayoutParams(0, -2, 2f))
                    w.addView(row)
                }
                "sidebar" -> {
                    val row = LinearLayout(kit.ctx).apply { setPadding(0, kit.dp(20), 0, 0) }
                    val sb = LinearLayout(kit.ctx).apply {
                        orientation = LinearLayout.VERTICAL; background = kit.glass(32); setPadding(kit.dp(20), kit.dp(22), kit.dp(20), kit.dp(16))
                    }
                    sb.addView(date()); sb.addView(clock(), lp(kit, top = 0))
                    if (s.optBoolean("show_weather", true)) sb.addView(WxCard(kit, home, "tall side") { nav.openScreen("weather") }.also { cards += it }, lp(kit, top = 14))
                    stat()?.let { sb.addView(it, lp(kit, top = 14)) }
                    row.addView(sb, LinearLayout.LayoutParams(kit.dp(kit.clamp(290f, kit.vw(29f), 380f)), -2).apply { marginEnd = kit.dp(26) })
                    val main = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    scenesView?.let { main.addView(it) }
                    main.addView(grid(), lp(kit, top = 14))
                    row.addView(main, LinearLayout.LayoutParams(0, -2, 1f))
                    w.addView(row)
                }
                "classic" -> {
                    val top = LinearLayout(kit.ctx).apply { gravity = Gravity.TOP; setPadding(0, kit.dp(36), 0, 0) }
                    val l = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    l.addView(date()); l.addView(clock(), lp(kit, top = 8))
                    top.addView(l)
                    stat()?.let { top.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(32); marginEnd = kit.dp(16) }) } ?: top.addView(View(kit.ctx), LinearLayout.LayoutParams(0, 1, 1f))
                    if (s.optBoolean("show_weather", true)) wxPill(kit, home) { nav.openScreen("weather") }?.let { top.addView(it) }
                    w.addView(top)
                    scenesView?.let { w.addView(it, lp(kit, top = 20)) }
                    w.addView(grid(), lp(kit, top = 22))
                }
                else -> { // "top"
                    val top = LinearLayout(kit.ctx).apply { gravity = Gravity.TOP; setPadding(0, kit.dp(36), 0, 0) }
                    val left = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    val r1 = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                    val cb = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                    cb.addView(date()); cb.addView(clock(), lp(kit, top = 6))
                    r1.addView(cb)
                    stat()?.let { st ->
                        val box = LinearLayout(kit.ctx).apply { setPadding(kit.dp(32), 0, 0, 0) }
                        box.addView(View(kit.ctx).apply { setBackgroundColor(Color.argb(30, 255, 255, 255)) }, LinearLayout.LayoutParams(kit.dp(1), kit.dp(96)).apply { marginEnd = kit.dp(32) })
                        box.addView(st, LinearLayout.LayoutParams(0, -2, 1f))
                        r1.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(36) })
                    }
                    left.addView(r1)
                    scenesView?.let { left.addView(it, lp(kit, top = 8)) }
                    top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                    if (s.optBoolean("show_weather", true))
                        top.addView(WxCard(kit, home, "big") { nav.openScreen("weather") }.also { cards += it }, LinearLayout.LayoutParams(kit.dp(kit.clamp(300f, kit.wdp * 0.36f, 470f)), -2).apply { marginStart = kit.dp(36) })
                    w.addView(top)
                    w.addView(grid(), lp(kit, top = 22))
                }
            }
        }
        update()
    }

    override fun update() {
        cards.forEach { it.update() }
        val ds = devices(home).map { it.id }
        val lights = ds.filter { Fmt.domain(it) == "light" }
        val on = lights.filter { Fmt.isOn(it) }
        val others = ds.filter { Fmt.domain(it) in setOf("switch", "input_boolean", "fan", "media_player", "climate", "vacuum") && Fmt.isOn(it) }
        status.setTextColor(kit.fg)
        status.text = when {
            on.isNotEmpty() -> Tr.pl(on.size, "luz acesa", "luzes acesas", "light on", "lights on")
            others.isNotEmpty() -> Tr.pl(others.size, "dispositivo ligado", "dispositivos ligados", "device on", "devices on")
            lights.isNotEmpty() -> Tr.t("Todas as luzes apagadas")
            else -> Tr.t("Tudo tranquilo em casa")
        }
        chips.removeAllViews()
        others.forEach { id ->
            chips.addView(LinearLayout(kit.ctx).apply {
                gravity = Gravity.CENTER_VERTICAL; background = kit.glass(40); setPadding(kit.dp(10), kit.dp(5), kit.dp(12), kit.dp(5))
                isClickable = true; setOnClickListener { nav.openDevice(id) }
                addView(kit.icon(Fmt.icon(id, Fmt.domain(id)), kit.fg, 16), LinearLayout.LayoutParams(kit.dp(16), kit.dp(16)).apply { marginEnd = kit.dp(6) })
                addView(kit.text(Fmt.name(id), 13f, kit.fg, 500))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(6) })
        }
        peopleBox.removeAllViews()
        val tint = listOf("#F3A96B", "#F6CF6A", "#C9D3DC", "#B9E0C3")
        home.persons.mapNotNull { id -> Fmt.state(id)?.let { id to it } }.take(4).forEachIndexed { k, (id, st) ->
            val box = FrameLayout(kit.ctx)
            box.addView(kit.text(Fmt.name(id).trim().take(1).uppercase(), 20f, kit.ink, 600, display = true).apply {
                gravity = Gravity.CENTER; background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor(tint[k % 4])); setStroke(kit.dp(2), Color.argb(89, 255, 255, 255))
                }
            }, FrameLayout.LayoutParams(kit.dp(52), kit.dp(52)))
            box.addView(View(kit.ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (st.state == "home") Color.parseColor("#34C759") else Color.parseColor("#6B6B6B")); setStroke(kit.dp(2), Color.parseColor("#3A2238"))
                }
            }, FrameLayout.LayoutParams(kit.dp(14), kit.dp(14), Gravity.BOTTOM or Gravity.END))
            peopleBox.addView(box, LinearLayout.LayoutParams(kit.dp(52), kit.dp(52)).apply { if (k > 0) marginStart = -kit.dp(12) })
        }
    }
}

/** Lista de todas as divisões. */
class RoomsPage(kit: Kit, home: Home, nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    override val view: View

    init {
        val rooms = Lay.rooms(home)
        view = pageOf(kit) { w ->
            w.addView(hero(kit, Tr.pl(rooms.size, "divisão", "divisões", "room", "rooms"), Tr.t("Divisões")))
            val sh = kit.short
            val g = if (sh) Grid(kit.ctx, kit.dp(210), kit.dp(kit.clamp(116f, (kit.hdp - 226f) / 3f, 250f).toInt()), kit.dp(14)).also { it.list = kit.list }
            else Grid(kit.ctx, kit.dp(230), kit.dp(250), kit.dp(20)).also { it.list = kit.list }
            rooms.forEach { r -> val c = RoomCard(kit, r, false, sh) { nav.openRoom(r) }; c.key = r.id; c.setupEdit(kit, "room", r.id, Lay.isBigRoom(home, r.id)); cards += c; g.addView(c) }
            if (Lay.edit != null) {
                Lay.hiddenRoomCount(home).takeIf { it > 0 }?.let { n -> g.addView(RestoreCard(kit, Tr.pl(n, "escondida", "escondidas", "hidden", "hidden")) { Lay.restoreRooms() }) }
                g.onReorder = { keys -> Lay.reorderRooms(keys) }
            }
            w.addView(g, lp(kit, top = if (sh) 16 else 22))
        }
    }

    override fun update() = cards.forEach { it.update() }
}

/** Uma divisão: cabeçalho com estado, filtros por tipo, cenas e os dispositivos em cartões. */
class RoomPage(kit: Kit, private val room: Room, private val nav: Nav) : Page(kit) {
    private val tiles = ArrayList<Pair<Dev, Card>>()
    private val chips = LinearLayout(kit.ctx)
    private val grid = Grid(kit.ctx, kit.dp(200), kit.dp(170), kit.dp(18)).also { it.list = kit.list }
    private val meta = eyebrow(kit, "")
    private val status = kit.text("", kit.clamp(22f, kit.vw(2.3f), 30f), kit.fg, 300, display = true)
    private var filter: Set<String>? = null
    override val view: View
    private val devs = Ha.home?.let { Lay.devs(it, room) } ?: room.devices.filter { !it.hidden }

    private fun cat(d: Dev) = Fmt.SECTIONS.indexOfFirst { (if (d.kind == "outlet") "outlet" else Fmt.domain(d.id)) in it.third }.let { if (it < 0) 99 else it }

    init {
        val sorted = devs.mapIndexed { n, d -> Triple(d, cat(d), n) }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
        view = pageOf(kit) { w ->
            val head = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, kit.dp(26), 0, 0) }
            head.addView(kit.roundButton("mdi:chevron-left", 46, kit.soft, kit.fg) { nav.back() }, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)).apply { marginEnd = kit.dp(18) })
            val titles = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
            titles.addView(meta)
            titles.addView(kit.text(room.name, kit.clamp(36f, kit.vw(5f), 52f), kit.fg, 400, display = true), lp(kit, top = 8))
            head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(24) })
            val lights = devs.filter { it.kind == "light" }.map { it.id }
            if (lights.isNotEmpty()) {
                head.addView(kit.pill(Tr.t("Acender"), "mdi:lightbulb-outline", false) { Ha.call("light", "turn_on", lights) })
                head.addView(kit.pill(Tr.t("Apagar"), "mdi:power", true) { Ha.call("light", "turn_off", lights) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
            }
            w.addView(head)
            w.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(kit, top = 18))
            sorted.forEach { d ->
                val card: Card = if (d.kind == "climate" && Fmt.state(d.id) != null) ClimateCard(kit, d, null) { nav.openDevice(it) } else if (Fmt.domain(d.id) == "vacuum" && Fmt.state(d.id) != null) VacCard(kit, d, room) { nav.openDevice(it) } else Tile(kit, d) { nav.openDevice(it) }
                card.key = d.id; card.setupEdit(kit, "dev", d.id, Ha.home?.let { Lay.isBigDev(it, d.id) } == true)
                tiles += d to card; grid.addView(card)
            }
            if (Lay.edit != null) {
                Ha.home?.let { h -> Lay.hiddenDevCount(h, room).takeIf { it > 0 }?.let { n -> grid.addView(RestoreCard(kit, Tr.pl(n, "escondido", "escondidos", "hidden", "hidden")) { Lay.restoreDevs(room) }) } }
                grid.onReorder = { keys -> Lay.reorderDevs(room, keys) }
            }
            w.addView(grid, lp(kit, top = 16))
        }
        buildChips(); update()
    }

    private fun dom(d: Dev) = if (d.kind == "outlet") "outlet" else Fmt.domain(d.id)

    private fun buildChips() {
        chips.removeAllViews()
        fun chip(label: String, icon: String?, count: Int, set: Set<String>?) {
            chips.addView(kit.pill("$label  $count", icon, filter == set) { filter = set; buildChips(); applyFilter() }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(10) })
        }
        if (Fmt.SECTIONS.count { s -> devs.any { dom(it) in s.third } } < 2) return
        chip(Tr.t("Tudo"), null, devs.size, null)
        Fmt.SECTIONS.forEach { (label, icon, set) -> val n = devs.count { dom(it) in set }; if (n > 0) chip(Tr.t(label), icon, n, set) }
    }

    private fun applyFilter() {
        tiles.forEach { (d, t) -> t.visibility = if (filter == null || dom(d) in filter!!) View.VISIBLE else View.GONE }
        grid.requestLayout()
    }

    override fun update() {
        tiles.forEach { it.second.update() }
        val (temp, hum) = Fmt.roomClimate(room)
        val sub = listOfNotNull(temp, hum).joinToString(" · ")
        meta.text = (Tr.pl(devs.size, "dispositivo", "dispositivos", "device", "devices") + if (sub.isNotEmpty()) " · $sub" else "").uppercase()
        val lights = devs.filter { it.kind == "light" }; val lit = lights.count { Fmt.isOn(it.id) }
        val others = devs.count { it.kind in setOf("switch", "input_boolean", "fan", "media_player", "climate", "vacuum") && Fmt.isOn(it.id) }
        status.setTextColor(kit.fg)
        status.text = when {
            lit > 0 -> Tr.pl(lit, "luz acesa", "luzes acesas", "light on", "lights on")
            others > 0 -> Tr.pl(others, "dispositivo ligado", "dispositivos ligados", "device on", "devices on")
            else -> Tr.t("Tudo desligado")
        }
    }
}

/** Ecrã Clima: cada ar condicionado/termóstato com controlo direto e a temperatura de cada divisão. */
class ClimatePage(kit: Kit, home: Home, nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    override val view: View

    init {
        val rooms = home.rooms.filter { !it.hidden }
        val cl = rooms.flatMap { r -> r.devices.filter { !it.hidden && it.kind == "climate" && Fmt.state(it.id) != null }.map { it to r } }
        val readings = rooms.map { it to Fmt.roomClimate(it) }.filter { it.second.first != null || it.second.second != null }
        view = pageOf(kit) { w ->
            val onN = cl.count { Fmt.isOn(it.first.id) }
            w.addView(hero(kit, if (cl.isNotEmpty()) "$onN" + Tr.t(" de ") + "${cl.size}" + Tr.t(" ligados") else Tr.t("Clima"), Tr.t("Clima")))
            if (cl.isNotEmpty()) {
                val g = Grid(kit.ctx, kit.dp(260), kit.dp(170), kit.dp(18)).also { it.list = kit.list }
                cl.forEach { (d, r) -> val c = ClimateCard(kit, d, r.name) { nav.openDevice(it) }; cards += c; g.addView(c) }
                w.addView(g, lp(kit, top = 16))
            }
            if (readings.isNotEmpty()) {
                w.addView(eyebrow(kit, Tr.t("Temperatura por divisão")), lp(kit, top = 32))
                val g = Grid(kit.ctx, kit.dp(170), 0, kit.dp(18))
                readings.forEach { (r, c) ->
                    g.addView(LinearLayout(kit.ctx).apply {
                        orientation = LinearLayout.VERTICAL; background = kit.ripple(kit.glass(26)); setPadding(kit.dp(18), kit.dp(18), kit.dp(18), kit.dp(18))
                        isClickable = true; setOnClickListener { nav.openRoom(r) }
                        addView(kit.text(r.name, 19f, kit.fg, 500, display = true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
                        val v = LinearLayout(kit.ctx).apply { gravity = Gravity.BOTTOM }
                        v.addView(kit.text((c.first ?: "–").replace("°C", "°"), 30f, kit.fg, 300, display = true))
                        if (c.second != null) {
                            v.addView(kit.icon("mdi:water", Color.parseColor("#64D2FF"), 14), LinearLayout.LayoutParams(kit.dp(14), kit.dp(14)).apply { marginStart = kit.dp(12); bottomMargin = kit.dp(4) })
                            v.addView(kit.text(c.second!!, 15f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(2) })
                        }
                        addView(v, lp(kit, top = 10))
                    })
                }
                w.addView(g, lp(kit, top = 16))
            }
            if (cl.isEmpty() && readings.isEmpty()) w.addView(kit.text(Tr.t("Nada para monitorizar."), 16f, kit.muted), lp(kit, top = 30))
        }
    }

    override fun update() = cards.forEach { it.update() }
}

/** Ecrã Segurança: câmaras, fechaduras e sensores de portas/janelas, com o estado geral. */
class SecurityPage(kit: Kit, home: Home, nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    override val view: View

    init {
        val devs = devices(home)
        val ids = devs.filter {
            it.kind == "lock" || it.kind == "camera" ||
                (it.kind == "binary_sensor" && Fmt.state(it.id)?.attrs?.optString("device_class") in setOf("door", "window", "garage_door", "opening", "motion", "smoke", "moisture", "gas"))
        }.sortedBy { when (it.kind) { "camera" -> 0; "lock" -> 1; else -> 2 } }
        val watch = ids.filter { (it.kind == "lock" || it.kind == "binary_sensor") && Fmt.state(it.id)?.state != "unavailable" }
        val bad = watch.count { if (it.kind == "lock") Fmt.state(it.id)?.state != "locked" else Fmt.state(it.id)?.state == "on" }
        view = pageOf(kit) { w ->
            val chip: View? = if (watch.isEmpty()) null else LinearLayout(kit.ctx).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(kit.dp(12), 0, kit.dp(16), 0); minimumHeight = kit.dp(44)
                val col = if (bad > 0) Color.parseColor("#FFB340") else Color.parseColor("#5FE08A")
                background = kit.rounded(if (bad > 0) Color.argb(51, 255, 159, 10) else Color.argb(46, 48, 209, 88), 40)
                addView(kit.icon(if (bad > 0) "mdi:shield-alert-outline" else "mdi:shield-check-outline", col, 22), LinearLayout.LayoutParams(kit.dp(22), kit.dp(22)).apply { marginEnd = kit.dp(8) })
                addView(kit.text(if (bad > 0) Tr.pl(bad, "item aberto", "itens abertos", "item open", "items open") else Tr.t("Tudo seguro"), 15f, col, 600))
            }
            w.addView(hero(kit, Tr.t("Segurança"), Tr.t("Portas, janelas e fechaduras"), chip))
            if (ids.isEmpty()) w.addView(kit.text(Tr.t("Nada para monitorizar."), 16f, kit.muted), lp(kit, top = 30))
            else {
                val g = Grid(kit.ctx, kit.dp(200), kit.dp(170), kit.dp(18)).also { it.list = kit.list }
                ids.forEach { d ->
                    val c: Card = if (d.kind == "camera") CamTile(kit, d) { nav.openDevice(it) } else Tile(kit, d, secWatch = d.kind == "lock" || d.kind == "binary_sensor") { nav.openDevice(it) }
                    cards += c; g.addView(c)
                }
                w.addView(g, lp(kit, top = 16))
            }
        }
    }

    override fun update() = cards.forEach { it.update() }
}

/** Cenas e scripts como cartões. */
class ScenesPage(kit: Kit, home: Home, nav: Nav) : Page(kit) {
    private val cards = ArrayList<Card>()
    override val view: View

    init {
        view = pageOf(kit) { w ->
            w.addView(hero(kit, Tr.t("Toca para executar"), Tr.t("Cenas e scripts")))
            fun section(title: String, ids: List<String>) {
                if (ids.isEmpty()) return
                w.addView(eyebrow(kit, title), lp(kit, top = 28))
                val g = Grid(kit.ctx, kit.dp(200), kit.dp(170), kit.dp(18)).also { it.list = kit.list }
                ids.forEach { id -> val c = Tile(kit, devOf(id)) { nav.openDevice(it) }; cards += c; g.addView(c) }
                w.addView(g, lp(kit, top = 14))
            }
            section(Tr.t("Cenas"), home.scenes); section("Scripts", home.scripts)
            if (home.scenes.isEmpty() && home.scripts.isEmpty()) w.addView(kit.text(Tr.t("Não há cenas nem scripts."), 16f, kit.muted), lp(kit, top = 30))
        }
    }

    override fun update() = cards.forEach { it.update() }
}

/** Marcador: a casa 3D (Canvas) vem mais tarde. */
class Placeholder(kit: Kit, title: String, msg: String) : Page(kit) {
    override val view: View = LinearLayout(kit.ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(kit.text(title, 40f, kit.fg, 400, display = true))
        addView(kit.text(msg, 16f, kit.muted), lp(kit, top = 10))
    }
}

/** Casa 3D: um bloco por divisão, empilhados por piso; as divisões com luzes acesas brilham. Arrasta para rodar, pinça para aproximar. */
class HousePage(kit: Kit, private val home: Home, private val nav: Nav) : Page(kit) {
    private val chips = LinearLayout(kit.ctx)
    private val eyebrowView = eyebrow(kit, "")
    private val houseView = HouseView(kit, { id -> Fmt.tap(id) }, { id -> home.rooms.firstOrNull { it.id == id }?.let { nav.openRoom(it) } }, { id -> nav.openDevice(id) })
    override val view: View

    init {
        view = pageOf(kit) { w ->
            val row = LinearLayout(kit.ctx).apply { gravity = Gravity.BOTTOM; setPadding(kit.dp(2), kit.dp(30), kit.dp(2), kit.dp(4)) }
            val l = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
            l.addView(eyebrowView)
            l.addView(kit.text(Tr.t("Casa 3D"), kit.clamp(36f, kit.vw(5f), 52f), kit.fg, 400, display = true), lp(kit, top = 8))
            row.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
            w.addView(row)
            w.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, lp(kit, top = 14))
            val stage = FrameLayout(kit.ctx).apply {
                background = kit.glass(32); clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, kit.dp(32).toFloat()) } }
            }
            stage.addView(houseView, FrameLayout.LayoutParams(-1, -1))
            val ctl = LinearLayout(kit.ctx)
            fun btn(icon: String, f: () -> Unit) = kit.roundButton(icon, 40, kit.soft, kit.fg) { f(); houseView.invalidate() }.also { ctl.addView(it, LinearLayout.LayoutParams(kit.dp(40), kit.dp(40)).apply { marginStart = kit.dp(8) }) }
            btn("mdi:minus") { houseView.zoomBy(1 / 1.35f) }
            btn("mdi:plus") { houseView.zoomBy(1.35f) }
            btn("mdi:rotate-left") { House.rz -= 30f }
            btn("mdi:crosshairs") { House.rx = 58f; House.rz = -38f; House.zoom = 1f; House.px = 0f; House.py = 0f }
            btn("mdi:rotate-right") { House.rz += 30f }
            btn("mdi:arrow-collapse-vertical") { wall(-6) }
            btn("mdi:arrow-expand-vertical") { wall(6) }
            stage.addView(ctl, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = kit.dp(14); rightMargin = kit.dp(14) })
            stage.addView(kit.text(Tr.t("Arrasta para rodar · Dois dedos para aproximar · Toca numa divisão para a abrir ou numa luz para a ligar"), 13f, kit.muted).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = kit.dp(12); leftMargin = kit.dp(16); rightMargin = kit.dp(16) })
            w.addView(stage, LinearLayout.LayoutParams(-1, kit.dp(kit.clamp(380f, minOf(kit.hdp * 0.68f, 640f), 640f))).apply { topMargin = kit.dp(16) })
        }
        update()
    }

    private fun wall(d: Int) {
        val cur = home.settings.optInt("wall_height", House.WALL_DEFAULT)
        val next = (cur + d).coerceIn(10, 100)
        if (next != cur) Ha.saveSettings(org.json.JSONObject().put("wall_height", next))
    }

    private fun buildChips(m: House.HModel) {
        chips.removeAllViews()
        if (m.levels.size < 2) return
        chips.addView(kit.pill(Tr.t("Todos os pisos"), null, House.floor == null) { House.floor = null; update() }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(10) })
        m.levels.forEach { lv -> chips.addView(kit.pill(m.floorNames[lv] ?: "$lv", null, House.floor == lv) { House.floor = lv; update() }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(10) }) }
    }

    override fun update() {
        val m = House.build(home)
        buildChips(m)
        val lit = home.rooms.filter { !it.hidden }.count { r -> Fmt.switchables(r.devices.filter { !it.hidden }.map { it.id }).any { Fmt.isOn(it) } }
        eyebrowView.text = Tr.pl(lit, "divisão acesa", "divisões acesas", "room lit", "rooms lit").uppercase()
        houseView.model = m
    }
}

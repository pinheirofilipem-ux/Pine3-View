package pt.pine3.view

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date

/** Previsão do tempo completa: agora, avisos do IPMA, UV e incêndio à esquerda; próximas horas e dias à direita. */
class ForecastSheet(private val act: Activity, private val kit: Kit, private val pref: String) {
    private val dialog = PokeDialog(act)
    private val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val muted = Color.argb(166, 255, 255, 255)
    private val h = Handler(Looper.getMainLooper())
    private val wide = kit.wdp >= 820f
    private var open = true
    private val redraw = Runnable { if (open) { Weather.load(pref) { if (open) h.post { render() } }; render() } }
    private val listener = object : Ha.Listener {
        // a ligação pode ainda estar a recuperar (o ecrã acabou de acordar): quando chegam os estados, volta a pedir e a desenhar
        override fun onStates(changed: Set<String>) { if (changed.isEmpty() || changed.any { it.startsWith("weather.") || it.contains("ipma") || it == "sun.sun" }) { h.removeCallbacks(redraw); h.postDelayed(redraw, 400) } }
    }

    private fun deg(v: Double?) = if (v == null) "–" else Fmt.num(Math.round(v).toDouble(), 0) + "°"
    private fun icon(c: String) = Fmt.WEATHER_ICON[c] ?: "mdi:weather-cloudy"
    private fun hm(t: Long) = SimpleDateFormat("HH:mm", Tr.locale).format(Date(t))
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(top) }
    private fun tempColor(t: Double): Int = when {
        t <= 0 -> Color.parseColor("#8ECBFF"); t <= 10 -> Color.parseColor("#64D2FF"); t <= 15 -> Color.parseColor("#5EE0B0")
        t <= 20 -> Color.parseColor("#E8E04A"); t <= 25 -> Color.parseColor("#FFB340"); else -> Color.parseColor("#FF7A45")
    }
    private fun sub(into: LinearLayout, t: String, top: Int = 18, badge: Int = 0) {
        val r = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        r.addView(kit.text(t.uppercase(), 12f, muted, 600, spacing = 0.14f))
        if (badge > 0) r.addView(kit.text("$badge", 11f, kit.ink, 700).apply { background = kit.rounded(kit.accent, 20); setPadding(kit.dp(8), kit.dp(2), kit.dp(8), kit.dp(2)) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(8) })
        into.addView(r, lp(top))
    }
    private fun chip(icon: String, label: String, value: String): View = LinearLayout(act).apply {
        gravity = Gravity.CENTER_VERTICAL; background = kit.rounded(Color.argb(30, 255, 255, 255), 20); setPadding(kit.dp(12), kit.dp(7), kit.dp(14), kit.dp(7))
        addView(kit.icon(icon, kit.accent, 18), LinearLayout.LayoutParams(kit.dp(18), kit.dp(18)).apply { marginEnd = kit.dp(6) })
        addView(kit.text("$label ", 13f, muted)); addView(kit.text(value, 13f, Color.WHITE, 700))
    }
    private fun chips(into: LinearLayout, list: List<View>) {
        if (list.isEmpty()) return
        val g = Grid(act, kit.dp(150), kit.dp(34), kit.dp(8))
        list.forEach { g.addView(it) }
        into.addView(g, lp(12))
    }

    private fun render() {
        content.removeAllViews()
        val cur = Weather.current(pref) { if (open) h.post { render() } }
        val daily = Weather.daily; val hourly = Weather.hourly
        val today = daily.firstOrNull()?.takeIf { SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date(it.time)) == SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date()) }

        val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(kit.text(Tr.t("Previsão do tempo"), 24f, Color.WHITE, 400, display = true), LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(kit.roundButton("mdi:close", 44, Color.argb(40, 255, 255, 255), Color.WHITE) { dismiss() })
        content.addView(hd)

        // à esquerda: agora, chips, avisos, UV e incêndio, fonte
        val left = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        if (cur != null) {
            val now = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
            now.addView(kit.icon(icon(cur.cond), kit.accent, 64), LinearLayout.LayoutParams(kit.dp(64), kit.dp(64)).apply { marginEnd = kit.dp(16) })
            now.addView(kit.text(if (cur.temp == null) "–" else Fmt.num(Math.round(cur.temp).toDouble(), 0) + "°", 64f, Color.WHITE, 200, display = true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(16) })
            val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            tx.addView(kit.text(Tr.t(Fmt.WEATHER_PT[cur.cond] ?: cur.cond), 18f, Color.WHITE, 500))
            if (today != null) tx.addView(kit.text(Tr.t("Máx. ") + deg(today.temp) + " · " + Tr.t("Mín. ") + deg(today.low), 14f, muted))
            now.addView(tx)
            left.addView(now, lp(10))
            val sun = Fmt.state("sun.sun")?.attrs
            fun at(k: String) = sun?.optString(k)?.takeIf { it.isNotBlank() }?.let { try { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() } catch (_: Exception) { null } }
            val cs = ArrayList<View>()
            today?.precip?.let { cs += chip("mdi:water-outline", Tr.t("Chuva"), Math.round(it).toString() + "%") }
            at("next_rising")?.let { cs += chip("mdi:weather-sunset-up", Tr.t("Nascer"), hm(it)) }
            at("next_setting")?.let { cs += chip("mdi:weather-sunset-down", Tr.t("Pôr"), hm(it)) }
            cur.humidity?.let { cs += chip("mdi:water-percent", Tr.t("Humidade"), Math.round(it).toString() + "%") }
            cur.wind?.let { cs += chip("mdi:weather-windy", Tr.t("Vento"), Math.round(it).toString() + " " + cur.windUnit) }
            chips(left, cs)
        } else left.addView(kit.text(Tr.t("A carregar o tempo…"), 15f, muted), lp(14))

        val als = Ipma.alerts { if (open) h.post { render() } }
        if (als.isNotEmpty()) {
            sub(left, Tr.t("Avisos"), 18, als.size)
            als.forEach { left.addView(Ipma.banner(kit, it, true), lp(8)) }
        } else if (Ipma.alertId != null) {
            left.addView(LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; background = kit.rounded(Color.argb(30, 52, 199, 89), 16); setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(10))
                addView(kit.icon("mdi:check-circle-outline", Color.parseColor("#34C759"), 20), LinearLayout.LayoutParams(kit.dp(20), kit.dp(20)).apply { marginEnd = kit.dp(10) })
                addView(kit.text(Tr.t("Sem avisos do IPMA"), 14f, Color.WHITE))
            }, lp(16))
        }
        val extra = ArrayList<View>()
        Ipma.uvId?.let { id -> Fmt.state(id)?.takeIf { it.state != "unknown" }?.let { extra += chip("mdi:sun-wireless-outline", Tr.t("Índice UV"), it.state) } }
        Ipma.fireId?.let { id -> Fmt.state(id)?.takeIf { it.state != "unknown" }?.let { extra += chip("mdi:fire", Tr.t("Risco de incêndio"), Fmt.sub(id)) } }
        chips(left, extra)
        left.addView(kit.text(Tr.t("Fonte") + ": " + Weather.sourceName(pref), 12f, muted), lp(16))

        // à direita: próximas horas e próximos dias
        val right = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        if (!Weather.loaded) right.addView(kit.text(Tr.t("A carregar a previsão…"), 15f, muted).apply { gravity = Gravity.CENTER }, lp(30))
        else if (hourly.isEmpty() && daily.isEmpty()) right.addView(kit.text(Tr.t("Sem previsão disponível."), 15f, muted).apply { gravity = Gravity.CENTER }, lp(30))
        else {
            if (hourly.isNotEmpty()) {
                sub(right, Tr.t("Próximas horas"), 6)
                val row = LinearLayout(act)
                hourly.forEachIndexed { k, f ->
                    val c = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; background = kit.rounded(Color.argb(24, 255, 255, 255), 18); setPadding(kit.dp(6), kit.dp(10), kit.dp(6), kit.dp(10)) }
                    fun cell(v: View, lp: LinearLayout.LayoutParams) = c.addView(v, lp.apply { gravity = Gravity.CENTER_HORIZONTAL })
                    cell(kit.text(if (k == 0 && f.time - System.currentTimeMillis() < 3600_000) Tr.t("Agora") else hm(f.time), 12f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                    cell(kit.icon(icon(f.cond), Color.WHITE, 24), LinearLayout.LayoutParams(kit.dp(24), kit.dp(24)).apply { topMargin = kit.dp(4); bottomMargin = kit.dp(4) })
                    cell(kit.text(deg(f.temp), 16f, Color.WHITE, 600).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                    cell(kit.text(f.precip?.let { Math.round(it).toString() + "%" } ?: " ", 11f, Color.parseColor("#64D2FF")).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, -2))
                    row.addView(c, LinearLayout.LayoutParams(kit.dp(66), -2).apply { marginEnd = kit.dp(8) })
                }
                right.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(row) }, lp(8))
            }
            if (daily.isNotEmpty()) {
                sub(right, Tr.t("Próximos dias"))
                val lows = daily.map { it.low ?: it.temp ?: 0.0 }; val highs = daily.map { it.temp ?: 0.0 }
                val lo = lows.min(); val hi = highs.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
                fun pos(v: Double) = ((v - lo) / span * 100).coerceIn(0.0, 100.0)
                daily.forEachIndexed { k, d ->
                    val low = d.low ?: d.temp ?: lo; val high = d.temp ?: hi
                    val r = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
                    r.addView(kit.text(if (k == 0 && today != null) Tr.t("Hoje") else SimpleDateFormat("EEE", Tr.locale).format(Date(d.time)).replace(".", "").take(3).replaceFirstChar { it.uppercase() }, 15f, Color.WHITE, 500), LinearLayout.LayoutParams(kit.dp(56), -2))
                    r.addView(kit.icon(icon(d.cond), Color.WHITE, 22), LinearLayout.LayoutParams(kit.dp(22), kit.dp(22)).apply { marginEnd = kit.dp(8) })
                    r.addView(kit.text(d.precip?.let { Math.round(it).toString() + "%" } ?: "", 12f, Color.parseColor("#64D2FF")), LinearLayout.LayoutParams(kit.dp(38), -2))
                    r.addView(kit.text(deg(low), 14f, muted), LinearLayout.LayoutParams(kit.dp(36), -2))
                    val bar = LinearLayout(act).apply { background = kit.rounded(Color.argb(40, 255, 255, 255), 4) }
                    bar.addView(View(act), LinearLayout.LayoutParams(0, kit.dp(6), pos(low).toFloat().coerceAtLeast(0.01f)))
                    bar.addView(View(act).apply { background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(tempColor(low), tempColor(high))).apply { cornerRadius = kit.dp(4).toFloat() } }, LinearLayout.LayoutParams(0, kit.dp(6), maxOf(4.0, pos(high) - pos(low)).toFloat()))
                    bar.addView(View(act), LinearLayout.LayoutParams(0, kit.dp(6), (100 - pos(high)).toFloat().coerceAtLeast(0.01f)))
                    r.addView(bar, LinearLayout.LayoutParams(0, kit.dp(6), 1f).apply { marginStart = kit.dp(6); marginEnd = kit.dp(10) })
                    r.addView(kit.text(deg(high), 15f, Color.WHITE, 600), LinearLayout.LayoutParams(kit.dp(40), -2))
                    right.addView(r, lp(if (k == 0) 8 else 12))
                }
            }
        }

        if (wide) {
            val cols = LinearLayout(act).apply { setPadding(0, kit.dp(6), 0, 0) }
            cols.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = kit.dp(28) })
            cols.addView(right, LinearLayout.LayoutParams(0, -2, 1.3f))
            content.addView(cols, lp(8))
        } else { content.addView(left, lp(4)); content.addView(right, lp(10)) }
    }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = kit.rounded(Color.parseColor("#F0221A26"), 32)
            setPadding(kit.dp(26), kit.dp(22), kit.dp(26), kit.dp(26)); isClickable = true
            addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(content) }, LinearLayout.LayoutParams(-1, -2))
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(150, 10, 4, 12)); isClickable = true; setOnClickListener { dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(320f, kit.wdp - 48f, 1250f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.setOnDismissListener { open = false; Ha.listeners -= listener; h.removeCallbacks(redraw) }
        Ha.listeners += listener
        Weather.load(pref) { if (open) h.post { render() } }
        render()
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.9f)
    }

    fun dismiss() { open = false; if (dialog.isShowing) dialog.dismiss() }
}

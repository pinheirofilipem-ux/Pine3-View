package pt.pine3.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

/** Avisos do IPMA (amarelo, laranja, vermelho), índice UV e risco de incêndio, a partir da integração IPMA do HA. */
object Ipma {
    class Alert(val level: String, val type: String, val text: String, val start: Long?, val end: Long?)

    private val LEVELS = listOf("green", "yellow", "orange", "red")
    private val RANK = mapOf("red" to 3, "orange" to 2, "yellow" to 1)
    private var cache: Triple<String, Long, List<Alert>?>? = null
    private var loading = false

    private fun sensors() = Ha.states.keys.filter { it.startsWith("sensor.") && it.contains("ipma") }

    val alertId: String? get() = sensors().firstOrNull { id ->
        val s = Ha.states[id]!!
        s.attrs.has("awarenessTypeName") || (s.state in LEVELS && Regex("alert|aviso|warning").containsMatchIn(id))
    }
    val uvId: String? get() = sensors().firstOrNull { it.contains("uv") }
    val fireId: String? get() = sensors().firstOrNull { Regex("rcm|fire|incend").containsMatchIn(it) }

    private fun time(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        val t = v.replace(" ", "T")
        return try { java.time.LocalDateTime.parse(t).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) {
            try { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (_: Exception) { null }
        }
    }

    private fun norm(level: String, o: JSONObject) = Alert(level, o.optString("awarenessTypeName"), o.optString("text"), time(o.optString("startTime")), time(o.optString("endTime")))

    /** Todos os avisos ativos, do mais grave para o menos grave. O sensor só guarda o primeiro: com a área, pede-se a lista ao backend (cada 10 min). */
    fun alerts(redraw: () -> Unit = {}): List<Alert> {
        val id = alertId ?: return emptyList()
        val s = Ha.states[id] ?: return emptyList()
        val area = s.attrs.optString("idAreaAviso")
        if (area.isNotBlank() && Ha.home != null) {
            val c = cache
            if ((c == null || c.first != area || System.currentTimeMillis() - c.second > 10 * 60_000) && !loading) {
                loading = true
                Ha.request(JSONObject().put("type", "pine3_view/ipma_warnings").put("area", area)) { r ->
                    loading = false
                    val arr = r?.optJSONArray("warnings")
                    cache = Triple(area, System.currentTimeMillis(), if (arr == null) null else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { norm(it.optString("awarenessLevelID"), it) })
                    redraw()
                }
            }
        }
        val c = cache
        val fromApi = if (area.isNotBlank() && c != null && c.first == area) c.third else null
        val list = fromApi ?: if (s.state in listOf("yellow", "orange", "red")) listOf(norm(s.state, s.attrs)) else emptyList()
        val now = System.currentTimeMillis()
        return list.filter { RANK[it.level] != null && !(it.end != null && it.end < now) }
            .sortedWith(compareByDescending<Alert> { RANK[it.level] }.thenBy { it.start ?: 0L })
    }

    fun color(level: String) = when (level) { "red" -> Color.parseColor("#FF453A"); "orange" -> Color.parseColor("#FF9F0A"); else -> Color.parseColor("#FFD60A") }

    private fun fmt(t: Long): String {
        val today = SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date(t)) == SimpleDateFormat("yyyyMMdd", Tr.locale).format(Date())
        val hm = SimpleDateFormat("HH:mm", Tr.locale).format(Date(t))
        return if (today) hm else SimpleDateFormat("EEE", Tr.locale).format(Date(t)).replace(".", "") + " " + hm
    }

    private fun levelName(l: String) = Tr.t(when (l) { "red" -> "Aviso vermelho"; "orange" -> "Aviso laranja"; else -> "Aviso amarelo" })

    /** Um aviso numa faixa ("+N" para os outros) ou por inteiro, com início, fim e texto. */
    fun banner(kit: Kit, a: Alert, full: Boolean, more: Int = 0): View {
        val c = color(a.level)
        val now = System.currentTimeMillis()
        val period = if (full) listOfNotNull(a.start?.let { Tr.t("Começa") + " " + fmt(it) }, a.end?.let { Tr.t("Termina") + " " + fmt(it) }).joinToString(" · ")
        else if (a.start != null && a.start > now) Tr.t("a partir de ") + fmt(a.start) else a.end?.let { Tr.t("até ") + fmt(it) }.orEmpty()
        val box = LinearLayout(kit.ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; background = kit.rounded(Color.argb(46, Color.red(c), Color.green(c), Color.blue(c)), 16)
            setPadding(kit.dp(12), kit.dp(10), kit.dp(12), kit.dp(10))
        }
        box.addView(kit.icon("mdi:alert", c, 22), LinearLayout.LayoutParams(kit.dp(22), kit.dp(22)).apply { marginEnd = kit.dp(10) })
        val tx = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        tx.addView(kit.text(levelName(a.level) + if (a.type.isNotBlank()) " · " + a.type else "", 14f, kit.fg, 600))
        if (period.isNotBlank()) tx.addView(kit.text(period, 12f, kit.muted))
        if (full && a.text.isNotBlank()) tx.addView(kit.text(a.text, 13f, kit.fg), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
        box.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
        if (more > 0) box.addView(kit.text("+$more", 13f, c, 700).apply { background = kit.rounded(Color.argb(40, Color.red(c), Color.green(c), Color.blue(c)), 14); setPadding(kit.dp(10), kit.dp(4), kit.dp(10), kit.dp(4)) })
        return box
    }
}

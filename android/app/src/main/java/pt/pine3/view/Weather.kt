package pt.pine3.view

import org.json.JSONArray
import org.json.JSONObject

/** Previsão do tempo pelo HA (weather.get_forecasts), com cache de 15 minutos. */
object Weather {
    class Hour(val time: Long, val cond: String, val temp: Double?, val precip: Double?)
    class Day(val time: Long, val cond: String, val temp: Double?, val low: Double?, val precip: Double?)

    @Volatile var hourly: List<Hour> = emptyList()
    @Volatile var daily: List<Day> = emptyList()
    @Volatile var loaded = false
    private var at = 0L
    private var entity = ""
    private var loading = false

    private fun pct(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k) && o.optDouble(k, -1.0) >= 0) o.optDouble(k) else null
    private fun num(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
    private fun ts(s: String): Long = try { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) { 0L }

    fun load(entityId: String, done: () -> Unit) {
        val fresh = System.currentTimeMillis() - at < 15 * 60_000 && entity == entityId
        if (fresh || loading) return
        loading = true; entity = entityId
        var pending = 2
        fun finish() { if (--pending == 0) { loading = false; loaded = true; at = System.currentTimeMillis(); done() } }
        fun ask(type: String, onList: (JSONArray) -> Unit) {
            Ha.request(
                JSONObject().put("type", "call_service").put("domain", "weather").put("service", "get_forecasts")
                    .put("service_data", JSONObject().put("type", type)).put("target", JSONObject().put("entity_id", entityId)).put("return_response", true),
            ) { res ->
                try { res?.optJSONObject("response")?.optJSONObject(entityId)?.optJSONArray("forecast")?.let(onList) } catch (_: Exception) {}
                finish()
            }
        }
        ask("hourly") { a -> hourly = (0 until a.length()).map { a.getJSONObject(it) }.map { Hour(ts(it.optString("datetime")), it.optString("condition"), num(it, "temperature"), pct(it, "precipitation_probability")) } }
        ask("daily") { a -> daily = (0 until a.length()).map { a.getJSONObject(it) }.map { Day(ts(it.optString("datetime")), it.optString("condition"), num(it, "temperature"), num(it, "templow"), pct(it, "precipitation_probability")) } }
    }
}

package pt.pine3.view

import org.json.JSONArray
import org.json.JSONObject

/** Tempo: de uma entidade weather.* do HA ou, se não houver (ou se escolhida), da Open-Meteo para a localização da casa. */
object Weather {
    class Hour(val time: Long, val cond: String, val temp: Double?, val precip: Double?)
    class Day(val time: Long, val cond: String, val temp: Double?, val low: Double?, val precip: Double?)
    class Cur(val entity: String?, val cond: String, val temp: Double?, val humidity: Double?, val wind: Double?, val windUnit: String)

    @Volatile var hourly: List<Hour> = emptyList()
    @Volatile var daily: List<Day> = emptyList()
    @Volatile var loaded = false
    private var at = 0L
    private var key = ""
    private var loading = false
    private var om: JSONObject? = null
    private var omAt = 0L
    private var omLoading = false
    private val waiting = ArrayList<() -> Unit>()

    private fun pct(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k) && o.optDouble(k, -1.0) >= 0) o.optDouble(k) else null
    private fun num(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
    private fun ts(s: String): Long = try { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) { try { java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { try { java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (_: Exception) { 0L } } }

    /** WMO (Open-Meteo) -> condição do HA. */
    private fun wmo(c: Int, day: Boolean = true) = when {
        c == 0 -> if (day) "sunny" else "clear-night"
        c == 1 || c == 2 -> if (day) "partlycloudy" else "clear-night"
        c == 3 -> "cloudy"
        c == 45 || c == 48 -> "fog"
        c in 51..57 || c == 61 || c == 63 || c == 80 || c == 81 -> "rainy"
        c == 65 || c == 82 -> "pouring"
        c == 66 || c == 67 -> "snowy-rainy"
        c in 71..77 || c == 85 || c == 86 -> "snowy"
        c == 95 -> "lightning-rainy"
        c == 96 || c == 99 -> "hail"
        else -> "cloudy"
    }

    /** A fonte: a entidade escolhida, a primeira com temperatura, ou a Open-Meteo. */
    fun source(pref: String): String? {
        if (pref == "open-meteo") return null
        fun ok(i: String) = Ha.states[i]?.let { it.state != "unavailable" && it.attrs.has("temperature") && !it.attrs.isNull("temperature") } == true
        if (pref.startsWith("weather.") && ok(pref)) return pref
        return Ha.states.keys.filter { it.startsWith("weather.") }.sorted().firstOrNull { ok(it) }
    }

    fun sourceName(pref: String) = source(pref)?.let { Fmt.name(it) } ?: "Open-Meteo"

    /** Condições atuais (null enquanto a Open-Meteo ainda carrega). */
    fun current(pref: String, redraw: () -> Unit = {}): Cur? {
        val e = source(pref)
        if (e != null) {
            val s = Ha.states[e]!!; val a = s.attrs
            return Cur(e, s.state, num(a, "temperature"), num(a, "humidity"), num(a, "wind_speed"), a.optString("wind_speed_unit"))
        }
        loadOm(redraw, false)
        val c = om?.optJSONObject("current") ?: return null
        val u = om?.optJSONObject("current_units")
        return Cur(null, wmo(c.optInt("weather_code"), c.optInt("is_day", 1) == 1), num(c, "temperature_2m"), num(c, "relative_humidity_2m"), num(c, "wind_speed_10m"), u?.optString("wind_speed_10m") ?: "km/h")
    }

    /** Carrega a Open-Meteo (cache de 15 min). Com [callIfFresh], [done] corre mesmo que a cache esteja fresca. */
    private fun loadOm(done: () -> Unit, callIfFresh: Boolean) {
        if (omLoading) { waiting += done; return }
        if (om != null && System.currentTimeMillis() - omAt < 15 * 60_000) { if (callIfFresh) done(); return }
        omLoading = true; waiting += done
        Ha.request(JSONObject().put("type", "get_config")) { cfg ->
            val lat = cfg?.optDouble("latitude", Double.NaN) ?: Double.NaN; val lon = cfg?.optDouble("longitude", Double.NaN) ?: Double.NaN
            if (lat.isNaN() || lon.isNaN()) { omLoading = false; val ws = ArrayList(waiting); waiting.clear(); ws.forEach { it() }; return@request }
            val f = if (cfg?.optJSONObject("unit_system")?.optString("temperature") == "°F") "&temperature_unit=fahrenheit" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=7$f"
            Thread {
                val j = try { JSONObject(java.net.URL(url).openStream().bufferedReader().use { it.readText() }) } catch (_: Exception) { null }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (j != null) { om = j; omAt = System.currentTimeMillis() }
                    omLoading = false
                    val w = ArrayList(waiting); waiting.clear(); w.forEach { it() }
                }
            }.start()
        }
    }

    private var loadingKey = ""
    private val loadWaiters = ArrayList<() -> Unit>()

    /** Previsão por hora e por dia, com cache de 15 minutos. [done] corre (na main thread) quando uma carga nova termina; com cache fresca não corre. */
    fun load(pref: String, done: () -> Unit) {
        val e = source(pref)
        val k = e ?: "om"
        if (loaded && key == k && System.currentTimeMillis() - at < 15 * 60_000) return
        loadWaiters += done
        if (loading && loadingKey == k) return
        if (key != k) loaded = false
        loading = true; loadingKey = k
        fun complete() {
            if (loadingKey != k) return
            loading = false; loaded = true; key = k; at = System.currentTimeMillis()
            val ws = ArrayList(loadWaiters); loadWaiters.clear(); ws.forEach { it() }
        }
        if (e == null) {
            loadOm({
                om?.let { j ->
                    val h = j.optJSONObject("hourly"); val d = j.optJSONObject("daily")
                    val now = System.currentTimeMillis() - 3600_000
                    if (h != null) {
                        val t = h.getJSONArray("time"); val c = h.getJSONArray("weather_code"); val tt = h.getJSONArray("temperature_2m"); val p = h.optJSONArray("precipitation_probability")
                        hourly = (0 until t.length()).map { Hour(ts(t.getString(it)), wmo(c.optInt(it)), tt.optDouble(it), p?.optDouble(it)?.takeIf { v -> !v.isNaN() }) }.filter { it.time >= now }.take(48)
                    }
                    if (d != null) {
                        val t = d.getJSONArray("time"); val c = d.getJSONArray("weather_code"); val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min"); val p = d.optJSONArray("precipitation_probability_max")
                        daily = (0 until t.length()).map { Day(ts(t.getString(it)), wmo(c.optInt(it)), hi.optDouble(it), lo.optDouble(it), p?.optDouble(it)?.takeIf { v -> !v.isNaN() }) }
                    }
                }
                complete()
            }, true)
            return
        }
        var pending = 2
        fun finish() { if (--pending == 0) complete() }
        fun ask(type: String, onList: (JSONArray) -> Unit) {
            Ha.request(
                JSONObject().put("type", "call_service").put("domain", "weather").put("service", "get_forecasts")
                    .put("service_data", JSONObject().put("type", type)).put("target", JSONObject().put("entity_id", e)).put("return_response", true),
            ) { res ->
                try { res?.optJSONObject("response")?.optJSONObject(e)?.optJSONArray("forecast")?.let(onList) } catch (_: Exception) {}
                finish()
            }
        }
        val now = System.currentTimeMillis() - 3600_000
        ask("hourly") { a -> hourly = (0 until a.length()).map { a.getJSONObject(it) }.map { Hour(ts(it.optString("datetime")), it.optString("condition"), num(it, "temperature"), pct(it, "precipitation_probability")) }.filter { it.time >= now }.take(48) }
        ask("daily") { a -> daily = (0 until a.length()).map { a.getJSONObject(it) }.map { Day(ts(it.optString("datetime")), it.optString("condition"), num(it, "temperature"), num(it, "templow"), pct(it, "precipitation_probability")) }.take(7) }
    }
}

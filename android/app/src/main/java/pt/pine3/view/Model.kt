package pt.pine3.view

import org.json.JSONArray
import org.json.JSONObject

/** Um dispositivo (um cartão) de uma divisão, como o modelo do Pine3 View o descreve. */
class Dev(val id: String, val name: String, val kind: String, val extras: List<String>, val members: List<String>, val hidden: Boolean)

class Room(
    val id: String, val name: String, val icon: String, val floor: String?,
    val devices: List<Dev>, val temperature: List<String>, val humidity: List<String>,
    val scenes: List<String>, val hidden: Boolean,
)

/** O modelo `pine3_view/home`: divisões, dispositivos, cenas, tempo, pessoas e as definições guardadas no HA. */
class Home(
    val rooms: List<Room>, val scenes: List<String>, val scripts: List<String>,
    val weather: String?, val persons: List<String>, val settings: JSONObject,
) {
    fun color(key: String, fallback: Int): Int = try {
        android.graphics.Color.parseColor(settings.optString(key, "").ifBlank { return fallback })
    } catch (_: Exception) { fallback }

    /** Todas as entidades que mostramos (para só reagir a mudanças relevantes). */
    fun watched(): Set<String> {
        val out = HashSet<String>()
        rooms.forEach { r ->
            out += r.temperature; out += r.humidity; out += r.scenes
            r.devices.forEach { d -> out += d.id; out += d.extras; out += d.members }
        }
        out += scenes; out += scripts; out += persons
        weather?.let { out += it }
        return out
    }

    companion object {
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun parse(o: JSONObject): Home {
            val rooms = o.optJSONArray("rooms")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    Room(
                        id = r.optString("id"), name = r.optString("name"), icon = r.optString("icon", "mdi:home-outline"),
                        floor = if (r.isNull("floor")) null else r.optString("floor"),
                        devices = r.optJSONArray("devices")?.let { d ->
                            (0 until d.length()).map { j ->
                                val x = d.getJSONObject(j)
                                Dev(
                                    x.optString("id"), x.optString("name"), x.optString("kind"),
                                    strings(x.optJSONArray("extras")), strings(x.optJSONArray("members")), x.optBoolean("hidden"),
                                )
                            }
                        } ?: emptyList(),
                        temperature = strings(r.optJSONArray("temperature")), humidity = strings(r.optJSONArray("humidity")),
                        scenes = strings(r.optJSONArray("scenes")), hidden = r.optBoolean("hidden"),
                    )
                }
            } ?: emptyList()
            return Home(
                rooms, strings(o.optJSONArray("scenes")), strings(o.optJSONArray("scripts")),
                if (o.isNull("weather")) null else o.optString("weather"), strings(o.optJSONArray("persons")),
                o.optJSONObject("settings") ?: JSONObject(),
            )
        }
    }
}

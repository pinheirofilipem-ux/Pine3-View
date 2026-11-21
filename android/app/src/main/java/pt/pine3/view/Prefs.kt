package pt.pine3.view

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Quando [entity] muda de estado (e o novo estado ou event_type é [whenValue]), o ecrã acorda [holdSec] segundos. */
data class WakeRule(val entity: String, val whenValue: String, val holdSec: Int)

/** Campainha da integração Comelit: acorda o ecrã 120 s quando o evento é "ring". */
val DEFAULT_DOORBELL = WakeRule("event.entry_hallway_comelit_intercom_doorbell", "ring", 120)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("pine3", Context.MODE_PRIVATE)

    /** Endereço do Home Assistant (ex.: http://192.168.1.10:8123). */
    var haUrl: String
        get() = sp.getString("haUrl", "") ?: ""
        set(v) = sp.edit().putString("haUrl", v.trim()).apply()

    /** Token de acesso de longa duração. Fica só neste dispositivo. */
    var haToken: String
        get() = sp.getString("haToken", "") ?: ""
        set(v) = sp.edit().putString("haToken", v.trim()).apply()

    var ignoreSsl: Boolean
        get() = sp.getBoolean("ssl", false)
        set(v) = sp.edit().putBoolean("ssl", v).apply()

    var timeoutSec: Int
        get() = sp.getInt("timeout", 15)
        set(v) = sp.edit().putInt("timeout", v.coerceIn(3, 3600)).apply()

    var sensitivity: Int
        get() = sp.getInt("sens", 6)
        set(v) = sp.edit().putInt("sens", v.coerceIn(1, 10)).apply()

    var bootStart: Boolean
        get() = sp.getBoolean("boot", true)
        set(v) = sp.edit().putBoolean("boot", v).apply()

    var wakeRules: List<WakeRule>
        get() {
            val raw = sp.getString("wakeRules", null) ?: return listOf(DEFAULT_DOORBELL)
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    WakeRule(o.optString("entity"), o.optString("when"), o.optInt("hold", 60))
                }
            } catch (_: Exception) { emptyList() }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("entity", it.entity).put("when", it.whenValue).put("hold", it.holdSec)) }
            sp.edit().putString("wakeRules", arr.toString()).apply()
        }

    /** ws://host:porta/api/websocket, ou null se não há endereço. */
    fun wsUrl(): String? {
        var base = haUrl.trim()
        if (base.isBlank()) return null
        if (!base.contains("://")) base = "http://$base"
        return try {
            val u = java.net.URI(base)
            val host = u.host ?: return null
            val scheme = if (u.scheme.equals("https", true)) "wss" else "ws"
            val port = if (u.port != -1) ":${u.port}" else ""
            "$scheme://$host$port/api/websocket"
        } catch (_: Exception) { null }
    }
}

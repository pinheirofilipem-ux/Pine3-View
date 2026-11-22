package pt.pine3.view

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Quando [entity] muda de estado (e o novo estado ou event_type é [whenValue]), o ecrã acorda [holdSec] segundos. */
data class WakeRule(val entity: String, val whenValue: String, val holdSec: Int)

/** Endereço inicial do Home Assistant (só a origem conta para a ligação; o /pine3-view é o painel web). */
const val DEFAULT_HA_URL = "http://homeassistant.local:8123/pine3-view"

/** Campainha da integração Comelit: acorda o ecrã 120 s quando o evento é "ring". */
val DEFAULT_DOORBELL = WakeRule("event.entry_hallway_comelit_intercom_doorbell", "ring", 120)

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("pine3", Context.MODE_PRIVATE)

    /** Endereço do Home Assistant (ex.: http://192.168.1.10:8123). */
    var haUrl: String
        get() = sp.getString("haUrl", null) ?: DEFAULT_HA_URL
        set(v) = sp.edit().putString("haUrl", v.trim()).apply()

    /** Token de acesso de longa duração. Fica só neste dispositivo. */
    var haToken: String
        get() = sp.getString("haToken", "") ?: ""
        set(v) = sp.edit().putString("haToken", v.trim()).apply()

    /** Escala da interface em % (60–130): menor = cabe mais por ecrã, como um painel web com a janela mais larga. */
    var uiScale: Int
        get() = sp.getInt("uiScale", 100)
        set(v) = sp.edit().putInt("uiScale", v.coerceIn(60, 130)).apply()

    /** Ecrã de descanso com os vídeos da Apple TV (em vez do relógio escuro). */
    var saverVideo: Boolean
        get() = sp.getBoolean("saverVideo", false)
        set(v) = sp.edit().putBoolean("saverVideo", v).apply()

    /** Qualidade dos vídeos: "1080" (H.264) ou "4k". */
    var saverQuality: String
        get() = sp.getString("saverQuality", "1080") ?: "1080"
        set(v) = sp.edit().putString("saverQuality", v).apply()

    /** Endereço do ficheiro de recursos da Apple (o tar com o entries.json). */
    var saverSource: String
        get() = sp.getString("saverSource", null) ?: Aerials.DEFAULT_SOURCE
        set(v) = sp.edit().putString("saverSource", v.trim()).apply()

    /** Segundos sem toques até entrar o screensaver (0 = desligado). -1 = ainda não escolhido: parte do valor em minutos do painel web. */
    var saverSecRaw: Int
        get() = sp.getInt("saverSec", -1)
        set(v) = sp.edit().putInt("saverSec", v.coerceIn(0, 120)).apply()

    /** O tempo efetivo de entrada: o screensaver entra sempre antes do bloqueio (pelo menos 1 s antes) quando está ativo. */
    fun saverSec(webMinutes: Int): Int {
        val s = saverSecRaw.let { if (it >= 0) it else (webMinutes * 60).coerceIn(0, 120) }
        return if (s <= 0) 0 else minOf(s, maxOf(1, timeoutSec - 1))
    }

    /** Cor de fundo (pastel) atrás dos clipes com transparência, ex. "#F7C6CE"; vazio = sem fundo. */
    var saverBg: String
        get() = sp.getString("saverBg", "") ?: ""
        set(v) = sp.edit().putString("saverBg", v).apply()

    /** Onde a cor de fundo aparece: "white" (nos brancos do clipe, multiplicando) ou "black" (nos pretos, clareando). */
    var saverBlend: String
        get() = sp.getString("saverBlend", "black") ?: "black"
        set(v) = sp.edit().putString("saverBlend", v).apply()

    /** Tamanho mínimo (MB) de um clipe para entrar por regra: os pequenos costumam ser loops soltos. */
    var clipMinMb: Int
        get() = sp.getInt("clipMinMb", 8)
        set(v) = sp.edit().putInt("clipMinMb", v.coerceIn(0, 500)).apply()

    /** Escolhas manuais por ficheiro (nome -> ligado); o que não está aqui segue a regra. */
    var clipChoices: Map<String, Boolean>
        get() = try { JSONObject(sp.getString("clipChoices", "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { k -> o.optBoolean(k) } } } catch (_: Exception) { emptyMap() }
        set(v) = sp.edit().putString("clipChoices", JSONObject(v.mapKeys { it.key }).toString()).apply()

    /** Pasta escolhida no tablet com os clipes (endereço da árvore de documentos do Android). */
    var clipFolder: String
        get() = sp.getString("clipFolder", "") ?: ""
        set(v) = sp.edit().putString("clipFolder", v).apply()

    /** Endereço do zip com os clipes a guardar no tablet. */
    var packUrl: String
        get() = sp.getString("packUrl", null) ?: Pack.DEFAULT_URL
        set(v) = sp.edit().putString("packUrl", v.trim()).apply()

    /** Endereço de um ficheiro com a lista de vídeos (JSON ou texto). */
    var saverList: String
        get() = sp.getString("saverList", "") ?: ""
        set(v) = sp.edit().putString("saverList", v.trim()).apply()

    /** Vídeos extra (um endereço por linha, .mp4/.mov), misturados com os da Apple. */
    var saverExtra: String
        get() = sp.getString("saverExtra", "") ?: ""
        set(v) = sp.edit().putString("saverExtra", v.trim()).apply()

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

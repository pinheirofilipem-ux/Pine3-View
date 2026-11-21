package pt.pine3.view

import org.json.JSONObject
import java.net.InetAddress

/** Teste de ligação passo a passo, para ver exatamente onde falha (rede, endereço, token ou Pine3 View). */
object HaTest {
    fun run(url: String?, token: String, trustAll: Boolean, line: (ok: Boolean, text: String) -> Unit, done: () -> Unit) {
        Thread {
            try {
                if (url == null) { line(false, "Falta o endereço do Home Assistant."); return@Thread }
                val uri = java.net.URI(url)
                val host = uri.host ?: run { line(false, "Endereço inválido: $url"); return@Thread }
                line(true, "Endereço: $url")
                val ip = try { InetAddress.getByName(host).hostAddress } catch (e: Exception) {
                    line(false, Ha.describe(e)); return@Thread
                }
                line(true, "1/5  DNS ok ($ip)")
                val ws = MiniWebSocket(url, trustAll, readTimeoutMs = 10_000)
                try {
                    try { ws.connect(6000) } catch (e: Exception) { line(false, "2/5  ${Ha.describe(e)}"); return@Thread }
                    line(true, "2/5  WebSocket aberto")
                    var m = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    if (m.optString("type") != "auth_required") { line(false, "Resposta inesperada: ${m.optString("type")}"); return@Thread }
                    ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                    m = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    if (m.optString("type") != "auth_ok") { line(false, "3/5  Token recusado. Cria um novo token de acesso de longa duração no teu perfil do HA."); return@Thread }
                    line(true, "3/5  Token aceite (HA ${m.optString("ha_version")})")
                    ws.send(JSONObject().put("id", 1).put("type", "get_states").toString())
                    val st = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    line(st.optBoolean("success"), "4/5  Entidades: ${st.optJSONArray("result")?.length() ?: 0}")
                    ws.send(JSONObject().put("id", 2).put("type", "pine3_view/home").toString())
                    val h = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    if (h.optBoolean("success")) {
                        val rooms = h.optJSONObject("result")?.optJSONArray("rooms")?.length() ?: 0
                        line(true, "5/5  Pine3 View respondeu: $rooms divisões")
                    } else {
                        line(false, "5/5  O Pine3 View não respondeu (${h.optJSONObject("error")?.optString("code")}). Está instalado no HA e o HA foi reiniciado?")
                    }
                } finally { ws.close() }
            } catch (e: Exception) {
                line(false, Ha.describe(e))
            } finally { done() }
        }.start()
    }
}

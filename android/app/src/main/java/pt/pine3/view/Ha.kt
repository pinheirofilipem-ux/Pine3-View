package pt.pine3.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ligação única ao Home Assistant (WebSocket), partilhada pela interface e pelo serviço:
 *  - estados de todas as entidades (get_states + state_changed);
 *  - o modelo da casa do Pine3 View (`pine3_view/home` + `pine3_view/subscribe`);
 *  - regras que acordam o ecrã (ex.: campainha);
 *  - chamadas de serviço (ligar/desligar...).
 * Reconecta sozinha. As notificações chegam sempre na main thread.
 */
object Ha {
    class State(val state: String, val attrs: JSONObject)

    interface Listener {
        fun onStates(changed: Set<String>) {}
        fun onHome() {}
        fun onStatus() {}
    }

    val states = ConcurrentHashMap<String, State>()
    @Volatile var home: Home? = null
    @Volatile var status = "Inativo"
    @Volatile var connected = false
    @Volatile var homeError: String? = null

    val listeners = CopyOnWriteArrayList<Listener>()
    /** Quem acorda o ecrã (o serviço). */
    @Volatile var onWake: ((WakeRule) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicInteger(100)
    @Volatile private var running = false
    @Volatile private var thread: Thread? = null
    @Volatile private var ws: MiniWebSocket? = null
    @Volatile private var sig = ""
    @Volatile private var watched: Set<String> = emptySet()
    @Volatile private var rules: List<WakeRule> = emptyList()

    private const val ID_EVENTS = 1
    private const val ID_STATES = 2
    private const val ID_HOME = 3
    private const val ID_HOME_SUB = 4

    private fun notifyMain(block: (Listener) -> Unit) = main.post { listeners.forEach(block) }
    private fun setStatus(text: String, ok: Boolean) { status = text; connected = ok; notifyMain { it.onStatus() } }

    /** (Re)liga se a configuração mudou; desliga se falta endereço/token. */
    @Synchronized
    fun configure(ctx: Context) {
        val prefs = Prefs(ctx)
        val url = prefs.wsUrl()
        val token = prefs.haToken
        val newRules = prefs.wakeRules.filter { it.entity.isNotBlank() }
        val newSig = if (url == null || token.isBlank()) "" else "$url|$token|${prefs.ignoreSsl}"
        rules = newRules
        recomputeWatched()
        if (newSig == sig && (newSig.isEmpty() || running)) return
        sig = newSig
        stop()
        if (newSig.isEmpty()) { setStatus("Inativo (falta endereço ou token)", false); return }
        running = true
        val trust = prefs.ignoreSsl
        thread = Thread({ loop(url!!, token, trust) }, "ha-client").apply { isDaemon = true; start() }
    }

    private fun recomputeWatched() {
        watched = (home?.watched() ?: emptySet()) + rules.map { it.entity }
    }

    @Synchronized
    fun stop() {
        running = false
        try { ws?.close() } catch (_: Exception) {}
        thread?.interrupt()
        thread = null
    }

    /** Chama um serviço do HA (sem esperar pelo resultado). */
    fun call(domain: String, service: String, entityIds: List<String>, data: JSONObject? = null) {
        if (entityIds.isEmpty()) return
        Thread {
            try {
                val msg = JSONObject().put("id", ids.incrementAndGet()).put("type", "call_service")
                    .put("domain", domain).put("service", service)
                    .put("target", JSONObject().put("entity_id", JSONArray(entityIds)))
                if (data != null) msg.put("service_data", data)
                ws?.send(msg.toString())
            } catch (_: Exception) {}
        }.start()
    }

    private fun loop(url: String, token: String, trustAll: Boolean) {
        var backoff = 2000L
        while (running) {
            var authFailed = false
            try {
                setStatus("A ligar…", false)
                session(url, token, trustAll)
                backoff = 2000L
            } catch (e: AuthFailed) {
                authFailed = true
                setStatus("Token inválido", false)
            } catch (e: Exception) {
                if (!running) break
                setStatus("Sem ligação, a tentar de novo…", false)
            } finally {
                try { ws?.close() } catch (_: Exception) {}
                ws = null
            }
            if (!running) break
            try { Thread.sleep(if (authFailed) 60_000L else backoff) } catch (_: InterruptedException) { break }
            backoff = minOf(backoff * 2, 30_000L)
        }
    }

    private class AuthFailed : Exception()

    private fun session(url: String, token: String, trustAll: Boolean) {
        val socket = MiniWebSocket(url, trustAll, readTimeoutMs = 25_000)
        ws = socket
        socket.connect()
        var msg = JSONObject(socket.receive() ?: throw IOException("fechado"))
        if (msg.optString("type") != "auth_required") throw IOException("resposta inesperada")
        socket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
        msg = JSONObject(socket.receive() ?: throw IOException("fechado"))
        when (msg.optString("type")) {
            "auth_ok" -> {}
            "auth_invalid" -> throw AuthFailed()
            else -> throw IOException("resposta inesperada")
        }
        socket.send(JSONObject().put("id", ID_EVENTS).put("type", "subscribe_events").put("event_type", "state_changed").toString())
        socket.send(JSONObject().put("id", ID_STATES).put("type", "get_states").toString())
        socket.send(JSONObject().put("id", ID_HOME).put("type", "pine3_view/home").toString())
        socket.send(JSONObject().put("id", ID_HOME_SUB).put("type", "pine3_view/subscribe").toString())

        var lastRx = System.currentTimeMillis()
        while (running) {
            val text = try {
                socket.receive() ?: throw IOException("fechado pelo servidor")
            } catch (e: IdleTimeoutException) {
                if (System.currentTimeMillis() - lastRx > 70_000) throw IOException("sem resposta ao ping")
                socket.send(JSONObject().put("id", ids.incrementAndGet()).put("type", "ping").toString())
                continue
            }
            lastRx = System.currentTimeMillis()
            handle(text)
        }
    }

    private fun handle(text: String) {
        // Filtro barato para o fluxo de state_changed: só analisa entidades que mostramos ou que acordam o ecrã.
        if (text.contains("\"state_changed\"")) {
            val i = text.indexOf("\"entity_id\":\"")
            if (i >= 0) {
                val s = i + 13
                val e = text.indexOf('"', s)
                if (e > s && text.substring(s, e) !in watched) return
            }
            val data = try { JSONObject(text).optJSONObject("event")?.optJSONObject("data") } catch (_: Exception) { null } ?: return
            onStateChanged(data)
            return
        }
        val m = try { JSONObject(text) } catch (_: Exception) { return }
        when (m.optString("type")) {
            "result" -> when (m.optInt("id")) {
                ID_STATES -> m.optJSONArray("result")?.let { loadStates(it) }
                ID_HOME -> {
                    if (m.optBoolean("success")) {
                        m.optJSONObject("result")?.let { setHome(it) }
                    } else {
                        homeError = "O Pine3 View não está instalado no Home Assistant (ou o token não tem acesso)."
                        notifyMain { it.onHome() }
                    }
                }
            }
            "event" -> if (m.optInt("id") == ID_HOME_SUB) m.optJSONObject("event")?.let { setHome(it) }
        }
    }

    private fun loadStates(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            states[o.optString("entity_id")] = State(o.optString("state"), o.optJSONObject("attributes") ?: JSONObject())
        }
        notifyMain { it.onStates(emptySet()) }
    }

    private fun setHome(o: JSONObject) {
        try {
            home = Home.parse(o)
            homeError = null
            recomputeWatched()
        } catch (e: Exception) {
            homeError = "Modelo da casa inválido: ${e.message}"
        }
        notifyMain { it.onHome() }
    }

    private fun onStateChanged(data: JSONObject) {
        val id = data.optString("entity_id")
        val new = data.optJSONObject("new_state")
        if (new == null) { states.remove(id); notifyMain { it.onStates(setOf(id)) }; return }
        val newState = new.optString("state")
        val attrs = new.optJSONObject("attributes") ?: JSONObject()
        val oldState = data.optJSONObject("old_state")?.optString("state")
        states[id] = State(newState, attrs)
        notifyMain { it.onStates(setOf(id)) }

        // Regras que acordam o ecrã (só mudanças de estado; um event.* muda sempre: o estado é a hora do evento).
        if (oldState == newState || newState == "unavailable" || newState == "unknown") return
        val eventType = attrs.optString("event_type")
        for (r in rules) {
            if (!r.entity.equals(id, ignoreCase = true)) continue
            val w = r.whenValue.trim()
            if (w.isEmpty() || w.equals(newState, true) || w.equals(eventType, true)) onWake?.let { cb -> main.post { cb(r) } }
        }
    }
}

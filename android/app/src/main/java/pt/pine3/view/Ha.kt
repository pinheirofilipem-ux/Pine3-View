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
        /** Tocaram à campainha (event.* de classe doorbell): tipo "ring", "floor_call", "answered", "missed_call". */
        fun onBell(id: String, type: String, attrs: JSONObject) {}
    }

    class Bell(val id: String, val type: String, val attrs: JSONObject, val at: Long)
    /** Último toque ainda não mostrado (a atividade pode estar a acordar quando o evento chega). */
    @Volatile var pendingBell: Bell? = null

    private fun isBellEntity(id: String) =
        (id.startsWith("event.") && states[id]?.attrs?.optString("device_class") == "doorbell") || (id.startsWith("image.") && id.contains("doorbell_snapshot"))

    val states = ConcurrentHashMap<String, State>()
    @Volatile var home: Home? = null
    @Volatile var status = "Inativo"
    @Volatile var connected = false
    @Volatile var homeError: String? = null

    /** Últimas linhas do registo de ligação (para o ecrã de diagnóstico). */
    val log = java.util.Collections.synchronizedList(ArrayList<String>())
    fun log(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        log.add("$t  $msg")
        while (log.size > 40) log.removeAt(0)
    }

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
        httpToken = token
        httpTrust = prefs.ignoreSsl
        httpBase = try {
            var b = prefs.haUrl.trim(); if (!b.contains("://")) b = "http://$b"
            val u = java.net.URI(b); u.scheme + "://" + u.host + (if (u.port != -1) ":${u.port}" else "")
        } catch (_: Exception) { "" }
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

    private val pending = ConcurrentHashMap<Int, (JSONObject?) -> Unit>()

    /** Pede algo ao HA e devolve o resultado na main thread (null se falhar). */
    fun request(payload: JSONObject, cb: (JSONObject?) -> Unit) {
        val id = ids.incrementAndGet()
        pending[id] = cb
        Thread {
            try { ws?.send(payload.put("id", id).toString()) } catch (_: Exception) { pending.remove(id)?.let { f -> main.post { f(null) } } }
        }.start()
        main.postDelayed({ pending.remove(id)?.let { f -> f(null) } }, 15_000)
    }

    @Volatile private var httpBase = ""
    @Volatile private var httpToken = ""
    @Volatile private var httpTrust = false

    /** Descarrega uma imagem do HA (ex.: /api/camera_proxy/camera.x) com o token; devolve o bitmap na main thread (null se falhar). */
    fun fetchImage(path: String, cb: (android.graphics.Bitmap?) -> Unit) {
        val base = httpBase; val token = httpToken; val trust = httpTrust
        if (base.isEmpty()) { main.post { cb(null) }; return }
        Thread {
            var bmp: android.graphics.Bitmap? = null
            try {
                val url = java.net.URL(if (path.startsWith("http")) path else base + path)
                val c = url.openConnection() as java.net.HttpURLConnection
                if (c is javax.net.ssl.HttpsURLConnection && trust) {
                    val tm = object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, a: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                    }
                    c.sslSocketFactory = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), java.security.SecureRandom()) }.socketFactory
                    c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }
                c.connectTimeout = 6000; c.readTimeout = 10000
                c.setRequestProperty("Authorization", "Bearer $token")
                if (c.responseCode == 200) bmp = android.graphics.BitmapFactory.decodeStream(c.inputStream)
                c.disconnect()
            } catch (_: Exception) {}
            main.post { cb(bmp) }
        }.start()
    }

    /** Guarda definições do Pine3 View no HA (partilhadas com o painel web). O HA devolve o modelo atualizado. */
    fun saveSettings(changes: JSONObject) {
        // Atualiza já localmente, para a interface responder logo.
        home?.settings?.let { s -> changes.keys().forEach { k -> s.put(k, changes.get(k)) } }
        notifyMain { it.onHome() }
        Thread {
            try {
                val msg = JSONObject().put("id", ids.incrementAndGet()).put("type", "pine3_view/settings")
                changes.keys().forEach { k -> msg.put(k, changes.get(k)) }
                ws?.send(msg.toString())
            } catch (_: Exception) {}
        }.start()
    }

    /** Explica uma falha de ligação em português, com a ação provável. */
    fun describe(e: Exception): String = when {
        e is java.net.UnknownHostException -> "não encontro \"${e.message}\". Usa o IP do HA (ex.: 192.168.1.10), o Android não resolve nomes .local"
        e is java.net.ConnectException -> "ligação recusada. Confirma o endereço e a porta (normalmente 8123)"
        e is java.net.SocketTimeoutException -> "tempo esgotado. O tablet está na mesma rede do HA?"
        e is javax.net.ssl.SSLException -> "erro de certificado HTTPS. Ativa \"Aceitar certificado auto-assinado\""
        e is IOException && (e.message ?: "").startsWith("handshake recusado") -> "o HA recusou o WebSocket (${e.message}). O endereço deve ser só http://IP:8123, sem /lovelace"
        else -> e.message ?: e.javaClass.simpleName
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
                log("O HA recusou o token")
                setStatus("Token inválido: cria outro token de acesso de longa duração no HA", false)
            } catch (e: Exception) {
                if (!running) break
                val why = describe(e)
                log("Falhou: $why")
                setStatus("Sem ligação: $why", false)
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
        log("A ligar a $url")
        socket.connect()
        log("WebSocket aberto")
        var msg = JSONObject(socket.receive() ?: throw IOException("fechado"))
        if (msg.optString("type") != "auth_required") throw IOException("resposta inesperada")
        socket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
        msg = JSONObject(socket.receive() ?: throw IOException("fechado"))
        when (msg.optString("type")) {
            "auth_ok" -> log("Autenticado")
            "auth_invalid" -> throw AuthFailed()
            else -> throw IOException("resposta inesperada")
        }
        setStatus("Ligado", true)
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
                if (e > s && text.substring(s, e).let { it !in watched && !isBellEntity(it) }) return
            }
            val data = try { JSONObject(text).optJSONObject("event")?.optJSONObject("data") } catch (_: Exception) { null } ?: return
            onStateChanged(data)
            return
        }
        val m = try { JSONObject(text) } catch (_: Exception) { return }
        when (m.optString("type")) {
            "result" -> if (pending.containsKey(m.optInt("id"))) {
                val cb = pending.remove(m.optInt("id"))
                val res = if (m.optBoolean("success")) m.optJSONObject("result") ?: JSONObject() else null
                if (cb != null) main.post { cb(res) }
            } else when (m.optInt("id")) {
                ID_STATES -> m.optJSONArray("result")?.let { loadStates(it) }
                ID_HOME -> {
                    if (m.optBoolean("success")) {
                        m.optJSONObject("result")?.let { setHome(it) }
                    } else {
                        val code = m.optJSONObject("error")?.optString("code").orEmpty()
                        log("pine3_view/home falhou: $code")
                        homeError = "O Pine3 View não respondeu no Home Assistant ($code). Está instalado e reiniciado?"
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
        log("${arr.length()} entidades recebidas")
        notifyMain { it.onStates(emptySet()) }
    }

    private fun setHome(o: JSONObject) {
        try {
            home = Home.parse(o)
            Fmt.names = home!!.rooms.flatMap { it.devices }.associate { it.id to it.name }
            log("Modelo da casa: ${home?.rooms?.size ?: 0} divisões")
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

        if (id.startsWith("event.") && attrs.optString("device_class") == "doorbell" && oldState != newState && newState != "unavailable" && newState != "unknown") {
            val at = try { java.time.Instant.parse(newState).toEpochMilli() } catch (_: Exception) { 0L }
            if (Math.abs(System.currentTimeMillis() - at) < 60_000) {
                val type = attrs.optString("event_type", "ring").ifBlank { "ring" }
                val b = Bell(id, type, attrs, System.currentTimeMillis())
                if (type == "ring" || type == "floor_call") pendingBell = b
                notifyMain { it.onBell(id, type, attrs) }
            }
        }

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

package pt.pine3.view

/** Textos e ícones dos estados, no português do Pine3 View. */
object Fmt {
    val STATE_PT = mapOf(
        "on" to "Ligado", "off" to "Desligado", "open" to "Aberto", "closed" to "Fechado", "opening" to "A abrir", "closing" to "A fechar",
        "locked" to "Trancado", "unlocked" to "Destrancado", "playing" to "A tocar", "paused" to "Em pausa", "idle" to "Inativo",
        "standby" to "Em espera", "unavailable" to "Indisponível", "unknown" to "Desconhecido", "heat" to "Aquecer", "cool" to "Arrefecer",
        "auto" to "Automático", "heat_cool" to "Aquecer/Arrefecer", "dry" to "Desumidificar", "fan_only" to "Ventilação",
        "charging" to "A carregar", "cleaning" to "A limpar", "docked" to "Na base", "returning" to "A regressar",
        "home" to "Em casa", "not_home" to "Fora", "away" to "Ausente",
    )
    val WEATHER_PT = mapOf(
        "clear-night" to "Céu limpo", "cloudy" to "Nublado", "exceptional" to "Excecional", "fog" to "Nevoeiro", "hail" to "Granizo",
        "lightning" to "Trovoada", "lightning-rainy" to "Trovoada e chuva", "partlycloudy" to "Pouco nublado", "pouring" to "Chuva forte",
        "rainy" to "Chuva", "snowy" to "Neve", "snowy-rainy" to "Chuva e neve", "sunny" to "Sol", "windy" to "Ventoso", "windy-variant" to "Ventoso",
    )
    val WEATHER_ICON = mapOf(
        "clear-night" to "mdi:weather-night", "cloudy" to "mdi:weather-cloudy", "fog" to "mdi:weather-fog", "hail" to "mdi:weather-hail",
        "lightning" to "mdi:weather-lightning", "lightning-rainy" to "mdi:weather-lightning-rainy", "partlycloudy" to "mdi:weather-partly-cloudy",
        "pouring" to "mdi:weather-pouring", "rainy" to "mdi:weather-rainy", "snowy" to "mdi:weather-snowy", "snowy-rainy" to "mdi:weather-snowy-rainy",
        "sunny" to "mdi:weather-sunny", "windy" to "mdi:weather-windy", "windy-variant" to "mdi:weather-windy-variant",
    )
    val DOMAIN_ICON = mapOf(
        "light" to "mdi:lightbulb", "switch" to "mdi:power-socket-eu", "outlet" to "mdi:power-socket-eu", "fan" to "mdi:fan",
        "cover" to "mdi:window-shutter", "lock" to "mdi:lock", "climate" to "mdi:thermostat", "media_player" to "mdi:speaker",
        "sensor" to "mdi:gauge", "binary_sensor" to "mdi:checkbox-blank-circle-outline", "scene" to "mdi:palette",
        "script" to "mdi:script-text-play", "vacuum" to "mdi:robot-vacuum", "camera" to "mdi:cctv", "input_boolean" to "mdi:toggle-switch",
        "button" to "mdi:gesture-tap-button",
    )
    val ACCENT = mapOf(
        "light" to 0xFFFFB340.toInt(), "switch" to 0xFF5AC8FA.toInt(), "outlet" to 0xFF5AC8FA.toInt(), "fan" to 0xFF64D2FF.toInt(),
        "cover" to 0xFF5E9CFF.toInt(), "lock" to 0xFF30D158.toInt(), "climate" to 0xFFFF9F0A.toInt(), "media_player" to 0xFFBF5AF2.toInt(),
        "input_boolean" to 0xFF5AC8FA.toInt(), "vacuum" to 0xFF30D158.toInt(), "script" to 0xFF5E5CE6.toInt(), "button" to 0xFF5E5CE6.toInt(),
        "binary_sensor" to 0xFFFF453A.toInt(), "camera" to 0xFF8E8E93.toInt(),
    )
    /** Filtros de uma divisão: rótulo, ícone, domínios. */
    val SECTIONS = listOf(
        Triple("Luzes", "mdi:lightbulb-outline", setOf("light")),
        Triple("Clima", "mdi:thermometer", setOf("climate", "fan")),
        Triple("Cortinas e estores", "mdi:window-shutter", setOf("cover")),
        Triple("Multimédia", "mdi:television", setOf("media_player")),
        Triple("Segurança", "mdi:shield-outline", setOf("lock", "camera")),
        Triple("Tomadas e interruptores", "mdi:power-socket-eu", setOf("switch", "outlet", "input_boolean")),
        Triple("Ações", "mdi:gesture-tap-button", setOf("script", "button", "vacuum")),
        Triple("Sensores", "mdi:gauge", setOf("sensor", "binary_sensor")),
    )

    fun domain(id: String) = id.substringBefore('.')
    fun state(id: String): Ha.State? = Ha.states[id]
    fun name(id: String): String = state(id)?.attrs?.optString("friendly_name")?.ifBlank { null } ?: id

    fun num(v: Double, decimals: Int = 1): String {
        val s = String.format(Tr.locale, "%.${decimals}f", v)
        return if (s.contains(',')) s.trimEnd('0').trimEnd(',') else s
    }

    /** Está "ligado" (cartão creme)? */
    fun isOn(id: String): Boolean {
        val st = state(id)?.state ?: return false
        return when (domain(id)) {
            "light", "switch", "fan", "input_boolean", "binary_sensor" -> st == "on"
            "cover" -> st != "closed" && st != "unavailable"
            "lock" -> st == "unlocked"
            "climate" -> st != "off" && st != "unavailable"
            "media_player" -> st == "playing" || st == "paused" || st == "on" || st == "buffering"
            "vacuum" -> st == "cleaning" || st == "returning"
            else -> false
        }
    }

    fun isToggle(id: String) = domain(id) in setOf("light", "switch", "fan", "input_boolean", "lock", "cover", "media_player")

    /** Uma leitura (sensor) ou null. */
    fun reading(id: String): String? {
        val st = state(id) ?: return null
        val v = st.state.toDoubleOrNull() ?: return Tr.t(STATE_PT[st.state] ?: st.state)
        val unit = st.attrs.optString("unit_of_measurement")
        return num(v) + (if (unit.isNotEmpty()) (if (unit == "°C" || unit == "%") unit else " $unit") else "")
    }

    /** O texto pequeno de um cartão de dispositivo. */
    fun sub(id: String): String {
        val st = state(id) ?: return Tr.t("Indisponível")
        val a = st.attrs
        if (st.state == "unavailable") return Tr.t("Indisponível")
        return when (domain(id)) {
            "light" -> if (st.state == "on") (if (a.has("brightness") && !a.isNull("brightness")) "${Math.round(a.optDouble("brightness") / 2.55)}%" else Tr.t("Ligado")) else Tr.t("Desligado")
            "cover" -> {
                val p = a.optInt("current_position", -1)
                if (st.state == "closed") Tr.t("Fechado") else if (p in 0..100) "$p% ${Tr.t("Aberto").lowercase()}" else Tr.t(STATE_PT[st.state] ?: st.state)
            }
            "climate" -> {
                val cur = a.optDouble("current_temperature", Double.NaN)
                val tgt = a.optDouble("temperature", Double.NaN)
                val mode = Tr.t(STATE_PT[st.state] ?: st.state)
                buildString {
                    append(mode)
                    if (!cur.isNaN()) append(" · ").append(num(cur)).append("°")
                    if (!tgt.isNaN() && st.state != "off") append(" → ").append(num(tgt)).append("°")
                }
            }
            "media_player" -> {
                val title = a.optString("media_title")
                if (st.state == "playing" && title.isNotEmpty()) {
                    val artist = a.optString("media_artist")
                    if (artist.isNotEmpty()) "$title · $artist" else title
                } else Tr.t(STATE_PT[st.state] ?: st.state)
            }
            "vacuum" -> {
                val bat = a.optInt("battery_level", -1)
                (Tr.t(STATE_PT[st.state] ?: st.state)) + if (bat >= 0) " · $bat%" else ""
            }
            "sensor" -> reading(id) ?: st.state
            "binary_sensor" -> when (a.optString("device_class")) {
                "door", "window", "garage_door", "opening" -> if (st.state == "on") Tr.t("Aberto") else Tr.t("Fechado")
                "motion", "occupancy", "presence" -> if (st.state == "on") Tr.t("Movimento") else Tr.t("Sem movimento")
                "moisture" -> if (st.state == "on") Tr.t("Molhado") else Tr.t("Seco")
                "smoke", "gas" -> if (st.state == "on") Tr.t("Alarme") else Tr.t("Normal")
                else -> if (st.state == "on") Tr.t("Ligado") else Tr.t("Desligado")
            }
            "scene", "script", "button" -> Tr.t("Ativar")
            "fan" -> if (st.state == "on") (if (a.has("percentage") && !a.isNull("percentage")) "${a.optInt("percentage")}%" else Tr.t("Ligado")) else Tr.t("Desligado")
            else -> Tr.t(STATE_PT[st.state] ?: st.state)
        }
    }

    /** Ícone de um dispositivo: pelo domínio/classe do estado. */
    fun icon(id: String, kind: String): String {
        val a = state(id)?.attrs
        val own = a?.optString("icon").orEmpty()
        if (own.startsWith("mdi:")) return own
        val dc = a?.optString("device_class").orEmpty()
        when (domain(id)) {
            "sensor" -> when (dc) {
                "temperature" -> return "mdi:thermometer"; "humidity" -> return "mdi:water-percent"; "power" -> return "mdi:flash"
                "energy" -> return "mdi:lightning-bolt"; "battery" -> return "mdi:battery"; "illuminance" -> return "mdi:brightness-5"
            }
            "binary_sensor" -> when (dc) {
                "door" -> return "mdi:door"; "window" -> return "mdi:window-closed-variant"; "motion" -> return "mdi:motion-sensor"
                "garage_door" -> return "mdi:garage"
            }
            "media_player" -> if (dc == "tv") return "mdi:television"
            "cover" -> return if (dc == "garage") "mdi:garage" else "mdi:window-shutter"
        }
        return DOMAIN_ICON[kind] ?: DOMAIN_ICON[domain(id)] ?: "mdi:circle-medium"
    }

    /** O que acontece ao tocar num cartão. */
    fun tap(id: String) {
        when (domain(id)) {
            "light", "switch", "fan", "input_boolean" -> Ha.call("homeassistant", "toggle", listOf(id))
            "cover" -> Ha.call("cover", "toggle", listOf(id))
            "lock" -> Ha.call("lock", if (state(id)?.state == "locked") "unlock" else "lock", listOf(id))
            "media_player" -> Ha.call("media_player", "media_play_pause", listOf(id))
            "scene" -> Ha.call("scene", "turn_on", listOf(id))
            "script" -> Ha.call("script", "turn_on", listOf(id))
            "button" -> Ha.call("button", "press", listOf(id))
            "vacuum" -> Ha.call("vacuum", if (isOn(id)) "return_to_base" else "start", listOf(id))
            "climate" -> Ha.call("climate", if (isOn(id)) "turn_off" else "turn_on", listOf(id))
        }
    }
}

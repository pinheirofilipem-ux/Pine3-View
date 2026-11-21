package pt.pine3.view

import java.util.Locale

/** Tradução simples pt -> en dos textos do painel (a língua vem das definições do Pine3 View: "language"). */
object Tr {
    @Volatile var en = false

    val locale: Locale get() = if (en) Locale.ENGLISH else Locale.forLanguageTag("pt-PT")

    fun t(s: String): String = if (en) EN[s] ?: s else s

    /** Plural: [n] com a forma singular ou plural na língua atual. */
    fun pl(n: Int, ptOne: String, ptMany: String, enOne: String, enMany: String): String =
        "$n " + if (en) (if (n == 1) enOne else enMany) else (if (n == 1) ptOne else ptMany)

    private val EN = mapOf(
        // início
        "Estado da casa" to "Home status", "Acender luzes" to "Turn lights on", "Apagar luzes" to "Turn lights off",
        "Nenhuma luz acesa" to "No lights on", "1 luz acesa" to "1 light on", " luzes acesas" to " lights on",
        "Também ligados: " to "Also on: ", "Cenas" to "Scenes", "Divisões" to "Rooms", "Casa" to "Home", "Casa 3D" to "3D Home",
        "Acender" to "On", "Apagar" to "Off", " DISPOSITIVOS" to " DEVICES", "Tudo" to "All", "Humidade " to "Humidity ",
        "A casa 3D desenhada em Canvas vem na fase 2." to "The 3D home, drawn on a Canvas, comes in phase 2.",
        " dispositivos" to " devices", " de " to " of ", " ligados" to " on",
        // secções
        "Luzes" to "Lights", "Clima" to "Climate", "Cortinas e estores" to "Blinds and shutters", "Multimédia" to "Media",
        "Segurança" to "Security", "Tomadas e interruptores" to "Plugs and switches", "Ações" to "Actions", "Sensores" to "Sensors",
        // estados
        "Ligado" to "On", "Desligado" to "Off", "Aberto" to "Open", "Fechado" to "Closed", "A abrir" to "Opening", "A fechar" to "Closing",
        "Trancado" to "Locked", "Destrancado" to "Unlocked", "A tocar" to "Playing", "Em pausa" to "Paused", "Inativo" to "Idle",
        "Em espera" to "Standby", "Indisponível" to "Unavailable", "Desconhecido" to "Unknown", "Aquecer" to "Heat", "Arrefecer" to "Cool",
        "Automático" to "Auto", "Aquecer/Arrefecer" to "Heat/Cool", "Desumidificar" to "Dry", "Ventilação" to "Fan only",
        "A carregar" to "Charging", "A limpar" to "Cleaning", "Na base" to "Docked", "A regressar" to "Returning",
        "Em casa" to "Home", "Fora" to "Away", "Ausente" to "Away", "Movimento" to "Motion", "Sem movimento" to "No motion",
        "Molhado" to "Wet", "Seco" to "Dry", "Alarme" to "Alarm", "Normal" to "Normal", "Ativar" to "Activate",
        // tempo
        "Céu limpo" to "Clear", "Nublado" to "Cloudy", "Excecional" to "Exceptional", "Nevoeiro" to "Fog", "Granizo" to "Hail",
        "Trovoada" to "Thunderstorm", "Trovoada e chuva" to "Thunderstorm and rain", "Pouco nublado" to "Partly cloudy",
        "Chuva forte" to "Heavy rain", "Chuva" to "Rain", "Neve" to "Snow", "Chuva e neve" to "Rain and snow", "Sol" to "Sunny", "Ventoso" to "Windy",
        // resumo
        "Segurança" to "Security", "Tudo seguro" to "All secure", "itens monitorizados" to "items monitored", "Aspirador" to "Vacuum",
        "A TOCAR" to "NOW PLAYING", " abertos" to " open", " destrancados" to " unlocked", " ligados · " to " on · ",
    )
}

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
        "Tudo desligado" to "All off", "Todas as luzes apagadas" to "All lights off", "Tudo tranquilo em casa" to "All quiet at home",
        "Amanhã" to "Tomorrow", "Máx. " to "High ", "Mín. " to "Low ", "A carregar o tempo…" to "Loading weather…",
        "Frio" to "Cool", "Calor" to "Heat", "Seco" to "Dry", "Ventilar" to "Fan", "Desligar" to "Turn off", "Atual" to "Current",
        "Temperatura por divisão" to "Temperature by room", "Nada para monitorizar." to "Nothing to monitor.",
        "Portas, janelas e fechaduras" to "Doors, windows and locks", "Toca para executar" to "Tap to run", "Cenas e scripts" to "Scenes and scripts",
        "Não há cenas nem scripts." to "No scenes or scripts.", "Modo" to "Mode", "Leituras" to "Readings", "Controlos" to "Controls",
        "Velocidade" to "Speed", "Oscilação" to "Swing", "Perfil" to "Profile", "Entrada" to "Input", "Luminosidade" to "Brightness",
        "Cor" to "Color", "Posição" to "Position", "Abrir" to "Open", "Parar" to "Stop", "Fechar" to "Close", "Iniciar" to "Start",
        "Pausar" to "Pause", "Base" to "Dock", "Destrancar" to "Unlock", "Trancar" to "Lock", "Executar" to "Run",
        "Toca para ver ao vivo" to "Tap to watch live", "Dispositivo" to "Device", "Última imagem" to "Last picture",
        "Abrir a porta" to "Open the door", "toca duas vezes" to "tap twice", "mantém ao vivo" to "keep live",
        "Fechar para o vídeo." to "Closing stops the video.", "Em direto" to "LIVE", "fecha em " to "closes in ", "Escolher portas" to "Choose doors",
    )
}

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
        "Definições" to "Settings", "A carregar a câmara…" to "Loading camera…", "Screensaver" to "Screensaver", "Entrada e bloqueio" to "Start and lock", "Vídeos" to "Videos", "Clipes no tablet" to "Clips on the tablet", "Interface e campainha" to "Interface and doorbell", "Vídeos no ecrã de descanso" to "Screensaver videos", "Os vídeos aéreos da Apple TV, sem som, com a hora e a data por cima. Quando aparecem define-se em Início → Ecrã de descanso." to "Apple TV aerial videos, muted, with the time and date on top. When they appear is set in Home → Screensaver.", "Usar vídeos da Apple TV" to "Use Apple TV videos", "Editar ecrã" to "Edit screen", "Cancelar" to "Cancel", "Concluído" to "Done", "Arrasta para mover. Toca em − para esconder e nas setas para duplicar o tamanho." to "Drag to move. Tap − to hide and the arrows to double the size.", "escondida" to "hidden", "escondidas" to "hidden", "escondido" to "hidden", "escondidos" to "hidden", "Casa 3D" to "3D Home", "Todos os pisos" to "All floors", "Piso " to "Floor ", "Arrasta para rodar · Dois dedos para aproximar · Toca numa divisão para a abrir ou numa luz para a ligar" to "Drag to rotate · Two fingers to zoom · Tap a room to open it or a light to switch it", "Previsão do tempo" to "Weather forecast", "Chuva" to "Rain", "Nascer" to "Sunrise", "Pôr" to "Sunset", "Humidade" to "Humidity", "Vento" to "Wind", "Avisos" to "Warnings", "Sem avisos do IPMA" to "No IPMA warnings", "Índice UV" to "UV index", "Risco de incêndio" to "Fire risk", "Fonte" to "Source", "Próximas horas" to "Next hours", "Próximos dias" to "Next days", "Hoje" to "Today", "Agora" to "Now", "Aviso amarelo" to "Yellow warning", "Aviso laranja" to "Orange warning", "Aviso vermelho" to "Red warning", "Começa" to "Starts", "Termina" to "Ends", "a partir de " to "from ", "até " to "until ", "A carregar a previsão…" to "Loading forecast…", "Sem previsão disponível." to "No forecast available.", "Esta divisão" to "This room", "Retomar" to "Resume", "Iniciar" to "Start", "Pausar" to "Pause", "Localizar" to "Locate", "Potência" to "Suction", "Rotinas" to "Routines", "Limpar divisões" to "Clean rooms", "Limpar" to "Clean", "Escolhe as divisões" to "Pick the rooms", "A carregar as divisões…" to "Loading rooms…", "Manutenção" to "Maintenance", "Definições do robô" to "Robot settings", "Voltar à base" to "Return to base", "Base" to "Dock", "À espera da foto do toque…" to "Waiting for the ring photo…", "Foto do toque" to "Ring photo", "A tocar" to "Ringing", "Atendida" to "Answered", "Chamada perdida" to "Missed call", "Tocaram à porta" to "Someone rang", "Tocaram à porta de casa" to "Someone is at the apartment door", "Ver câmara" to "Watch camera", "Guardar alterações" to "Save changes", "Alterações guardadas" to "Changes saved", "Aparência" to "Appearance",
        "Início" to "Home", "Entidades" to "Entities", "Planta 3D" to "3D floor plan", "Navegação" to "Navigation", "Sistema" to "System",
        "Idioma" to "Language", "Disposição" to "Layout", "Grelha" to "Grid", "Lista" to "List", "Animações" to "Animations",
        "Cor dos cartões" to "Card color", "Cor de destaque" to "Accent color", "Fundo" to "Background", "Fixo" to "Fixed", "Automático" to "Auto",
        "De dia" to "By day", "À noite" to "At night", "Ambiente" to "Ambient", "Vivos" to "Vivid", "Escuros" to "Dark", "Claros" to "Light",
        "Ecrã inicial" to "Home screen", "Meteorologia" to "Weather", "Fonte do tempo" to "Weather source", "Pessoas" to "People",
        "Disposição do início" to "Home layout", "Relógio grande" to "Big clock", "Duas colunas" to "Two columns", "Barra lateral" to "Sidebar", "Clássica" to "Classic",
        "Estado da casa" to "Home status", "Cartões de resumo" to "Summary cards", "Relógio de 24 horas" to "24-hour clock", "Ecrã de descanso" to "Screensaver",
        "Desligado" to "Off", "Dock" to "Dock", "Versão" to "Version", "Reconstruir agora" to "Rebuild now", "Procurar entidades…" to "Search entities…",
        "Principal" to "Main", "Secundária" to "Secondary", "Diagnóstico" to "Diagnostic", "Escondida no HA" to "Hidden in HA", "Mostrar" to "Show",
        "Dispositivos escondidos" to "Hidden devices", "Mostrar diagnóstico e escondidas no HA" to "Show diagnostic and HA-hidden",
        "Ligação" to "Connection", "Endereço" to "Address", "Ecrã e campainha" to "Screen and doorbell", "Permissões" to "Permissions",
    )
}

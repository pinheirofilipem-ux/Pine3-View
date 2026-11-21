package pt.pine3.view

/** Estado do ecrã partilhado entre o serviço (decide) e a atividade (mostra). */
object PanelState {
    @Volatile var sleeping = false
        private set
    /** As definições estão à frente: o serviço acorda o ecrã mas não troca de ecrã. */
    @Volatile var settingsVisible = false
    /** Janelas (vídeo, folhas, campainha) abertas por cima do ecrã principal. */
    @Volatile var dialogs = 0
    /** Último toque ou atividade, em qualquer janela da app. */
    @Volatile var lastTouch = System.currentTimeMillis()
    var listener: ((Boolean) -> Unit)? = null // main thread

    /** Janelas por cima do ecrã principal: escondem-se de imediato quando o ecrã vai desligar. */
    val sleepListeners = java.util.concurrent.CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun set(value: Boolean) {
        if (sleeping == value) return
        sleeping = value
        listener?.invoke(value)
        sleepListeners.forEach { it(value) }
    }
}

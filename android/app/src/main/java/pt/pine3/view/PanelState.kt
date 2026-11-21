package pt.pine3.view

/** Estado do ecrã partilhado entre o serviço (decide) e a atividade (mostra). */
object PanelState {
    @Volatile var sleeping = false
        private set
    var listener: ((Boolean) -> Unit)? = null // main thread

    fun set(value: Boolean) {
        if (sleeping == value) return
        sleeping = value
        listener?.invoke(value)
    }
}

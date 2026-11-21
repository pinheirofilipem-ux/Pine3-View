package pt.pine3.view

/** Estado do ecrã partilhado entre o serviço (decide) e a atividade (mostra). */
object PanelState {
    @Volatile var sleeping = false
        private set
    /** As definições estão à frente: o serviço acorda o ecrã mas não troca de ecrã. */
    @Volatile var settingsVisible = false
    var listener: ((Boolean) -> Unit)? = null // main thread

    fun set(value: Boolean) {
        if (sleeping == value) return
        sleeping = value
        listener?.invoke(value)
    }
}

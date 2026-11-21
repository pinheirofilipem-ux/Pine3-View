package pt.pine3.view

import android.content.Context

/**
 * Aplica a escala da interface: todas as medidas (dp e sp) das atividades passam a ser multiplicadas por ela.
 * Assim a largura em "px CSS" (largura / densidade) aumenta quando a escala baixa, como num painel web mais largo.
 */
object UiScale {
    private var baseDensity = 0f
    private var baseScaled = 0f

    fun apply(ctx: Context) {
        val dm = ctx.resources.displayMetrics
        if (baseDensity == 0f) { baseDensity = dm.density; baseScaled = dm.scaledDensity }
        val s = Prefs(ctx).uiScale / 100f
        dm.density = baseDensity * s
        dm.scaledDensity = baseScaled * s
    }
}

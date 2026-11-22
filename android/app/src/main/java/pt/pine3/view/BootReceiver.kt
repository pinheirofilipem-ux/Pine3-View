package pt.pine3.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Abre o painel quando o tablet arranca (precisa da permissão "Aparecer por cima de outras apps" em Android 10+). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Prefs(ctx).bootStart) return
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}

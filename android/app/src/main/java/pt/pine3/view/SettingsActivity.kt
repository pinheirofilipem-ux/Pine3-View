package pt.pine3.view

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Definições: ligação ao Home Assistant, ecrã/movimento, campainha e permissões. */
class SettingsActivity : Activity() {

    private lateinit var kit: Kit
    private lateinit var prefs: Prefs
    private lateinit var url: EditText
    private lateinit var token: EditText
    private lateinit var timeout: EditText
    private lateinit var sens: SeekBar
    private lateinit var sensValue: TextView
    private lateinit var ssl: Switch
    private lateinit var boot: Switch
    private lateinit var bellEntity: EditText
    private lateinit var bellWhen: EditText
    private lateinit var bellHold: EditText
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private val perms = ArrayList<Triple<View, TextView, () -> Boolean>>()
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            (statusDot.background as android.graphics.drawable.GradientDrawable).setColor(if (Ha.connected) Color.parseColor("#8FD9A8") else Color.parseColor("#F2755C"))
            statusText.text = Ha.homeError?.takeIf { Ha.connected } ?: Ha.status
            refreshPerms()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kit = Kit(this); prefs = Prefs(this)
        val d = kit::dp

        fun card(title: String, sub: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.rounded(Color.argb(255, 43, 26, 45), 20)
            setPadding(d(18), d(16), d(18), d(18))
            addView(kit.text(title, 17f, kit.white, 500, display = true))
            addView(kit.text(sub, 13f, kit.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(2) })
        }
        fun input(v: String, hint: String, type: Int) = EditText(this).apply {
            setText(v); this.hint = hint; setHintTextColor(Color.parseColor("#7C687A")); setTextColor(kit.white)
            textSize = 16f; inputType = type; setSingleLine(); typeface = kit.font(400, false)
            background = kit.rounded(Color.parseColor("#3A2339"), 12); setPadding(d(16), d(13), d(16), d(13))
        }
        fun labeled(p: LinearLayout, label: String, v: View) {
            p.addView(kit.text(label, 13f, Color.parseColor("#BFA9BB")), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14); bottomMargin = d(6) })
            p.addView(v, LinearLayout.LayoutParams(-1, -2))
        }
        fun switchRow(p: LinearLayout, label: String, on: Boolean): Switch {
            val sw = Switch(this).apply { isChecked = on }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(kit.text(label, 15f, kit.white), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(sw)
            p.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
            return sw
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(kit.text("Definições", 26f, kit.white, 500, display = true))
        titles.addView(kit.text("Pine3 View (nativo)", 14f, kit.muted))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(kit.pill("Guardar e voltar", null, true) { save(); finish() })
        col.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(16) })

        val cards = LinearLayout(this)
        col.addView(cards, LinearLayout.LayoutParams(-1, -2))
        fun add(c: LinearLayout, w: Float = 1f) = cards.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w).apply { marginEnd = d(14) })

        // Home Assistant
        val ha = card("Home Assistant", "A app lê a casa do Pine3 View e acorda com as tuas entidades")
        url = input(prefs.haUrl, "http://192.168.1.10:8123", InputType.TYPE_TEXT_VARIATION_URI)
        labeled(ha, "Endereço", url)
        token = input(prefs.haToken, "Token de acesso de longa duração", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        labeled(ha, "Token (perfil do HA, separador Segurança)", token)
        ssl = switchRow(ha, "Aceitar certificado HTTPS auto-assinado", prefs.ignoreSsl)
        val st = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this).apply { background = kit.circle(Color.parseColor("#F2755C")) }
        st.addView(statusDot, LinearLayout.LayoutParams(d(10), d(10)).apply { marginEnd = d(12) })
        statusText = kit.text(Ha.status, 14f, kit.white)
        st.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        ha.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        add(ha)

        // Ecrã e campainha
        val scr = card("Ecrã e campainha", "Acorda com movimento ou com a campainha")
        timeout = input(prefs.timeoutSec.toString(), "15", InputType.TYPE_CLASS_NUMBER)
        labeled(scr, "Desligar o ecrã após (segundos sem movimento)", timeout)
        val sh = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sh.addView(kit.text("Sensibilidade", 13f, Color.parseColor("#BFA9BB")), LinearLayout.LayoutParams(0, -2, 1f))
        sensValue = kit.text("", 15f, kit.accent, 600); sh.addView(sensValue)
        scr.addView(sh, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        sens = SeekBar(this).apply {
            max = 9; progress = prefs.sensitivity - 1
            progressTintList = android.content.res.ColorStateList.valueOf(kit.accent); thumbTintList = android.content.res.ColorStateList.valueOf(kit.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { sensValue.text = "${p + 1} / 10" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        sensValue.text = "${prefs.sensitivity} / 10"
        scr.addView(sens, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })
        val rule = prefs.wakeRules.firstOrNull() ?: DEFAULT_DOORBELL
        bellEntity = input(rule.entity, "event.entry_hallway_comelit_intercom_doorbell", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        labeled(scr, "Campainha: entidade que acorda o ecrã", bellEntity)
        bellWhen = input(rule.whenValue, "ring", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        labeled(scr, "Só quando (estado ou event_type)", bellWhen)
        bellHold = input(rule.holdSec.toString(), "120", InputType.TYPE_CLASS_NUMBER)
        labeled(scr, "Manter o ecrã ligado (segundos)", bellHold)
        add(scr)

        // Sistema
        val sys = card("Sistema", "Para funcionar sempre, mesmo com o ecrã desligado")
        fun permRow(label: String, check: () -> Boolean, action: () -> Unit) {
            val dot = View(this).apply { background = kit.circle(Color.parseColor("#F2755C")) }
            val btn = kit.text("Ativar", 13f, kit.accent, 600).apply {
                background = kit.rounded(Color.parseColor("#44294A"), 10); setPadding(d(14), d(8), d(14), d(8)); setOnClickListener { action() }
            }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(dot, LinearLayout.LayoutParams(d(10), d(10)).apply { marginEnd = d(14) })
            row.addView(kit.text(label, 15f, kit.white), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(btn)
            sys.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
            perms += Triple(dot, btn, check)
        }
        permRow("Câmara", { checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1)
        }
        permRow("Desligar ecrã por completo (administrador)", { MotionService.isAdminActive(this) }) {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Necessário para desligar o ecrã por completo (sem luz)."))
        }
        permRow("Sem otimização de bateria", { (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) }) {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")))
        }
        permRow("Aparecer por cima (arranque automático)", { Settings.canDrawOverlays(this) }) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:$packageName")))
        }
        boot = switchRow(sys, "Abrir quando o tablet arranca", prefs.bootStart)
        add(sys)

        val holder = FrameLayout(this).apply {
            setPadding(d(24), d(18), d(10), d(18))
            addView(col, FrameLayout.LayoutParams(-1, -2))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1C0F1E")); isFillViewport = true; addView(holder) })
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        save()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { MotionService.poke(); return super.dispatchTouchEvent(ev) }

    private fun refreshPerms() = perms.forEach { (dot, btn, check) ->
        val ok = check()
        (dot.background as android.graphics.drawable.GradientDrawable).setColor(if (ok) Color.parseColor("#8FD9A8") else Color.parseColor("#F2755C"))
        btn.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun save() {
        prefs.haUrl = url.text.toString(); prefs.haToken = token.text.toString(); prefs.ignoreSsl = ssl.isChecked
        prefs.timeoutSec = timeout.text.toString().toIntOrNull() ?: 15
        prefs.sensitivity = sens.progress + 1
        prefs.bootStart = boot.isChecked
        val entity = bellEntity.text.toString().trim().lowercase()
        prefs.wakeRules = if (entity.isBlank()) emptyList()
        else listOf(WakeRule(entity, bellWhen.text.toString().trim(), (bellHold.text.toString().toIntOrNull() ?: 120).coerceIn(5, 3600)))
        Ha.configure(this) // (re)liga ao HA se mudou o endereço/token; atualiza as regras
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) MotionService.start(this)
    }
}

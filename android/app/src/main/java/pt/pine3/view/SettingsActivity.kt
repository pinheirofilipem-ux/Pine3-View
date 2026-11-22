package pt.pine3.view

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Miniatura de um tema (o mesmo fundo do painel, num quadrado arredondado). */
private class Swatch(val kit: Kit, theme: Theme, val selected: Boolean) : View(kit.ctx) {
    private val bg = ThemeBackground(theme)
    private val clip = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(3).toFloat(); color = kit.accent }
    override fun onDraw(c: Canvas) {
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset(); clip.addRoundRect(r, kit.dp(14).toFloat(), kit.dp(14).toFloat(), Path.Direction.CW)
        val save = c.save(); c.clipPath(clip); bg.setBounds(0, 0, width, height); bg.draw(c); c.restoreToCount(save)
        if (selected) { r.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2); c.drawRoundRect(r, kit.dp(13).toFloat(), kit.dp(13).toFloat(), stroke) }
    }
}

/** Alternador (.tg do painel web). */
private class Toggle(val kit: Kit, var on: Boolean, val onChange: (Boolean) -> Unit) : View(kit.ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; setOnClickListener { on = !on; invalidate(); onChange(on) } }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(kit.dp(56), kit.dp(32))
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = if (on) Color.parseColor("#34C759") else Color.argb(80, 128, 128, 128)
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
        p.color = Color.WHITE
        c.drawCircle(if (on) w - h / 2 else h / 2, h / 2, h / 2 - kit.dp(3), p)
    }
}

/**
 * Definições, com o mesmo formato do painel web (barra lateral e "Guardar alterações"), mais uma secção inicial "App"
 * com o que é só deste dispositivo (ligação ao HA, ecrã, campainha, permissões).
 */
class SettingsActivity : Activity() {

    private lateinit var kit: Kit
    private lateinit var prefs: Prefs
    private lateinit var navBox: LinearLayout
    private lateinit var mainBox: LinearLayout
    private lateinit var mainScroll: ScrollView
    private lateinit var saveBtn: TextView
    private lateinit var toast: TextView
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())

    private var section = "app"
    private var draft = JSONObject()
    private var saved = ""
    private var saveApp: (() -> Unit)? = null
    private var entQuery = ""
    private var entAll = false
    private var packStatus: TextView? = null
    private var statusDot: View? = null
    private var statusText: TextView? = null
    private val perms = ArrayList<Triple<View, TextView, () -> Boolean>>()

    private val listener = object : Ha.Listener { override fun onHome() { if (!dirty()) reloadDraft() } }

    private val refresh = object : Runnable {
        override fun run() {
            statusDot?.let { (it.background as android.graphics.drawable.GradientDrawable).setColor(if (Ha.connected) Color.parseColor("#34C759") else Color.parseColor("#F2755C")) }
            statusText?.text = Ha.homeError?.takeIf { Ha.connected } ?: Ha.status
            packStatus?.text = Pack.describe(this@SettingsActivity)
            perms.forEach { (dot, btn, check) ->
                val ok = check()
                (dot.background as android.graphics.drawable.GradientDrawable).setColor(if (ok) Color.parseColor("#34C759") else Color.parseColor("#F2755C"))
                btn.visibility = if (ok) View.GONE else View.VISIBLE
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------- rascunho

    private fun reloadDraft() {
        draft = JSONObject(Ha.home?.settings?.toString() ?: "{}")
        saved = draft.toString(); changed = false
        updateSave()
    }
    private var appDirty = false
    private fun markApp() { if (!appDirty) { appDirty = true; updateSave() } }
    private var changed = false
    private fun dirty() = changed || draft.toString() != saved || appDirty
    private fun updateSave() {
        val d = dirty()
        saveBtn.background = kit.ripple(kit.rounded(if (d) kit.cream else kit.soft, 40))
        saveBtn.setTextColor(if (d) kit.ink else kit.muted)
        saveBtn.alpha = if (d) 1f else 0.6f
        saveBtn.isEnabled = d
    }
    private fun setD(k: String, v: Any) { draft.put(k, v); changed = true; if (k in LOOK_KEYS) relook() else updateSave() }
    private fun bool(k: String, def: Boolean) = draft.optBoolean(k, def)
    private fun list(k: String): MutableList<String> = (0 until (draft.optJSONArray(k)?.length() ?: 0)).map { draft.optJSONArray(k)!!.getString(it) }.toMutableList()
    private fun setList(k: String, l: List<String>) = setD(k, JSONArray(l))

    private fun save() {
        saveApp?.invoke()
        val appChanged = appDirty
        appDirty = false
        val old = JSONObject(saved.ifBlank { "{}" })
        val changes = JSONObject()
        draft.keys().forEach { k -> if (!old.has(k) || old.get(k).toString() != draft.get(k).toString()) changes.put(k, draft.get(k)) }
        if (changes.length() > 0 && Ha.connected) {
            Ha.saveSettings(changes)
            saved = draft.toString(); changed = false
            showToast(Tr.t("Alterações guardadas"))
        } else if (appChanged) showToast(Tr.t("Alterações guardadas"))
        updateSave()
    }

    private fun showToast(t: String) {
        toast.text = t; toast.visibility = View.VISIBLE
        handler.postDelayed({ toast.visibility = View.GONE }, 1800)
    }

    // ---------------------------------------------------------------- ciclo de vida

    private val LOOK_KEYS = setOf("theme", "theme_day", "theme_night", "appearance", "language", "card_color", "accent_color")

    // navegação com lente deslizante
    private lateinit var navWrap: FrameLayout
    private lateinit var navLens: LensView
    private val navRows = ArrayList<Triple<FrameLayout, android.widget.ImageView, TextView>>()
    private val navItems = ArrayList<View>()
    private var navTop = 0f
    private var navH = 0f
    private var navMove: android.animation.ValueAnimator? = null
    private var navSquish: android.animation.ValueAnimator? = null
    private var navActive = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiScale.apply(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        Glass.level = prefs.glassLevel
        reloadDraft0()
        buildUi(0)
    }

    private fun colorOf(k: String, def: Int) = try { Color.parseColor(draft.optString(k)) } catch (e: Exception) { def }

    /** (Re)constrói todo o ecrã com o aspeto do rascunho atual (tema, língua, cores): é o que aplica as alterações de aspeto de imediato. */
    private fun buildUi(scrollY: Int) {
        kit = Kit(this)
        val theme = Themes.pick(draft, Ha.states["sun.sun"]?.state, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        kit.applyTheme(theme)
        kit.cream = colorOf("card_color", kit.cream)
        kit.accent = colorOf("accent_color", kit.accent)
        Tr.en = draft.optString("language", "pt") == "en"
        Elastic.on = draft.optBoolean("animations", true)

        root = FrameLayout(this).apply { clipChildren = false; background = ThemeBackground(theme) }
        val page = LinearLayout(this).apply { clipChildren = false; clipToPadding = false; orientation = LinearLayout.VERTICAL; setPadding(kit.dp(40), kit.dp(24), kit.dp(40), kit.dp(20)) }

        // cabeçalho
        val hd = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(kit.text("PINE3 VIEW", 13f, kit.muted, 600, spacing = 0.2f))
        titles.addView(kit.text(Tr.t("Definições"), kit.clamp(32f, kit.vw(4.2f), 48f), kit.fg, 400, display = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(6) })
        hd.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        saveBtn = kit.text("✓  " + Tr.t("Guardar alterações"), 16f, kit.ink, 600).apply {
            setPadding(kit.dp(22), kit.dp(13), kit.dp(22), kit.dp(13)); isClickable = true; setOnClickListener { save() }
            Elastic.press(this, 0.95f)
        }
        hd.addView(saveBtn)
        hd.addView(kit.roundButton("mdi:close", 48, kit.soft, kit.fg) { saveApp?.invoke(); finish() }, LinearLayout.LayoutParams(kit.dp(48), kit.dp(48)).apply { marginStart = kit.dp(12) })
        page.addView(hd, LinearLayout.LayoutParams(-1, -2))

        // corpo: barra lateral (com a lente) + conteúdo
        val body = LinearLayout(this).apply { setPadding(0, kit.dp(18), 0, 0); clipChildren = false; clipToPadding = false }
        navBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        navLens = LensView(kit)
        navWrap = FrameLayout(this).apply {
            clipChildren = false; clipToPadding = false
            addView(navLens, FrameLayout.LayoutParams(0, 0))
            addView(navBox, FrameLayout.LayoutParams(-1, -2))
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (navMove?.isRunning == true) applyNavLens(navTop, navH) else placeNav(false) }
        }
        val ov = kit.dp(14)
        body.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false; clipChildren = false; setPadding(ov, ov, ov, ov); addView(navWrap)
        }, LinearLayout.LayoutParams(kit.dp(kit.clamp(200f, kit.vw(20f), 270f)) + 2 * ov, -1).apply { marginStart = -ov; marginEnd = kit.dp(22) - ov; topMargin = -ov; bottomMargin = -ov })
        mainBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(mainBox) }
        body.addView(mainScroll, LinearLayout.LayoutParams(0, -1, 1f))
        page.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        toast = kit.text("", 15f, kit.ink, 600).apply { background = kit.surfaceOn(40); setPadding(kit.dp(20), kit.dp(12), kit.dp(20), kit.dp(12)); visibility = View.GONE }
        root.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = kit.dp(26) })
        setContentView(root)
        navActive = -1
        buildNavItems()
        updateSave()
        showSection(section, keepScroll = false, animateNav = false)
        if (scrollY > 0) restoreScroll(scrollY)
    }

    /** O conteúdo só tem altura depois do layout: restaura o scroll nesse momento (com post ficava sempre no topo). */
    private fun restoreScroll(y: Int) {
        val sv = mainScroll
        sv.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (sv.getChildAt(0).height <= 0) return true
                sv.viewTreeObserver.removeOnPreDrawListener(this)
                sv.scrollTo(0, y)
                return true
            }
        })
    }

    /** Muda o aspeto já, sem sair do ecrã: refaz tudo com as cores novas e mantém a secção e o scroll. */
    private fun relook() {
        saveApp?.invoke()
        val y = mainScroll.scrollY
        buildUi(y)
    }

    private fun reloadDraft0() { draft = JSONObject(Ha.home?.settings?.toString() ?: "{}"); saved = draft.toString() }

    private val REQ_FOLDER = 4821

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.clipFolder = uri.toString(); Pack.refreshFolder(); Aerials.setLocal(this)
            if (section == "saver") redraw()
        }
    }

    override fun onResume() { super.onResume(); PanelState.settingsVisible = true; Ha.listeners += listener; handler.post(refresh) }

    override fun onPause() {
        PanelState.settingsVisible = false
        handler.removeCallbacks(refresh)
        Ha.listeners -= listener
        saveApp?.invoke()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { MotionService.poke(); return super.dispatchTouchEvent(ev) }

    // ---------------------------------------------------------------- navegação

    private val sections = listOf(
        Triple("app", "mdi:tablet", "App"), Triple("look", "mdi:palette-outline", "Aparência"), Triple("home", "mdi:home-outline", "Início"),
        Triple("rooms", "mdi:view-grid-outline", "Divisões"), Triple("entities", "mdi:format-list-checks", "Entidades"), Triple("saver", "mdi:movie-open-play-outline", "Screensaver"),
        Triple("plan", "mdi:floor-plan", "Planta 3D"), Triple("nav", "mdi:dock-bottom", "Navegação"), Triple("system", "mdi:cog-outline", "Sistema"),
    )

    private fun buildNavItems() {
        navBox.removeAllViews(); navRows.clear(); navItems.clear()
        sections.forEach { (k, icon, label) ->
            val circle = FrameLayout(this).apply { background = kit.circle(kit.soft) }
            val ic = kit.icon(icon, kit.fg, 22)
            circle.addView(ic, FrameLayout.LayoutParams(kit.dp(22), kit.dp(22), Gravity.CENTER))
            val tx = kit.text(if (k == "app") "App" else Tr.t(label), 18f, kit.fg, 500)
            val item = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(kit.dp(12), kit.dp(10), kit.dp(16), kit.dp(10)); isClickable = true
                setOnClickListener { if (k != section) showSection(k) }
                Elastic.press(this, 0.96f)
                addView(circle, LinearLayout.LayoutParams(kit.dp(44), kit.dp(44)).apply { marginEnd = kit.dp(14) })
                addView(tx)
            }
            navBox.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(4) })
            navRows += Triple(circle, ic, tx); navItems += item
        }
    }

    private fun markNav(i: Int, animate: Boolean) {
        navRows.forEachIndexed { n, (circle, ic, tx) ->
            val on = n == i
            val to = if (on) kit.ink else kit.fg
            circle.background = kit.circle(if (on) Color.argb(20, 42, 34, 31) else kit.soft)
            val from = tx.currentTextColor
            if (!animate || from == to) { tx.setTextColor(to); ic.setColorFilter(to) }
            else android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
                duration = 350
                addUpdateListener { tx.setTextColor(it.animatedValue as Int); ic.setColorFilter(it.animatedValue as Int) }
                start()
            }
        }
    }

    private fun applyNavLens(t: Float, h: Float) {
        navTop = t; navH = h
        val o = navLens.over
        navLens.layout(-o, t.toInt() - o, navWrap.width + o, (t + Math.max(0f, h)).toInt() + o)
        navLens.invalidate()
    }

    /** Põe a lente no item ativo; com [animate] desliza com mola e estica como uma gota (na vertical). */
    private fun placeNav(animate: Boolean) {
        val i = navActive
        if (i < 0 || i >= navItems.size) return
        val item = navItems[i]
        if (item.height <= 0) return
        val t1 = item.top.toFloat(); val h1 = item.height.toFloat()
        navMove?.cancel(); navSquish?.cancel()
        if (!animate || !Elastic.on) { applyNavLens(t1, h1); navLens.scaleXf = 1f; navLens.scaleYf = 1f; navLens.glass = 0f; return }
        val t0 = navTop; val h0 = navH
        navMove = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550; interpolator = android.view.animation.PathInterpolator(0.34f, 1.36f, 0.5f, 1f)
            addUpdateListener { val f = it.animatedValue as Float; applyNavLens(t0 + (t1 - t0) * f, h0 + (h1 - h0) * f) }
            start()
        }
        val ease = android.view.animation.PathInterpolator(0.3f, 0.9f, 0.4f, 1f)
        navSquish = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            addUpdateListener {
                val f = it.animatedValue as Float
                fun seg(a: Float, b: Float, x0: Float, x1: Float): Float { val k = ease.getInterpolation(((f - a) / (b - a)).coerceIn(0f, 1f)); return x0 + (x1 - x0) * k }
                // esticar no sentido do movimento (vertical)
                when {
                    f < 0.35f -> { navLens.scaleYf = seg(0f, 0.35f, 1f, 1.12f); navLens.scaleXf = seg(0f, 0.35f, 1f, 0.95f); navLens.glass = seg(0f, 0.35f, 0f, 1f) }
                    f < 0.70f -> { navLens.scaleYf = seg(0.35f, 0.70f, 1.12f, 0.98f); navLens.scaleXf = seg(0.35f, 0.70f, 0.95f, 1.02f); navLens.glass = seg(0.35f, 0.70f, 1f, 0f) }
                    else -> { navLens.scaleYf = seg(0.70f, 1f, 0.98f, 1f); navLens.scaleXf = seg(0.70f, 1f, 1.02f, 1f); navLens.glass = 0f }
                }
                navLens.invalidate()
            }
            start()
        }
    }

    private fun showSection(key: String, keepScroll: Boolean = false, animateNav: Boolean = true) {
        saveApp?.invoke(); saveApp = null; statusDot = null; statusText = null; packStatus = null; perms.clear()
        val y = if (keepScroll) mainScroll.scrollY else 0
        section = key
        val prev = navActive
        navActive = sections.indexOfFirst { it.first == key }
        markNav(navActive, animateNav && prev >= 0)
        placeNav(animateNav && prev >= 0 && prev != navActive)
        mainBox.removeAllViews()
        when (key) {
            "app" -> buildApp(); "look" -> buildLook(); "home" -> buildHome(); "rooms" -> buildRooms()
            "entities" -> buildEntities(); "saver" -> buildSaver(); "plan" -> buildPlan(); "nav" -> buildNav(); else -> buildSystem()
        }
        if (keepScroll) { if (y > 0) restoreScroll(y) }
        else if (animateNav) Elastic.popGrids(mainBox)
    }

    private fun redraw() = showSection(section, keepScroll = true, animateNav = false)

    // ---------------------------------------------------------------- blocos de interface

    private fun d(v: Int) = kit.dp(v)

    private fun panel(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = kit.glass(28); setPadding(d(26), d(22), d(26), d(22)) }
        mainBox.addView(p, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(16) })
        return p
    }
    private fun h3(p: LinearLayout, t: String, first: Boolean = false) = p.addView(kit.text(Tr.t(t), 19f, kit.fg, 500, display = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(if (first) 0 else 22) })
    private fun para(p: LinearLayout, t: String) = p.addView(kit.text(Tr.t(t), 14f, kit.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) })

    private fun divider(p: LinearLayout) = p.addView(View(this).apply { setBackgroundColor(kit.soft) }, LinearLayout.LayoutParams(-1, d(1)).apply { topMargin = d(14) })

    private fun titleBlock(title: String, sub: String?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(kit.text(Tr.t(title), 17f, kit.fg, 600))
        if (sub != null) addView(kit.text(Tr.t(sub), 13f, kit.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(2) })
    }

    private fun optToggle(p: LinearLayout, title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
        if (p.childCount > 0) divider(p)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(titleBlock(title, sub), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = d(16) })
        row.addView(Toggle(kit, on, onChange))
        p.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
    }

    private fun optChoice(p: LinearLayout, title: String, sub: String?, options: List<Pair<String, String>>, cur: String, onPick: (String) -> Unit) {
        if (p.childCount > 0) divider(p)
        p.addView(titleBlock(title, sub), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
        val g = Grid(this, d(110), d(42), d(8))
        options.forEach { (v, l) ->
            val sel = v == cur
            g.addView(kit.text(Tr.t(l), 15f, if (sel) kit.ink else kit.fg, 600).apply {
                gravity = Gravity.CENTER; background = kit.ripple(kit.rounded(if (sel) kit.cream else kit.soft, 40)); setSingleLine(); ellipsize = TextUtils.TruncateAt.END
                isClickable = true; setOnClickListener { onPick(v) }
            })
        }
        p.addView(g, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
    }

    private fun input(v: String, hint: String, type: Int, app: Boolean = false) = EditText(this).apply {
        setText(v); this.hint = Tr.t(hint); setHintTextColor(kit.muted); setTextColor(kit.fg); textSize = 16f; inputType = type; setSingleLine()
        typeface = kit.font(400, false); background = kit.rounded(kit.soft, 14); setPadding(d(16), d(13), d(16), d(13))
        if (app) addTextChangedListener(object : TextWatcher { override fun afterTextChanged(e: Editable?) { markApp() } override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {} override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {} })
    }
    private fun label(p: LinearLayout, t: String) = p.addView(kit.text(Tr.t(t), 13f, kit.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14); bottomMargin = d(6) })

    private fun pillBtn(t: String, icon: String?, filled: Boolean = false, click: () -> Unit) = kit.pill(Tr.t(t), icon, filled, click)

    // ---------------------------------------------------------------- App (só deste dispositivo)

    private fun buildApp() {
        // Ligação ao HA
        val ha = panel()
        h3(ha, Tr.t("Ligação"), true); para(ha, "A app lê a casa do Pine3 View e acorda com as tuas entidades.")
        label(ha, Tr.t("Endereço"))
        val url = input(prefs.haUrl, DEFAULT_HA_URL, InputType.TYPE_TEXT_VARIATION_URI, app = true); ha.addView(url)
        label(ha, "Token (perfil do HA, separador Segurança)")
        val token = input(prefs.haToken, "Token de acesso de longa duração", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, app = true); ha.addView(token)
        var ssl = prefs.ignoreSsl
        optToggle(ha, "Aceitar certificado HTTPS auto-assinado", null, ssl) { ssl = it; markApp() }
        val st = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this).apply { background = kit.circle(Color.parseColor("#F2755C")) }
        st.addView(statusDot, LinearLayout.LayoutParams(d(10), d(10)).apply { marginEnd = d(12) })
        statusText = kit.text(Ha.status, 14f, kit.fg); st.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        ha.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        val testOut = kit.text("", 12f, kit.muted)
        fun commitHa() { prefs.haUrl = url.text.toString(); prefs.haToken = token.text.toString(); prefs.ignoreSsl = ssl }
        ha.addView(pillBtn("Testar ligação", "mdi:wifi") {
            commitHa(); testOut.text = ""
            HaTest.run(prefs.wsUrl(), prefs.haToken, prefs.ignoreSsl,
                line = { ok, t -> runOnUiThread { testOut.append((if (ok) "✔ " else "✘ ") + t + "\n") } },
                done = { runOnUiThread { testOut.append("\n" + Ha.log.takeLast(4).joinToString("\n")) } })
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(14) })
        ha.addView(testOut, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })

        // Ecrã e campainha
        val scr = panel()
        h3(scr, Tr.t("Interface e campainha"), true); para(scr, "O tamanho da interface e a campainha que acorda o ecrã. O bloqueio do ecrã e o screensaver estão na secção Screensaver.")
        val sc = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sc.addView(kit.text(Tr.t("Tamanho da interface"), 13f, kit.muted), LinearLayout.LayoutParams(0, -2, 1f))
        val scValue = kit.text("${prefs.uiScale}%", 15f, kit.accent, 600); sc.addView(scValue)
        scr.addView(sc, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        scr.addView(SeekBar(this).apply {
            max = 70; progress = prefs.uiScale - 60
            progressTintList = ColorStateList.valueOf(kit.accent); thumbTintList = ColorStateList.valueOf(kit.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { scValue.text = "${p + 60}%" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { saveApp?.invoke(); prefs.uiScale = s.progress + 60; recreate() }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })
        para(scr, "Se o painel web mostra mais coisas por ecrã, baixa este valor.")
        val rule = prefs.wakeRules.firstOrNull() ?: DEFAULT_DOORBELL
        label(scr, "Campainha: entidade que acorda o ecrã")
        val bellEntity = input(rule.entity, DEFAULT_DOORBELL.entity, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, app = true); scr.addView(bellEntity)
        label(scr, "Só quando (estado ou event_type)")
        val bellWhen = input(rule.whenValue, "ring", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, app = true); scr.addView(bellWhen)
        label(scr, "Manter o ecrã ligado (segundos)")
        val bellHold = input(rule.holdSec.toString(), "120", InputType.TYPE_CLASS_NUMBER, app = true); scr.addView(bellHold)
        var boot = prefs.bootStart
        optToggle(scr, "Abrir quando o tablet arranca", null, boot) { boot = it; markApp() }

        // Permissões
        val sys = panel()
        h3(sys, Tr.t("Permissões"), true); para(sys, "Para funcionar sempre, mesmo com o ecrã desligado.")
        fun permRow(label: String, check: () -> Boolean, action: () -> Unit) {
            val dot = View(this).apply { background = kit.circle(Color.parseColor("#F2755C")) }
            val btn = kit.text(Tr.t("Ativar"), 14f, kit.accent, 600).apply { background = kit.rounded(kit.soft, 12); setPadding(d(16), d(8), d(16), d(8)); setOnClickListener { action() } }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(dot, LinearLayout.LayoutParams(d(10), d(10)).apply { marginEnd = d(14) })
            row.addView(kit.text(Tr.t(label), 16f, kit.fg), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(btn)
            sys.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
            perms += Triple(dot, btn, check)
        }
        permRow("Câmara", { checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }) { requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1) }
        permRow("Desligar ecrã por completo (administrador)", { MotionService.isAdminActive(this) }) {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Necessário para desligar o ecrã por completo (sem luz)."))
        }
        permRow("Sem otimização de bateria", { (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) }) {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")))
        }
        permRow("Aparecer por cima (arranque automático)", { Settings.canDrawOverlays(this) }) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:$packageName")))
        }

        saveApp = {
            commitHa()
            prefs.bootStart = boot
            val entity = bellEntity.text.toString().trim().lowercase()
            prefs.wakeRules = if (entity.isBlank()) emptyList() else listOf(WakeRule(entity, bellWhen.text.toString().trim(), (bellHold.text.toString().toIntOrNull() ?: 120).coerceIn(5, 3600)))
            Ha.configure(this)
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) MotionService.start(this)
        }
    }

    // ---------------------------------------------------------------- Screensaver (e bloqueio do ecrã)

    private val PASTELS = listOf("#F7C6CE", "#FBD5B5", "#FFF1B8", "#CDEBC5", "#BFE3F0", "#D6CCF0", "#F3D1E8", "#E8E2D5")

    private fun buildSaver() {
        val ent = panel()
        h3(ent, Tr.t("Entrada e bloqueio"), true)
        para(ent, "O screensaver entra sempre antes do bloqueio: se o screensaver estiver ativo, o bloqueio fica sempre acima dele. O bloqueio desliga o ecrã quando ninguém se mexe e conta sempre, também com o screensaver ativo.")
        var lockV = prefs.timeoutSec.coerceIn(3, 180)
        var saverV = prefs.saverSec(draft.optInt("screensaver", 0)).coerceIn(0, 120)
        lateinit var lockBar: SeekBar; lateinit var saverBar: SeekBar
        lateinit var lockTx: TextView; lateinit var saverTx: TextView
        fun saverText() = if (saverV == 0) Tr.t("Desligado") else "$saverV s"
        fun bar(title: String, init: Int, lo: Int, hi: Int, onUser: (Int) -> Unit): Pair<SeekBar, TextView> {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(kit.text(Tr.t(title), 13f, kit.muted), LinearLayout.LayoutParams(0, -2, 1f))
            val tx = kit.text("", 15f, kit.accent, 600); row.addView(tx)
            ent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
            val sb = SeekBar(this).apply {
                max = hi - lo; progress = init - lo
                progressTintList = ColorStateList.valueOf(kit.accent); thumbTintList = ColorStateList.valueOf(kit.accent)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { if (u) { onUser(p + lo); markApp() } }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            }
            ent.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })
            return sb to tx
        }
        // o screensaver entra sempre ANTES do bloqueio: o bloqueio fica sempre acima do screensaver (quando este está ativo)
        val s1 = bar("Entrar no screensaver após (segundos sem toques, 0 = desligado)", saverV, 0, 120) { v ->
            saverV = v
            if (saverV > 0 && lockV <= saverV) { lockV = minOf(180, saverV + 1); lockBar.progress = lockV - 3; lockTx.text = "$lockV s" }
            saverTx.text = saverText()
        }
        saverBar = s1.first; saverTx = s1.second; saverTx.text = saverText()
        val s2 = bar("Bloquear o ecrã após (segundos sem movimento)", lockV, 3, 180) { v ->
            lockV = v
            if (saverV > 0 && saverV >= lockV) { saverV = lockV - 1; saverBar.progress = saverV; saverTx.text = saverText() }
            lockTx.text = "$lockV s"
        }
        lockBar = s2.first; lockTx = s2.second; lockTx.text = "$lockV s"
        val sh = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sh.addView(kit.text(Tr.t("Sensibilidade ao movimento"), 13f, kit.muted), LinearLayout.LayoutParams(0, -2, 1f))
        val sensValue = kit.text("${prefs.sensitivity} / 10", 15f, kit.accent, 600); sh.addView(sensValue)
        ent.addView(sh, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        val sens = SeekBar(this).apply {
            max = 9; progress = prefs.sensitivity - 1
            progressTintList = ColorStateList.valueOf(kit.accent); thumbTintList = ColorStateList.valueOf(kit.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { sensValue.text = "${p + 1} / 10"; if (u) markApp() }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        ent.addView(sens, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6) })

        val vid = panel()
        h3(vid, Tr.t("Vídeos"), true)
        para(vid, "Vídeos sem som em ecrã inteiro, com a hora e a data por cima.")
        var saverVideo = prefs.saverVideo
        optToggle(vid, "Usar vídeos no screensaver", null, saverVideo) { saverVideo = it; markApp() }

        var fitV = prefs.saverFit
        optChoice(vid, "Ajuste do vídeo ao ecrã", "Pela altura: o vídeo ocupa a altura toda do ecrã, sem esticar (se for mais largo, corta um pouco dos lados). Preencher corta os dois eixos; Inteiro mostra tudo, com barras.", listOf("height" to "Pela altura", "fill" to "Preencher", "all" to "Inteiro"), fitV) { fitV = it; prefs.saverFit = it; markApp(); redraw() }
        var quality = prefs.saverQuality
        optChoice(vid, "Qualidade (vídeos da Apple TV)", "1080p gasta menos dados; 4K só em tablets potentes.", listOf("1080" to "1080p (H.264)", "4k" to "4K"), quality) { quality = it; markApp(); redraw() }
        label(vid, "Lista de vídeos (endereço de um ficheiro .json ou .txt)")
        val listIn = input(prefs.saverList, "https://…/videos.json", InputType.TYPE_TEXT_VARIATION_URI, app = true); vid.addView(listIn)
        label(vid, "Fonte Apple TV (opcional: endereço do resources-atv….tar)")
        val src = input(prefs.saverSource, "", InputType.TYPE_TEXT_VARIATION_URI, app = true); vid.addView(src)
        label(vid, "Vídeos extra (um endereço por linha)")
        val extraIn = input(prefs.saverExtra, "https://…/clip.mp4", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI, app = true).apply { setSingleLine(false); minLines = 3; gravity = Gravity.TOP }
        vid.addView(extraIn)
        val vstatus = kit.text(if (Aerials.count > 0) Tr.s("${Aerials.count} vídeos", "${Aerials.count} videos") else "", 13f, kit.muted)
        vid.addView(pillBtn("Atualizar listas", "mdi:refresh") {
            prefs.saverSource = src.text.toString(); prefs.saverQuality = quality; prefs.saverList = listIn.text.toString()
            vstatus.text = Tr.s("A descarregar…", "Downloading…")
            Aerials.loadList(this, prefs.saverList) { Aerials.load(this, prefs.saverSource, quality, force = true) { _ -> vstatus.text = if (Aerials.count > 0) Tr.s("${Aerials.count} vídeos", "${Aerials.count} videos") else "✘ " + Tr.t(Aerials.error ?: Tr.s("sem vídeos", "no videos")) } }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(14) })
        vid.addView(vstatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        // clipes guardados no tablet, com tamanho e escolha
        val pk = panel()
        h3(pk, Tr.t("Clipes no tablet"), true)
        para(pk, "Descarrega o pacote uma vez (cerca de 530 MB, melhor com Wi-Fi) e os clipes passam do disco, sem rede. Por omissão é o snoopy.saver.zip do repositório hitnology/snoopy.")
        // pasta do tablet com os teus ficheiros
        label(pk, "Pasta no tablet")
        val folderTx = kit.text(Pack.folderName(this).ifBlank { "Nenhuma pasta escolhida" }, 14f, kit.fg)
        pk.addView(folderTx, LinearLayout.LayoutParams(-1, -2))
        val frow = LinearLayout(this)
        frow.addView(pillBtn("Escolher pasta…", "mdi:folder-open-outline") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_FOLDER)
        })
        frow.addView(pillBtn("Limpar", "mdi:close") { prefs.clipFolder = ""; Pack.refreshFolder(); Aerials.setLocal(this); redraw() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        pk.addView(frow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(10) })
        para(pk, "Copia os vídeos para uma pasta do tablet (por exemplo em Filmes) e escolhe-a aqui: a app lê os .mov e .mp4 dela (e de subpastas) com a mesma regra e a mesma lista abaixo.")
        label(pk, "Ou descarrega um pacote zip")
        val packUrl = input(prefs.packUrl, Pack.DEFAULT_URL, InputType.TYPE_TEXT_VARIATION_URI, app = true); pk.addView(packUrl)
        packStatus = kit.text(Pack.describe(this), 13f, kit.muted)
        val prow = LinearLayout(this)
        prow.addView(pillBtn("Descarregar clipes", "mdi:download") {
            prefs.packUrl = packUrl.text.toString().ifBlank { Pack.DEFAULT_URL }
            Pack.download(this, prefs.packUrl) { packStatus?.text = Pack.describe(this); if (section == "saver") redraw() }
            packStatus?.text = Pack.describe(this)
        })
        prow.addView(pillBtn("Apagar clipes", "mdi:delete-outline") { Pack.delete(this); prefs.clipChoices = emptyMap(); redraw() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
        pk.addView(prow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(14) })
        pk.addView(packStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })

        val files = Pack.files(this)
        var choices = prefs.clipChoices.toMutableMap()
        var minMb = prefs.clipMinMb
        if (files.isNotEmpty()) {
            divider(pk)
            label(pk, "Regra: ignorar clipes com menos de (MB) e os de intro, loop ou outro")
            val minIn = input(minMb.toString(), "8", InputType.TYPE_CLASS_NUMBER, app = true); pk.addView(minIn)
            minIn.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { minMb = (e?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 500) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            val brow = LinearLayout(this)
            brow.addView(pillBtn("Aplicar regra", "mdi:filter-outline") { choices.clear(); prefs.clipMinMb = minMb; prefs.clipChoices = emptyMap(); redraw() })
            brow.addView(pillBtn("Todos", null) { files.forEach { choices[it.name] = true }; prefs.clipChoices = choices; redraw() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
            brow.addView(pillBtn("Nenhum", null) { files.forEach { choices[it.name] = false }; prefs.clipChoices = choices; redraw() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(10) })
            pk.addView(brow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(14) })
            val on = files.count { choices[it.name] ?: Pack.defaultOn(it, minMb) }
            para(pk, Tr.s("$on de ${files.size} clipes vão passar.", "$on of ${files.size} clips will play."))
            files.forEach { f ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(8)) }
                val mb = f.length() / 1_000_000.0
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(kit.text(f.name.removeSuffix(".mov"), 15f, kit.fg, 500).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
                    addView(kit.text(String.format(java.util.Locale.US, "%.1f MB", mb) + Regex("loop|intro|outro", RegexOption.IGNORE_CASE).find(f.name)?.let { " · " + it.value.lowercase() } ?: "", 12f, kit.muted))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = d(12) })
                row.addView(Toggle(kit, choices[f.name] ?: Pack.defaultOn(f, minMb)) { v -> choices[f.name] = v; prefs.clipChoices = choices; Aerials.setLocal(this) })
                pk.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }

        saveApp = {
            prefs.timeoutSec = lockV; prefs.saverSecRaw = saverV
            prefs.sensitivity = sens.progress + 1
            prefs.saverVideo = saverVideo; prefs.saverFit = fitV; prefs.saverQuality = quality; prefs.saverSource = src.text.toString(); prefs.saverList = listIn.text.toString()
            prefs.packUrl = packUrl.text.toString().ifBlank { Pack.DEFAULT_URL }; prefs.saverExtra = extraIn.text.toString(); 
            if (files.isNotEmpty()) prefs.clipMinMb = minMb
            Aerials.reparse(this, quality)
        }
    }

    // ---------------------------------------------------------------- Aparência

    private val CARD_COLORS = listOf("#f5efe1", "#efe3c8", "#e6dcc2", "#d6e2e8", "#c3d3da", "#d9d3e3", "#e6d3d6", "#cfe0cc", "#bccfc3", "#cbb9a8", "#bcc6d3", "#d8ccb4", "#cdc6d8", "#b9d3cd")
    private val ACCENTS = listOf("#f6b83f", "#f5a524", "#ff9f43", "#ff7a59", "#ff6b81", "#f472b6", "#a78bfa", "#7c8cff", "#4aa8ff", "#2dd4bf", "#4ade80", "#a3e635")
    private val AMBIENT = setOf("nebulosa", "floresta", "ardosia", "vinho", "meia_noite", "brasa")
    private val VIVID = setOf("ambar", "coral", "lima", "azul", "violeta", "magenta", "turquesa", "por_do_sol")

    private fun swatchRow(p: LinearLayout, colors: List<String>, cur: String, key: String) {
        val g = Grid(this, d(44), d(44), d(10))
        colors.forEach { c ->
            val sel = c.equals(cur, true)
            g.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor(c)); setStroke(d(if (sel) 3 else 1), if (sel) kit.fg else kit.soft) }
                isClickable = true; setOnClickListener { setD(key, c); redraw() }
            })
        }
        p.addView(g, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
    }

    private fun themeGrid(p: LinearLayout, title: String, keys: List<String>, cur: String, key: String) {
        if (keys.isEmpty()) return
        h3(p, title)
        val g = Grid(this, d(86), d(92), d(12))
        keys.forEach { k ->
            val t = Themes.get(k)
            g.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; isClickable = true
                setOnClickListener { setD(key, k); redraw() }
                addView(Swatch(kit, t, k == cur), LinearLayout.LayoutParams(d(64), d(64)))
                addView(kit.text(Tr.t(t.name), 12f, kit.fg).apply { setSingleLine() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(5) })
            })
        }
        p.addView(g, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
    }

    private fun buildLook() {
        val a = panel()
        optChoice(a, Tr.t("Idioma"), "Idioma de todo o painel.", listOf("pt" to "Português", "en" to "English"), draft.optString("language", "pt")) { setD("language", it); redraw() }
        optChoice(a, Tr.t("Disposição"), "Como as divisões e os dispositivos aparecem.", listOf("grid" to Tr.t("Grelha"), "list" to Tr.t("Lista")), draft.optString("layout", "grid")) { setD("layout", it); redraw() }
        optChoice(a, Tr.t("Estilo do vidro"), "Atual: enchimento liso. Suave: gradiente com aro de luz e brilho. Liquid Glass: aro duplo, luz de canto, lente com relevo e desfoque real nas janelas (Android 12 ou mais recente). Só deste dispositivo.",
            listOf("0" to Tr.t("Atual"), "1" to Tr.t("Suave"), "2" to "Liquid Glass"), Glass.level.toString()) { prefs.glassLevel = it.toInt(); Glass.level = it.toInt(); relook() }
        optToggle(a, Tr.t("Animações"), "Transições ao mudar de ecrã, a lente da dock e a abertura dos cartões. Desliga para um painel mais leve.", bool("animations", true)) { setD("animations", it) }

        val c = panel()
        h3(c, Tr.t("Cor dos cartões"), true); para(c, "Superfície suave dos cartões ligados.")
        swatchRow(c, CARD_COLORS, draft.optString("card_color", "#f5efe1"), "card_color")
        h3(c, Tr.t("Cor de destaque")); para(c, "Ícones acesos, pontos das luzes, sliders e contadores.")
        swatchRow(c, ACCENTS, draft.optString("accent_color", "#f6b83f"), "accent_color")

        val f = panel()
        h3(f, Tr.t("Fundo"), true); para(f, "O ambiente por trás dos cartões. Os fundos claros põem todo o painel em modo claro.")
        val auto = draft.optString("appearance", "fixed") == "auto"
        optChoice(f, Tr.t("Modo"), if (auto) "Usa o fundo de dia entre o nascer e o pôr do sol (sun.sun) e o de noite no resto do tempo." else "Um fundo sempre igual, ou um para o dia e outro para a noite.",
            listOf("fixed" to Tr.t("Fixo"), "auto" to Tr.t("Automático")), if (auto) "auto" else "fixed") { setD("appearance", it); redraw() }
        val all = Themes.all.map { it.key }
        val light = Themes.all.filter { it.light }.map { it.key }.toSet()
        val dark = all.filter { it !in light && it !in VIVID && it !in AMBIENT }
        val amb = all.filter { it in AMBIENT }; val viv = all.filter { it in VIVID }; val lig = all.filter { it in light }
        if (auto) {
            themeGrid(f, Tr.t("De dia"), lig + viv + amb + dark, draft.optString("theme_day", "linho"), "theme_day")
            themeGrid(f, Tr.t("À noite"), amb + dark + viv + lig, draft.optString("theme_night", "ameixa"), "theme_night")
        } else {
            val cur = draft.optString("theme", "ameixa")
            themeGrid(f, Tr.t("Ambiente"), amb, cur, "theme"); themeGrid(f, Tr.t("Vivos"), viv, cur, "theme")
            themeGrid(f, Tr.t("Escuros"), dark, cur, "theme"); themeGrid(f, Tr.t("Claros"), lig, cur, "theme")
        }
    }

    // ---------------------------------------------------------------- Início

    private fun buildHome() {
        val p = panel()
        h3(p, Tr.t("Ecrã inicial"), true); para(p, "O que aparece no início.")
        optToggle(p, Tr.t("Meteorologia"), "Pílula com a temperatura e o estado do tempo. Toca nela para ver a previsão.", bool("show_weather", true)) { setD("show_weather", it) }
        val wx = Ha.states.keys.filter { it.startsWith("weather.") }.sorted()
        optChoice(p, Tr.t("Fonte do tempo"), "Automático usa a primeira entidade de tempo do Home Assistant ou, se não houver, a Open-Meteo para a localização da casa.",
            listOf("" to Tr.t("Automático")) + wx.map { it to Fmt.name(it) } + listOf("open-meteo" to "Open-Meteo"), draft.optString("weather_entity", "")) { setD("weather_entity", it); redraw() }
        optToggle(p, Tr.t("Pessoas"), "Quem está em casa, ao lado da data.", bool("home_people", false)) { setD("home_people", it) }
        optChoice(p, Tr.t("Disposição do início"), "Relógio grande com o estado da casa ao lado; duas colunas; barra lateral de vidro; ou a disposição original.",
            listOf("top" to Tr.t("Relógio grande"), "columns" to Tr.t("Duas colunas"), "sidebar" to Tr.t("Barra lateral"), "classic" to Tr.t("Clássica")), draft.optString("home_layout", "top")) { setD("home_layout", it); redraw() }
        optToggle(p, Tr.t("Estado da casa"), "Luzes acesas, outros aparelhos ligados e botões Acender/Apagar luzes.", bool("show_status", true)) { setD("show_status", it) }
        optToggle(p, Tr.t("Cenas"), "Faixa de cenas no início.", bool("show_scenes", true)) { setD("show_scenes", it) }
        optToggle(p, Tr.t("Cartões de resumo"), "Clima, Segurança e A tocar.", bool("show_summary", true)) { setD("show_summary", it) }
        optToggle(p, Tr.t("Relógio de 24 horas"), "Desliga para mostrar AM/PM.", bool("clock_24h", true)) { setD("clock_24h", it) }
    }

    // ---------------------------------------------------------------- Divisões

    private fun buildRooms() {
        val rooms = Ha.home?.rooms
        val p = panel()
        h3(p, Tr.t("Divisões"), true); para(p, "Criadas automaticamente a partir das áreas do Home Assistant. Ordena, esconde e escolhe o tamanho de cada uma na Casa 3D.")
        if (rooms == null) { para(p, "Liga primeiro ao Home Assistant (secção App)."); return }
        val byId = rooms.associateBy { it.id }
        val order = (list("room_order").filter { it in byId } + rooms.map { it.id }.filter { it !in list("room_order") }).toMutableList()
        val sizes = draft.optJSONObject("house_size") ?: JSONObject()
        val hiddenRooms = list("hidden_rooms")
        order.forEachIndexed { k, id ->
            val r = byId[id]!!
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(8)) }
            row.addView(FrameLayout(this).apply {
                background = kit.circle(kit.soft); addView(kit.icon(r.icon, kit.fg, 20), FrameLayout.LayoutParams(d(20), d(20), Gravity.CENTER))
            }, LinearLayout.LayoutParams(d(40), d(40)).apply { marginEnd = d(14) })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(kit.text(r.name, 16f, kit.fg, 500)); addView(kit.text(Tr.pl(r.devices.size, "dispositivo", "dispositivos", "device", "devices"), 12f, kit.muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val seg = LinearLayout(this).apply { background = kit.rounded(kit.soft, 14); setPadding(d(3), d(3), d(3), d(3)) }
            listOf(0 to "A", 1 to "P", 2 to "M", 3 to "G").forEach { (v, l) ->
                val sel = sizes.optInt(id, 0) == v
                seg.addView(kit.text(l, 13f, if (sel) kit.ink else kit.fg, 600).apply {
                    gravity = Gravity.CENTER; background = if (sel) kit.surfaceOn(11) else null; isClickable = true
                    setOnClickListener { val o = JSONObject(sizes.toString()); if (v == 0) o.remove(id) else o.put(id, v); setD("house_size", o); redraw() }
                }, LinearLayout.LayoutParams(d(34), d(32)))
            }
            row.addView(seg, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = d(12) })
            fun move(dir: Int) { val i = order.indexOf(id); val j = i + dir; if (j in order.indices) { order.removeAt(i); order.add(j, id); setList("room_order", order); redraw() } }
            row.addView(kit.roundButton("mdi:chevron-up", 36, kit.soft, kit.fg) { move(-1) }.apply { alpha = if (k == 0) 0.3f else 1f })
            row.addView(kit.roundButton("mdi:chevron-down", 36, kit.soft, kit.fg) { move(1) }.apply { alpha = if (k == order.size - 1) 0.3f else 1f }, LinearLayout.LayoutParams(d(36), d(36)).apply { marginStart = d(6); marginEnd = d(14) })
            row.addView(Toggle(kit, id !in hiddenRooms) { on -> val l = list("hidden_rooms"); if (on) l.remove(id) else if (id !in l) l.add(id); setList("hidden_rooms", l) })
            p.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (k < order.size - 1) p.addView(View(this).apply { setBackgroundColor(kit.soft) }, LinearLayout.LayoutParams(-1, d(1)))
        }
        para(p, "Tamanho na Casa 3D: A = automático, P = pequeno, M = médio, G = grande. Os pisos vêm dos pisos do Home Assistant.")

        val hidden = list("hidden")
        val hiddenDevs = rooms.flatMap { r -> r.devices.filter { it.id in hidden }.map { it to r.name } }
        if (hiddenDevs.isNotEmpty()) {
            val h = panel()
            h3(h, Tr.t("Dispositivos escondidos"), true)
            hiddenDevs.forEach { (dev, room) ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(8)) }
                row.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(kit.text(dev.name, 16f, kit.fg, 500)); addView(kit.text(room, 12f, kit.muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(pillBtn(Tr.t("Mostrar"), null) { val l = list("hidden"); l.remove(dev.id); setList("hidden", l); redraw() })
                h.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }

    // ---------------------------------------------------------------- Entidades

    private fun shown(e: RoomEntity): Boolean { val hidden = list("hidden"); return if (e.primary) e.id !in hidden else e.id in list("shown") && e.id !in hidden }

    private fun buildEntities() {
        val rooms = Ha.home?.rooms
        val p = panel()
        h3(p, Tr.t("Entidades"), true)
        para(p, "Escolhe o que aparece no painel. As entidades principais são os mosaicos criados automaticamente; as secundárias podes mostrá-las como mosaicos próprios.")
        if (rooms == null) { para(p, "Esta secção precisa da integração Pine3 View e da ligação ao HA."); return }
        val q = input(entQuery, Tr.t("Procurar entidades…"), InputType.TYPE_CLASS_TEXT)
        p.addView(q, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14) })
        optToggle(p, Tr.t("Mostrar diagnóstico e escondidas no HA"), "Inclui entidades de diagnóstico, de configuração e as escondidas no Home Assistant.", entAll) { entAll = it; redraw() }
        val groups = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mainBox.addView(groups, LinearLayout.LayoutParams(-1, -2))
        fun fill() {
            groups.removeAllViews()
            val query = entQuery.trim().lowercase()
            rooms.forEach { r ->
                val list = r.entities.filter { e -> (entAll || (!e.category && !e.haHidden) || shown(e)) && (query.isEmpty() || e.name.lowercase().contains(query) || e.id.contains(query)) }
                if (list.isEmpty()) return@forEach
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = kit.glass(28); setPadding(d(26), d(20), d(26), d(14)) }
                val hd = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                hd.addView(kit.icon(r.icon, kit.fg, 22), LinearLayout.LayoutParams(d(22), d(22)).apply { marginEnd = d(10) })
                hd.addView(kit.text(r.name, 19f, kit.fg, 500, display = true))
                hd.addView(kit.text("   " + r.entities.count { shown(it) } + Tr.t(" de ") + r.entities.size, 14f, kit.muted))
                box.addView(hd)
                list.forEach { e ->
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(9), 0, d(9)); alpha = if (shown(e)) 1f else 0.55f }
                    row.addView(kit.icon(Fmt.icon(e.id, Fmt.domain(e.id)), kit.fg, 20), LinearLayout.LayoutParams(d(20), d(20)).apply { marginEnd = d(14) })
                    row.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
                        addView(kit.text(e.name, 15f, kit.fg, 500).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }); addView(kit.text(e.id, 12f, kit.muted).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(kit.text(Tr.t(if (e.primary) "Principal" else if (e.category) "Diagnóstico" else if (e.haHidden) "Escondida no HA" else "Secundária"), 12f, if (e.primary) kit.accent else kit.muted, 600).apply {
                        background = kit.rounded(kit.soft, 20); setPadding(d(10), d(3), d(10), d(3))
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = d(14) })
                    row.addView(Toggle(kit, shown(e)) { on ->
                        val hidden = list("hidden"); val sh = list("shown")
                        if (e.primary) { if (on) hidden.remove(e.id) else if (e.id !in hidden) hidden.add(e.id) }
                        else { if (on) { if (e.id !in sh) sh.add(e.id); hidden.remove(e.id) } else sh.remove(e.id) }
                        setList("hidden", hidden); setList("shown", sh); row.alpha = if (on) 1f else 0.55f
                    })
                    box.addView(row)
                }
                groups.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(16) })
            }
        }
        fill()
        q.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { entQuery = s?.toString().orEmpty(); fill() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    // ---------------------------------------------------------------- Planta 3D, Navegação, Sistema

    private fun buildPlan() {
        val p = panel()
        h3(p, Tr.t("Planta 3D"), true)
        para(p, "A casa 3D e o editor de planta ainda não estão na app nativa. Edita a planta no painel web: as alterações aparecem aqui quando a casa 3D for acrescentada.")
    }

    private fun buildNav() {
        val p = panel()
        h3(p, "Dock", true); para(p, "Botões da barra de navegação. A Casa está sempre presente.")
        val dock = list("dock").ifEmpty { mutableListOf("home", "rooms", "house", "scenes") }
        listOf("rooms" to ("Divisões" to "Todas as divisões numa grelha."), "house" to ("Casa 3D" to "Vista 3D da casa, por pisos."), "scenes" to ("Cenas" to "Cenas e scripts."),
            "climate" to ("Clima" to "Ar condicionado, termóstatos e temperatura por divisão."), "security" to ("Segurança" to "Portas, janelas, fechaduras e câmaras.")).forEach { (k, v) ->
            optToggle(p, Tr.t(v.first), v.second, k in dock) { on ->
                val l = list("dock").ifEmpty { mutableListOf("home", "rooms", "house", "scenes") }
                if (on && k !in l) l.add(k) else if (!on) l.remove(k)
                if ("home" !in l) l.add(0, "home")
                setList("dock", listOf("home", "rooms", "house", "scenes", "climate", "security").filter { it in l })
            }
        }
    }

    private fun buildSystem() {
        val home = Ha.home
        val p = panel()
        h3(p, Tr.t("Sistema"), true)
        val facts = Grid(this, d(180), d(70), d(14))
        fun fact(k: String, v: String) = facts.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = kit.rounded(kit.soft, 18); setPadding(d(16), d(12), d(16), d(12))
            addView(kit.text(Tr.t(k), 12f, kit.muted)); addView(kit.text(v, 20f, kit.fg, 500, display = true))
        })
        fact(Tr.t("Versão"), Tr.s("nativa 0.1", "native 0.1")); fact("Modo", if (home != null) Tr.t("Integração") else Tr.t("Sem ligação"))
        fact(Tr.t("Divisões"), "${home?.rooms?.size ?: "—"}"); fact("Dispositivos", "${home?.rooms?.sumOf { it.devices.size } ?: "—"}")
        p.addView(facts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(16) })
        val row = LinearLayout(this)
        row.addView(pillBtn(Tr.t("Reconstruir agora"), "mdi:refresh") { Ha.configure(this); showToast(Tr.s("A atualizar…", "Updating…")) })
        p.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(20) })
    }
}

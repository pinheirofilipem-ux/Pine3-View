package pt.pine3.view

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Pine3 View nativo: a mesma casa do painel web, desenhada com views do Android. */
class MainActivity : Activity(), Nav {

    private companion object {
        const val SETTLE_MS = 250L
        const val FADE_MS = PanelState.FADE_MS
        const val UPDATE_DEBOUNCE_MS = 120L
        /** Os ecrãs da dock, pela ordem do painel web: chave, ícone, nome. */
        val DOCK_ITEMS = listOf(
            Triple("home", "mdi:home-outline", "Casa"), Triple("rooms", "mdi:view-grid-outline", "Divisões"),
            Triple("house", "mdi:cube-outline", "Casa 3D"), Triple("scenes", "mdi:creation-outline", "Cenas"),
            Triple("climate", "mdi:thermometer", "Clima"), Triple("security", "mdi:shield-outline", "Segurança"),
        )
    }

    private lateinit var prefs: Prefs
    private lateinit var kit: Kit
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private var themeKey = ""
    private var dockKeysSig = ""
    private lateinit var dockHost: FrameLayout
    private var dockView: DockView? = null
    private var animationsOn = true
    private lateinit var blackout: View
    private lateinit var banner: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var page: Page? = null
    private var tab = "home"
    private var roomId: String? = null
    private var resumed = false
    private var dirty = false
    private var shownHome: Home? = null
    private var shownKey = ""

    private val reveal = Runnable {
        if (blackout.visibility == View.VISIBLE && !PanelState.sleeping) {
            blackout.animate().alpha(0f).setDuration(FADE_MS).withEndAction { blackout.visibility = View.GONE; blackout.alpha = 1f }.start()
        }
    }
    private val flush = Runnable {
        dirty = false
        val home = Ha.home
        if (home != null && lookChanged(home)) rebuild() else page?.update()
    }

    private val haListener = object : Ha.Listener {
        override fun onStates(changed: Set<String>) {
            if (!dirty) { dirty = true; handler.postDelayed(flush, UPDATE_DEBOUNCE_MS) }
        }
        override fun onHome() = rebuild()
        override fun onStatus() = updateBanner()
        override fun onBell(id: String, type: String, attrs: org.json.JSONObject) = bellEvent(id, type, attrs)
    }

    private var bell: BellDialog? = null
    private lateinit var gear: View
    private lateinit var editBar: LinearLayout

    /** O menu "⋮": editar o ecrã (início, divisões ou uma divisão) e as definições. */
    private fun showMenu() {
        val canEdit = Ha.home != null && (tab == "home" || tab == "rooms" || roomId != null)
        android.widget.PopupMenu(this, gear).apply {
            if (canEdit) menu.add(Tr.t("Editar ecrã")).setOnMenuItemClickListener { startEdit(); true }
            menu.add(Tr.t("Definições")).setOnMenuItemClickListener { openSettings(); true }
        }.show()
    }

    private fun startEdit() {
        val home = Ha.home ?: return
        Lay.begin(home)
        dockHost.visibility = View.GONE; gear.visibility = View.GONE; editBar.visibility = View.VISIBLE
        rebuild()
    }

    private fun finishEdit(save: Boolean) {
        if (Lay.edit == null) return
        Lay.finish(Ha.home, save)
        dockHost.visibility = View.VISIBLE; gear.visibility = View.VISIBLE; editBar.visibility = View.GONE
        rebuild()
    }

    // ecrã de descanso
    private var saver: SaverView? = null
    private var lastActivity = System.currentTimeMillis()
    private val saverTick = object : Runnable {
        override fun run() {
            val secs = prefs.saverSec(Ha.home?.settings?.optInt("screensaver", 0) ?: 0)
            if (secs > 0 && Lay.edit == null && saver == null && resumed && !PanelState.sleeping && bell?.isShowing != true && System.currentTimeMillis() - maxOf(lastActivity, PanelState.lastTouch) > secs * 1000L) showSaver()
            handler.postDelayed(this, 1_000)
        }
    }
    /** Se os vídeos não carregarem, o ecrã de descanso volta ao relógio escuro (sem insistir). */
    private var saverVideoFailed = false

    private fun showSaver() {
        PokeDialog.closeIdle() // o screensaver fica por cima de tudo: as janelas normais fecham primeiro
        val video = prefs.saverVideo && !saverVideoFailed
        Aerials.setExtra(prefs.saverExtra); Aerials.setLocal(this)
        val s = SaverView(kit, Ha.home?.settings?.optBoolean("clock_24h", true) ?: true, video, null, "black", prefs.saverFit) { hideSaver() }
        if (video) {
            val begin = { if (Aerials.count > 0) s.startVideo() else if (saver === s) { saverVideoFailed = true; hideSaver(); showSaver() } }
            // a lista própria e a fonte da Apple (se houver) carregam em paralelo; começa quando a primeira trouxer vídeos
            var pending = 2
            val done = { if (--pending == 0) begin() else if (Aerials.count > 0) s.startVideo() }
            Aerials.loadList(this, prefs.saverList) { done() }
            Aerials.load(this, prefs.saverSource, prefs.saverQuality) { done() }
        }
        saver = s
        root.addView(s, root.indexOfChild(blackout), FrameLayout.LayoutParams(-1, -1))
        s.alpha = 0f; s.animate().alpha(1f).setDuration(600).start()
    }
    private fun hideSaver() {
        lastActivity = System.currentTimeMillis()
        val s = saver ?: return
        saver = null
        s.animate().alpha(0f).setDuration(300).withEndAction { root.removeView(s) }.start()
    }

    private fun bellEvent(id: String, type: String, attrs: org.json.JSONObject) {
        if (!resumed) return // fica em Ha.pendingBell; abre-se no onResume
        if (type == "ring" || type == "floor_call") {
            Ha.pendingBell = null
            if (!prefs.bellPopup) return
            bell?.dismiss(); hideSaver()
            val cam = attrs.optString("camera_entity")
            val rel = attrs.optString("release_entity").takeIf { Ha.states.containsKey(it) }
            val doors = if (rel != null) listOf(rel) else doorsFor(cam)
            bell = BellDialog(this, kit, id, type, attrs, doors) { c, n -> camViewer(c, n) }.also { it.show() }
        } else bell?.onEvent(id, type, attrs)
    }

    private fun doorsFor(camId: String): List<String> = doorsForCamera(camId)

    // Dois dedos premidos > 2 s abrem as definições.
    private val openSettingsRun = Runnable { openSettings() }
    private var gx = 0f
    private var gy = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiScale.apply(this)
        prefs = Prefs(this)
        Glass.level = prefs.glassLevel
        kit = Kit(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this).apply { clipChildren = false }
        root.background = ThemeBackground(Themes.get("ameixa"))
        // swipe left/right: next/previous view of the dock (not inside a room, in edit mode or with a window open)
        content = SwipeFrame(this, { Lay.edit == null && roomId == null && Ha.home != null && dockKeys().size > 1 }) { dir -> swipeTab(dir) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        dockHost = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        root.addView(dockHost, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = kit.dp(22) })

        banner = kit.text("", 14f, kit.fg, 500).apply {
            background = kit.rounded(Color.argb(190, 120, 40, 50), 20)
            setPadding(kit.dp(16), kit.dp(8), kit.dp(16), kit.dp(8))
            visibility = View.GONE
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = kit.dp(10) })

        gear = kit.roundButton("mdi:dots-vertical", 44, kit.soft, kit.fg) { showMenu() }
        root.addView(gear, FrameLayout.LayoutParams(kit.dp(44), kit.dp(44), Gravity.TOP or Gravity.END).apply { topMargin = kit.dp(16); rightMargin = kit.dp(18) })

        // barra do modo de edição (substitui a dock enquanto se edita)
        editBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        editBar.addView(kit.text(Tr.t("Arrasta para mover. Toca em − para esconder e nas setas para duplicar o tamanho."), 13f, kit.muted).apply { gravity = Gravity.CENTER })
        val eb = LinearLayout(this)
        eb.addView(kit.pill(Tr.t("Cancelar"), null, false) { finishEdit(false) })
        eb.addView(kit.pill(Tr.t("Concluído"), "mdi:check", true) { finishEdit(true) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(12) })
        editBar.addView(eb, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) })
        root.addView(editBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = kit.dp(22) })
        Lay.onChange = { rebuild() }

        blackout = View(this).apply { setBackgroundColor(Color.BLACK); isClickable = true }
        root.addView(blackout, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        buildDock()
        requestRuntimePermissions()
        if (prefs.haUrl.isBlank() || prefs.haToken.isBlank()) openSettings()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        dirty = false
        saverVideoFailed = false
        if (Glass.level != prefs.glassLevel) { Glass.level = prefs.glassLevel; buildDock(); shownKey = "" } // o estilo do vidro mudou nas definições
        cover()
        hideSystemBars()
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        PanelState.listener = { applySleeping(it) }
        applySleeping(PanelState.sleeping)
        Ha.listeners += haListener
        lastActivity = System.currentTimeMillis()
        handler.removeCallbacks(saverTick); handler.postDelayed(saverTick, 1_000)
        Ha.configure(this)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) MotionService.start(this)
        MotionService.poke()
        rebuild()
        updateBanner()
        // o ecrã acordou por causa da campainha: o evento chegou antes de a atividade estar pronta
        Ha.pendingBell?.let { b -> if (System.currentTimeMillis() - b.at < 20_000) handler.postDelayed({ bellEvent(b.id, b.type, b.attrs) }, 400) else Ha.pendingBell = null }
    }

    override fun onPause() {
        resumed = false
        bell?.dismiss()
        cover()
        handler.removeCallbacks(openSettingsRun)
        handler.removeCallbacks(saverTick)
        handler.removeCallbacks(flush); dirty = false
        Ha.listeners -= haListener
        PanelState.listener = null
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { hideSystemBars(); scheduleReveal() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (Lay.edit != null) finishEdit(false) else if (roomId != null) back() }

    // --- Navegação

    override fun openRoom(room: Room) { roomId = room.id; rebuild() }
    override fun back() { roomId = null; rebuild() }
    override fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }

    private fun dockKeys(): List<String> {
        val chosen = Ha.home?.settings?.optJSONArray("dock")
        val list = if (chosen == null) listOf("home", "rooms", "house", "scenes") else (0 until chosen.length()).map { chosen.getString(it) }
        return DOCK_ITEMS.map { it.first }.filter { it == "home" || it in list }
    }

    private fun showTab(key: String) {
        if (Lay.edit != null) finishEdit(false)
        tab = key; roomId = null
        dockView?.setActive(dockKeys().indexOf(key).coerceAtLeast(0), animate = true)
        rebuild()
    }

    private var slideDir = 0

    /** A swipe on the page: the next (1) or previous (-1) view in the dock's order, wrapping around. */
    private fun swipeTab(dir: Int) {
        val keys = dockKeys()
        val i = keys.indexOf(tab).coerceAtLeast(0)
        val next = keys[(i + dir + keys.size) % keys.size]
        slideDir = dir
        showTab(next)
        slideDir = 0
    }

    /** Câmara ao vivo + as portas ao lado dela. */
    private fun camViewer(id: String, name: String) { CamViewer(this, kit, id, name, Doors.forCamera(id)).show() }

    override fun openScreen(key: String) {
        if (key == "weather") { ForecastSheet(this, kit, Ha.home?.settings?.optString("weather_entity", "").orEmpty()).show(); return }
        if (key in DOCK_ITEMS.map { it.first }) showTab(key)
    }

    override fun openDevice(id: String) {
        val dev = Ha.home?.rooms?.flatMap { it.devices }?.firstOrNull { it.id == id } ?: devOf(id)
        if (Fmt.domain(id) == "camera") { camViewer(id, dev.name); return }
        DeviceSheet(this, kit, id, dev).show()
    }

    private fun buildDock() {
        dockHost.removeAllViews()
        val keys = dockKeys()
        val d = DockView(kit, keys.map { k -> DOCK_ITEMS.first { it.first == k }.let { Triple(Tr.t(it.third), it.second, it.first) } }, animationsOn) { showTab(keys[it]) }
        dockHost.addView(d)
        dockView = d
        d.setActive(keys.indexOf(tab).coerceAtLeast(0), animate = false)
        (dockHost.layoutParams as FrameLayout.LayoutParams).bottomMargin = kit.dp(if (kit.compact) 14 else 22)
    }

    /** (Re)constrói a página atual a partir do modelo. Só é chamada quando o modelo muda ou se navega. */
    private fun rebuild() {
        val home = Ha.home
        if (home == null) {
            val msg = Ha.homeError ?: Ha.status
            show(Placeholder(kit, "Pine3 View", msg))
            shownHome = null; shownKey = ""
            return
        }
        val anim = home.settings.optBoolean("animations", true)
        Elastic.on = anim
        if (anim != animationsOn) { animationsOn = anim; buildDock() }
        kit.cream = home.color("card_color", kit.cream)
        kit.accent = home.color("accent_color", kit.accent)
        if (applyLook(home)) { shownKey = "" }
        if (dockKeysSig != dockKeys().joinToString()) { dockKeysSig = dockKeys().joinToString(); buildDock() }
        val room = roomId?.let { id -> home.rooms.firstOrNull { it.id == id } }
        kit.list = home.settings.optString("layout", "grid") == "list"
        val key = (if (Lay.edit != null) "edit${Lay.version}:" else "") + if (room != null) "room:${room.id}" else "tab:$tab:" + home.settings.optString("home_layout") + home.settings.optString("layout") + home.settings.optString("dock") + home.settings.optBoolean("home_people") + home.settings.optBoolean("show_summary", true) + home.settings.optBoolean("show_status", true) + home.settings.optBoolean("show_scenes", true) + home.settings.optBoolean("show_weather", true) + home.settings.optBoolean("clock_24h", true)
        if (shownHome === home && shownKey == key && page != null) { page?.update(); return }
        val p: Page = when {
            room != null -> RoomPage(kit, room, this)
            tab == "rooms" -> RoomsPage(kit, home, this)
            tab == "house" -> HousePage(kit, home, this)
            tab == "scenes" -> ScenesPage(kit, home, this)
            tab == "climate" -> ClimatePage(kit, home, this)
            tab == "security" -> SecurityPage(kit, home, this)
            else -> HomePage(kit, home, this)
        }
        val animate = animationsOn && shownKey.isNotEmpty() && shownKey != key
        shownHome = home
        shownKey = key
        show(p, animate)
    }

    /** Aplica tema (fixo ou dia/noite) e língua; devolve true se algo mudou (e então a página tem de ser refeita). */
    private fun applyLook(home: Home): Boolean {
        val theme = Themes.pick(home.settings, Ha.states["sun.sun"]?.state, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        val en = home.settings.optString("language", "pt") == "en"
        val changed = theme.key != themeKey || en != Tr.en
        if (changed) {
            themeKey = theme.key; Tr.en = en
            kit.applyTheme(theme)
            root.background = ThemeBackground(theme)
            buildDock()
        }
        return changed
    }

    private fun show(p: Page, animate: Boolean = false) {
        content.removeAllViews()
        page = p
        content.addView(p.view, FrameLayout.LayoutParams(-1, -1))
        p.update()
        if (slideDir != 0) slideIn(p.view, slideDir) else if (animate) enter(p.view)
    }

    /** A page that comes in from the side after a swipe. */
    private fun slideIn(v: View, dir: Int) {
        val ease = android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        v.translationX = dir * kit.dp(90).toFloat(); v.alpha = 0f
        v.animate().translationX(0f).alpha(1f).setDuration(if (animationsOn) 320 else 0).setInterpolator(ease).start()
    }

    /** Entrada de uma página (como o "vin" do painel web): aparece, sobe 14 dp, assenta e perde o desfoque. */
    private fun enter(v: View) {
        val ease = android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        v.alpha = 0f; v.translationY = kit.dp(14).toFloat(); v.scaleX = 0.985f; v.scaleY = 0.985f
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(ease).start()
        Elastic.popGrids(v)
        if (Build.VERSION.SDK_INT >= 31) {
            android.animation.ValueAnimator.ofFloat(10f, 0f).apply {
                duration = 460; interpolator = ease
                addUpdateListener {
                    val r = (it.animatedValue as Float) * kit.density
                    v.setRenderEffect(if (r > 0.5f) android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP) else null)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { v.setRenderEffect(null) } })
                start()
            }
        }
    }

    private fun lookChanged(home: Home): Boolean {
        val theme = Themes.pick(home.settings, Ha.states["sun.sun"]?.state, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        return theme.key != themeKey
    }

    private fun updateBanner() {
        val bad = !Ha.connected && Ha.home != null
        banner.visibility = if (bad) View.VISIBLE else View.GONE
        if (bad) banner.text = Ha.status
    }

    // --- Ecrã: preto + fade ao acordar (suave, sem saltos)

    private fun cover() {
        handler.removeCallbacks(reveal)
        blackout.animate().cancel()
        blackout.alpha = 1f
        blackout.visibility = View.VISIBLE
    }

    private fun scheduleReveal() {
        if (!resumed || (!hasWindowFocus() && PanelState.dialogs == 0) || PanelState.sleeping || blackout.visibility != View.VISIBLE) return
        handler.removeCallbacks(reveal)
        handler.postDelayed(reveal, SETTLE_MS)
    }

    /** Sem permissão de administrador: ecrã preto + brilho no mínimo. */
    private fun setBrightnessOff(off: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (off) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun applySleeping(sleeping: Boolean) {
        if (!sleeping) hideSaver()
        if (sleeping) {
            // desvanece para preto com a mesma duração do acordar; o brilho só desce no fim (e o serviço só bloqueia o ecrã depois)
            handler.removeCallbacks(reveal)
            blackout.animate().cancel()
            if (blackout.visibility != View.VISIBLE) { blackout.alpha = 0f; blackout.visibility = View.VISIBLE }
            blackout.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
                if (PanelState.sleeping) {
                    setBrightnessOff(true)
                    // só agora, com o ecrã já preto, se tira o screensaver (pára o vídeo e a rede): assim o painel nunca aparece entre os dois
                    saver?.let { root.removeView(it); saver = null }
                }
            }.start()
            return
        }
        scheduleReveal()
        setBrightnessOff(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        MotionService.poke()
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastActivity = System.currentTimeMillis()
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacks(openSettingsRun)
                if (ev.pointerCount == 2) { gx = ev.getX(0); gy = ev.getY(0); handler.postDelayed(openSettingsRun, 2000) }
            }
            MotionEvent.ACTION_MOVE -> if (ev.pointerCount == 2) {
                val slop = ViewConfiguration.get(this).scaledTouchSlop * 4
                if (Math.abs(ev.getX(0) - gx) > slop || Math.abs(ev.getY(0) - gy) > slop) handler.removeCallbacks(openSettingsRun)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(openSettingsRun)
        }
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) MotionService.start(this)
    }
}

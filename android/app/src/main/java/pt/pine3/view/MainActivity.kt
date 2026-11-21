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
        const val FADE_MS = 300L
        const val UPDATE_DEBOUNCE_MS = 120L
        const val TAB_HOME = 0
        const val TAB_ROOMS = 1
        const val TAB_HOUSE = 2
        const val TAB_SCENES = 3
    }

    private lateinit var prefs: Prefs
    private lateinit var kit: Kit
    private lateinit var content: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var blackout: View
    private lateinit var banner: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var page: Page? = null
    private var tab = TAB_HOME
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
    private val flush = Runnable { dirty = false; page?.update() }

    private val haListener = object : Ha.Listener {
        override fun onStates(changed: Set<String>) {
            if (!dirty) { dirty = true; handler.postDelayed(flush, UPDATE_DEBOUNCE_MS) }
        }
        override fun onHome() = rebuild()
        override fun onStatus() = updateBanner()
    }

    // Dois dedos premidos > 2 s abrem as definições.
    private val openSettingsRun = Runnable { openSettings() }
    private var gx = 0f
    private var gy = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        kit = Kit(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { background = PlumBackground() }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        dock = LinearLayout(this).apply {
            background = kit.glass(40)
            setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(8))
            elevation = kit.dp(6).toFloat()
        }
        root.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = kit.dp(22) })

        banner = kit.text("", 14f, kit.white, 500).apply {
            background = kit.rounded(Color.argb(190, 120, 40, 50), 20)
            setPadding(kit.dp(16), kit.dp(8), kit.dp(16), kit.dp(8))
            visibility = View.GONE
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = kit.dp(10) })

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
        cover()
        hideSystemBars()
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        PanelState.listener = { applySleeping(it) }
        applySleeping(PanelState.sleeping)
        Ha.listeners += haListener
        Ha.configure(this)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) MotionService.start(this)
        MotionService.poke()
        rebuild()
        updateBanner()
    }

    override fun onPause() {
        resumed = false
        cover()
        handler.removeCallbacks(openSettingsRun)
        handler.removeCallbacks(flush)
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
    override fun onBackPressed() { if (roomId != null) back() }

    // --- Navegação

    override fun openRoom(room: Room) { roomId = room.id; rebuild() }
    override fun back() { roomId = null; rebuild() }
    override fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }

    private fun showTab(t: Int) { tab = t; roomId = null; rebuild(); buildDock() }

    private fun buildDock() {
        dock.removeAllViews()
        listOf(
            Triple(TAB_HOME, "Casa", "mdi:home-outline"), Triple(TAB_ROOMS, "Divisões", "mdi:view-grid-outline"),
            Triple(TAB_HOUSE, "Casa 3D", "mdi:cube-outline"), Triple(TAB_SCENES, "Cenas", "mdi:creation"),
        ).forEach { (t, label, icon) ->
            val sel = t == tab
            val fg = if (sel) kit.ink else kit.white
            dock.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = if (sel) kit.rounded(kit.cream, 30) else null
                setPadding(kit.dp(22), kit.dp(12), kit.dp(24), kit.dp(12))
                isClickable = true
                setOnClickListener { showTab(t) }
                addView(kit.icon(icon, fg, 22).also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = kit.dp(8) })
                addView(kit.text(label, 16f, fg, 500))
            })
        }
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
        kit.cream = home.color("card_color", kit.cream)
        kit.accent = home.color("accent_color", kit.accent)
        val room = roomId?.let { id -> home.rooms.firstOrNull { it.id == id } }
        val key = if (room != null) "room:${room.id}" else "tab:$tab"
        if (shownHome === home && shownKey == key && page != null) { page?.update(); return }
        val p: Page = when {
            room != null -> RoomPage(kit, room, this)
            tab == TAB_ROOMS -> RoomsPage(kit, home, this)
            tab == TAB_HOUSE -> Placeholder(kit, "Casa 3D", "A casa 3D desenhada em Canvas vem na fase 2.")
            tab == TAB_SCENES -> ScenesPage(kit, home)
            else -> HomePage(kit, home, this)
        }
        shownHome = home
        shownKey = key
        show(p)
    }

    private fun show(p: Page) {
        content.removeAllViews()
        page = p
        content.addView(p.view, FrameLayout.LayoutParams(-1, -1))
        p.update()
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
        if (!resumed || !hasWindowFocus() || PanelState.sleeping || blackout.visibility != View.VISIBLE) return
        handler.removeCallbacks(reveal)
        handler.postDelayed(reveal, SETTLE_MS)
    }

    /** Sem permissão de administrador: ecrã preto + brilho no mínimo. */
    private fun applySleeping(sleeping: Boolean) {
        if (sleeping) cover() else scheduleReveal()
        val lp = window.attributes
        lp.screenBrightness = if (sleeping) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        MotionService.poke()
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

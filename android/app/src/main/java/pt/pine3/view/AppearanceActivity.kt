package pt.pine3.view

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import org.json.JSONObject

/** Miniatura de um tema (o mesmo fundo do painel, num quadrado arredondado). */
private class Swatch(val kit: Kit, theme: Theme, val selected: Boolean) : View(kit.ctx) {
    private val bg = ThemeBackground(theme)
    private val clip = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kit.dp(3).toFloat(); color = kit.accent }

    override fun onDraw(c: Canvas) {
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset(); clip.addRoundRect(r, kit.dp(14).toFloat(), kit.dp(14).toFloat(), Path.Direction.CW)
        val save = c.save()
        c.clipPath(clip)
        bg.setBounds(0, 0, width, height); bg.draw(c)
        c.restoreToCount(save)
        if (selected) { r.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2); c.drawRoundRect(r, kit.dp(13).toFloat(), kit.dp(13).toFloat(), stroke) }
    }
}

/** Aspeto e idioma: as mesmas definições do painel web (guardadas no Home Assistant). */
class AppearanceActivity : Activity() {

    private lateinit var kit: Kit
    private lateinit var holder: FrameLayout
    private var target = "theme_day" // em modo automático: que tema se está a escolher
    private val listener = object : Ha.Listener { override fun onHome() = render() }

    private val CARD_COLORS = listOf("#F5EFE1", "#FFFFFF", "#E8E1F5", "#DDEEE6", "#F7E3D4", "#E3EAF7", "#D8D2C4", "#2B2430")
    private val ACCENTS = listOf("#F6B83F", "#FF9F0A", "#FF6B5A", "#30D158", "#64D2FF", "#5E9CFF", "#BF5AF2", "#FF6EC7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kit = Kit(this)
        holder = FrameLayout(this).apply { setPadding(kit.dp(24), kit.dp(18), kit.dp(10), kit.dp(18)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1C0F1E")); isFillViewport = true; addView(holder) })
    }

    override fun onResume() { super.onResume(); Ha.listeners += listener; render() }
    override fun onPause() { Ha.listeners -= listener; super.onPause() }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { MotionService.poke(); return super.dispatchTouchEvent(ev) }

    private fun set(vararg kv: Pair<String, Any>) {
        val o = JSONObject(); kv.forEach { (k, v) -> o.put(k, v) }
        Ha.saveSettings(o)
    }

    private fun card(parent: LinearLayout, title: String, sub: String, w: Float = 1f): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.rounded(Color.parseColor("#2B1A2D"), 20)
            setPadding(kit.dp(18), kit.dp(16), kit.dp(18), kit.dp(18))
            addView(kit.text(title, 17f, Color.WHITE, 500, display = true))
            addView(kit.text(sub, 13f, Color.argb(166, 255, 255, 255)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(2) })
        }
        parent.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w).apply { marginEnd = kit.dp(14) })
        return c
    }

    private fun segmented(parent: LinearLayout, options: List<Pair<String, String>>, current: String, top: Int, onPick: (String) -> Unit) {
        val row = LinearLayout(this).apply { background = kit.rounded(Color.parseColor("#3A2339"), 14); setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(4)) }
        options.forEach { (key, label) ->
            val sel = key == current
            row.addView(kit.text(label, 14f, if (sel) kit.ink else Color.WHITE, 500).apply {
                gravity = Gravity.CENTER
                background = if (sel) kit.rounded(kit.cream, 11) else null
                setPadding(kit.dp(10), kit.dp(10), kit.dp(10), kit.dp(10))
                isClickable = true; setOnClickListener { onPick(key) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(top) })
    }

    private fun toggle(parent: LinearLayout, label: String, key: String, s: JSONObject, def: Boolean) {
        val sw = Switch(this).apply {
            isChecked = s.optBoolean(key, def)
            thumbTintList = ColorStateList.valueOf(kit.accent)
            trackTintList = ColorStateList.valueOf(Color.argb(110, 246, 184, 63))
            setOnCheckedChangeListener { _, on -> set(key to on) }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(kit.text(label, 15f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(sw)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(14) })
    }

    private fun colorRow(parent: LinearLayout, label: String, key: String, presets: List<String>, current: String) {
        parent.addView(kit.text(label, 13f, Color.argb(190, 255, 255, 255)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(16); bottomMargin = kit.dp(8) })
        val row = LinearLayout(this)
        presets.forEach { hex ->
            val sel = hex.equals(current, ignoreCase = true)
            row.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor(hex))
                    setStroke(kit.dp(if (sel) 3 else 1), if (sel) Color.WHITE else Color.argb(70, 255, 255, 255))
                }
                isClickable = true; setOnClickListener { set(key to hex.lowercase()) }
            }, LinearLayout.LayoutParams(kit.dp(32), kit.dp(32)).apply { marginEnd = kit.dp(8) })
        }
        parent.addView(HorizontalScrollViewCompat(row))
    }

    private fun HorizontalScrollViewCompat(child: View) = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; addView(child)
    }

    private fun render() {
        holder.removeAllViews()
        val home = Ha.home
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(kit.text("Aspeto e idioma", 26f, Color.WHITE, 500, display = true))
        titles.addView(kit.text("Partilhado com o painel web do Pine3 View", 14f, Color.argb(166, 255, 255, 255)))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(kit.pill("Concluído", null, true) { finish() })
        col.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(16) })

        if (home == null) {
            col.addView(kit.text("Liga primeiro ao Home Assistant (Definições → Home Assistant) para ver e mudar estas opções.", 16f, Color.WHITE))
            holder.addView(col, FrameLayout.LayoutParams(-1, -2)); return
        }
        val s = home.settings
        val cards = LinearLayout(this)
        col.addView(cards, LinearLayout.LayoutParams(-1, -2))

        // Tema
        val themeCard = card(cards, "Tema", "Fixo, ou um de dia e outro de noite (pelo sol)", 1.25f)
        val auto = s.optString("appearance", "fixed") == "auto"
        segmented(themeCard, listOf("fixed" to "Fixo", "auto" to "Dia e noite"), if (auto) "auto" else "fixed", 12) { set("appearance" to it) }
        if (auto) segmented(themeCard, listOf("theme_day" to "Tema de dia", "theme_night" to "Tema de noite"), target, 8) { target = it; render() }
        val key = if (auto) target else "theme"
        val current = s.optString(key, if (key == "theme_day") "linho" else "ameixa")
        val grid = Grid(this, kit.dp(74), kit.dp(86), kit.dp(10))
        Themes.all.forEach { t ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true; setOnClickListener { set(key to t.key) }
                addView(Swatch(kit, t, t.key == current), LinearLayout.LayoutParams(kit.dp(60), kit.dp(60)))
                addView(kit.text(t.name, 11f, Color.WHITE).apply { setSingleLine() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
            }
            grid.addView(cell)
        }
        themeCard.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(14) })

        // Início
        val homeCard = card(cards, "Início", "O que se vê e em que língua")
        segmented(homeCard, listOf("pt" to "Português", "en" to "English"), s.optString("language", "pt"), 12) { set("language" to it) }
        toggle(homeCard, "Meteorologia", "show_weather", s, true)
        toggle(homeCard, "Pessoas", "home_people", s, false)
        toggle(homeCard, "Estado da casa", "show_status", s, true)
        toggle(homeCard, "Cenas", "show_scenes", s, true)
        toggle(homeCard, "Cartões de resumo", "show_summary", s, true)
        toggle(homeCard, "Relógio de 24 horas", "clock_24h", s, true)

        // Cores
        val colorCard = card(cards, "Cores", "Cartões e cor de destaque")
        colorRow(colorCard, "Cor dos cartões", "card_color", CARD_COLORS, s.optString("card_color", "#f5efe1"))
        colorRow(colorCard, "Cor de destaque", "accent_color", ACCENTS, s.optString("accent_color", "#f6b83f"))
        val hex = EditText(this).apply {
            hint = "#RRGGBB"; setHintTextColor(Color.parseColor("#7C687A")); setTextColor(Color.WHITE); textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; setSingleLine()
            background = kit.rounded(Color.parseColor("#3A2339"), 12); setPadding(kit.dp(14), kit.dp(12), kit.dp(14), kit.dp(12))
        }
        colorCard.addView(kit.text("Outra cor de destaque (hex)", 13f, Color.argb(190, 255, 255, 255)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(16); bottomMargin = kit.dp(6) })
        colorCard.addView(hex, LinearLayout.LayoutParams(-1, -2))
        colorCard.addView(kit.pill("Aplicar cor", null, false) {
            val v = hex.text.toString().trim()
            if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) set("accent_color" to v.lowercase()) else Toast.makeText(this, "Usa o formato #RRGGBB", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) })

        holder.addView(col, FrameLayout.LayoutParams(-1, -2))
    }
}

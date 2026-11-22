package pt.pine3.view

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject

/** Que portas se abrem ao lado de uma câmara: as mesmas regras do painel web e a mesma definição (`cam_doors`). */
object Doors {
    private val DOOR = Regex("lock|door|gate|entrance|porta|port[aã]o|fechadura|trinco|entrada", RegexOption.IGNORE_CASE)

    private fun doorLike(id: String) = Fmt.domain(id) == "lock" || (Fmt.domain(id) == "button" && DOOR.containsMatchIn(id + " " + Fmt.name(id)))

    /**
     * 1) as portas escolhidas para esta câmara (cam_doors); 2) a abertura de uma campainha que nomeia esta câmara (release_entity);
     * 3) as fechaduras da divisão da câmara, ou os botões de porta dessa divisão.
     */
    fun forCamera(camId: String): List<String> {
        val home = Ha.home
        home?.settings?.optJSONObject("cam_doors")?.optJSONArray(camId)?.let { a -> return (0 until a.length()).map { a.getString(it) }.filter { Ha.states.containsKey(it) } }
        Ha.states.forEach { (_, st) ->
            if (st.attrs.optString("device_class") == "doorbell" && st.attrs.optString("camera_entity") == camId) {
                st.attrs.optString("release_entity").takeIf { Ha.states.containsKey(it) }?.let { return listOf(it) }
            }
        }
        val room = home?.rooms?.firstOrNull { r -> r.devices.any { d -> d.id == camId || camId in d.extras } }
        val ents = room?.devices?.flatMap { listOf(it.id) + it.extras }?.distinct()?.filter { Ha.states.containsKey(it) }.orEmpty()
        val locks = ents.filter { Fmt.domain(it) == "lock" }
        return if (locks.isNotEmpty()) locks else ents.filter { Fmt.domain(it) == "button" && DOOR.containsMatchIn(it + " " + Fmt.name(it)) }
    }

    /** Todas as fechaduras e botões de porta da casa, para escolher. */
    fun candidates(): List<String> = Ha.states.keys.filter { doorLike(it) }.sortedBy { Fmt.name(it).lowercase() }

    fun roomOf(id: String): String = Ha.home?.rooms?.firstOrNull { r -> r.devices.any { d -> d.id == id || id in d.extras } }?.name.orEmpty()

    fun save(camId: String, list: List<String>) {
        val o = JSONObject(Ha.home?.settings?.optJSONObject("cam_doors")?.toString() ?: "{}")
        o.put(camId, JSONArray(list))
        Ha.saveSettings(JSONObject().put("cam_doors", o))
    }
}

/** Escolher as portas desta câmara (guarda no HA, por isso vale também para o painel web). */
class DoorPicker(private val act: Activity, private val kit: Kit, private val camId: String, private val onClose: () -> Unit) {
    private val dialog = PokeDialog(act)

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var chosen = Doors.forCamera(camId).toMutableList()
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun fill() {
            list.removeAllViews()
            val all = Doors.candidates()
            if (all.isEmpty()) list.addView(kit.text(Tr.t("Não há fechaduras nem botões de porta no Home Assistant."), 14f, Color.argb(166, 255, 255, 255)))
            all.forEach { id ->
                val on = id in chosen
                val row = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = kit.ripple(kit.rounded(if (on) Color.argb(60, 255, 255, 255) else Color.argb(24, 255, 255, 255), 16)); setPadding(kit.dp(14), kit.dp(12), kit.dp(14), kit.dp(12)); isClickable = true
                    addView(kit.icon(if (on) "mdi:checkbox-marked-circle" else "mdi:checkbox-blank-circle-outline", if (on) kit.accent else Color.argb(166, 255, 255, 255), 22), LinearLayout.LayoutParams(kit.dp(22), kit.dp(22)).apply { marginEnd = kit.dp(12) })
                    addView(LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(kit.text(Fmt.name(id), 16f, Color.WHITE, 500))
                        addView(kit.text(Doors.roomOf(id), 12f, Color.argb(166, 255, 255, 255)))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    Elastic.press(this, 0.97f)
                    setOnClickListener { if (on) chosen.remove(id) else chosen.add(id); Doors.save(camId, chosen); fill() }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(8) })
            }
        }
        fill()
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; background = kit.sheetBg(32); setPadding(kit.dp(22), kit.dp(20), kit.dp(22), kit.dp(22)); isClickable = true
            val hd = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
            hd.addView(kit.text(Tr.t("Portas ao lado desta câmara"), 22f, Color.WHITE, 400, display = true), LinearLayout.LayoutParams(0, -2, 1f))
            hd.addView(kit.pill(Tr.t("Concluído"), "mdi:check", true) { dialog.dismiss() })
            addView(hd)
            addView(kit.text(Fmt.name(camId), 13f, Color.argb(166, 255, 255, 255)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2); bottomMargin = kit.dp(14) })
            addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(-1, kit.dp(kit.clamp(200f, kit.hdp - 260f, 520f))))
        }
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(160, 10, 4, 12)); isClickable = true; setOnClickListener { dialog.dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(320f, kit.wdp - 80f, 560f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.setOnDismissListener { onClose() }
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.92f)
    }
}

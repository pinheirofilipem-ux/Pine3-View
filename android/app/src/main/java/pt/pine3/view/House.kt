package pt.pine3.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A casa vista de cima/em perspetiva: as mesmas contas do painel web (casa desenhada num canvas), sem WebView. */
object House {
    const val U = 56f
    const val WALL_DEFAULT = 30
    private const val SLAB = 14f
    private val SIZES = mapOf(1 to (2 to 2), 2 to (3 to 2), 3 to (4 to 3))

    class Glow(val rgb: IntArray?, val k: Float)
    class HLight(val id: String, val x: Float, val y: Float, val on: Boolean, val glow: Glow?, val kind: String, val len: Float = 0f, val rot: Float = 0f)
    class HThing(val id: String, val x: Float, val y: Float, val on: Boolean, val icon: String)
    class HRoom(
        val id: String, val name: String, val w: Float, val h: Float, var cx: Float, var cy: Float, val lights: ArrayList<HLight> = ArrayList(), val things: ArrayList<HThing> = ArrayList(),
        var lit: Boolean = false, var open: Boolean = false, var glow: Glow? = null, var meta: String = "", var shape: Boolean = false,
        var cells: List<FloatArray> = emptyList(), var edges: List<FloatArray> = emptyList(), var lx: Float = 0f, var ly: Float = 0f,
    )
    class HFloor(val z: Float, val lv: Int, val slab: FloatArray, val rooms: ArrayList<HRoom> = ArrayList(), var name: String = "")
    class HModel(val wall: Float, val w: Float, val d: Float, val stack: Float, val floors: List<HFloor>, val levels: List<Int>, val floorNames: Map<Int, String>)

    /** Estado da vista (rotação, zoom, piso) que sobrevive às reconstruções da página. */
    var rx = 58f; var rz = -38f; var zoom = 1f; var px = 0f; var py = 0f
    var floor: Int? = null // null = todos os pisos

    private fun kelvinRgb(k: Double): IntArray {
        val t = k.coerceIn(1000.0, 40000.0) / 100
        fun cl(v: Double) = Math.round(v.coerceIn(0.0, 255.0)).toInt()
        val r = if (t <= 66) 255.0 else 329.698727446 * Math.pow(t - 60, -0.1332047592)
        val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661 else 288.1221695283 * Math.pow(t - 60, -0.0755148492)
        val b = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * Math.log(t - 10) - 305.0447927307
        return intArrayOf(cl(r), cl(g), cl(b))
    }

    fun lightGlow(id: String): Glow? {
        val s = Fmt.state(id) ?: return null
        if (Fmt.domain(id) != "light" || !Fmt.isOn(id)) return null
        val a = s.attrs
        val rgb = a.optJSONArray("rgb_color")?.let { intArrayOf(it.optInt(0), it.optInt(1), it.optInt(2)) }
            ?: a.optDouble("color_temp_kelvin", Double.NaN).takeIf { !it.isNaN() }?.let { kelvinRgb(it) }
            ?: a.optDouble("color_temp", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.let { kelvinRgb(1e6 / it) }
        val k = if (a.has("brightness") && !a.isNull("brightness")) max(0.25f, a.optDouble("brightness").toFloat() / 255f) else 1f
        return Glow(rgb, k)
    }

    private fun roomGlow(glows: List<Glow>): Glow {
        val withRgb = glows.filter { it.rgb != null }
        val k = if (glows.isNotEmpty()) glows.maxOf { it.k } else 0.7f
        if (withRgb.isEmpty()) return Glow(null, k)
        val w = withRgb.sumOf { it.k.toDouble() }
        return Glow(IntArray(3) { i -> Math.round(withRgb.sumOf { it.rgb!![i] * it.k.toDouble() } / w).toInt() }, k)
    }

    private class Box(val room: Room, var w: Int, var d: Int, var x: Int = 0, var y: Int = 0, var cells: List<IntArray>? = null)

    private fun autoSize(r: Room) = r.devices.count { !it.hidden }.let { if (it <= 3) 1 else if (it <= 7) 2 else 3 }

    private fun pack(rooms: List<Room>, sizes: JSONObject): Pair<List<Box>, Pair<Int, Int>> {
        val boxes = rooms.map { r -> val (w, d) = SIZES[sizes.optInt(r.id, autoSize(r)).takeIf { it in 1..3 } ?: autoSize(r)]!!; Box(r, w, d) }
        val area = boxes.sumOf { it.w * it.d }
        val rowW = maxOf(6, Math.ceil(Math.sqrt(area.toDouble()) * 1.35).toInt(), boxes.maxOfOrNull { it.w } ?: 0)
        var x = 0; var y = 0; var rowD = 0; var W = 0
        for (b in boxes.sortedWith(compareByDescending<Box> { it.d }.thenByDescending { it.w })) {
            if (x + b.w > rowW) { y += rowD; x = 0; rowD = 0 }
            b.x = x; b.y = y
            x += b.w; rowD = max(rowD, b.d); W = max(W, x)
        }
        return boxes to (W to y + rowD)
    }

    private fun ents(r: Room) = r.devices.flatMap { listOf(it.id) + it.extras }.distinct()

    fun build(home: Home): HModel {
        val cfg = home.settings
        val layout = cfg.optJSONObject("house_layout") ?: JSONObject()
        val rooms = home.rooms.filter { !it.hidden && it.id !in (cfg.optJSONArray("hidden_rooms")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()) }
        fun lvOf(r: Room) = layout.optJSONObject(r.id)?.optInt("f", r.floorLevel) ?: r.floorLevel
        val levels = rooms.map { lvOf(it) }.distinct().sorted()
        val names = levels.associateWith { lv -> rooms.firstOrNull { it.floorLevel == lv && !it.floor.isNullOrBlank() }?.floor ?: (Tr.t("Piso ") + lv) }
        if (floor != null && floor !in levels) floor = null
        val shown = if (floor == null) levels else listOf(floor!!)
        val sizes = cfg.optJSONObject("house_size") ?: JSONObject()
        val placements = cfg.optJSONObject("placements") ?: JSONObject()

        class Plan(val lv: Int, val items: List<Box>, val x0: Int, val y0: Int, val x1: Int, val y1: Int) { val w get() = x1 - x0; val d get() = y1 - y0 }
        val plans = shown.map { lv ->
            val onFloor = rooms.filter { lvOf(it) == lv }
            val placed = onFloor.filter { layout.has(it.id) }.map { r ->
                val l = layout.getJSONObject(r.id)
                Box(r, l.optInt("w", 2), l.optInt("d", 2), l.optInt("x"), l.optInt("y"), l.optJSONArray("cells")?.let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { intArrayOf(it.optInt(0), it.optInt(1)) } } })
            }
            val auto = pack(onFloor.filter { !layout.has(it.id) }, sizes)
            val ox = if (placed.isNotEmpty()) placed.maxOf { it.x + it.w } + 1 else 0
            auto.first.forEach { it.x += ox }
            val items = placed + auto.first
            val x0 = items.minOfOrNull { it.x } ?: 0; val y0 = items.minOfOrNull { it.y } ?: 0
            Plan(lv, items, x0, y0, max(x0 + 1, items.maxOfOrNull { it.x + it.w } ?: 0), max(y0 + 1, items.maxOfOrNull { it.y + it.d } ?: 0))
        }
        val drawn = layout.length() > 0
        val gx0 = if (drawn) plans.minOfOrNull { it.x0 } ?: 0 else 0; val gy0 = if (drawn) plans.minOfOrNull { it.y0 } ?: 0 else 0
        val W = if (drawn) (plans.maxOfOrNull { it.x1 } ?: 1) - gx0 else max(plans.maxOfOrNull { it.w } ?: 1, 1)
        val D = if (drawn) (plans.maxOfOrNull { it.y1 } ?: 1) - gy0 else max(plans.maxOfOrNull { it.d } ?: 1, 1)
        val wall = cfg.optInt("wall_height", WALL_DEFAULT).coerceIn(8, 120).toFloat()
        val gap = max(110f, wall + 100f)

        val floors = plans.mapIndexed { k, pl ->
            val z = k * gap
            val ox = if (drawn) -gx0 * U else ((W - pl.w) / 2f - pl.x0) * U
            val oy = if (drawn) -gy0 * U else ((D - pl.d) / 2f - pl.y0) * U
            val F = HFloor(z, pl.lv, floatArrayOf(ox + pl.x0 * U - SLAB, oy + pl.y0 * U - SLAB, pl.w * U + 2 * SLAB, pl.d * U + 2 * SLAB), name = names[pl.lv] ?: "")
            pl.items.forEach { b ->
                val r = b.room
                val ids = ents(r)
                val sw = Fmt.switchables(r.devices.filter { !it.hidden }.map { it.id })
                val lit = sw.filter { Fmt.isOn(it) }
                val X = ox + b.x * U; val Y = oy + b.y * U
                val R = HRoom(r.id, r.name, b.w * U, b.d * U, X + b.w * U / 2, Y + b.d * U / 2)
                F.rooms += R
                val placedIds = if (layout.has(r.id)) ids.filter { id -> placements.optJSONObject(id)?.let { it.optInt("f", Int.MIN_VALUE) == lvOf(r) } == true } else emptyList()
                placedIds.forEach { id ->
                    val P = placements.getJSONObject(id)
                    val ty = P.optString("t").takeIf { DeviceTypes.has(it) } ?: DeviceTypes.auto(id, Fmt.state(id), r.devices.firstOrNull { it.id == id })
                    val (icon, kind) = DeviceTypes.info(ty)
                    val at = Pair(X + (P.optDouble("x") - b.x).toFloat() * U, Y + (P.optDouble("y") - b.y).toFloat() * U)
                    val g = lightGlow(id); val on = Fmt.isOn(id)
                    if (kind == "thing") R.things += HThing(id, at.first, at.second, on, icon)
                    else if (kind == "strip") {
                        val len = P.optDouble("l", 1.0).coerceIn(0.25, 30.0).toFloat(); val rot = (((P.optDouble("r", 0.0) % 360) + 360) % 360).toFloat()
                        R.lights += HLight(id, at.first, at.second, on, g, "strip", len * U, rot)
                    } else R.lights += HLight(id, at.first, at.second, on, g, kind)
                }
                val lights = ids.filter { Fmt.domain(it) == "light" && it !in placedIds }.take(6)
                val cells = b.cells?.map { intArrayOf(it[0] - b.x, it[1] - b.y) }
                val spots = if (cells != null) lights.indices.map { n -> cells[Math.floor((n + 0.5) * cells.size / max(1, lights.size)).toInt().coerceIn(0, cells.size - 1)] }.map { floatArrayOf((it[0] + 0.5f) / b.w * 100f, (it[1] + 0.5f) / b.d * 100f) } else null
                val cols = min(3, max(1, lights.size))
                lights.forEachIndexed { n, id ->
                    val (pxp, pyp) = if (spots != null) spots[n][0] to spots[n][1] else (((n % cols) + 1f) / (cols + 1) * 100f) to ((n / cols + 1f) / (Math.ceil(lights.size / cols.toDouble()).toInt() + 1) * 100f)
                    R.lights += HLight(id, X + pxp / 100f * b.w * U, Y + pyp / 100f * b.d * U, Fmt.isOn(id), lightGlow(id), "lamp")
                }
                val c = Fmt.roomClimate(r)
                R.meta = listOfNotNull(c.first?.replace(Regex("[,.]\\d"), "")?.replace("°C", "°"), if (lit.isNotEmpty()) Tr.pl(lit.size, "ligado", "ligados", "on", "on") else null).joinToString(" · ")
                R.lit = lit.isNotEmpty()
                R.open = ids.any { id -> val s = Fmt.state(id); (Fmt.domain(id) == "binary_sensor" && s?.attrs?.optString("device_class") in setOf("door", "window", "garage_door", "opening") && s?.state == "on") || (Fmt.domain(id) == "cover" && Fmt.isOn(id)) }
                R.glow = if (lit.isNotEmpty()) roomGlow(ids.mapNotNull { lightGlow(it) }) else null
                if (cells != null) {
                    R.shape = true
                    R.cells = cells.map { floatArrayOf(X + it[0] * U, Y + it[1] * U, U, U) }
                    val set = cells.map { it[0] to it[1] }.toSet()
                    val edges = ArrayList<FloatArray>()
                    cells.forEach { (x, y) ->
                        val L = X + x * U; val T = Y + y * U
                        if ((x to y - 1) !in set) edges += floatArrayOf(L, T, L + U, T)
                        if ((x to y + 1) !in set) edges += floatArrayOf(L, T + U, L + U, T + U)
                        if ((x - 1 to y) !in set) edges += floatArrayOf(L, T, L, T + U)
                        if ((x + 1 to y) !in set) edges += floatArrayOf(L + U, T, L + U, T + U)
                    }
                    R.edges = edges
                    val mx = cells.sumOf { it[0].toDouble() } / cells.size; val my = cells.sumOf { it[1].toDouble() } / cells.size
                    R.cx = X + (mx.toFloat() + 0.5f) * U; R.cy = Y + (my.toFloat() + 0.5f) * U
                    val best = cells.minByOrNull { hypot(it[0] - mx, it[1] - my) }!!
                    R.lx = X + (best[0] + 0.5f) * U; R.ly = Y + (best[1] + 0.5f) * U
                } else {
                    val x0 = X + 3; val y0 = Y + 3; val x1 = X + b.w * U - 3; val y1 = Y + b.d * U - 3
                    R.cells = listOf(floatArrayOf(x0, y0, x1 - x0, y1 - y0))
                    R.edges = listOf(floatArrayOf(x0, y0, x1, y0), floatArrayOf(x1, y0, x1, y1), floatArrayOf(x1, y1, x0, y1), floatArrayOf(x0, y1, x0, y0))
                    R.lx = R.cx; R.ly = R.cy
                }
            }
            F
        }
        return HModel(wall, W * U, D * U, (floors.size - 1) * gap, floors, levels, names)
    }
}

/** Tipos de dispositivo da planta (ícone e comportamento), como `devtypes.js`. */
object DeviceTypes {
    private val T = mapOf(
        "ceiling" to ("mdi:ceiling-light" to "ceiling"), "lamp" to ("mdi:floor-lamp" to "light"), "led" to ("mdi:led-strip-variant" to "strip"), "spot" to ("mdi:spotlight-beam" to "ceiling"),
        "bulb" to ("mdi:lightbulb" to "light"), "tv" to ("mdi:television" to "thing"), "appletv" to ("mdi:television-box" to "thing"), "homepod" to ("mdi:speaker" to "thing"),
        "speaker" to ("mdi:speaker-wireless" to "thing"), "ac" to ("mdi:air-conditioner" to "thing"), "fan" to ("mdi:fan" to "thing"), "purifier" to ("mdi:air-purifier" to "thing"),
        "blind" to ("mdi:blinds" to "thing"), "door" to ("mdi:door" to "thing"), "window" to ("mdi:window-closed-variant" to "thing"), "plug" to ("mdi:power-socket-eu" to "thing"),
        "sensor" to ("mdi:thermometer" to "thing"), "motion" to ("mdi:motion-sensor" to "thing"), "camera" to ("mdi:cctv" to "thing"), "lock" to ("mdi:lock" to "thing"),
        "vacuum" to ("mdi:robot-vacuum" to "thing"), "other" to ("mdi:circle-medium" to "thing"),
    )
    fun has(t: String) = T.containsKey(t)
    fun info(t: String) = T[t] ?: T["other"]!!
    private fun has(s: String, vararg w: String) = w.any { s.contains(it) }

    fun auto(id: String, st: Ha.State?, dev: Dev?): String {
        val d = Fmt.domain(id); val a = st?.attrs
        val name = java.text.Normalizer.normalize("${dev?.name.orEmpty()} ${a?.optString("friendly_name").orEmpty()} $id".lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        val dc = a?.optString("device_class").orEmpty()
        return when (d) {
            "light" -> when {
                has(name, "fita", "led", "strip", "lightstrip", "barra") -> "led"
                has(name, "teto", "tecto", "ceiling", "plafon", "lustre", "pendente", "suspens") -> "ceiling"
                has(name, "spot", "foco", "embutid") -> "spot"
                has(name, "candeeiro", "lamp", "abajur", "de pe", "cabeceira", "floor") -> "lamp"
                else -> "ceiling"
            }
            "media_player" -> when { has(name, "homepod") -> "homepod"; has(name, "apple tv", "appletv") -> "appletv"; dc == "tv" || has(name, "tv", "televis") -> "tv"; else -> "speaker" }
            "climate" -> "ac"
            "fan" -> if (has(name, "purif", "air clean")) "purifier" else "fan"
            "cover" -> if (dc == "door" || dc == "garage") "door" else "blind"
            "lock" -> "lock"; "camera" -> "camera"; "vacuum" -> "vacuum"
            "switch", "input_boolean" -> if (has(name, "tv", "televis")) "tv" else "plug"
            "binary_sensor" -> when { dc in setOf("door", "garage_door", "opening") -> "door"; dc == "window" -> "window"; dc in setOf("motion", "occupancy", "presence") -> "motion"; else -> "sensor" }
            "sensor" -> "sensor"
            else -> "other"
        }
    }
}

/** O canvas da casa: desenha o modelo com a câmara em perspetiva e trata arrastar (rodar), pinça (zoom) e toques. */
class HouseView(private val kit: Kit, private val onPin: (String) -> Unit, private val onRoom: (String) -> Unit, private val onThing: (String) -> Unit) : View(kit.ctx) {
    var model: House.HModel? = null
        set(v) { field = v; invalidate() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val m = Matrix()
    private val P = 1600f

    private class Pin(val id: String, val x: Float, val y: Float, val seg: FloatArray? = null)
    private val pins = ArrayList<Pin>()
    private val roomsHit = ArrayList<Pair<String, List<FloatArray>>>()
    private val thingsHit = ArrayList<Pin>()

    private fun scale(M: House.HModel, cw: Float, ch: Float): Float {
        val diag = hypot(M.w, M.d) + 80f
        return (min(min(1.25f, (cw - 24f) / diag), (ch - 60f) / (diag * 0.62f + M.stack)).coerceAtLeast(0.35f)) * House.zoom
    }

    private inner class Cam(val M: House.HModel, val cw: Float, val ch: Float) {
        val rxr = Math.toRadians(House.rx.toDouble()); val rzr = Math.toRadians(House.rz.toDouble())
        val cz = cos(rzr).toFloat(); val sz = sin(rzr).toFloat(); val cx = cos(rxr).toFloat(); val sx = sin(rxr).toFloat()
        val sc = scale(M, cw, ch)
        val ox = cw / 2 + House.px; val oy = ch * 0.52f + House.py; val ppx = cw / 2; val ppy = ch * 0.35f
        fun pr(x: Float, y: Float, z: Float): FloatArray {
            val X = x - M.w / 2; val Y = y - M.d / 2; val Z = z - M.stack / 2
            val x1 = X * cz - Y * sz; val y1 = X * sz + Y * cz
            val y2 = y1 * cx - Z * sx; val z2 = y1 * sx + Z * cx
            val ax = ox + x1 * sc; val ay = oy + y2 * sc
            val f = P / (P - z2)
            return floatArrayOf(ppx + (ax - ppx) * f, ppy + (ay - ppy) * f, z2)
        }
    }

    private fun quad(cam: Cam, c: FloatArray, z: Float) = listOf(cam.pr(c[0], c[1], z), cam.pr(c[0] + c[2], c[1], z), cam.pr(c[0] + c[2], c[1] + c[3], z), cam.pr(c[0], c[1] + c[3], z))
    private fun addPoly(p: Path, q: List<FloatArray>) { p.moveTo(q[0][0], q[0][1]); for (i in 1 until q.size) p.lineTo(q[i][0], q[i][1]); p.close() }
    private fun argb(c: IntArray, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), c[0], c[1], c[2])

    override fun onDraw(c: Canvas) {
        val M = model ?: return
        val cw = width.toFloat(); val ch = height.toFloat()
        if (cw <= 0 || ch <= 0) return
        val cam = Cam(M, cw, ch)
        val amber = intArrayOf(Color.red(kit.accent), Color.green(kit.accent), Color.blue(kit.accent))
        val W = if (kit.light) intArrayOf(40, 30, 46) else intArrayOf(255, 255, 255)
        fun w(a: Float) = argb(W, a)
        pins.clear(); roomsHit.clear(); thingsHit.clear()
        val wall = M.wall

        fun plane(x: Float, y: Float, z: Float) {
            val o = cam.pr(x, y, z); val a = cam.pr(x + 1, y, z); val b = cam.pr(x, y + 1, z)
            m.setValues(floatArrayOf(a[0] - o[0], b[0] - o[0], o[0], a[1] - o[1], b[1] - o[1], o[1], 0f, 0f, 1f))
            c.concat(m)
        }

        for (F in M.floors) {
            val z = F.z
            path.reset(); addPoly(path, quad(cam, F.slab, z - 2))
            fill.style = Paint.Style.FILL; fill.shader = null; fill.color = w(.07f); c.drawPath(path, fill)
            line.strokeWidth = 1f; line.color = w(.14f); line.pathEffect = null; c.drawPath(path, line)

            for (R in F.rooms) {
                val lc = R.glow?.rgb ?: amber; val k = R.glow?.k ?: 0.7f
                val qs = R.cells.map { quad(cam, it, z) }
                roomsHit += R.id to qs.flatMap { listOf(it.map { p -> p[0] }.toFloatArray(), it.map { p -> p[1] }.toFloatArray()) }
                path.reset(); qs.forEach { addPoly(path, it) }
                fill.color = w(.10f); c.drawPath(path, fill)
                if (R.lit) {
                    c.save(); c.clipPath(path)
                    plane(R.cx, R.cy, z)
                    val rad = max(R.w, R.h) * 0.62f
                    fill.shader = RadialGradient(0f, 0f, rad, intArrayOf(argb(lc, .85f * k), argb(lc, .5f * k), argb(lc, .22f * k)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                    c.drawRect(-rad, -rad, rad, rad, fill); fill.shader = null
                    c.restore()
                }
                if (!R.shape) { line.strokeWidth = 1f; line.color = if (R.lit) argb(lc, .75f) else w(.16f); c.drawPath(path, line) }
            }

            for (R in F.rooms) for (L in R.lights) {
                val g = L.glow ?: continue
                val lc = g.rgb ?: amber; val k = g.k
                c.save()
                path.reset(); R.cells.forEach { addPoly(path, quad(cam, it, z)) }; c.clipPath(path)
                plane(L.x, L.y, z + 1)
                if (L.kind == "strip") { c.rotate(L.rot); c.scale((L.len + 30 + k * 40) / 2, (28 + k * 34) / 2) } else { val r = (60 + k * 90) / 2; c.scale(r, r) }
                fill.shader = RadialGradient(0f, 0f, 1f, intArrayOf(argb(lc, .75f * k), argb(lc, .3f * k), argb(lc, 0f)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(0f, 0f, 1f, fill); fill.shader = null
                c.restore()
            }

            // paredes, as mais distantes primeiro
            class Wl(val R: House.HRoom, val q: List<FloatArray>, val d: Float)
            val ws = ArrayList<Wl>()
            for (R in F.rooms) for (e in R.edges) {
                val a0 = cam.pr(e[0], e[1], z); val b0 = cam.pr(e[2], e[3], z); val a1 = cam.pr(e[0], e[1], z + wall); val b1 = cam.pr(e[2], e[3], z + wall)
                ws += Wl(R, listOf(a0, b0, b1, a1), a0[2] + b0[2] + a1[2] + b1[2])
            }
            ws.sortBy { it.d }
            for (wl in ws) {
                val R = wl.R; val lc = R.glow?.rgb ?: amber; val k = R.glow?.k ?: 0.7f
                path.reset(); addPoly(path, wl.q)
                fill.color = w(.10f); c.drawPath(path, fill)
                if (R.lit) { fill.color = argb(lc, .24f * k); c.drawPath(path, fill) }
                line.strokeWidth = 1f; line.color = if (R.lit) argb(lc, .5f) else w(.22f)
                line.pathEffect = if (R.open) DashPathEffect(floatArrayOf(4f, 3f), 0f) else null
                c.drawPath(path, line); line.pathEffect = null
            }

            // as luzes
            for (R in F.rooms) for (L in R.lights) {
                val g = L.glow; val lc = g?.rgb ?: amber; val k = g?.k ?: 1f
                val lz = z + (if (L.kind == "ceiling") wall - 2f else 3f)
                val fl = if (L.on) Color.rgb((lc[0] * .35 + 255 * .65).toInt(), (lc[1] * .35 + 255 * .65).toInt(), (lc[2] * .35 + 255 * .65).toInt()) else w(.35f)
                val st = if (L.on) argb(lc, 1f) else w(.6f)
                if (L.kind == "strip") {
                    val a = Math.toRadians(L.rot.toDouble()); val h = L.len / 2
                    val A = cam.pr(L.x - cos(a).toFloat() * h, L.y - sin(a).toFloat() * h, lz); val B = cam.pr(L.x + cos(a).toFloat() * h, L.y + sin(a).toFloat() * h, lz)
                    if (L.on) { line.color = argb(lc, .35f * k + .15f); line.strokeWidth = 14f; c.drawLine(A[0], A[1], B[0], B[1], line) }
                    line.color = st; line.strokeWidth = 8f; c.drawLine(A[0], A[1], B[0], B[1], line)
                    line.color = fl; line.strokeWidth = 4f; c.drawLine(A[0], A[1], B[0], B[1], line)
                    pins += Pin(L.id, 0f, 0f, floatArrayOf(A[0], A[1], B[0], B[1]))
                    continue
                }
                val p = cam.pr(L.x, L.y, lz); val r = if (L.kind == "ceiling") 10f else 8f
                if (L.on) {
                    val gr = r + 6 + k * 14
                    fill.shader = RadialGradient(p[0], p[1], gr, intArrayOf(argb(lc, .55f + .35f * k), argb(lc, 0f)), floatArrayOf(r * .6f / gr, 1f), Shader.TileMode.CLAMP)
                    c.drawCircle(p[0], p[1], gr, fill); fill.shader = null
                }
                fill.color = fl; c.drawCircle(p[0], p[1], r, fill)
                line.color = st; line.strokeWidth = 2f; c.drawCircle(p[0], p[1], r, line)
                pins += Pin(L.id, p[0], p[1])
            }
        }

        // equipamentos (televisão, estore...) e nomes por cima
        val ls = cam.sc.coerceIn(0.55f, 1f)
        for (F in M.floors) for (R in F.rooms) for (T in R.things) {
            val p = cam.pr(T.x, T.y, F.z + 16)
            val r = kit.dp(16).toFloat() * ls.coerceAtLeast(0.8f)
            fill.color = if (T.on) kit.cream else Color.argb(217, 25, 14, 28); c.drawCircle(p[0], p[1], r, fill)
            line.color = if (T.on) Color.WHITE else Color.argb(115, 255, 255, 255); line.strokeWidth = 2f; c.drawCircle(p[0], p[1], r, line)
            val d = kit.mdi(T.icon); val s = (r * 1.1f).toInt()
            d.setColorFilter(if (T.on) kit.ink else Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            d.setBounds((p[0] - s / 2).toInt(), (p[1] - s / 2).toInt(), (p[0] + s / 2).toInt(), (p[1] + s / 2).toInt()); d.draw(c)
            thingsHit += Pin(T.id, p[0], p[1])
        }
        for (F in M.floors) {
            for (R in F.rooms) {
                val p = cam.pr(R.lx, R.ly, F.z + wall + 26)
                label(c, p[0], p[1], R.name, R.meta, ls, R.lit)
            }
            if (M.floors.size > 1) { val p = cam.pr(F.slab[0], F.slab[1], F.z); label(c, p[0], p[1], F.name.uppercase(), "", ls, false, floorTag = true) }
        }
    }

    private fun label(c: Canvas, x: Float, y: Float, title: String, meta: String, ls: Float, lit: Boolean, floorTag: Boolean = false) {
        val t1 = kit.dp(if (floorTag) 12 else 15) * ls; val t2 = kit.dp(12) * ls
        txt.typeface = kit.font(if (floorTag) 600 else 500, true); txt.textSize = t1
        val w1 = txt.measureText(title)
        var w2 = 0f
        if (meta.isNotBlank()) { txt.typeface = kit.font(400, false); txt.textSize = t2; w2 = txt.measureText(meta) }
        val padX = kit.dp(11) * ls; val padY = kit.dp(5) * ls
        val bw = max(w1, w2) + 2 * padX; val bh = t1 + (if (meta.isNotBlank()) t2 + 2 else 0f) + 2 * padY
        val r = RectF(if (floorTag) x else x - bw / 2, y - bh, (if (floorTag) x else x - bw / 2) + bw, y)
        fill.color = if (lit) kit.cream else Color.argb(if (floorTag) 140 else 200, 20, 10, 22)
        c.drawRoundRect(r, kit.dp(13) * ls, kit.dp(13) * ls, fill)
        txt.typeface = kit.font(if (floorTag) 600 else 500, true); txt.textSize = t1; txt.color = if (lit) kit.ink else Color.WHITE
        txt.textAlign = Paint.Align.CENTER
        c.drawText(title, r.centerX(), r.top + padY + t1 * 0.82f, txt)
        if (meta.isNotBlank()) { txt.typeface = kit.font(400, false); txt.textSize = t2; txt.color = if (lit) kit.inkSoft else Color.argb(166, 255, 255, 255); c.drawText(meta, r.centerX(), r.top + padY + t1 + t2 * 0.9f, txt) }
    }

    // --- gestos
    private var sx = 0f; private var sy = 0f; private var srx = 0f; private var srz = 0f
    private var moved = false
    private var pinch = false
    private var pd = 1f; private var pz = 1f
    private var pm = floatArrayOf(0f, 0f); private var pp = floatArrayOf(0f, 0f)
    private val slop = kit.dp(8)

    private fun zoomTo(z0: Float, m0: FloatArray, m: FloatArray, zNew: Float, p0: FloatArray) {
        val z = zNew.coerceIn(0.6f, 3f)
        val cx = width / 2f; val cy = height * 0.52f; val k = z / z0
        val lx = width * z / 2; val ly = height * z / 2
        House.zoom = z
        House.px = (m[0] - cx - (m0[0] - cx - p0[0]) * k).coerceIn(-lx, lx)
        House.py = (m[1] - cy - (m0[1] - cy - p0[1]) * k).coerceIn(-ly, ly)
        invalidate()
    }

    fun zoomBy(f: Float) { val c = floatArrayOf(width / 2f, height * 0.52f); zoomTo(House.zoom, c, c, House.zoom * f, floatArrayOf(House.px, House.py)) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; srx = House.rx; srz = House.rz; moved = false; pinch = false; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                pinch = true; moved = true
                pd = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)
                pm = floatArrayOf((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2); pz = House.zoom; pp = floatArrayOf(House.px, House.py)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinch && e.pointerCount >= 2) {
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)
                    zoomTo(pz, pm, floatArrayOf((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2), pz * d / pd, pp)
                } else if (!pinch) {
                    val dx = e.x - sx; val dy = e.y - sy
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) { House.rz = srz - dx * 0.35f; House.rx = (srx - dy * 0.25f).coerceIn(18f, 78f); invalidate() }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.pointerCount <= 2) { pinch = false; sx = e.x; sy = e.y; srx = House.rx; srz = House.rz }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); if (!moved) tap(e.x, e.y) }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun segDist(x: Float, y: Float, s: FloatArray): Float {
        val dx = s[2] - s[0]; val dy = s[3] - s[1]; val l = dx * dx + dy * dy
        val u = if (l == 0f) 0f else (((x - s[0]) * dx + (y - s[1]) * dy) / l).coerceIn(0f, 1f)
        return hypot(x - (s[0] + u * dx), y - (s[1] + u * dy))
    }

    private fun inQuad(xs: FloatArray, ys: FloatArray, x: Float, y: Float): Boolean {
        var ins = false; var j = xs.size - 1
        for (i in xs.indices) { if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) ins = !ins; j = i }
        return ins
    }

    private fun tap(x: Float, y: Float) {
        var best: String? = null; var bd = kit.dp(28).toFloat()
        for (t in thingsHit) { val d = hypot(x - t.x, y - t.y); if (d < kit.dp(22)) { onThing(t.id); return } }
        for (p in pins) { val d = if (p.seg != null) segDist(x, y, p.seg) + 4 else hypot(x - p.x, y - p.y); if (d < bd) { bd = d; best = p.id } }
        if (best != null) { onPin(best); return }
        for (i in roomsHit.indices.reversed()) {
            val (id, polys) = roomsHit[i]
            var k = 0
            while (k < polys.size) { if (inQuad(polys[k], polys[k + 1], x, y)) { onRoom(id); return }; k += 2 }
        }
    }
}

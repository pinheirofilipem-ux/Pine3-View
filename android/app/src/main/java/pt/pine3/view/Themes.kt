package pt.pine3.view

/** Gerado a partir de THEMES / THEME_NAMES / LIGHT_THEMES do const.js do Pine3 View. */
class Glow(val rx: Float, val ry: Float, val cx: Float, val cy: Float, val color: Int, val stop: Float)
class LinearBg(val angle: Float, val colors: IntArray, val pos: FloatArray)
class Theme(val key: String, val name: String, val light: Boolean, val glows: List<Glow>, val base: LinearBg)

object Themes {
    val all: List<Theme> = listOf(
        Theme("ameixa", "Ameixa", false, listOf(Glow(0.9f, 0.7f, 0.55f, 0.35f, 0xFF7B4467.toInt(), 0.6f), Glow(0.7f, 0.6f, 0.0f, 0.0f, 0xFF4A2440.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 1.0f, 0xFF2C1630.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFF3A1D36.toInt(), 0xFF1D0F1F.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("pinheiro", "Pinheiro", false, listOf(Glow(1.2f, 0.9f, 0.1f, 0.0f, 0xFF2F6B4A.toInt(), 0.55f), Glow(0.9f, 0.8f, 1.0f, 1.0f, 0xFF1D3F5E.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFF11261B.toInt(), 0xFF0B1520.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("noite", "Noite", false, listOf(Glow(1.0f, 0.8f, 0.8f, 0.0f, 0xFF3A2D6B.toInt(), 0.55f), Glow(0.9f, 0.7f, 0.0f, 1.0f, 0xFF1A3A5C.toInt(), 0.6f)), LinearBg(170.0f, intArrayOf(0xFF121026.toInt(), 0xFF070912.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("aurora", "Aurora", false, listOf(Glow(0.9f, 0.7f, 0.0f, 0.0f, 0xFF1F8A7A.toInt(), 0.55f), Glow(0.8f, 0.7f, 1.0f, 0.3f, 0xFF6A3FA0.toInt(), 0.6f)), LinearBg(180.0f, intArrayOf(0xFF0E1E2A.toInt(), 0xFF120C22.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("areia", "Areia", false, listOf(Glow(1.0f, 0.8f, 0.2f, 0.0f, 0xFFA0724A.toInt(), 0.55f), Glow(0.8f, 0.7f, 1.0f, 1.0f, 0xFF5A4A7A.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFF3A2A20.toInt(), 0xFF1A1420.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("oceano", "Oceano", false, listOf(Glow(1.0f, 0.8f, 0.9f, 0.0f, 0xFF1E6FA8.toInt(), 0.55f), Glow(0.8f, 0.7f, 0.0f, 1.0f, 0xFF0F8A8A.toInt(), 0.6f)), LinearBg(170.0f, intArrayOf(0xFF0A1F33.toInt(), 0xFF06121C.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("nebulosa", "Nebulosa", false, listOf(Glow(0.6f, 0.55f, 0.18f, 0.2f, 0x57966CD6.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.84f, 0.28f, 0x425482CE.toInt(), 0.7f), Glow(0.6f, 0.55f, 0.58f, 0.98f, 0x3D7A48A6.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF1A1530.toInt(), 0xFF111827.toInt(), 0xFF0A0F18.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("floresta", "Floresta", false, listOf(Glow(0.6f, 0.55f, 0.16f, 0.22f, 0x4756A87C.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.86f, 0.72f, 0x383A8C98.toInt(), 0.7f), Glow(0.5f, 0.45f, 0.7f, 0.08f, 0x248CAA60.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF18241E.toInt(), 0xFF0F1613.toInt(), 0xFF080C0A.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("ardosia", "Ardósia", false, listOf(Glow(0.6f, 0.55f, 0.22f, 0.14f, 0x388894B6.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.86f, 0.86f, 0x2E687C9E.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF1C1E26.toInt(), 0xFF13151B.toInt(), 0xFF0B0C10.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("vinho", "Vinho", false, listOf(Glow(0.6f, 0.55f, 0.18f, 0.18f, 0x4CC45C86.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.86f, 0.62f, 0x427C3E92.toInt(), 0.7f), Glow(0.5f, 0.45f, 0.4f, 1.0f, 0x2E963C5A.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF2A1424.toInt(), 0xFF1A0E1A.toInt(), 0xFF100810.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("meia_noite", "Meia-noite", false, listOf(Glow(0.6f, 0.55f, 0.82f, 0.14f, 0x4C407CE2.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.14f, 0.86f, 0x332AAACC.toInt(), 0.7f), Glow(0.45f, 0.4f, 0.4f, 0.3f, 0x246E5AC8.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF0F1630.toInt(), 0xFF0B1122.toInt(), 0xFF060912.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("brasa", "Brasa", false, listOf(Glow(0.6f, 0.55f, 0.18f, 0.14f, 0x47E88E3E.toInt(), 0.7f), Glow(0.55f, 0.5f, 0.86f, 0.76f, 0x38CC4A3E.toInt(), 0.7f), Glow(0.45f, 0.4f, 0.6f, 0.4f, 0x14F0BE6E.toInt(), 0.7f)), LinearBg(155.0f, intArrayOf(0xFF261A14.toInt(), 0xFF18110E.toInt(), 0xFF0E0A08.toInt()), floatArrayOf(0.0f, 0.55f, 1.0f))),
        Theme("ambar", "Âmbar", false, listOf(Glow(0.9f, 0.75f, 0.15f, 0.05f, 0xFFFFC24A.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.95f, 0.3f, 0xFFFF7A2F.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.5f, 1.1f, 0xFFB8360F.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFFF0901F.toInt(), 0xFFA33A12.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("coral", "Coral", false, listOf(Glow(0.9f, 0.75f, 0.1f, 0.0f, 0xFFFF9A7A.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 0.4f, 0xFFFF4F6D.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.4f, 1.1f, 0xFF9C1F4A.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFFF0625A.toInt(), 0xFF8F1F45.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("lima", "Lima", false, listOf(Glow(0.9f, 0.75f, 0.15f, 0.0f, 0xFFC6F25A.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 0.5f, 0xFF2FBF71.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.5f, 1.1f, 0xFF0B6B4A.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFF6FCF3F.toInt(), 0xFF0F6A45.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("azul", "Azul", false, listOf(Glow(0.9f, 0.75f, 0.85f, 0.0f, 0xFF5AC8FF.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.0f, 0.5f, 0xFF2F6BFF.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.6f, 1.1f, 0xFF1B2A9E.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFF3D8BFF.toInt(), 0xFF1B2B8F.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("violeta", "Violeta", false, listOf(Glow(0.9f, 0.75f, 0.15f, 0.0f, 0xFFC08BFF.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 0.4f, 0xFF7A4DFF.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.4f, 1.1f, 0xFF3C1A8F.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFF8D5CF6.toInt(), 0xFF3D1C8A.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("magenta", "Magenta", false, listOf(Glow(0.9f, 0.75f, 0.85f, 0.0f, 0xFFFF8AD8.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.0f, 0.5f, 0xFFE0359F.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.5f, 1.1f, 0xFF7A1466.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFFE64AA8.toInt(), 0xFF6F1A6B.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("turquesa", "Turquesa", false, listOf(Glow(0.9f, 0.75f, 0.15f, 0.0f, 0xFF7FF0E0.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 0.5f, 0xFF14B8C4.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.5f, 1.1f, 0xFF0B5C78.toInt(), 0.65f)), LinearBg(160.0f, intArrayOf(0xFF22C3B8.toInt(), 0xFF0D5A74.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("por_do_sol", "Pôr do sol", false, listOf(Glow(0.9f, 0.7f, 0.1f, 0.0f, 0xFFFFD36B.toInt(), 0.55f), Glow(0.8f, 0.7f, 1.0f, 0.2f, 0xFFFF6B6B.toInt(), 0.6f), Glow(0.9f, 0.8f, 0.3f, 1.1f, 0xFF6A3FD0.toInt(), 0.65f)), LinearBg(170.0f, intArrayOf(0xFFFF9A4A.toInt(), 0xFF5A2FA8.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("linho", "Linho", true, listOf(Glow(0.9f, 0.7f, 0.15f, 0.0f, 0xFFFFFAF1.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 1.0f, 0xFFE8D9C4.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFFF6EFE4.toInt(), 0xFFE9DFD0.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("nevoa", "Névoa", true, listOf(Glow(0.9f, 0.7f, 0.85f, 0.0f, 0xFFFFFFFF.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.0f, 1.0f, 0xFFCFDBE8.toInt(), 0.6f)), LinearBg(170.0f, intArrayOf(0xFFEEF2F7.toInt(), 0xFFDFE6EF.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("pessego", "Pêssego", true, listOf(Glow(0.9f, 0.7f, 0.1f, 0.0f, 0xFFFFF3EC.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 0.9f, 0xFFF4CDBF.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFFFBE9E1.toInt(), 0xFFF1DCD6.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("menta", "Menta", true, listOf(Glow(0.9f, 0.7f, 0.9f, 0.0f, 0xFFF6FFFB.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.0f, 1.0f, 0xFFC6E5D8.toInt(), 0.6f)), LinearBg(170.0f, intArrayOf(0xFFE9F5F0.toInt(), 0xFFD9EBE3.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("lavanda", "Lavanda", true, listOf(Glow(0.9f, 0.7f, 0.2f, 0.0f, 0xFFFBF8FF.toInt(), 0.6f), Glow(0.8f, 0.7f, 1.0f, 1.0f, 0xFFD6CDEE.toInt(), 0.6f)), LinearBg(160.0f, intArrayOf(0xFFF0ECF8.toInt(), 0xFFE2DCF0.toInt()), floatArrayOf(0.0f, 1.0f))),
        Theme("ceu", "Céu", true, listOf(Glow(0.9f, 0.7f, 0.8f, 0.0f, 0xFFFFFFFF.toInt(), 0.6f), Glow(0.8f, 0.7f, 0.0f, 1.0f, 0xFFBCD8F0.toInt(), 0.6f)), LinearBg(170.0f, intArrayOf(0xFFE7F1FA.toInt(), 0xFFD4E5F4.toInt()), floatArrayOf(0.0f, 1.0f))),
    )

    fun get(key: String?): Theme = all.firstOrNull { it.key == key } ?: all[0]

    /** O tema em uso: o fixo, ou o de dia/noite conforme o sol (sun.sun; sem ele, das 7h às 19h). */
    fun pick(settings: org.json.JSONObject, sunState: String?, hour: Int): Theme {
        if (settings.optString("appearance", "fixed") == "auto") {
            val day = if (sunState != null) sunState == "above_horizon" else hour in 7..18
            return get(settings.optString(if (day) "theme_day" else "theme_night", if (day) "linho" else "ameixa"))
        }
        return get(settings.optString("theme", "ameixa"))
    }
}

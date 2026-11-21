package pt.pine3.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import java.io.BufferedReader
import java.io.InputStreamReader

/** Os 7000+ ícones Material Design Icons (os mesmos do Home Assistant), lidos de um único ficheiro em assets. */
object Mdi {
    private var table: HashMap<String, String>? = null
    private val paths = HashMap<String, Path>()

    private fun table(ctx: Context): HashMap<String, String> = table ?: synchronized(this) {
        table ?: HashMap<String, String>(8192).also { map ->
            ctx.applicationContext.assets.open("mdi.tsv").use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8), 64 * 1024).forEachLine { line ->
                    val t = line.indexOf('\t')
                    if (t > 0) map[line.substring(0, t)] = line.substring(t + 1)
                }
            }
            table = map
        }
    }

    /** Nome com ou sem prefixo "mdi:"; devolve null se o ícone não existir. */
    fun path(ctx: Context, name: String): Path? {
        val key = name.removePrefix("mdi:")
        synchronized(paths) { paths[key]?.let { return it } }
        val d = table(ctx)[key] ?: return null
        val p = Path()
        try {
            SvgPath.parse(d, object : PathSink {
                override fun moveTo(x: Float, y: Float) = p.moveTo(x, y)
                override fun lineTo(x: Float, y: Float) = p.lineTo(x, y)
                override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = p.cubicTo(x1, y1, x2, y2, x, y)
                override fun close() = p.close()
            })
        } catch (_: Exception) { return null }
        synchronized(paths) { paths[key] = p }
        return p
    }

    fun drawable(ctx: Context, name: String): Drawable {
        val p = path(ctx, name) ?: path(ctx, "help-circle-outline") ?: Path()
        return MdiDrawable(p)
    }
}

/** Um ícone MDI (viewBox 24x24) desenhado a branco; a cor vem do filtro de cor do ImageView. */
class MdiDrawable(private val path: Path) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 24f, b.height() / 24f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

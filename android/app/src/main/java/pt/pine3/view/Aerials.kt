package pt.pine3.view

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Os vídeos do ecrã de descanso da Apple TV (aerials). A fonte é o `resources-atv-*.tar` da Apple, onde está o `entries.json`
 * com os endereços dos vídeos por qualidade. O tar é lido em fluxo e pára-se assim que o `entries.json` aparece; a lista fica em cache.
 */
object Aerials {
    const val DEFAULT_SOURCE = "https://sylvan.apple.com/itunes-assets/Aerials126/v4/c0/45/d9/c045d9d0-9606-1535-62fe-189edb4f79eb/resources-atv-23J-2.tar"
    private const val TTL_MS = 7L * 24 * 3600 * 1000

    class Clip(val id: String, val label: String, val url: String)

    @Volatile private var clips: List<Clip> = emptyList()
    @Volatile var error: String? = null
    private var loading = false

    private fun cache(ctx: Context) = File(ctx.filesDir, "aerials-entries.json")

    /** Os chaves de URL por ordem de preferência, para a qualidade escolhida ("1080" ou "4k"). */
    private fun keys(quality: String) = if (quality == "4k")
        listOf("url-4K-SDR-240FPS", "url-4K-SDR", "url-4K-HDR", "url-1080-SDR", "url-1080-H264")
    else listOf("url-1080-H264", "url-1080-SDR", "url-1080-HDR")

    private fun parse(json: String, quality: String): List<Clip> {
        val assets = JSONObject(json).optJSONArray("assets") ?: return emptyList()
        val prefs = keys(quality)
        return (0 until assets.length()).mapNotNull { i ->
            val a = assets.optJSONObject(i) ?: return@mapNotNull null
            if (a.has("includeInShuffle") && !a.optBoolean("includeInShuffle", true)) return@mapNotNull null
            val url = prefs.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.startsWith("http") } }
                ?: a.keys().asSequence().filter { it.startsWith("url-") }.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.startsWith("http") } }
                ?: return@mapNotNull null
            Clip(a.optString("id", "$i"), a.optString("accessibilityLabel"), url)
        }
    }

    /** Lê do tar (em fluxo) o conteúdo do primeiro ficheiro cujo nome acaba em [wanted]. */
    internal fun readTarEntry(input: InputStream, wanted: String): ByteArray? {
        val din = DataInputStream(input.buffered(1 shl 16))
        val hdr = ByteArray(512)
        var longName: String? = null
        while (true) {
            try { din.readFully(hdr) } catch (_: java.io.EOFException) { return null }
            if (hdr.all { it.toInt() == 0 }) return null
            fun str(off: Int, len: Int) = String(hdr, off, len, Charsets.UTF_8).substringBefore('\u0000')
            var name = str(0, 100)
            val prefix = str(345, 155)
            if (prefix.isNotEmpty() && str(257, 5) == "ustar") name = "$prefix/$name"
            val size = str(124, 12).trim().ifEmpty { "0" }.toLong(8)
            val type = hdr[156].toInt().toChar()
            val padded = (size + 511) / 512 * 512
            fun skipAll(n: Long) { var left = n; while (left > 0) { val s = din.skip(left); if (s <= 0) { din.readByte(); left-- } else left -= s } }
            if (type == 'L') { // nome longo do GNU tar: o conteúdo é o nome do ficheiro seguinte
                val data = ByteArray(size.toInt()); din.readFully(data); skipAll(padded - size)
                longName = String(data, Charsets.UTF_8).substringBefore('\u0000'); continue
            }
            if (type == 'x') { // cabeçalho pax: procura "path=..."
                val data = ByteArray(size.toInt()); din.readFully(data); skipAll(padded - size)
                Regex("\\d+ path=([^\\n]*)\\n").find(String(data, Charsets.UTF_8))?.let { longName = it.groupValues[1] }
                continue
            }
            val full = longName ?: name
            longName = null
            if ((type == '0' || type == '\u0000') && full.endsWith(wanted)) {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(1 shl 16); var left = size
                while (left > 0) { val n = din.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; out.write(buf, 0, n); left -= n }
                return out.toByteArray()
            }
            skipAll(padded)
        }
    }

    /** A lista de vídeos (da cache, ou descarregada). [done] corre na main thread com o número de vídeos (0 = falhou, ver [error]). */
    fun load(ctx: Context, source: String, quality: String, force: Boolean = false, done: (Int) -> Unit) {
        val f = cache(ctx)
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < TTL_MS) {
            try { clips = parse(f.readText(), quality); if (clips.isNotEmpty()) { error = null; done(clips.size); return } } catch (_: Exception) {}
        }
        if (loading) return
        loading = true
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var n = 0
            try {
                val c = (URL(source.ifBlank { DEFAULT_SOURCE }).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
                val data = c.inputStream.use { readTarEntry(it, "entries.json") } ?: throw java.io.IOException("entries.json não encontrado no ficheiro da Apple")
                val json = String(data, Charsets.UTF_8)
                val list = parse(json, quality)
                if (list.isEmpty()) throw java.io.IOException("a lista de vídeos veio vazia")
                f.writeText(json); clips = list; error = null; n = list.size
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            h.post { loading = false; done(n) }
        }.start()
    }

    /** Volta a escolher o URL de cada vídeo (ex.: mudou a qualidade) sem ir à rede. */
    fun reparse(ctx: Context, quality: String) { try { cache(ctx).takeIf { it.exists() }?.let { clips = parse(it.readText(), quality) } } catch (_: Exception) {} }

    @Volatile private var extra: List<Clip> = emptyList()

    /** Vídeos próprios (um endereço por linha), por exemplo os clipes do Snoopy alojados no teu servidor. */
    fun setExtra(text: String) {
        extra = text.lines().map { it.trim() }.filter { it.startsWith("http") }.mapIndexed { i, u -> Clip("extra$i", "", u) }
    }

    /** Os vídeos pela ordem do sorteio. */
    fun shuffled(): List<Clip> = (clips + extra).shuffled()
    val count get() = clips.size + extra.size
}

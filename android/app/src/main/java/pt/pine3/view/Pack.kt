package pt.pine3.view

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Pacote de clipes guardado no tablet: descarrega um zip (por omissão o `snoopy.saver.zip` do repositório hitnology/snoopy), tira dele só os
 * ficheiros .mov e guarda-os na pasta da app. Depois o ecrã de descanso reproduz-os do disco, sem rede.
 */
object Pack {
    const val DEFAULT_URL = "https://github.com/hitnology/snoopy/releases/download/0.2.2/snoopy.saver.zip"

    @Volatile var running = false
    @Volatile var progress = ""
    @Volatile var error: String? = null

    private fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "clips").also { it.mkdirs() }

    fun files(ctx: Context): List<File> = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".mov", true) && f.length() > 0 }?.sortedBy { it.name }.orEmpty()

    /** Regra por omissão: ignora os "loop" e os ficheiros pequenos. */
    fun defaultOn(f: File, minMb: Int) = !f.name.contains("loop", true) && f.length() >= minMb * 1_000_000L

    fun isOn(f: File, prefs: Prefs) = prefs.clipChoices[f.name] ?: defaultOn(f, prefs.clipMinMb)

    /** Só os clipes escolhidos. */
    fun enabled(ctx: Context): List<File> { val p = Prefs(ctx); return files(ctx).filter { isOn(it, p) } }

    fun describe(ctx: Context): String {
        if (running) return progress
        val f = files(ctx)
        val mb = f.sumOf { it.length() } / 1_000_000
        return error?.let { "✘ $it" } ?: if (f.isEmpty()) "Nenhum clipe no tablet" else "${f.size} clipes no tablet ($mb MB)"
    }

    fun delete(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() }; error = null }

    private class Counting(input: InputStream, val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        var n = 0L
        override fun read(): Int = super.read().also { if (it >= 0) { n++; if (n % 65536 == 0L) onBytes(n) } }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { n += it; onBytes(n) } }
    }

    private fun save(ctx: Context, name: String, input: InputStream): Boolean {
        val out = File(dir(ctx), name.substringAfterLast('/'))
        if (out.exists() && out.length() > 0) return false
        val part = File(out.path + ".part")
        part.outputStream().buffered(1 shl 16).use { input.copyTo(it) }
        part.renameTo(out)
        return true
    }

    private fun open(url: String): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = false }
            val code = c.responseCode
            if (code in 300..399) { u = c.getHeaderField("Location") ?: throw java.io.IOException("redirecionamento sem destino"); c.disconnect() }
            else if (code == 200) return c
            else throw java.io.IOException("HTTP $code")
        }
        throw java.io.IOException("demasiados redirecionamentos")
    }

    /** Descarrega e extrai os .mov. [done] corre na main thread. */
    fun download(ctx: Context, url: String, done: () -> Unit) {
        if (running) return
        running = true; error = null; progress = "A ligar…"
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val app = ctx.applicationContext
        Thread {
            try {
                var size = 0L; var count = 0
                try {
                    // 1.º tentativa: em fluxo, sem guardar o zip (só ocupa o espaço dos clipes)
                    val c = open(url); size = c.contentLengthLong
                    val counting = Counting(c.inputStream) { n -> progress = "A descarregar… ${if (size > 0) (n * 100 / size).toString() + "%" else (n / 1_000_000).toString() + " MB"}  ($count clipes)" }
                    ZipInputStream(BufferedInputStream(counting, 1 shl 16)).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            if (!e.isDirectory && e.name.endsWith(".mov", true) && !e.name.contains("__MACOSX")) { save(app, e.name, z); count++ }
                        }
                    }
                } catch (e: java.util.zip.ZipException) {
                    // zip com entradas sem compressão e sem tamanho: descarrega o ficheiro todo e abre-o com acesso direto
                    val tmp = File(app.cacheDir, "pack.zip")
                    val c = open(url); size = c.contentLengthLong
                    tmp.outputStream().buffered(1 shl 16).use { o -> Counting(c.inputStream) { n -> progress = "A descarregar… ${if (size > 0) (n * 100 / size).toString() + "%" else ""}" }.use { it.copyTo(o) } }
                    progress = "A extrair…"
                    ZipFile(tmp).use { zf ->
                        zf.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".mov", true) && !it.name.contains("__MACOSX") }.forEach { en -> zf.getInputStream(en).use { save(app, en.name, it) }; count++ }
                    }
                    tmp.delete()
                }
                if (files(app).isEmpty()) error = "o pacote não tem ficheiros .mov"
            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            h.post { running = false; done() }
        }.start()
    }
}

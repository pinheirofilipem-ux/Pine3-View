package pt.pine3.view

import android.content.Context
import android.net.Uri
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

    /** Um clipe no tablet: ficheiro descarregado pela app ou ficheiro da pasta escolhida. [ref] é um caminho ou um endereço content://. */
    class Item(val name: String, private val size: Long, val ref: String) { fun length() = size }

    private fun downloaded(ctx: Context): List<Item> = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".mov", true) && f.length() > 0 }.orEmpty().map { Item(it.name, it.length(), it.absolutePath) }

    private var folderCache: Triple<String, Long, List<Item>>? = null

    private fun walkTree(ctx: Context, tree: String, out: MutableList<Item>) {
        try {
            val uri = android.net.Uri.parse(tree)
            fun walk(docId: String, depth: Int) {
                val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
                ctx.contentResolver.query(children, arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE, android.provider.DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2) ?: ""; val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 2) walk(id, depth + 1) }
                        else if (mime.startsWith("video/") || Regex("\\.(mov|mp4|m4v)$", RegexOption.IGNORE_CASE).containsMatchIn(name)) out += Item(name, size, android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, id).toString())
                    }
                }
            }
            walk(android.provider.DocumentsContract.getTreeDocumentId(uri), 0)
        } catch (_: Exception) {}
    }

    /** Os vídeos das pastas escolhidas (acesso por pastas do Android, sem permissões extra), até 2 níveis de subpastas. */
    private fun folder(ctx: Context): List<Item> {
        val trees = Prefs(ctx).clipFolders
        if (trees.isEmpty()) return emptyList()
        val key = trees.joinToString("|")
        folderCache?.let { if (it.first == key && System.currentTimeMillis() - it.second < 30_000) return it.third }
        val out = ArrayList<Item>()
        trees.forEach { walkTree(ctx, it, out) }
        val list = out.sortedBy { it.name }
        folderCache = Triple(key, System.currentTimeMillis(), list)
        return list
    }

    fun folderName(tree: String): String = Uri.decode(tree).substringAfterLast(':').substringAfterLast('/').ifBlank { tree }

    fun files(ctx: Context): List<Item> = (downloaded(ctx) + folder(ctx)).distinctBy { it.name }.sortedBy { it.name }

    fun refreshFolder() { folderCache = null }

    /** Regra por omissão: ignora os fragmentos (loop, intro, outro) e os ficheiros pequenos; ficam as cenas completas. */
    fun defaultOn(f: Item, minMb: Int) = !Regex("loop|intro|outro", RegexOption.IGNORE_CASE).containsMatchIn(f.name) && f.length() >= minMb * 1_000_000L

    fun isOn(f: Item, prefs: Prefs) = prefs.clipChoices[f.name] ?: defaultOn(f, prefs.clipMinMb)

    /** Só os clipes escolhidos. */
    fun enabled(ctx: Context): List<Item> { val p = Prefs(ctx); return files(ctx).filter { isOn(it, p) } }

    fun describe(ctx: Context): String {
        if (running) return progress
        val f = files(ctx)
        val mb = f.sumOf { it.length() } / 1_000_000
        return error?.let { "✘ " + Tr.t(it) } ?: if (f.isEmpty()) Tr.s("Nenhum clipe no tablet", "No clips on the tablet") else Tr.s("${f.size} clipes no tablet ($mb MB)", "${f.size} clips on the tablet ($mb MB)")
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
        running = true; error = null; progress = Tr.s("A ligar…", "Connecting…")
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val app = ctx.applicationContext
        Thread {
            try {
                var size = 0L; var count = 0
                try {
                    // 1.º tentativa: em fluxo, sem guardar o zip (só ocupa o espaço dos clipes)
                    val c = open(url); size = c.contentLengthLong
                    val counting = Counting(c.inputStream) { n -> progress = Tr.s("A descarregar… ", "Downloading… ") + (if (size > 0) (n * 100 / size).toString() + "%" else (n / 1_000_000).toString() + " MB") + Tr.s("  ($count clipes)", "  ($count clips)") }
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
                    tmp.outputStream().buffered(1 shl 16).use { o -> Counting(c.inputStream) { n -> progress = Tr.s("A descarregar… ", "Downloading… ") + (if (size > 0) (n * 100 / size).toString() + "%" else "") }.use { it.copyTo(o) } }
                    progress = Tr.s("A extrair…", "Extracting…")
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

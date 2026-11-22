package pt.pine3.view

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface

/**
 * Reprodução sem som com MediaExtractor + MediaCodec, para quando o MediaPlayer recusa o ficheiro (ex.: 4K HEVC que o descodificador de
 * hardware não aceita): tenta todos os descodificadores do sistema, o de hardware primeiro e o de software (c2.android…) a seguir.
 */
open class CodecPlayer(
    private val ctx: Context, private val src: String, private val surface: Surface,
    private val onSize: (Int, Int) -> Unit, private val onFirstFrame: (String) -> Unit, private val onDone: () -> Unit, private val onError: (String) -> Unit,
) {
    @Volatile private var stop = false
    private var thread: Thread? = null

    fun start() { thread = Thread { run() }.also { it.start() } }
    fun stop() { stop = true }

    private fun run() {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (src.startsWith("content:")) ex.setDataSource(ctx, Uri.parse(src), null) else ex.setDataSource(src)
            val t = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: run { onError("sem faixa de vídeo"); return }
            ex.selectTrack(t)
            val f = ex.getTrackFormat(t)
            val mime = f.getString(MediaFormat.KEY_MIME)!!
            onSize(f.getInteger(MediaFormat.KEY_WIDTH), f.getInteger(MediaFormat.KEY_HEIGHT))
            // hardware primeiro, software depois
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder && it.supportedTypes.any { s -> s.equals(mime, true) } }
                .sortedBy { if (it.name.startsWith("c2.android") || it.name.startsWith("OMX.google")) 1 else 0 }
            var last = "nenhum descodificador para $mime"
            var name = ""
            for (info in infos) {
                try {
                    val c = MediaCodec.createByCodecName(info.name)
                    c.configure(f, surface, null, 0); c.start()
                    codec = c; name = info.name; break
                } catch (e: Exception) { last = info.name + ": " + (e.message ?: e.javaClass.simpleName) }
            }
            val c = codec ?: run { onError(last); return }
            val bi = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false; var shown = false
            var firstPts = -1L; var startNs = 0L
            while (!outDone && !stop) {
                if (!inDone) {
                    val i = c.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = c.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { c.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = c.dequeueOutputBuffer(bi, 5_000)
                if (o >= 0) {
                    if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (firstPts < 0) { firstPts = bi.presentationTimeUs; startNs = System.nanoTime() }
                    val waitMs = (startNs + (bi.presentationTimeUs - firstPts) * 1000 - System.nanoTime()) / 1_000_000
                    if (waitMs > 0 && !stop) try { Thread.sleep(minOf(waitMs, 200)) } catch (_: InterruptedException) {}
                    c.releaseOutputBuffer(o, bi.size > 0)
                    if (!shown && bi.size > 0) { shown = true; onFirstFrame(name) }
                }
            }
            if (!stop) onDone()
        } catch (e: Exception) {
            if (!stop) onError(e.message ?: e.javaClass.simpleName)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { ex.release() } catch (_: Exception) {}
        }
    }
}

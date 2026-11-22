package pt.pine3.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Última imagem de cada câmara, guardada em disco ao fechar a vista ao vivo. Os cartões mostram só isto, nunca ligam a câmara. */
object CamCache {
    private fun file(ctx: Context, id: String) = File(ctx.filesDir, "cam_" + id.replace(Regex("[^a-zA-Z0-9_]"), "_") + ".jpg")

    fun stamp(ctx: Context, id: String): Long = file(ctx, id).let { if (it.exists()) it.lastModified() else 0L }

    fun load(ctx: Context, id: String): Bitmap? = try { file(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } } catch (_: Exception) { null }

    fun save(ctx: Context, id: String, bmp: Bitmap) {
        try { file(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) } } catch (_: Exception) {}
    }
}

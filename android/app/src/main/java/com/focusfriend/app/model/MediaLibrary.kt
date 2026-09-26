package com.focusfriend.app.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MediaKind { SOUND, PICTURE }

/** A sound or picture the person added. The file lives in the app's private storage only. */
data class UserMedia(val id: String, val kind: MediaKind, val name: String, val file: File)

/** Your own sounds and pictures, copied into the app's private storage. */
class MediaLibrary(private val context: Context) {
    private val dir = File(context.filesDir, "media").apply { mkdirs() }
    private val index = File(dir, "index.json")

    /** Skips missing or corrupted entries instead of failing the whole list. */
    fun list(): List<UserMedia> = try {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = when (o.optString("kind")) { "snd" -> MediaKind.SOUND; "img" -> MediaKind.PICTURE; else -> null } ?: return@mapNotNull null
            val file = File(dir, o.optString("file"))
            val id = o.optString("id")
            if (id.isEmpty() || !file.isFile) null else UserMedia(id, kind, o.optString("name").ifEmpty { "Untitled" }, file)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Copies a picked file in. Returns the new item, or a message saying why it couldn't be added. */
    fun add(uri: Uri, kind: MediaKind): Result<UserMedia> = runCatching {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty()
        val (displayName, size) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "Untitled") to (if (c.isNull(1)) -1L else c.getLong(1)) else null
        } ?: ("Untitled" to -1L)

        val prefix = if (kind == MediaKind.SOUND) "snd:" else "img:"
        val id = prefix + newId()
        val fileName = id.replace(':', '_')
        val file = File(dir, fileName)

        when (kind) {
            MediaKind.SOUND -> {
                if (type.isNotEmpty() && !type.startsWith("audio/")) fail("That file isn't a sound.")
                if (size > 20L * 1024 * 1024) fail("That sound is too large. Pick one under 20 MB.")
                resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: fail("That sound can't be opened.")
                if (!isPlayable(file)) { file.delete(); fail("That sound can't be played on this device.") }
            }
            MediaKind.PICTURE -> {
                if (type.isNotEmpty() && !type.startsWith("image/")) fail("That file isn't a picture.")
                if (size > 30L * 1024 * 1024) fail("That picture is too large.")
                val bitmap = resolver.openInputStream(uri)?.use { decodeScaled(it.readBytes(), 1100) } ?: fail("That picture can't be opened.")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                bitmap.recycle()
            }
        }
        val item = UserMedia(id, kind, shortName(displayName), file)
        write(list() + item)
        item
    }

    fun remove(id: String): Boolean {
        val items = list()
        val gone = items.firstOrNull { it.id == id } ?: return false
        gone.file.delete()
        write(items - gone)
        return true
    }

    private fun write(items: List<UserMedia>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("kind", if (it.kind == MediaKind.SOUND) "snd" else "img").put("name", it.name).put("file", it.file.name))
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
    }

    private fun isPlayable(file: File): Boolean = try {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.path)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null
            } finally {
                release()
            }
        }
    } catch (e: Exception) {
        false
    }

    private fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) fail("That picture can't be opened.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: fail("That picture can't be opened.")
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    companion object {
        fun shortName(name: String): String {
            val n = name.replace(Regex("""\.[a-z0-9]{2,5}$""", RegexOption.IGNORE_CASE), "").trim().ifEmpty { "Untitled" }
            return if (n.length > 26) n.take(25) + "…" else n
        }
    }
}

class MediaError(message: String) : Exception(message)

private fun fail(message: String): Nothing = throw MediaError(message)

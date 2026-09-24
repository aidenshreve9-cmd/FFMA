package app.focusfriend.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.focusfriend.core.Catalog
import app.focusfriend.core.FocusSettings
import app.focusfriend.core.TrustedContact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Settings live in app-private SharedPreferences. The timer duration is never written.
 * Backups are disabled in the manifest, so nothing is copied off the device.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)

    fun load(): FocusSettings = try {
        FocusSettings(
            sound = prefs.getString("sound", null) ?: Catalog.DEFAULT_SOUND,
            scene = prefs.getString("scene", null) ?: Catalog.DEFAULT_SCENE,
            alarmSafety = prefs.getBoolean("alarmSafety", true),
            trustedContactsOn = prefs.getBoolean("trustedOn", false),
            contacts = parseContacts(prefs.getString("contacts", null)),
            permissionSheetSeen = prefs.getBoolean("permissionSheetSeen", false),
        )
    } catch (e: RuntimeException) {
        FocusSettings() // corrupted settings: start fresh rather than crash
    }

    fun save(s: FocusSettings) {
        prefs.edit()
            .putString("sound", s.sound)
            .putString("scene", s.scene)
            .putBoolean("alarmSafety", s.alarmSafety)
            .putBoolean("trustedOn", s.trustedContactsOn)
            .putString("contacts", JSONArray().apply {
                s.contacts.forEach { c ->
                    put(JSONObject().put("id", c.id).put("name", c.name).put("number", c.number).put("normalized", c.normalized)
                        .apply { c.lookupKey?.let { put("lookupKey", it) } })
                }
            }.toString())
            .putBoolean("permissionSheetSeen", s.permissionSheetSeen)
            .apply()
    }

    private fun parseContacts(json: String?): List<TrustedContact> {
        if (json.isNullOrEmpty()) return emptyList()
        val arr = try { JSONArray(json) } catch (e: org.json.JSONException) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TrustedContact(o.optString("id"), o.optString("name"), o.optString("number"), o.optString("normalized"), o.optString("lookupKey").ifEmpty { null })
        }
    }
}

/** One of the person's own sounds or pictures, stored in app-private files. */
data class MediaItem(val id: String, val kind: Kind, val name: String, val file: File) {
    enum class Kind { SOUND, PICTURE }
}

/**
 * Imports, lists and removes the person's own sounds and pictures. Files are copied into
 * app-private storage (pictures shrunk to 1100 px, JPEG 82) and never uploaded.
 */
class MediaLibrary(private val context: Context) {
    sealed interface ImportResult {
        data class Added(val item: MediaItem) : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    private val dir = File(context.filesDir, "media").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun all(): List<MediaItem> = try {
        val arr = JSONArray(index.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val f = File(dir, o.optString("file"))
            val kind = runCatching { MediaItem.Kind.valueOf(o.optString("kind")) }.getOrNull()
            // Deleted or missing media is skipped; selections fall back to defaults.
            if (kind == null || !f.isFile) null else MediaItem(o.optString("id"), kind, o.optString("name", "Untitled"), f)
        }
    } catch (e: IOException) { emptyList() } catch (e: org.json.JSONException) { emptyList() }

    fun sounds() = all().filter { it.kind == MediaItem.Kind.SOUND }
    fun pictures() = all().filter { it.kind == MediaItem.Kind.PICTURE }

    fun importSound(uri: Uri): ImportResult {
        val name = displayName(uri)
        val size = sizeOf(uri)
        if (size > MAX_SOUND_BYTES) return ImportResult.Failed("That sound is too large. Pick one under 20 MB.")
        val id = Catalog.USER_SOUND_PREFIX + UUID.randomUUID().toString().take(12)
        val out = File(dir, id.replace(':', '_') + ".audio")
        return try {
            copy(uri, out)
            if (!isPlayableAudio(out)) { out.delete(); return ImportResult.Failed("That sound can't be played on this device.") }
            val item = MediaItem(id, MediaItem.Kind.SOUND, shortName(name), out)
            persist(all() + item)
            ImportResult.Added(item)
        } catch (e: IOException) {
            out.delete()
            ImportResult.Failed(if (isStorageFull(e)) "Your phone is full. Free up space and try again." else "Couldn't save that file on this phone.")
        }
    }

    fun importPicture(uri: Uri): ImportResult {
        val id = Catalog.USER_PICTURE_PREFIX + UUID.randomUUID().toString().take(12)
        val out = File(dir, id.replace(':', '_') + ".jpg")
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_PICTURE_PX) {
                    val s = MAX_PICTURE_PX.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            out.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)) throw IOException("encode") }
            bitmap.recycle()
            val item = MediaItem(id, MediaItem.Kind.PICTURE, shortName(displayName(uri)), out)
            persist(all() + item)
            ImportResult.Added(item)
        } catch (e: IOException) {
            out.delete()
            ImportResult.Failed(if (isStorageFull(e)) "Your phone is full. Free up space and try again." else "That picture can't be opened.")
        } catch (e: RuntimeException) {
            out.delete()
            ImportResult.Failed("That picture can't be opened.")
        }
    }

    fun remove(id: String): Boolean {
        val items = all()
        val target = items.firstOrNull { it.id == id } ?: return false
        target.file.delete()
        return try { persist(items - target); true } catch (e: IOException) { false }
    }

    fun decodePicture(item: MediaItem, maxPx: Int): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(item.file.path, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(item.file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: RuntimeException) { null }

    private fun persist(items: List<MediaItem>) {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("name", it.name).put("file", it.file.name)) }
        }.toString())
        if (!tmp.renameTo(index)) { index.delete(); if (!tmp.renameTo(index)) throw IOException("could not save media index") }
    }

    private fun copy(uri: Uri, out: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("unreadable")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    }

    private fun isPlayableAudio(f: File): Boolean {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: RuntimeException) { false } finally { runCatching { r.release() } }
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Untitled"
    } catch (e: RuntimeException) { "Untitled" }

    private fun sizeOf(uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    } catch (e: RuntimeException) { 0L }

    private fun isStorageFull(e: IOException) = e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true

    companion object {
        const val MAX_SOUND_BYTES = 20L * 1024 * 1024
        const val MAX_PICTURE_PX = 1100
        fun shortName(n: String): String {
            val base = n.replace(Regex("""\.[A-Za-z0-9]{2,5}$"""), "").trim().ifEmpty { "Untitled" }
            return if (base.length > 26) base.take(25) + "…" else base
        }
    }
}

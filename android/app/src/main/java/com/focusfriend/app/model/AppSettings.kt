package com.focusfriend.app.model

import org.json.JSONArray
import org.json.JSONObject

data class Contact(val id: String, val name: String, val number: String, val normalized: String)

/** What the person has chosen. Stored on this device only. */
data class AppSettings(
    val audio: String = Catalog.DEFAULT_SOUND,
    val scene: String = Catalog.DEFAULT_SCENE,
    val alarmSafety: Boolean = true,
    val trustedOn: Boolean = false,
    val contacts: List<Contact> = emptyList(),
)

fun digitsOf(value: String): String = value.filter { it in '0'..'9' }

fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) +
    java.lang.Long.toString((Math.random() * 1e9).toLong(), 36)

object SettingsCodec {
    const val MAX_CONTACTS = 50

    fun encode(s: AppSettings): String = JSONObject()
        .put("v", 1)
        .put("audio", s.audio)
        .put("scene", s.scene)
        .put("alarmSafety", s.alarmSafety)
        .put("trustedOn", s.trustedOn)
        .put("contacts", JSONArray().apply {
            s.contacts.forEach { c ->
                put(JSONObject().put("id", c.id).put("name", c.name).put("number", c.number).put("normalized", c.normalized))
            }
        })
        .toString()

    /** Anything missing, corrupt or from another version falls back to the defaults. */
    fun decode(raw: String?): AppSettings {
        val defaults = AppSettings()
        if (raw.isNullOrBlank()) return defaults
        return try {
            val o = JSONObject(raw)
            if (o.optInt("v") != 1) return defaults
            val contacts = mutableListOf<Contact>()
            val arr = o.optJSONArray("contacts") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val name = c.opt("name") as? String ?: continue
                val number = c.opt("number") as? String ?: continue
                if (digitsOf(number).length < 3) continue
                val id = (c.opt("id") as? String)?.takeIf { it.isNotEmpty() } ?: newId()
                contacts += Contact(id, name.take(40), number, digitsOf(number))
                if (contacts.size >= MAX_CONTACTS) break
            }
            AppSettings(
                audio = o.opt("audio") as? String ?: defaults.audio,
                scene = o.opt("scene") as? String ?: defaults.scene,
                alarmSafety = o.opt("alarmSafety") as? Boolean ?: defaults.alarmSafety,
                trustedOn = o.opt("trustedOn") as? Boolean ?: defaults.trustedOn,
                contacts = contacts,
            )
        } catch (e: Exception) {
            defaults
        }
    }

    /** Drops choices that no longer exist (a removed sound or picture, an old scene id). */
    fun validate(s: AppSettings, userMediaIds: Set<String>): AppSettings {
        val soundOk = if (s.audio.startsWith("snd:")) s.audio in userMediaIds
        else s.audio == Catalog.RANDOM || s.audio == Catalog.NO_SOUND || Catalog.sound(s.audio) != null
        val sceneOk = if (s.scene.startsWith("img:")) s.scene in userMediaIds
        else s.scene == Catalog.RANDOM || Catalog.scene(s.scene) != null
        return s.copy(
            audio = if (soundOk) s.audio else Catalog.DEFAULT_SOUND,
            scene = if (sceneOk) s.scene else Catalog.DEFAULT_SCENE,
        )
    }
}

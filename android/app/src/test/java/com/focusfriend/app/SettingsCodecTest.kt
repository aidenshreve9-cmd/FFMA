package com.focusfriend.app

import com.focusfriend.app.model.AppSettings
import com.focusfriend.app.model.Catalog
import com.focusfriend.app.model.Contact
import com.focusfriend.app.model.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {
    @Test fun roundTrips() {
        val s = AppSettings("pink", "galaxy", false, true, listOf(Contact("a", "Sam", "780 555 1234", "7805551234")))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test fun corruptOrMissingFallsBackToDefaults() {
        assertEquals(AppSettings(), SettingsCodec.decode(null))
        assertEquals(AppSettings(), SettingsCodec.decode("{not json"))
        assertEquals(AppSettings(), SettingsCodec.decode("""{"v":2,"audio":"pink"}"""))
    }

    @Test fun badContactsAreDropped() {
        val s = SettingsCodec.decode("""{"v":1,"contacts":[{"name":"A","number":"12"},{"name":"B","number":"403 555 1234"},{"number":"5551234"}]}""")
        assertEquals(listOf("B"), s.contacts.map { it.name })
        assertEquals("4035551234", s.contacts[0].normalized)
    }

    @Test fun missingChoicesAreReset() {
        val s = SettingsCodec.validate(AppSettings(audio = "snd:gone", scene = "dusk"), emptySet())
        assertEquals(Catalog.DEFAULT_SOUND, s.audio)
        assertEquals(Catalog.DEFAULT_SCENE, s.scene)
        assertEquals("snd:x", SettingsCodec.validate(AppSettings(audio = "snd:x"), setOf("snd:x")).audio)
    }
}

package com.focusfriend.app

import com.focusfriend.app.audio.Chime
import com.focusfriend.app.audio.NoiseSynth
import com.focusfriend.app.model.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AudioTest {
    private fun rms(s: ShortArray) = sqrt(s.sumOf { (it / 32768.0) * (it / 32768.0) } / s.size)

    @Test fun everyColourIsAFourSecondAudibleLoop() {
        for (sound in Catalog.SOUNDS) {
            val buf = NoiseSynth.render(sound.id, sound.gain, sampleRate = 8000)
            assertEquals(sound.id, 8000 * NoiseSynth.SECONDS, buf.size)
            val level = rms(buf)
            assertTrue("${sound.id} too quiet: $level", level > 0.005)
            assertTrue("${sound.id} too loud: $level", level < 0.5)
        }
    }

    @Test fun chimeRisesAndFadesOut() {
        val c = Chime.render(8000)
        assertTrue(c.any { it > 1000 })
        assertTrue(c.takeLast(400).all { kotlin.math.abs(it.toInt()) < 50 })
    }
}

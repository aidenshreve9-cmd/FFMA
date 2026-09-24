package app.focusfriend.core

import kotlin.test.*

/** A fake clock and fake voices, so the preview's timing can be checked exactly. */
private class Clock : Timers {
    var now = 0L
    private val queue = ArrayList<Triple<Long, Int, () -> Unit>>()
    private var seq = 0
    override fun after(ms: Long, block: () -> Unit): Any { val t = Triple(now + ms, seq++, block); queue += t; return t }
    override fun cancel(token: Any) { queue.remove(token) }
    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = queue.filter { it.first <= end }.minWithOrNull(compareBy({ it.first }, { it.second })) ?: break
            queue.remove(next); now = next.first; next.third()
        }
        now = end
    }
}

private class FakeVoice(override val id: String, private val clock: Clock, private val log: MutableList<String>) : PreviewVoice {
    private var from = 0f; private var to = 0f; private var t0 = 0L; private var t1 = 0L
    var stoppedAt: Long? = null
    override fun fadeTo(level: Float, ms: Long) { from = level(); to = level; t0 = clock.now; t1 = clock.now + ms }
    override fun level(): Float = if (clock.now >= t1) to else from + (to - from) * (clock.now - t0).toFloat() / (t1 - t0)
    override fun stop(fadeMs: Long) { fadeTo(0f, fadeMs); stoppedAt = clock.now + fadeMs; log += "stop $id"; }
}

class PreviewTest {
    private val clock = Clock()
    private val log = mutableListOf<String>()
    private val voices = mutableListOf<FakeVoice>()
    private val preview = SoundPreview(clock) { id -> FakeVoice(id, clock, log).also { voices += it; log += "start $id" } }
    /** Voices that are started and not yet fully stopped. */
    private fun playing() = voices.count { v -> v.stoppedAt.let { it == null || it > clock.now } }

    @Test fun fiveSecondPreviewWithFades() {
        preview.play("pink")
        clock.advance(100); assertTrue(voices[0].level() in 0.05f..0.2f, "fading in")
        clock.advance(900); assertEquals(1f, voices[0].level(), "full after the fade-in")
        clock.advance(3500); assertTrue(voices[0].level() < 1f, "fading out before the end")
        clock.advance(499); assertEquals(1, playing())
        clock.advance(1); assertEquals(0, playing(), "silent at exactly 5 s"); assertNull(preview.voice)
    }

    @Test fun switchingStopsAtTheMidpointNeverOverlaps() {
        preview.play("pink"); clock.advance(2000)
        preview.play("brown")
        repeat(45) { clock.advance(10); assertTrue(playing() <= 1) }
        assertEquals(listOf("start pink", "stop pink", "start brown"), log)
        assertEquals(0f, voices[0].level(), "old sound was silent when it stopped")
        clock.advance(900); assertEquals(1f, voices[1].level(), "new sound faded in")
    }

    @Test fun quickTapsPlayOnlyTheLastChoice() {
        preview.play("brown")
        clock.advance(40); preview.play("grey")
        clock.advance(40); preview.play("blue")
        clock.advance(1000)
        assertEquals(listOf("start brown", "stop brown", "start blue"), log)
        assertEquals("blue", preview.voice?.id)
    }

    @Test fun reTapFadesOutAndLeavingStopsAtOnce() {
        preview.play("pink"); clock.advance(1500)
        preview.fadeOut()
        assertNull(preview.voice); assertEquals(900L, voices[0].stoppedAt!! - clock.now, "a full, smooth fade-out")
        preview.play("violet"); clock.advance(500)
        preview.stopNow()
        assertEquals(SoundPreview.LEAVE_MS, voices[1].stoppedAt!! - clock.now)
        clock.advance(10_000); assertEquals(2, voices.size, "nothing restarts later")
    }

    @Test fun tappingTheChosenSoundDeselectsIt() {
        assertEquals("pink", Catalog.soundAfterTap("white", "pink"))
        assertEquals(Catalog.NONE, Catalog.soundAfterTap("pink", "pink"))
        assertEquals("pink", Catalog.soundAfterTap("pink", "pink", fromSearch = true))
        assertEquals(Catalog.NONE, FocusSettings(sound = Catalog.NONE).validated(emptySet(), emptySet()).sound, "no sound is a valid choice")
    }
}

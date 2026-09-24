package app.focusfriend.core

import kotlin.math.max

/** One playing sound, as the Settings preview sees it. Levels run 0..1 and every fade is smooth. */
interface PreviewVoice {
    /** The sound actually playing (Random is resolved to one sound). */
    val id: String
    fun fadeTo(level: Float, ms: Long)
    /** The level you can hear right now. */
    fun level(): Float
    /** Fades to silence over [fadeMs] (0 = it is already silent), then releases everything. */
    fun stop(fadeMs: Long)
}

/** Delayed work on the UI thread. */
interface Timers {
    fun after(ms: Long, block: () -> Unit): Any
    fun cancel(token: Any)
}

/**
 * Settings sound preview.
 *
 * - Tapping a sound plays a 5-second preview: fade in, hold, fade out.
 * - Tapping another sound while one plays: the current one fades out; at the midpoint of the
 *   switch (silence) it stops and the new one starts fading in. Taps during a switch only change
 *   which sound comes next, so two sounds never play at once and nothing clicks.
 * - Tapping the chosen sound again fades it out ([fadeOut]).
 * - Leaving Settings stops it at once ([stopNow]; a 30 ms fade only to avoid a click).
 */
class SoundPreview(private val timers: Timers, private val open: (String) -> PreviewVoice?) {
    var voice: PreviewVoice? = null
        private set
    private var pending: String? = null
    private val tokens = ArrayList<Any>()

    private fun later(ms: Long, block: () -> Unit) { tokens += timers.after(ms, block) }
    private fun clearLater() { tokens.forEach(timers::cancel); tokens.clear() }

    fun play(id: String) {
        clearLater()
        val v = voice ?: return begin(id)
        pending = id
        // Proportional to the current level, never under 120 ms, so a burst of taps lands in one fade.
        val ms = max(MIN_SWITCH_MS, (SWITCH_MS * v.level()).toLong())
        v.fadeTo(0f, ms)
        later(ms) {
            val next = pending; pending = null
            if (voice === v) { voice = null; v.stop(0) }
            if (next != null) begin(next)
        }
    }

    fun fadeOut() = release(voice?.let { max(LEAVE_MS, (FADE_MS * it.level()).toLong()) } ?: 0L)

    fun stopNow() = release(LEAVE_MS)

    private fun begin(id: String) {
        val v = open(id) ?: return
        voice = v
        v.fadeTo(1f, FADE_MS)
        later(LENGTH_MS - FADE_MS) {
            v.fadeTo(0f, FADE_MS)
            later(FADE_MS) { if (voice === v) { voice = null; v.stop(0) } }
        }
    }

    private fun release(ms: Long) {
        clearLater(); pending = null
        val v = voice ?: return
        voice = null
        v.stop(ms)
    }

    companion object {
        const val LENGTH_MS = 5000L
        const val FADE_MS = 900L
        const val SWITCH_MS = 450L
        const val MIN_SWITCH_MS = 120L
        const val LEAVE_MS = 30L
    }
}

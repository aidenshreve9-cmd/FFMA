package com.focusfriend.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/** What to play: a built-in noise colour or one of your own sound files. */
sealed interface SoundChoice {
    val id: String
    val name: String

    data class Noise(override val id: String, override val name: String, val gain: Float) : SoundChoice
    data class UserFile(override val id: String, override val name: String, val file: File) : SoundChoice
}

/**
 * Plays one sound at a time. Every start and stop is a fade, so nothing clicks, and two sounds
 * never overlap: switching fades the current one out, then the next one in.
 */
class SoundEngine(private val onError: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val buffers = HashMap<String, ShortArray>()
    private var voice: Voice? = null
    private var pending: SoundChoice? = null
    private val timers = mutableListOf<Job>()

    private inner class Voice(val choice: SoundChoice) {
        var level = 0f
            private set
        private var track: AudioTrack? = null
        private var player: MediaPlayer? = null
        private var fadeJob: Job? = null

        suspend fun open() {
            when (choice) {
                is SoundChoice.Noise -> {
                    val pcm = withContext(Dispatchers.Default) { bufferFor(choice) }
                    track = staticTrack(pcm, loop = true).apply { setVolume(0f); play() }
                }
                is SoundChoice.UserFile -> {
                    val p = MediaPlayer()
                    try {
                        withContext(Dispatchers.IO) {
                            p.setAudioAttributes(ATTRIBUTES)
                            p.setDataSource(choice.file.path)
                            p.isLooping = true
                            p.prepare()
                        }
                    } catch (e: Exception) {
                        p.release()
                        throw e
                    }
                    p.setVolume(0f, 0f)
                    p.start()
                    player = p
                }
            }
        }

        fun setLevel(v: Float) {
            level = v.coerceIn(0f, 1f)
            track?.setVolume(level)
            player?.setVolume(level, level)
        }

        /** Eases from the current level to [to] over [secs]. */
        fun fade(to: Float, secs: Float): Job {
            fadeJob?.cancel()
            val from = level
            val job = scope.launch {
                val start = SystemClock.uptimeMillis()
                val ms = max(1f, secs * 1000f)
                while (true) {
                    val k = ((SystemClock.uptimeMillis() - start) / ms).coerceAtMost(1f)
                    setLevel(from + (to - from) * easeInOut(k))
                    if (k >= 1f) break
                    delay(16)
                }
            }
            fadeJob = job
            return job
        }

        fun stop() {
            fadeJob?.cancel()
            try { track?.stop() } catch (e: IllegalStateException) { }
            track?.release(); track = null
            try { player?.stop() } catch (e: IllegalStateException) { }
            player?.release(); player = null
        }
    }

    private fun bufferFor(choice: SoundChoice.Noise): ShortArray =
        synchronized(buffers) { buffers.getOrPut(choice.id) { NoiseSynth.render(choice.id, choice.gain) } }

    /** Makes the noise ahead of time, so the first tap starts without a pause. */
    fun warmUp(choice: SoundChoice.Noise) {
        scope.launch(Dispatchers.Default) { bufferFor(choice) }
    }

    private suspend fun makeVoice(choice: SoundChoice): Voice? {
        val v = Voice(choice)
        return try {
            v.open()
            v
        } catch (e: CancellationException) {
            v.stop()
            throw e
        } catch (e: Exception) {
            v.stop()
            onError(if (choice is SoundChoice.UserFile) "Couldn't play that sound. Try another in Settings." else "Couldn't start the sound.")
            null
        }
    }

    private fun later(block: suspend CoroutineScope.() -> Unit) {
        timers.removeAll { it.isCompleted }
        timers += scope.launch(block = block)
    }

    private fun clearLater() {
        timers.forEach { it.cancel() }
        timers.clear()
    }

    /** Settings preview: fade in, hold, fade out, silent at exactly 5 s. */
    private fun beginPreview(choice: SoundChoice) = later {
        val v = makeVoice(choice) ?: return@later
        voice = v
        v.fade(1f, PREVIEW_FADE)
        delay(PREVIEW_MS - (PREVIEW_FADE * 1000).toLong())
        v.fade(0f, PREVIEW_FADE)
        delay((PREVIEW_FADE * 1000).toLong())
        if (voice === v) { voice = null; v.stop() }
    }

    /**
     * Switching sounds: the current one fades out, stops at silence, then the next one fades in.
     * Quick taps only change which sound comes next.
     */
    fun preview(choice: SoundChoice) {
        clearLater()
        val v = voice
        if (v == null) { beginPreview(choice); return }
        pending = choice
        val secs = max(0.12f, SWITCH_FADE * v.level)
        v.fade(0f, secs)
        later {
            delay((secs * 1000).toLong())
            val next = pending
            pending = null
            if (voice === v) { voice = null; v.stop() }
            if (next != null) beginPreview(next)
        }
    }

    /** Re-tapping the chosen sound fades it out from wherever it is. */
    fun fadeOutPreview() {
        val v = voice
        if (v == null) { clearLater(); pending = null; return }
        release(max(0.03f, PREVIEW_FADE * v.level))
    }

    /** Focus: fades in alongside the page transition, then loops until the session ends. */
    fun startSession(choice: SoundChoice?) {
        release(0.03f)
        if (choice == null) return
        later {
            val v = makeVoice(choice) ?: return@later
            voice = v
            delay(80)
            v.fade(1f, 0.62f)
        }
    }

    /** Fades the current sound out and lets it go; nothing is left playing afterwards. */
    fun release(secs: Float) {
        clearLater()
        pending = null
        val v = voice ?: return
        voice = null
        v.fade(0f, secs)
        scope.launch {
            delay((secs * 1000).toLong() + 60)
            v.stop()
        }
    }

    fun chime() {
        scope.launch {
            val pcm = withContext(Dispatchers.Default) { Chime.render() }
            val track = try { staticTrack(pcm, loop = false) } catch (e: Exception) { return@launch }
            track.play()
            delay(pcm.size * 1000L / NoiseSynth.SAMPLE_RATE + 200)
            try { track.stop() } catch (e: IllegalStateException) { }
            track.release()
        }
    }

    fun shutdown() {
        clearLater()
        voice?.stop()
        voice = null
        scope.cancel()
    }

    private fun staticTrack(pcm: ShortArray, loop: Boolean): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(ATTRIBUTES)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(NoiseSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        if (loop) track.setLoopPoints(0, pcm.size, -1)
        return track
    }

    private companion object {
        const val PREVIEW_MS = 5000L
        const val PREVIEW_FADE = 0.9f
        const val SWITCH_FADE = 0.45f

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun easeInOut(k: Float) = (0.5 - 0.5 * cos(PI * k)).toFloat()
    }
}

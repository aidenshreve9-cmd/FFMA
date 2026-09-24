package app.focusfriend.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A level change scheduled on the clock (nanoseconds), read by whichever thread makes the sound. */
class LevelRamp(val from: Float, val to: Float, val startNs: Long, val durNs: Long, val curve: (Float) -> Float) {
    fun at(ns: Long): Float = when {
        durNs <= 0L || ns >= startNs + durNs -> to
        ns <= startNs -> from
        else -> from + (to - from) * curve((ns - startNs).toFloat() / durNs)
    }
    val endNs get() = startNs + durNs
}

val EASE_IN_OUT: (Float) -> Float = { k -> (0.5 - 0.5 * cos(PI * k)).toFloat() }
val LINEAR: (Float) -> Float = { it }

/**
 * Anything that plays: a session sound or a Settings preview. It starts silent, and its level
 * only ever changes through smooth fades. Loudness is otherwise only the phone's media volume.
 */
interface SoundOutput {
    fun start()
    fun fadeTo(level: Float, ms: Long, curve: (Float) -> Float = EASE_IN_OUT)
    fun level(): Float
    /** Fades to silence over [fadeMs], then releases the player. */
    fun stop(fadeMs: Long)
}

abstract class FadingOutput : SoundOutput {
    @Volatile protected var ramp = LevelRamp(0f, 0f, 0L, 0L, LINEAR)
    @Volatile protected var stopping = false
    override fun level() = ramp.at(System.nanoTime())
    override fun fadeTo(level: Float, ms: Long, curve: (Float) -> Float) {
        if (stopping) return   // once stopping, nothing can bring it back up
        val now = System.nanoTime()
        ramp = LevelRamp(ramp.at(now), level.coerceIn(0f, 1f), now, ms * 1_000_000L, curve)
        onFade()
    }
    protected open fun onFade() {}
    protected fun fadeOutForStop(fadeMs: Long) {
        val now = System.nanoTime()
        ramp = LevelRamp(ramp.at(now), 0f, now, fadeMs * 1_000_000L, EASE_IN_OUT)
        stopping = true
        onFade()
    }
}

private val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

/** RBJ-cookbook biquad. */
class Biquad private constructor(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        private fun w(f: Double, sr: Int) = 2 * PI * f / sr
        fun lowpass(f: Double, q: Double, sr: Int): Biquad {
            val w0 = w(f, sr); val a = sin(w0) / (2 * q); val c = cos(w0)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)
        }
        fun bandpass(f: Double, q: Double, sr: Int): Biquad {
            val w0 = w(f, sr); val a = sin(w0) / (2 * q); val c = cos(w0)
            return make(a, 0.0, -a, 1 + a, -2 * c, 1 - a)
        }
        fun peaking(f: Double, q: Double, gainDb: Double, sr: Int): Biquad {
            val A = 10.0.pow(gainDb / 40); val w0 = w(f, sr); val a = sin(w0) / (2 * q); val c = cos(w0)
            return make(1 + a * A, -2 * c, 1 - a * A, 1 + a / A, -2 * c, 1 - a / A)
        }
        fun shelf(f: Double, gainDb: Double, sr: Int, high: Boolean): Biquad {
            val A = 10.0.pow(gainDb / 40); val w0 = w(f, sr); val c = cos(w0); val s = sin(w0)
            val alpha = s / 2 * sqrt(2.0); val k = 2 * sqrt(A) * alpha
            return if (!high) make(A * ((A + 1) - (A - 1) * c + k), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - k),
                (A + 1) + (A - 1) * c + k, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - k)
            else make(A * ((A + 1) + (A - 1) * c + k), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - k),
                (A + 1) - (A - 1) * c + k, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - k)
        }
    }
}

/**
 * Generates the eight noise colours live (no audio files shipped): white, pink (Kellet),
 * brown (integrated), green (500 Hz band), grey (equal-loudness-ish EQ), blue and violet
 * (differentiated pink/white) and black (sub-bass rumble under 90 Hz).
 */
class NoiseColor(private val kind: String, private val sr: Int, seed: Long = 1L) {
    private val rng = Random(seed)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var brown = 0.0; private var prevPink = 0.0; private var prevWhite = 0.0
    private val filters: List<Biquad> = when (kind) {
        "brown" -> listOf(Biquad.lowpass(1400.0, 0.7, sr))
        "black" -> listOf(Biquad.lowpass(90.0, 0.7, sr), Biquad.lowpass(90.0, 0.7, sr))
        "green" -> listOf(Biquad.bandpass(500.0, 0.55, sr))
        "grey" -> listOf(Biquad.shelf(220.0, 7.0, sr, high = false), Biquad.peaking(3200.0, 1.0, -10.0, sr), Biquad.shelf(9000.0, 3.0, sr, high = true))
        "blue" -> listOf(Biquad.lowpass(14000.0, 0.7, sr))
        "violet" -> listOf(Biquad.lowpass(16000.0, 0.7, sr))
        else -> emptyList()
    }

    fun next(): Double {
        val w = rng.nextDouble() * 2 - 1
        b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759; b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856; b4 = 0.55000 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362; b6 = w * 0.115926
        brown = (brown + 0.02 * w) / 1.02
        var v = when (kind) {
            "pink" -> pink
            "brown", "black" -> brown
            "blue" -> pink - prevPink
            "violet" -> w - prevWhite
            else -> w
        }
        prevPink = pink; prevWhite = w
        for (f in filters) v = f.process(v)
        return v
    }
}

/**
 * Streams a noise colour to a media-volume AudioTrack. Its level follows [ramp]; each block is
 * shaped for the moment it will actually be heard (the track's buffer is taken into account).
 */
class NoiseEngine(private val kind: String, private val gain: Float) : FadingOutput() {
    @Volatile private var running = false
    @Volatile private var stopAtNs = Long.MAX_VALUE

    override fun start() {
        if (running) return
        running = true
        Thread({ run() }, "focus-noise").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    override fun stop(fadeMs: Long) {
        if (!running) return
        fadeOutForStop(fadeMs)
        stopAtNs = System.nanoTime() + fadeMs * 1_000_000L + LATENCY_NS
    }

    private fun run() {
        val sr = SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(MEDIA_ATTRIBUTES)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, BUFFER_FRAMES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: RuntimeException) { running = false; return }
        try {
            val gen = NoiseColor(kind, sr, System.nanoTime())
            // Normalize each colour to the same RMS before the per-colour gain, so switching sounds doesn't jump in loudness.
            var sum = 0.0
            repeat(sr) { val v = gen.next(); sum += v * v }
            val norm = 0.14 / maxOf(1e-6, sqrt(sum / sr))
            val buf = FloatArray(1024)
            val blockNs = buf.size * 1_000_000_000L / sr
            track.play()
            while (running) {
                val t = System.nanoTime()
                if (t >= stopAtNs) break
                val r = ramp
                val e0 = r.at(t + LATENCY_NS); val e1 = r.at(t + LATENCY_NS + blockNs)
                for (i in buf.indices) {
                    val env = e0 + (e1 - e0) * i / buf.size
                    buf[i] = (gen.next() * norm * gain * env).toFloat().coerceIn(-1f, 1f)
                }
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: RuntimeException) {
            // Audio failure must not end the session; the timer and silencing keep working.
        } finally {
            running = false
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BUFFER_FRAMES = SAMPLE_RATE / 12                    // ~83 ms: steady, yet close to the picture
        const val LATENCY_NS = BUFFER_FRAMES * 1_000_000_000L / SAMPLE_RATE
    }
}

/** Plays one of the person's own sounds on a loop from app-private storage. Call from the main thread. */
class UserSoundPlayer(private val path: String) : FadingOutput() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val v = level()
            runCatching { p.setVolume(v, v) }
            if (System.nanoTime() < ramp.endNs + 20_000_000L) handler.postDelayed(this, 16)
        }
    }

    override fun start() {
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(MEDIA_ATTRIBUTES)
            p.setDataSource(path)
            p.isLooping = true
            p.setVolume(0f, 0f)
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            // Missing or unreadable file: silence rather than a crash; the session continues.
            player?.release(); player = null
        }
    }

    override fun onFade() { handler.removeCallbacks(tick); handler.post(tick) }

    override fun stop(fadeMs: Long) {
        val p = player ?: return
        fadeOutForStop(fadeMs)
        handler.postDelayed({
            handler.removeCallbacks(tick)
            if (player === p) player = null
            runCatching { p.stop() }; p.release()
        }, fadeMs + 20)
    }
}

/** Connects the Focus page transition (in the activity) to the session sound (in the service). */
object SessionAudio {
    @Volatile var output: SoundOutput? = null
    /** How far the page transition has got; a session sound that starts late begins from here. */
    @Volatile var target = 1f
    fun follow(level: Float, ms: Long) { target = level; output?.fadeTo(level, ms, LINEAR) }
}

/** Soft three-note chime (C5, E5, G5) for a natural finish only. */
object Chime {
    fun play() {
        Thread({
            val sr = 44_100
            val len = (sr * 2.4).toInt()
            val data = FloatArray(len)
            doubleArrayOf(523.25, 659.25, 783.99).forEachIndexed { k, f ->
                val start = (0.05 + k * 0.16) * sr
                for (i in start.toInt() until len) {
                    val t = (i - start) / sr
                    val env = if (t < 0.02) t / 0.02 * 0.18 else 0.18 * exp(-t * 3.2)
                    data[i] += (sin(2 * PI * f * t) * env).toFloat()
                }
            }
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(MEDIA_ATTRIBUTES)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(len * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (e: RuntimeException) { return@Thread }
            try {
                track.write(data, 0, len, AudioTrack.WRITE_BLOCKING)
                track.play()
                Thread.sleep(2500)
            } catch (e: Exception) {
            } finally { runCatching { track.stop() }; track.release() }
        }, "focus-chime").start()
    }
}

package com.focusfriend.app.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** The three rising notes that play when a session finishes on its own. */
object Chime {
    private val NOTES = doubleArrayOf(523.25, 659.25, 783.99)

    fun render(sampleRate: Int = NoiseSynth.SAMPLE_RATE): ShortArray {
        val start = 0.05
        val step = 0.16
        val ring = 2.0
        val total = ((start + step * (NOTES.size - 1) + ring + 0.1) * sampleRate).toInt()
        val out = FloatArray(total)
        NOTES.forEachIndexed { n, f ->
            val t0 = start + n * step
            val first = (t0 * sampleRate).toInt()
            val count = (ring * sampleRate).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                // Quick 20 ms rise to 0.18, then an exponential fall to near silence over 2 s.
                val env = if (t < 0.02) 0.18 * t / 0.02 else 0.18 * exp(ln(0.0001 / 0.18) * (t - 0.02) / (ring - 0.02))
                val idx = first + i
                if (idx < total) out[idx] += (sin(2 * PI * f * t) * env).toFloat()
            }
        }
        return ShortArray(total) { (out[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    }
}

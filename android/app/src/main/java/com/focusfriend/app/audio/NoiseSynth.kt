package com.focusfriend.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Builds a 4-second seamless loop for each noise colour: white, pink, brown, green, grey, blue,
 * violet and black. Every colour is normalised to the same level before its filters, then shaped
 * by the same filters the web version used, so they sound alike and similarly loud.
 */
object NoiseSynth {
    const val SAMPLE_RATE = 44100
    const val SECONDS = 4

    fun render(kind: String, gain: Float, sampleRate: Int = SAMPLE_RATE, seed: Int = kind.hashCode()): ShortArray {
        val len = sampleRate * SECONDS
        val fade = (sampleRate * 0.3).toInt()
        val d = FloatArray(len + fade)
        val rnd = Random(seed)
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        var brown = 0.0; var prevP = 0.0; var prevW = 0.0
        for (i in d.indices) {
            val w = rnd.nextDouble() * 2 - 1
            // Paul Kellet's pink filter
            b0 = .99886 * b0 + w * .0555179; b1 = .99332 * b1 + w * .0750759; b2 = .96900 * b2 + w * .1538520
            b3 = .86650 * b3 + w * .3104856; b4 = .55000 * b4 + w * .5329522; b5 = -.7616 * b5 - w * .0168980
            val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * .5362; b6 = w * .115926
            brown = (brown + .02 * w) / 1.02
            val v = when (kind) {
                "pink" -> p
                "brown", "black" -> brown
                "blue" -> p - prevP        // +3 dB/octave
                "violet" -> w - prevW      // +6 dB/octave
                else -> w                  // white, green, grey (shaped by filters)
            }
            prevP = p; prevW = w
            d[i] = v.toFloat()
        }
        // Crossfade the tail into the start so the loop has no seam.
        for (i in 0 until fade) {
            val k = i.toFloat() / fade
            d[i] = d[i] * sqrt(k) + d[len + i] * sqrt(1 - k)
        }
        var sum = 0.0
        for (i in 0 until len) sum += d[i] * d[i]
        val scale = (0.14 / maxOf(1e-6, sqrt(sum / len))).toFloat()
        val x = FloatArray(len) { d[it] * scale }

        // Run the loop through the filters twice and keep the second pass, so the filters are
        // already settled where the loop wraps around.
        val chain = filtersFor(kind, sampleRate)
        val out = FloatArray(len)
        for (pass in 0 until 2) for (i in 0 until len) {
            var s = x[i]
            for (f in chain) s = f.process(s)
            if (pass == 1) out[i] = s
        }
        return ShortArray(len) { (out[it] * gain).coerceIn(-1f, 1f).times(32767f).toInt().toShort() }
    }

    private fun filtersFor(kind: String, sr: Int): List<Biquad> = when (kind) {
        "brown" -> listOf(Biquad.lowpass(1400.0, 0.7, sr))
        "black" -> listOf(Biquad.lowpass(90.0, 0.7, sr), Biquad.lowpass(90.0, 0.7, sr))
        "green" -> listOf(Biquad.bandpass(500.0, 0.55, sr))
        "grey" -> listOf(Biquad.lowShelf(220.0, 7.0, sr), Biquad.peaking(3200.0, 1.0, -10.0, sr), Biquad.highShelf(9000.0, 3.0, sr))
        "blue" -> listOf(Biquad.lowpass(14000.0, 0.7, sr))
        "violet" -> listOf(Biquad.lowpass(16000.0, 0.7, sr))
        else -> emptyList()
    }
}

/** Standard audio-EQ-cookbook filters, matching the Web Audio filters the web version used. */
class Biquad private constructor(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val nb0 = b0 / a0
    private val nb1 = b1 / a0
    private val nb2 = b2 / a0
    private val na1 = a1 / a0
    private val na2 = a2 / a0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Float): Float {
        val y = nb0 * x + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
        x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
        return y.toFloat()
    }

    companion object {
        // Kept below the Nyquist limit so a filter can never go unstable.
        private fun w0(f: Double, sr: Int) = 2 * PI * minOf(f, sr * 0.45) / sr

        /** Web Audio gives low-pass Q in decibels. */
        fun lowpass(f: Double, qDb: Double, sr: Int): Biquad {
            val w = w0(f, sr); val c = cos(w); val alpha = sin(w) / (2 * 10.0.pow(qDb / 20))
            return Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun bandpass(f: Double, q: Double, sr: Int): Biquad {
            val w = w0(f, sr); val c = cos(w); val alpha = sin(w) / (2 * q)
            return Biquad(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun peaking(f: Double, q: Double, gainDb: Double, sr: Int): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(f, sr); val c = cos(w); val alpha = sin(w) / (2 * q)
            return Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowShelf(f: Double, gainDb: Double, sr: Int): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(f, sr); val c = cos(w); val alpha = sin(w) / 2 * sqrt(2.0)
            val s = 2 * sqrt(a) * alpha
            return Biquad(
                a * ((a + 1) - (a - 1) * c + s), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - s),
                (a + 1) + (a - 1) * c + s, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - s,
            )
        }

        fun highShelf(f: Double, gainDb: Double, sr: Int): Biquad {
            val a = 10.0.pow(gainDb / 40); val w = w0(f, sr); val c = cos(w); val alpha = sin(w) / 2 * sqrt(2.0)
            val s = 2 * sqrt(a) * alpha
            return Biquad(
                a * ((a + 1) + (a - 1) * c + s), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - s),
                (a + 1) - (a - 1) * c + s, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - s,
            )
        }
    }
}

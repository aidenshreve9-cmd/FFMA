package com.focusfriend.app.ui.welcome

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.focusfriend.app.ui.background.glow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The welcome screen's wormhole: a funnel of dotted rings narrows into a neck of bright rings and
 * flares out again beyond it. Rings stream through it continuously while the colour drifts
 * mint → pink → violet → blue → cyan over a dark teal field.
 */
@Composable
fun Wormhole(time: State<Float>, modifier: Modifier = Modifier) {
    val painter = remember { WormholePainter() }
    Canvas(modifier) { painter.draw(this, time.value) }
}

private const val TAU = 6.2831855f
private const val RINGS = 62
private const val DOTS = 170
private const val SEGMENTS = 10
private const val S = 26f
private const val TN = 10f
private const val N = 62f
private const val NECK = 0.12f
private const val HALF = 0.30f
private const val RMAX = 1.9f
private const val THROAT_X = 0.02f
private const val THROAT_Y = 0.52f

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

private val PALETTE = arrayOf(
    floatArrayOf(0.45f, 1.0f, 0.62f),  // mint
    floatArrayOf(1.0f, 0.45f, 0.86f),  // pink
    floatArrayOf(0.66f, 0.42f, 1.0f),  // violet
    floatArrayOf(0.36f, 0.58f, 1.0f),  // blue
    floatArrayOf(0.36f, 0.96f, 0.9f),  // cyan
)

/** The colour cycle, written into [out] as r, g, b. */
private fun pal(hue: Float, out: FloatArray) {
    val h = (hue - floor(hue)) * 5f
    val i = h.toInt().coerceIn(0, 4)
    val f = smoothstep(0f, 1f, h - i)
    val a = PALETTE[i]
    val b = PALETTE[(i + 1) % 5]
    for (c in 0..2) out[c] = mix(a[c], b[c], f)
}

private class WormholePainter {
    private val points = FloatArray((DOTS / SEGMENTS + 2) * 2)
    private val rgb = FloatArray(3)
    private val tint = FloatArray(3)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    fun draw(scope: DrawScope, t: Float) = with(scope) {
        val w = size.width
        val h = size.height
        val m = min(w, h)
        val cx = w / 2
        val cy = h / 2
        fun sx(x: Float) = cx + x * m
        fun sy(y: Float) = cy - y * m

        // Dark teal field, lit toward the upper funnel.
        drawRect(Color(0xFF010809))
        val lit = Offset(sx(THROAT_X + 0.12f), sy(THROAT_Y + 0.2f))
        withTransform({ scale(1 / 0.8f, 1 / 0.6f, pivot = lit) }) {
            drawCircle(
                Brush.radialGradient(0f to Color(0xFF06302E), 0.35f to Color(0xFF042220), 1f to Color(0x00010809), center = lit, radius = m * 1.3f),
                m * 1.3f, lit,
            )
        }
        pal(t / 30f, tint)
        glow(sx(THROAT_X), sy(THROAT_Y), m * 0.8f, Color(tint[0], tint[1], tint[2]), 0.12f, BlendMode.Plus)
        // A soft rainbow haze drifting across the middle.
        val hx = sx(THROAT_X - 0.12f + 0.1f * sin(t * 0.19f))
        val hy = sy(THROAT_Y - 0.2f + 0.1f * cos(t * 0.15f))
        pal(t / 12f + 0.5f, tint)
        glow(hx, hy, m * 0.4f, Color(tint[0], tint[1], tint[2]), 0.10f * (0.5f + 0.5f * sin(t * 0.23f)), BlendMode.Plus)

        drawIntoCanvas { canvas -> drawRings(canvas.nativeCanvas, t, w, h, m, cx, cy) }

        // Vignette.
        drawRect(
            Brush.radialGradient(0f to Color.Transparent, 0.38f to Color.Transparent, 1f to Color(0x73000000), center = Offset(cx, cy), radius = m * 1.3f),
        )
    }

    private fun drawRings(canvas: android.graphics.Canvas, t: Float, w: Float, h: Float, m: Float, cx: Float, cy: Float) {
        // The wormhole's axis: tilted up-right and away from the viewer, swaying slowly.
        var ax = 0.42f + 0.04f * sin(t * 0.11f)
        var ay = 0.80f
        var az = -0.46f
        val al = sqrt(ax * ax + ay * ay + az * az)
        ax /= al; ay /= al; az /= al
        val e1l = sqrt(ay * ay + ax * ax)
        val e1x = ay / e1l
        val e1y = -ax / e1l
        val e2x = ax / e1l
        val e2y = ay / e1l
        val k = abs(az)
        val flow = t * 0.16f - floor(t * 0.16f)
        val spin = t * 0.05f
        val margin = m * 0.05f
        val perSeg = DOTS / SEGMENTS

        for (i in 0 until RINGS) {
            val u = i + flow
            val z: Float
            val r: Float
            val wv: Float
            if (u < S) {
                wv = (S - u) / S; r = NECK + RMAX * wv.pow(1.7f); z = -HALF - 1.2f * sqrt(NECK * (r - NECK))
            } else if (u < S + TN) {
                wv = 0f; val s = (u - S) / TN; z = -HALF + 2f * HALF * s; r = NECK * (1f + 0.08f * sin(s * PI.toFloat()))
            } else {
                wv = (u - S - TN) / S; r = NECK + RMAX * wv.pow(1.7f); z = HALF + 1.2f * sqrt(NECK * (r - NECK))
            }
            val g = 1.6f / (1.8f - az * z)
            val ccx = THROAT_X + ax * z * g
            val ccy = THROAT_Y + ay * z * g
            val rr = r * g

            val neck = 1f - smoothstep(0f, 0.12f, wv)
            val fade = smoothstep(0f, 2f, u) * smoothstep(N, N - 3f, u)
            val base = fade * mix(1f, 0.3f, wv.pow(0.6f)) * (if (z > 0f) mix(1f, 0.65f, wv) else 1f)
            if (base <= 0.002f) continue
            pal(t / 30f + u * 0.012f, rgb)

            // Where dots crowd together (the neck), they're dimmed so they merge into an even glowing line.
            val dr = 0.0032f * g * m
            val halo = mix(3f * dr, 0.011f * m, neck)
            val spacing = TAU * rr * sqrt(0.5f + 0.5f * k * k) * m / DOTS
            val coreW = min(1f, spacing / (2f * dr)) * mix(1f, 1.1f, neck)
            val haloW = 0.35f * min(1f, spacing / (1.77f * halo))

            for (seg in 0 until SEGMENTS) {
                var n = 0
                for (j in seg * perSeg until (seg + 1) * perSeg) {
                    val ang = j.toFloat() / DOTS * TAU + spin
                    val px = cx + (ccx + e1x * rr * cos(ang) + e2x * rr * k * sin(ang)) * m
                    val py = cy - (ccy + e1y * rr * cos(ang) + e2y * rr * k * sin(ang)) * m
                    if (px < -margin || px > w + margin || py < -margin || py > h + margin) continue
                    points[n++] = px
                    points[n++] = py
                }
                if (n == 0) continue
                val mid = (seg + 0.5f) * perSeg / DOTS * TAU + spin
                val light = base * (0.6f + 0.4f * cos(mid - 2.2f))
                // Glow first, then the bright dot on top; both add light.
                setColor(light, haloW * 0.55f)
                paint.strokeWidth = halo * 1.8f
                canvas.drawPoints(points, 0, n, paint)
                setColor(light, coreW)
                paint.strokeWidth = dr * 2f
                canvas.drawPoints(points, 0, n, paint)
            }
        }
    }

    private fun setColor(light: Float, weight: Float) {
        val a = weight.coerceIn(0f, 1f)
        paint.setARGB(
            (a * 255).toInt(),
            (rgb[0] * light * 255).toInt().coerceIn(0, 255),
            (rgb[1] * light * 255).toInt().coerceIn(0, 255),
            (rgb[2] * light * 255).toInt().coerceIn(0, 255),
        )
    }
}

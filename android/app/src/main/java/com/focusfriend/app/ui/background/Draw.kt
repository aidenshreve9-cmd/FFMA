package com.focusfriend.app.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.sin

/** Seeded random numbers (mulberry32), so every scene is laid out the same way each time. */
fun rng(seed: Int): () -> Float {
    var s = seed
    return {
        s += 0x6D2B79F5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
    }
}

/** A soft radial glow, like the web version's glow(): full colour at the centre fading to nothing. */
fun DrawScope.glow(x: Float, y: Float, r: Float, color: Color, a: Float, blend: BlendMode = BlendMode.SrcOver) {
    if (r <= 0f || a <= 0f) return
    val alpha = a.coerceAtMost(1f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.45f to color.copy(alpha = alpha * 0.45f),
            1f to color.copy(alpha = 0f),
            center = Offset(x, y),
            radius = r,
        ),
        radius = r,
        center = Offset(x, y),
        blendMode = blend,
    )
}

/** A glow stretched and rotated into a wisp. */
fun DrawScope.streak(x: Float, y: Float, r: Float, color: Color, a: Float, rotation: Float, sx: Float, sy: Float, blend: BlendMode = BlendMode.SrcOver) {
    withTransform({
        translate(x, y)
        rotate(Math.toDegrees(rotation.toDouble()).toFloat(), pivot = Offset.Zero)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        glow(0f, 0f, r, color, a, blend)
    }
}

fun DrawScope.verticalGradient(w: Float, h: Float, vararg colors: Color) {
    drawRect(Brush.verticalGradient(colors.toList(), startY = 0f, endY = h), size = Size(w, h))
}

/**
 * Draws in CSS pixels, like the web version's canvases: [block] gets the width and height in
 * those units, so the scene code can use the same numbers.
 */
inline fun DrawScope.cssPixels(block: DrawScope.(w: Float, h: Float) -> Unit) {
    val w = size.width / density
    val h = size.height / density
    withTransform({ scale(density, density, pivot = Offset.Zero) }) { block(w, h) }
}

private class Star(val x: Float, val y: Float, val s: Float, val b: Float, val ph: Float, val f: Float)

private val STARS: List<Star> = rng(77).let { r -> List(260) { Star(r(), r(), 0.5f + r() * 1.3f, 0.4f + r() * 0.6f, r() * 6.28f, 0.3f + r() * 1.1f) } }

private val STAR_COLOR = Color(0xFFECE4FF)

/** Twinkling stars across a [w] × [h] area. */
fun DrawScope.starfield(w: Float, h: Float, t: Float, count: Int, alpha: Float) {
    for (i in 0 until count.coerceAtMost(STARS.size)) {
        val s = STARS[i]
        val a = alpha * s.b * (0.45f + 0.55f * (0.5f + 0.5f * sin(t * s.f + s.ph)))
        drawRect(STAR_COLOR.copy(alpha = a.coerceIn(0f, 1f)), Offset(s.x * w, s.y * h), Size(s.s, s.s))
    }
}

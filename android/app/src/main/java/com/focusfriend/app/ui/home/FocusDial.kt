package com.focusfriend.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.focusfriend.app.ui.orb.Orb
import com.focusfriend.app.ui.theme.Fonts
import com.focusfriend.app.ui.theme.Palette
import com.focusfriend.app.util.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val ARC_R = 84f
private val ARC_BRUSH_COLORS = listOf(Palette.Rose, Palette.Magenta, Palette.Violet2)
private val LABELS = (5..60 step 5).toList()

/**
 * The orbital dial: a compass-style scale (one tick per minute, longer every 5), a 60-minute arc,
 * and the glowing orb in the middle. [fraction] is how much of the hour the arc covers.
 */
@Composable
fun DialStack(
    fraction: Float,
    activeMinutes: Int,
    showHandle: Boolean,
    dragging: Boolean,
    pressed: Boolean,
    act: () -> Float,
    time: State<Float>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val still = rememberReducedMotion()
    val arc by animateFloatAsState(fraction.coerceIn(0f, 1f), if (dragging || still) snap() else tween(700), label = "arc")
    val ripples = remember { mutableStateListOf<Float>() }
    if (!still) {
        LaunchedEffect(pressed) {
            while (pressed) {
                ripples.add(time.value)
                ripples.removeAll { time.value - it > 2.8f }
                delay(1500)
            }
        }
    }
    val measurer = rememberTextMeasurer(cacheSize = 16)

    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val side = maxWidth
        // Self-illumination cast into the void, stronger while touched.
        Canvas(Modifier.requiredSize(side * 1.68f)) {
            val a = act()
            drawCircle(
                Brush.radialGradient(
                    0f to Palette.Violet2.copy(alpha = .20f + a * .14f),
                    .32f to Palette.Magenta.copy(alpha = .07f + a * .08f),
                    .64f to Palette.Indigo.copy(alpha = 0f),
                    center = center, radius = size.minDimension / 2,
                ),
            )
        }
        // Slow warm-magenta ripples while the dial is held.
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            ripples.forEach { born ->
                val k = ((t - born) / 2.8f).coerceIn(0f, 1f)
                if (k >= 1f) return@forEach
                val e = 1 - (1 - k) * (1 - k)
                val r = size.minDimension * 0.31f * (1 + 2.1f * e)
                val alpha = 0.75f * (1 - k)
                drawCircle(Palette.Rose.copy(alpha = 0.55f * alpha), r, style = Stroke(1.dp.toPx()))
                drawCircle(Palette.Magenta.copy(alpha = 0.25f * alpha), r, style = Stroke(10.dp.toPx()))
            }
        }
        Orb(time, act, Modifier.fillMaxSize(0.886f))
        Canvas(Modifier.fillMaxSize()) {
            drawFace(arc, activeMinutes, showHandle, dragging, time.value, still) { text, color, at, px ->
                val layout = measurer.measure(text, TextStyle(fontFamily = Fonts.Mono, fontSize = px.toSp()))
                drawText(layout, color = color, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
        content()
    }
}

private fun DrawScope.drawFace(
    frac: Float,
    active: Int,
    showHandle: Boolean,
    dragging: Boolean,
    t: Float,
    still: Boolean,
    label: DrawScope.(String, Color, Offset, Float) -> Unit,
) {
    val s = size.minDimension / 200f
    val c = center
    fun at(deg: Float, r: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(c.x + (sin(a) * r * s).toFloat(), c.y - (cos(a) * r * s).toFloat())
    }

    drawCircle(Palette.Halo.copy(alpha = .16f), 97 * s, c, style = Stroke(.6f * s))
    for (i in 0 until 60) {
        val major = i % 5 == 0
        drawLine(
            Palette.Halo.copy(alpha = if (major) .7f else .30f),
            at(i * 6f, if (major) 87.5f else 90.5f), at(i * 6f, 93f),
            strokeWidth = (if (major) .7f else .35f) * s,
        )
    }
    drawCircle(Color(0xD92A085C), ARC_R * s, c, style = Stroke(2.2f * s))
    if (frac > 0.002f) {
        val topLeft = Offset(c.x - ARC_R * s, c.y - ARC_R * s)
        val arcSize = Size(ARC_R * 2 * s, ARC_R * 2 * s)
        val brush = Brush.linearGradient(ARC_BRUSH_COLORS, start = topLeft, end = topLeft + Offset(arcSize.width, arcSize.height))
        drawArc(brush, -90f, 360f * frac, false, topLeft, arcSize, alpha = 0.35f, style = Stroke(7f * s, cap = StrokeCap.Round))
        drawArc(brush, -90f, 360f * frac, false, topLeft, arcSize, style = Stroke(2.6f * s, cap = StrokeCap.Round))
        drawCircle(Color.White, 2.2f * s, at(360f * frac, ARC_R))
    }
    if (showHandle) {
        val p = at(360f * frac, ARC_R)
        drawCircle(Palette.Rose.copy(alpha = .75f), 7.5f * s, p, style = Stroke(.8f * s))
        drawCircle(Palette.Magenta.copy(alpha = .35f), (if (dragging) 9f else 7f) * s, p)
        drawCircle(Color.White, (if (dragging) 6.2f else 4.4f) * s, p)
    }
    drawCircle(Palette.Halo.copy(alpha = .09f), 77 * s, c, style = Stroke(.5f * s))
    val breath = if (still) .5f else .22f + .53f * (.5f - .5f * cos(t * 2f * Math.PI.toFloat() / 9f))
    drawCircle(Palette.Magenta.copy(alpha = breath * .45f), 66 * s, c, style = Stroke(3.5f * s))
    drawCircle(Palette.Magenta.copy(alpha = breath), 66 * s, c, style = Stroke(1.1f * s))
    LABELS.forEach { mn ->
        label(mn.toString(), if (mn == active) Palette.Halo else Palette.Halo.copy(alpha = .5f), at(mn * 6f, 72.5f), 7f * s)
    }
}

/**
 * Home's input: drag around the ring to set 1–60 minutes (6° per minute; a drag never jumps across
 * the top), or tap the middle to start. A stray slide in the middle never starts a session.
 */
fun Modifier.dialInput(
    minutes: () -> Int,
    onSet: (Int) -> Unit,
    onTap: () -> Unit,
    onPress: (Boolean) -> Unit,
    onDrag: (Boolean) -> Unit,
    slopPx: Float,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val radius = size.width / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val (d, deg) = DialMath.polar(down.position.x, down.position.y, cx, cy, radius)
        val onRing = d >= DialMath.RING_INNER
        onPress(true)
        if (onRing) {
            onDrag(true)
            onSet(DialMath.minutesAt(deg))
            down.consume()
        }
        var moved = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) { cancelled = true; break }
            if (!change.pressed) {
                if (change.isConsumed && !onRing) cancelled = true
                break
            }
            if (onRing) {
                val (_, now) = DialMath.polar(change.position.x, change.position.y, cx, cy, radius)
                onSet(DialMath.follow(minutes(), now))
                change.consume()
            } else if (!moved && (change.position - down.position).getDistance() > slopPx) {
                moved = true
            }
        }
        onPress(false)
        onDrag(false)
        if (!onRing && !moved && !cancelled) onTap()
    }
}

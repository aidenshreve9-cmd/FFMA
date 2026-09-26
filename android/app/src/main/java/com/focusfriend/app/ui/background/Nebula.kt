package com.focusfriend.app.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.focusfriend.app.ui.theme.hex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class Filament(val x: Float, val y: Float, val r: Float, val color: Color, val a: Float, val rot: Float, val st: Float, val p: Float, val sx: Float, val sy: Float)
private class Orbit(val rx: Float, val k: Float, val tilt: Float, val a: Float, val w: Float, val ph: Float, val color: Color, val pr: Float)
private class Mote(val x: Float, val y: Float, val z: Float, val vx: Float, val vy: Float, val ph: Float, val color: Color)

private val R1 = rng(42)
private val FILAMENTS = listOf(
    floatArrayOf(.22f, .30f, .75f, .42f, -.5f, 2.2f) to hex("#6A0DAD"),
    floatArrayOf(.78f, .58f, .70f, .30f, .6f, 2.6f) to hex("#8A2BE2"),
    floatArrayOf(.55f, .44f, .55f, .16f, -.9f, 3.0f) to hex("#D900FF"),
    floatArrayOf(.30f, .72f, .60f, .14f, .3f, 2.4f) to hex("#F72585"),
    floatArrayOf(.82f, .22f, .45f, .12f, -.2f, 2.8f) to hex("#FF5400"),
    floatArrayOf(.15f, .55f, .40f, .10f, .9f, 2.0f) to hex("#E65100"),
    floatArrayOf(.62f, .86f, .55f, .30f, -.3f, 2.5f) to hex("#6A0DAD"),
).map { (v, c) -> Filament(v[0], v[1], v[2], c, v[3], v[4], v[5], R1() * 6.28f, .012f + R1() * .018f, .010f + R1() * .016f) }
private val ORBITS = listOf(
    Orbit(.62f, .34f, -.32f, .13f, .050f, .4f, hex("#FF5400"), 3.2f),
    Orbit(.86f, .30f, -.32f, .09f, -.032f, 2.1f, hex("#E0C3FC"), 2.2f),
    Orbit(1.12f, .28f, -.32f, .07f, .021f, 4.0f, hex("#F72585"), 2.6f),
)
private val MOTES = List(90) {
    val x = R1(); val y = R1(); val z = R1(); val vx = (R1() - .5f) * .004f; val vy = -.002f - R1() * .004f; val ph = R1() * 6.28f
    val c = if (R1() < .18f) hex("#FF5400") else if (R1() < .3f) hex("#F72585") else hex("#E0C3FC")
    Mote(x, y, z, vx, vy, ph, c)
}
private val DEEP = hex("#1E005B")
private val DEEP2 = hex("#2A085C")
private val LAVENDER = hex("#E0C3FC")

/** How far the nebula comes up behind each screen: Home keeps the orb in an almost pitch-black void. */
object NebulaLevel {
    const val START = .55f
    const val HOME = .18f
    const val SETTINGS = 1f
    const val DONE = .8f
    const val SESSION = .8f
}

@Composable
fun Nebula(time: State<Float>, level: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawNebula(time.value, level.value) }
}

private fun frac(v: Float) = ((v % 1f) + 1f) % 1f

fun DrawScope.drawNebula(t: Float, level: Float) = cssPixels { w, h ->
    val m = max(w, h)
    val l = level
    drawRect(Color.Black, size = Size(w, h))
    glow(w * .5f, h * .5f, m * .85f, DEEP, .85f * l)
    glow(w * (.35f + sin(t * .02f) * .06f), h * .32f, m * .6f, DEEP2, .7f * l)
    FILAMENTS.forEach { f ->
        val x = w * (f.x + sin(t * f.sx + f.p) * .12f)
        val y = h * (f.y + cos(t * f.sy + f.p) * .08f)
        val r = m * f.r * .5f * (1 + .1f * sin(t * .03f + f.p))
        streak(x, y, r, f.color, f.a * l, f.rot + sin(t * .01f + f.p) * .25f, f.st, 1 / f.st * 1.4f, BlendMode.Plus)
    }
    val cx = w * .5f
    val cy = h * .47f
    val voidAmt = ((1 - l) / .6f).coerceIn(0f, 1f)
    if (voidAmt > 0) glow(cx, cy, min(w, h) * .75f, Color.Black, .95f * voidAmt)
    ORBITS.forEach { o ->
        val rx = w * o.rx
        val ry = rx * o.k
        translate(cx, cy) {
            rotate(Math.toDegrees(o.tilt.toDouble()).toFloat(), pivot = Offset.Zero) {
                drawOval(LAVENDER.copy(alpha = o.a * (.5f + .5f * l)), Offset(-rx, -ry), Size(rx * 2, ry * 2), style = Stroke(.6f))
                val ang = o.ph + t * o.w
                val px = cos(ang) * rx
                val py = sin(ang) * ry
                glow(px, py, o.pr * 5, o.color, .5f)
                drawCircle(Color.White, o.pr * .45f, Offset(px, py))
            }
        }
    }
    MOTES.forEach { d ->
        val x = frac(d.x + t * d.vx) * w
        val y = frac(d.y + t * d.vy) * h
        val tw = .55f + .45f * sin(t * .6f + d.ph)
        if (d.z > .82f) glow(x, y, 6 + d.z * 10, d.color, .10f * tw)
        else drawRect(d.color.copy(alpha = ((.25f + d.z * .6f) * tw).coerceIn(0f, 1f)), Offset(x, y), Size(.6f + d.z * 1.2f, .6f + d.z * 1.2f))
    }
}

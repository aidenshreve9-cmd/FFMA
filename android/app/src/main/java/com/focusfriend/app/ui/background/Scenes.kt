package com.focusfriend.app.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.focusfriend.app.ui.theme.hex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The eight space scenes, drawn in code so they move and never need downloading. */
object Scenes {
    private val PLUS = BlendMode.Plus
    private val RG = rng(3)

    private class GalaxyStar(val ang: Float, val rr: Float, val off: Float, val s: Float, val a: Float, val color: Color)
    private class DiskMote(val k: Float, val ang: Float, val sp: Float, val s: Float, val a: Float, val color: Color, val z: Float)
    private class DustStar(val u: Float, val v: Float, val s: Float, val a: Float, val color: Color)
    private class Node(val x: Float, val y: Float, val ph: Float, val r: Float)
    private class YoungStar(val x: Float, val y: Float, val s: Float, val ph: Float)

    private val GALAXY = List(1500) { i ->
        val arm = i % 2
        val rr = RG().pow(.75f)
        val spread = (RG() - .5f) * .55f * (1 - rr * .4f)
        val color = if (rr < .16f) hex("#FFE3CC") else if (rr < .35f) (if (RG() < .5f) hex("#E0C3FC") else hex("#F72585")) else (if (RG() < .6f) hex("#8A2BE2") else hex("#6f7dff"))
        val off = (RG() - .5f) * .05f
        val s = .6f + RG() * 1.2f
        val a = .35f + RG() * .6f
        GalaxyStar(arm * PI.toFloat() + rr * 5.2f + spread, rr, off, s, a, color)
    }
    private val DISK = List(900) {
        val k = 1.55f + RG().pow(1.4f) * 2.6f
        val color = if (k < 1.9f) hex("#FFE3CC") else if (k < 2.4f) hex("#FF9A5A") else if (k < 3.1f) hex("#FF5400") else if (RG() < .5f) hex("#F72585") else hex("#8A2BE2")
        val ang = RG() * PI.toFloat() * 2
        val s = .6f + RG() * 1.4f
        val a = .3f + RG() * .6f
        val z = (RG() - .5f) * .06f
        DiskMote(k, ang, .35f / k.pow(1.5f), s, a, color, z)
    }
    private val MW = List(900) {
        val g = (RG() + RG() + RG() - 1.5f) / 1.5f
        val u = RG()
        val s = .5f + RG() * 1.1f
        val a = .25f + RG() * .7f
        val color = if (RG() < .15f) hex("#FFD2B0") else if (RG() < .3f) hex("#E0C3FC") else hex("#cfd6ff")
        DustStar(u, g * .16f, s, a, color)
    }
    private val NODES = List(28) { Node(.05f + RG() * .9f, .05f + RG() * .9f, RG() * 6.28f, .6f + RG() * 1.2f) }
    private val EDGES: List<Triple<Int, Int, Float>> = mutableListOf<Triple<Int, Int, Float>>().apply {
        NODES.forEachIndexed { i, a ->
            NODES.mapIndexed { j, b -> j to hypot(a.x - b.x, a.y - b.y) }.filter { it.first != i }.sortedBy { it.second }.take(3).forEach { (j, _) ->
                if (none { it.first == j && it.second == i }) add(Triple(i, j, RG()))
            }
        }
    }
    private val YOUNG = List(16) { YoungStar(RG(), RG() * .7f, .6f + RG() * 1.4f, RG() * 6.28f) }

    private fun nodeAt(n: Node, t: Float, w: Float, h: Float) = Offset((n.x + .012f * sin(t * .03f + n.ph)) * w, (n.y + .012f * cos(t * .025f + n.ph)) * h)
    private fun frac(v: Float) = ((v % 1f) + 1f) % 1f

    fun paint(scope: DrawScope, id: String, t: Float) = scope.cssPixels { w, h ->
        when (id) {
            "galaxy" -> galaxy(w, h, t)
            "horizon" -> horizon(w, h, t)
            "aurora" -> aurora(w, h, t)
            "dust" -> dust(w, h, t)
            "nursery" -> nursery(w, h, t)
            "web" -> web(w, h, t)
            "void" -> void(w, h, t)
            else -> quantum(w, h, t)
        }
    }

    private val QUANTUM_WISPS = listOf(
        floatArrayOf(.5f, .30f, .38f, .55f, -.5f, 2.4f) to hex("#6A0DAD"),
        floatArrayOf(.22f, .62f, .50f, .45f, .7f, 2.8f) to hex("#D900FF"),
        floatArrayOf(.18f, .40f, .66f, .42f, -.2f, 3.0f) to hex("#F72585"),
        floatArrayOf(.35f, .75f, .30f, .5f, .4f, 2.2f) to hex("#1E3AA8"),
        floatArrayOf(.35f, .50f, .80f, .5f, -.8f, 2.6f) to hex("#8A2BE2"),
        floatArrayOf(.10f, .70f, .22f, .3f, .2f, 2.4f) to hex("#FF5400"),
    )

    private fun DrawScope.quantum(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#07021a"), hex("#10042c"))
        val m = max(w, h)
        QUANTUM_WISPS.forEachIndexed { i, (v, color) ->
            val a = v[0]; val x = v[1]; val y = v[2]; val r = v[3]; val rot = v[4]; val st = v[5]
            streak(w * (x + .05f * sin(t * .02f + i)), h * (y + .04f * cos(t * .017f + i)), m * r * .5f, color, a, rot + .15f * sin(t * .01f + i), st, 1.3f / st, PLUS)
        }
        starfield(w, h, t, 150, .8f)
    }

    private fun DrawScope.galaxy(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#02010a"), hex("#05031a"))
        starfield(w, h, t, 120, .7f)
        val cx = w * .5f
        val cy = h * .44f
        val sc = min(w, h) * .47f
        val rot = t * .012f
        streak(cx, cy, sc * 1.1f, hex("#2A085C"), .55f, -.35f, 1f, .55f, PLUS)
        glow(cx, cy, sc * .38f, hex("#FFB080"), .45f, PLUS)
        glow(cx, cy, sc * .14f, Color.White, .8f, PLUS)
        val cr = cos(-.35f)
        val sr = sin(-.35f)
        GALAXY.forEach { p ->
            val a = p.ang + rot * (1.4f - p.rr)
            val x0 = cos(a) * p.rr * sc + p.off * sc
            val y0 = sin(a) * p.rr * sc * .52f
            drawRect(p.color.copy(alpha = p.a), Offset(cx + x0 * cr - y0 * sr, cy + x0 * sr + y0 * cr), Size(p.s, p.s), blendMode = PLUS)
        }
    }

    private fun DrawScope.horizon(w: Float, h: Float, t: Float) {
        drawRect(Color.Black, size = Size(w, h))
        starfield(w, h, t, 140, .6f)
        val cx = w * .5f
        val cy = h * .44f
        val r = min(w, h) * .13f
        glow(cx, cy, r * 4, hex("#FF5400"), .22f, PLUS)
        fun part(back: Boolean) = DISK.forEach { p ->
            val a = p.ang + t * p.sp
            val s = sin(a)
            if ((s < 0) != back) return@forEach
            val x = cx + cos(a) * p.k * r
            val y = cy + (s * .2f + p.z) * p.k * r
            val dop = .55f + .45f * cos(a) // brighter on the approaching side
            drawRect(p.color.copy(alpha = (p.a * dop).coerceIn(0f, 1f)), Offset(x, y), Size(p.s * 1.4f, p.s), blendMode = PLUS)
        }
        part(true)
        // Lensed image of the far side of the disk, arching over the shadow.
        listOf(Triple(r * 1.55f, .5f, 3f), Triple(r * 1.75f, .25f, 6f), Triple(r * 2.1f, .08f, 10f)).forEach { (rr, a, lw) ->
            drawArc(
                Color(255, 154, 90).copy(alpha = a), 189f, 162f, false,
                Offset(cx - rr, cy - rr * .92f), Size(rr * 2, rr * 1.84f),
                style = Stroke(lw, cap = StrokeCap.Round), blendMode = PLUS,
            )
        }
        drawCircle(Brush.radialGradient(.714f to Color.Black, 1f to Color.Transparent, center = Offset(cx, cy), radius = r * 1.12f), r * 1.12f, Offset(cx, cy))
        drawCircle(Color.Black, r, Offset(cx, cy))
        drawCircle(Color(255, 227, 204).copy(alpha = .85f), r * 1.03f, Offset(cx, cy), style = Stroke(1.2f), blendMode = PLUS)
        part(false)
    }

    private val AURORA_BANDS = listOf(hex("#8A2BE2"), hex("#D900FF"), hex("#3a5bff"))

    private fun DrawScope.aurora(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#02010c"), hex("#0a0626"))
        starfield(w, h, t, 160, .75f)
        AURORA_BANDS.forEachIndexed { b, color ->
            fun top(x: Float) = h * (.24f + .08f * b + .06f * sin(x / w * 5 + t * .07f + b * 2) + .025f * sin(x / w * 13 - t * .05f + b))
            val bandH = h * (.34f - .06f * b)
            val path = Path().apply {
                moveTo(0f, top(0f))
                var x = 0f
                while (x <= w) { lineTo(x, top(x)); x += 6f }
                x = w
                while (x >= 0f) { lineTo(x, top(x) + bandH * (.6f + .4f * sin(x / w * 7 + t * .04f + b))); x -= 6f }
                close()
            }
            val fill = Brush.verticalGradient(
                0f to color.copy(alpha = 0f), .35f to color.copy(alpha = .30f), .55f to color.copy(alpha = .12f), 1f to color.copy(alpha = 0f),
                startY = h * .12f, endY = h * .9f,
            )
            drawPath(path, fill, blendMode = PLUS)
            var x = 0f
            while (x <= w) {
                val a = .05f * (.5f + .5f * sin(x * .31f + t * .2f + b))
                if (a >= .01f) drawLine(color.copy(alpha = a), Offset(x, top(x)), Offset(x, top(x) + bandH * .9f), strokeWidth = 1f, blendMode = PLUS)
                x += 5f
            }
        }
        val ground = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w) { lineTo(x, h * .9f + sin(x / w * 6) * h * .02f + sin(x / w * 17) * h * .008f); x += 8f }
            lineTo(w, h)
            close()
        }
        drawPath(ground, hex("#010005"))
    }

    private fun DrawScope.dust(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#030724"), hex("#081138"))
        starfield(w, h, t, 120, .6f)
        val ang = -.62f
        val cx = w * .5f
        val cy = h * .5f
        val len = hypot(w, h)
        val ca = cos(ang)
        val sa = sin(ang)
        fun at(u: Float, v: Float) = Offset(cx + (u - .5f) * len * ca - v * len * sa, cy + (u - .5f) * len * sa + v * len * ca)
        for (i in 0..8) {
            val p = at(i / 8f, 0f)
            glow(p.x, p.y, len * .2f, if (i % 3 == 1) hex("#8A2BE2") else hex("#2A3A9A"), .28f, PLUS)
        }
        val drift = t * .002f
        MW.forEach { s ->
            val p = at(frac(s.u + drift), s.v)
            drawRect(s.color.copy(alpha = s.a.coerceAtMost(1f)), p, Size(s.s, s.s), blendMode = PLUS)
        }
        listOf(floatArrayOf(.22f, .01f, .18f), floatArrayOf(.45f, -.02f, .22f), floatArrayOf(.7f, .015f, .2f), floatArrayOf(.9f, -.01f, .15f)).forEachIndexed { i, v ->
            val p = at(v[0] + .02f * sin(t * .01f + i), v[1])
            streak(p.x, p.y, len * v[2] * .5f, Color.Black, .75f, ang, 3f, .28f)
        }
    }

    private val PILLARS = listOf(floatArrayOf(.28f, .16f, .06f, .44f), floatArrayOf(.55f, .22f, .08f, .34f), floatArrayOf(.8f, .14f, .05f, .5f))

    private fun DrawScope.nursery(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#0d030d"), hex("#1a0619"))
        starfield(w, h, t, 90, .5f)
        val m = max(w, h)
        streak(w * .5f, h * .3f, m * .4f, hex("#F72585"), .38f, .3f + .05f * sin(t * .02f), 2.2f, .6f, PLUS)
        streak(w * .35f, h * .42f, m * .32f, hex("#FF5400"), .26f, -.4f, 2f, .6f, PLUS)
        streak(w * .7f, h * .22f, m * .3f, hex("#D900FF"), .22f, .8f, 2.2f, .5f, PLUS)
        glow(w * .52f, h * .36f, m * .12f, hex("#FFE3CC"), .35f, PLUS)
        PILLARS.forEachIndexed { i, (x0, wb, wt, top) ->
            val path = Path().apply {
                moveTo(w * (x0 - wb / 2), h)
                var y = h
                while (y >= h * top) {
                    val k = (h - y) / (h * (1 - top))
                    lineTo(w * (x0 - (wb + (wt - wb) * k) / 2) + sin(y * .05f + i) * 4, y); y -= 6f
                }
                quadraticBezierTo(w * x0, h * (top - .04f), w * (x0 + wt / 2), h * top)
                y = h * top
                while (y <= h) {
                    val k = (h - y) / (h * (1 - top))
                    lineTo(w * (x0 + (wb + (wt - wb) * k) / 2) + sin(y * .045f + i * 2) * 4, y); y += 6f
                }
                close()
            }
            drawPath(path, Color(4, 1, 6).copy(alpha = .94f))
            glow(w * x0, h * top, w * .08f, hex("#FF9A5A"), .35f, PLUS)
        }
        YOUNG.forEach { s ->
            val x = s.x * w
            val y = s.y * h
            val a = .55f + .45f * sin(t * .5f + s.ph)
            val l = 5 + s.s * 6
            glow(x, y, l, hex("#E0C3FC"), .45f * a, PLUS)
            val c = Color.White.copy(alpha = (.5f * a).coerceIn(0f, 1f))
            drawLine(c, Offset(x - l, y), Offset(x + l, y), strokeWidth = .7f, blendMode = PLUS)
            drawLine(c, Offset(x, y - l), Offset(x, y + l), strokeWidth = .7f, blendMode = PLUS)
        }
    }

    private fun DrawScope.web(w: Float, h: Float, t: Float) {
        drawRect(Color.Black, size = Size(w, h))
        glow(w * .5f, h * .5f, max(w, h) * .7f, hex("#1E005B"), .5f, PLUS)
        val line = Color(138, 43, 226).copy(alpha = .28f)
        EDGES.forEach { (i, j, ph) ->
            val p1 = nodeAt(NODES[i], t, w, h)
            val p2 = nodeAt(NODES[j], t, w, h)
            drawLine(line, p1, p2, strokeWidth = .8f, blendMode = PLUS)
            val k = frac(t * .04f + ph)
            glow(p1.x + (p2.x - p1.x) * k, p1.y + (p2.y - p1.y) * k, 5f, hex("#E0C3FC"), .5f, PLUS)
        }
        NODES.forEach { n ->
            val p = nodeAt(n, t, w, h)
            glow(p.x, p.y, 12 + n.r * 12, hex("#D900FF"), .22f + .08f * sin(t * .3f + n.ph), PLUS)
            drawCircle(Color(255, 240, 255).copy(alpha = .85f), n.r, p, blendMode = PLUS)
        }
        starfield(w, h, t, 60, .4f)
    }

    private fun DrawScope.void(w: Float, h: Float, t: Float) {
        verticalGradient(w, h, hex("#000000"), hex("#020106"), hex("#05020e"))
        val m = max(w, h)
        glow(w * (.3f + .05f * sin(t * .012f)), h * (.34f + .03f * cos(t * .01f)), m * .35f, hex("#8A2BE2"), .22f, PLUS)
        glow(w * (.72f + .04f * cos(t * .009f)), h * (.6f + .04f * sin(t * .011f)), m * .3f, hex("#E0C3FC"), .12f, PLUS)
        glow(w * (.5f + .06f * sin(t * .007f)), h * (.82f + .02f * cos(t * .013f)), m * .4f, hex("#1E005B"), .5f, PLUS)
        listOf(.3f, .55f, .78f).forEachIndexed { i, y ->
            streak(w * (.5f + .2f * sin(t * .008f + i * 2)), h * y, m * .3f, hex("#B7A6CC"), .05f, 0f, 3.4f, .22f, PLUS)
        }
        starfield(w, h, t, 45, .45f)
    }
}

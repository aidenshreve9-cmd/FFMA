package app.focusfriend.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Shared drawing helpers for the nebula and scenic views. */
class Painter {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (r <= 0f || a <= 0f) return
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(x, y, r, intArrayOf(Palette.withAlpha(color, a), Palette.withAlpha(color, a * .45f), Palette.withAlpha(color, 0f)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint)
        paint.shader = null
    }
    fun streak(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float, rot: Float, sx: Float, sy: Float) {
        c.save(); c.translate(x, y); c.rotate((rot * 180 / PI).toFloat()); c.scale(sx, sy); glow(c, 0f, 0f, r, color, a); c.restore()
    }
    fun vgrad(c: Canvas, w: Float, h: Float, vararg colors: Int) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint); paint.shader = null
    }
    fun additive(on: Boolean) { paint.blendMode = if (on) PLUS else null }
    fun fillRect(c: Canvas, x: Float, y: Float, s1: Float, s2: Float, color: Int) {
        paint.shader = null; paint.color = color; c.drawRect(x, y, x + s1, y + s2, paint)
    }
    fun ridge(c: Canvas, w: Float, h: Float, base: Float, amp: Float, phase: Float, color: Int) {
        path.reset(); path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            val u = x / w * 6.283f + phase
            path.lineTo(x, base + sin(u) * amp + sin(u * 2.3f + phase) * amp * .45f + sin(u * 5.1f) * amp * .15f); x += 6f
        }
        path.lineTo(w, h); path.close()
        paint.shader = null; paint.color = color; c.drawPath(path, paint)
    }
    fun pathOf() = path.also { it.reset() }
}

private class Star(val x: Float, val y: Float, val s: Float, val b: Float, val ph: Float, val f: Float)
private val STARS = Random(77).let { r -> List(260) { Star(r.nextFloat(), r.nextFloat(), .5f + r.nextFloat() * 1.3f, .4f + r.nextFloat() * .6f, r.nextFloat() * 6.28f, .3f + r.nextFloat() * 1.1f) } }

private fun Painter.starfield(c: Canvas, w: Float, h: Float, t: Float, n: Int, alpha: Float, density: Float) {
    for (i in 0 until n) {
        val s = STARS[i]
        val a = alpha * s.b * (.45f + .55f * (.5f + .5f * sin(t * s.f + s.ph)))
        fillRect(c, s.x * w, s.y * h, s.s * density, s.s * density, Palette.withAlpha(0xFFECE4FF.toInt(), a))
    }
}

/**
 * The eight built-in scenic views, drawn in code (no image files), ported from the
 * browser preview: Quantum Nebula, Spiral Galaxy, Event Horizon, Aurora Veil,
 * Cosmic Dust, Stellar Nursery, Dark Matter Web and Ethereal Void.
 */
object ScenePainter {
    private class Particle(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val color: Int)

    private val rg = Random(3)
    private val galaxy = List(1500) { i ->
        val arm = i % 2; val rr = rg.nextFloat().pow(.75f); val spread = (rg.nextFloat() - .5f) * .55f * (1 - rr * .4f)
        val col = when { rr < .16f -> 0xFFFFE3CC.toInt(); rr < .35f -> if (rg.nextFloat() < .5f) Palette.HALO else Palette.ROSE; else -> if (rg.nextFloat() < .6f) Palette.VIOLET_2 else 0xFF6F7DFF.toInt() }
        Particle((arm * PI + rr * 5.2 + spread).toFloat(), rr, (rg.nextFloat() - .5f) * .05f, .6f + rg.nextFloat() * 1.2f, .35f + rg.nextFloat() * .6f, col)
    }
    private val disk = List(900) {
        val k = 1.55f + rg.nextFloat().pow(1.4f) * 2.6f
        val col = when { k < 1.9f -> 0xFFFFE3CC.toInt(); k < 2.4f -> 0xFFFF9A5A.toInt(); k < 3.1f -> Palette.COPPER; else -> if (rg.nextFloat() < .5f) Palette.ROSE else Palette.VIOLET_2 }
        Particle(k, rg.nextFloat() * 6.283f, .35f / k.pow(1.5f), .6f + rg.nextFloat() * 1.4f, .3f + rg.nextFloat() * .6f, col)
    }
    private val band = List(900) {
        val g = (rg.nextFloat() + rg.nextFloat() + rg.nextFloat() - 1.5f) / 1.5f
        Particle(rg.nextFloat(), g * .16f, .5f + rg.nextFloat() * 1.1f, .25f + rg.nextFloat() * .7f, 0f,
            when { rg.nextFloat() < .15f -> 0xFFFFD2B0.toInt(); rg.nextFloat() < .3f -> Palette.HALO; else -> 0xFFCFD6FF.toInt() })
    }
    private val nodes = List(28) { Particle(.05f + rg.nextFloat() * .9f, .05f + rg.nextFloat() * .9f, rg.nextFloat() * 6.28f, .6f + rg.nextFloat() * 1.2f, 0f, 0) }
    private val edges: List<Triple<Int, Int, Float>> = buildList {
        nodes.forEachIndexed { i, a ->
            nodes.indices.filter { it != i }.sortedBy { j -> hypot((a.a - nodes[j].a).toDouble(), (a.b - nodes[j].b).toDouble()) }.take(3).forEach { j ->
                if (none { it.first == j && it.second == i }) add(Triple(i, j, rg.nextFloat()))
            }
        }
    }
    private val young = List(16) { Particle(rg.nextFloat(), rg.nextFloat() * .7f, .6f + rg.nextFloat() * 1.4f, rg.nextFloat() * 6.28f, 0f, 0) }

    fun draw(id: String, c: Canvas, w: Float, h: Float, t: Float, p: Painter, density: Float) {
        when (id) {
            "galaxy" -> galaxy(c, w, h, t, p, density)
            "horizon" -> horizon(c, w, h, t, p, density)
            "aurora" -> aurora(c, w, h, t, p, density)
            "dust" -> dust(c, w, h, t, p, density)
            "nursery" -> nursery(c, w, h, t, p, density)
            "web" -> web(c, w, h, t, p, density)
            "void" -> void(c, w, h, t, p, density)
            else -> quantum(c, w, h, t, p, density)
        }
        p.additive(false)
    }

    private fun quantum(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF07021A.toInt(), 0xFF10042C.toInt())
        val m = max(w, h)
        p.additive(true)
        listOf(
            floatArrayOf(.5f, .30f, .38f, .55f, -.5f, 2.4f), floatArrayOf(.22f, .62f, .50f, .45f, .7f, 2.8f), floatArrayOf(.18f, .40f, .66f, .42f, -.2f, 3.0f),
            floatArrayOf(.35f, .75f, .30f, .5f, .4f, 2.2f), floatArrayOf(.35f, .50f, .80f, .5f, -.8f, 2.6f), floatArrayOf(.10f, .70f, .22f, .3f, .2f, 2.4f),
        ).zip(listOf(Palette.VIOLET, Palette.MAGENTA, Palette.ROSE, 0xFF1E3AA8.toInt(), Palette.VIOLET_2, Palette.COPPER)).forEachIndexed { i, (v, col) ->
            p.streak(c, w * (v[1] + .05f * sin(t * .02f + i)), h * (v[2] + .04f * cos(t * .017f + i)), m * v[3] * .5f, col, v[0], v[4] + .15f * sin(t * .01f + i), v[5], 1.3f / v[5])
        }
        p.additive(false)
        p.starfield(c, w, h, t, 150, .8f, d)
    }

    private fun galaxy(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF02010A.toInt(), 0xFF05031A.toInt())
        p.starfield(c, w, h, t, 120, .7f, d)
        val cx = w * .5f; val cy = h * .44f; val sc = min(w, h) * .47f; val rot = t * .012f
        p.additive(true)
        p.streak(c, cx, cy, sc * 1.1f, Palette.INDIGO_2, .55f, -.35f, 1f, .55f)
        p.glow(c, cx, cy, sc * .38f, 0xFFFFB080.toInt(), .45f)
        p.glow(c, cx, cy, sc * .14f, 0xFFFFFFFF.toInt(), .8f)
        val cr = cos(-.35f); val sr = sin(-.35f)
        for (q in galaxy) {
            val a = q.a + rot * (1.4f - q.b)
            val x0 = cos(a) * q.b * sc + q.c * sc; val y0 = sin(a) * q.b * sc * .52f
            p.fillRect(c, cx + x0 * cr - y0 * sr, cy + x0 * sr + y0 * cr, q.d * d, q.d * d, Palette.withAlpha(q.color, q.e))
        }
    }

    private fun horizon(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.fillRect(c, 0f, 0f, w, h, Palette.PITCH)
        p.starfield(c, w, h, t, 140, .6f, d)
        val cx = w * .5f; val cy = h * .44f; val r = min(w, h) * .13f
        p.additive(true)
        p.glow(c, cx, cy, r * 4, Palette.COPPER, .22f)
        fun part(back: Boolean) = disk.forEach { q ->
            val a = q.b + t * q.c; val s = sin(a); if ((s < 0) != back) return@forEach
            val dop = .55f + .45f * cos(a)                       // brighter on the approaching side
            p.fillRect(c, cx + cos(a) * q.a * r, cy + (s * .2f) * q.a * r, q.d * 1.4f * d, q.d * d, Palette.withAlpha(q.color, q.e * dop))
        }
        part(true)
        val arcP = p.paint
        arcP.style = Paint.Style.STROKE; arcP.strokeCap = Paint.Cap.ROUND
        for ((rr, a, lw) in listOf(Triple(1.55f, .5f, 3f), Triple(1.75f, .25f, 6f), Triple(2.1f, .08f, 10f))) {
            arcP.color = Palette.withAlpha(0xFFFF9A5A.toInt(), a); arcP.strokeWidth = lw * d
            c.drawArc(RectF(cx - r * rr, cy - r * rr * .92f, cx + r * rr, cy + r * rr * .92f), 189f, 162f, false, arcP)
        }
        arcP.style = Paint.Style.FILL
        p.additive(false)
        arcP.shader = RadialGradient(cx, cy, r * 1.12f, intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0), floatArrayOf(0f, .71f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.2f, arcP); arcP.shader = null
        arcP.color = 0xFF000000.toInt(); c.drawCircle(cx, cy, r, arcP)
        p.additive(true)
        arcP.style = Paint.Style.STROKE; arcP.strokeWidth = 1.2f * d; arcP.color = Palette.withAlpha(0xFFFFE3CC.toInt(), .85f)
        c.drawCircle(cx, cy, r * 1.03f, arcP); arcP.style = Paint.Style.FILL
        part(false)
    }

    private fun aurora(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF02010C.toInt(), 0xFF0A0626.toInt())
        p.starfield(c, w, h, t, 160, .75f, d)
        p.additive(true)
        listOf(Palette.VIOLET_2, Palette.MAGENTA, 0xFF3A5BFF.toInt()).forEachIndexed { b, col ->
            fun top(x: Float) = h * (.24f + .08f * b + .06f * sin(x / w * 5 + t * .07f + b * 2) + .025f * sin(x / w * 13 - t * .05f + b))
            val H = h * (.34f - .06f * b)
            val path = p.pathOf(); path.moveTo(0f, top(0f))
            var x = 0f; while (x <= w) { path.lineTo(x, top(x)); x += 6f }
            x = w; while (x >= 0f) { path.lineTo(x, top(x) + H * (.6f + .4f * sin(x / w * 7 + t * .04f + b))); x -= 6f }
            path.close()
            p.paint.shader = LinearGradient(0f, h * .12f, 0f, h * .9f, intArrayOf(Palette.withAlpha(col, 0f), Palette.withAlpha(col, .3f), Palette.withAlpha(col, .12f), Palette.withAlpha(col, 0f)),
                floatArrayOf(0f, .35f, .55f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(path, p.paint); p.paint.shader = null
            p.paint.style = Paint.Style.STROKE; p.paint.strokeWidth = d
            x = 0f
            while (x <= w) {
                val a = .05f * (.5f + .5f * sin(x * .31f + t * .2f + b))
                if (a >= .01f) { p.paint.color = Palette.withAlpha(col, a); c.drawLine(x, top(x), x, top(x) + H * .9f, p.paint) }
                x += 5f
            }
            p.paint.style = Paint.Style.FILL
        }
        p.additive(false)
        p.ridge(c, w, h, h * .9f, h * .02f, 0f, 0xFF010005.toInt())
    }

    private fun dust(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF030724.toInt(), 0xFF081138.toInt())
        p.starfield(c, w, h, t, 120, .6f, d)
        val ang = -.62f; val cx = w * .5f; val cy = h * .5f; val len = hypot(w, h); val ca = cos(ang); val sa = sin(ang)
        fun at(u: Float, v: Float) = floatArrayOf(cx + (u - .5f) * len * ca - v * len * sa, cy + (u - .5f) * len * sa + v * len * ca)
        p.additive(true)
        for (i in 0..8) { val q = at(i / 8f, 0f); p.glow(c, q[0], q[1], len * .2f, if (i % 3 == 1) Palette.VIOLET_2 else 0xFF2A3A9A.toInt(), .28f) }
        val drift = t * .002f
        for (s in band) { val q = at(((s.a + drift) % 1f + 1f) % 1f, s.b); p.fillRect(c, q[0], q[1], s.c * d, s.c * d, Palette.withAlpha(s.color, s.d)) }
        p.additive(false)
        listOf(floatArrayOf(.22f, .01f, .18f), floatArrayOf(.45f, -.02f, .22f), floatArrayOf(.7f, .015f, .2f), floatArrayOf(.9f, -.01f, .15f)).forEachIndexed { i, v ->
            val q = at(v[0] + .02f * sin(t * .01f + i), v[1]); p.streak(c, q[0], q[1], len * v[2] * .5f, Palette.PITCH, .75f, ang, 3f, .28f)
        }
    }

    private fun nursery(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF0D030D.toInt(), 0xFF1A0619.toInt())
        p.starfield(c, w, h, t, 90, .5f, d)
        val m = max(w, h)
        p.additive(true)
        p.streak(c, w * .5f, h * .3f, m * .4f, Palette.ROSE, .38f, .3f + .05f * sin(t * .02f), 2.2f, .6f)
        p.streak(c, w * .35f, h * .42f, m * .32f, Palette.COPPER, .26f, -.4f, 2f, .6f)
        p.streak(c, w * .7f, h * .22f, m * .3f, Palette.MAGENTA, .22f, .8f, 2.2f, .5f)
        p.glow(c, w * .52f, h * .36f, m * .12f, 0xFFFFE3CC.toInt(), .35f)
        p.additive(false)
        listOf(floatArrayOf(.28f, .16f, .06f, .44f), floatArrayOf(.55f, .22f, .08f, .34f), floatArrayOf(.8f, .14f, .05f, .5f)).forEachIndexed { i, (x0, wb, wt, top) ->
            val path = p.pathOf(); path.moveTo(w * (x0 - wb / 2), h)
            var y = h
            while (y >= h * top) { val k = (h - y) / (h * (1 - top)); path.lineTo(w * (x0 - (wb + (wt - wb) * k) / 2) + sin(y * .05f + i) * 4 * d, y); y -= 6f }
            path.quadTo(w * x0, h * (top - .04f), w * (x0 + wt / 2), h * top)
            y = h * top
            while (y <= h) { val k = (h - y) / (h * (1 - top)); path.lineTo(w * (x0 + (wb + (wt - wb) * k) / 2) + sin(y * .045f + i * 2) * 4 * d, y); y += 6f }
            path.close()
            p.paint.shader = null; p.paint.color = 0xF0040106.toInt(); c.drawPath(path, p.paint)
            p.additive(true); p.glow(c, w * x0, h * top, w * .08f, 0xFFFF9A5A.toInt(), .35f); p.additive(false)
        }
        p.additive(true)
        for (s in young) {
            val x = s.a * w; val y = s.b * h; val a = .55f + .45f * sin(t * .5f + s.d); val L = (5 + s.c * 6) * d
            p.glow(c, x, y, L, Palette.HALO, .45f * a)
            p.paint.style = Paint.Style.STROKE; p.paint.strokeWidth = .7f * d; p.paint.color = Palette.withAlpha(0xFFFFFFFF.toInt(), .5f * a)
            c.drawLine(x - L, y, x + L, y, p.paint); c.drawLine(x, y - L, x, y + L, p.paint); p.paint.style = Paint.Style.FILL
        }
    }

    private fun web(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.fillRect(c, 0f, 0f, w, h, Palette.PITCH)
        p.additive(true)
        p.glow(c, w * .5f, h * .5f, max(w, h) * .7f, Palette.INDIGO, .5f)
        fun node(n: Particle) = floatArrayOf((n.a + .012f * sin(t * .03f + n.c)) * w, (n.b + .012f * cos(t * .025f + n.c)) * h)
        p.paint.strokeWidth = .8f * d
        for ((i, j, ph) in edges) {
            val a = node(nodes[i]); val b = node(nodes[j])
            p.paint.style = Paint.Style.STROKE; p.paint.color = Palette.withAlpha(Palette.VIOLET_2, .28f); c.drawLine(a[0], a[1], b[0], b[1], p.paint)
            p.paint.style = Paint.Style.FILL
            val k = ((t * .04f + ph) % 1f + 1f) % 1f
            p.glow(c, a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, 5 * d, Palette.HALO, .5f)
        }
        for (n in nodes) {
            val q = node(n)
            p.glow(c, q[0], q[1], (12 + n.d * 12) * d, Palette.MAGENTA, .22f + .08f * sin(t * .3f + n.c))
            p.paint.color = Palette.withAlpha(0xFFFFF0FF.toInt(), .85f); c.drawCircle(q[0], q[1], n.d * d, p.paint)
        }
        p.additive(false)
        p.starfield(c, w, h, t, 60, .4f, d)
    }

    private fun void(c: Canvas, w: Float, h: Float, t: Float, p: Painter, d: Float) {
        p.vgrad(c, w, h, Palette.PITCH, 0xFF020106.toInt(), 0xFF05020E.toInt())
        val m = max(w, h)
        p.additive(true)
        p.glow(c, w * (.3f + .05f * sin(t * .012f)), h * (.34f + .03f * cos(t * .01f)), m * .35f, Palette.VIOLET_2, .22f)
        p.glow(c, w * (.72f + .04f * cos(t * .009f)), h * (.6f + .04f * sin(t * .011f)), m * .3f, Palette.HALO, .12f)
        p.glow(c, w * (.5f + .06f * sin(t * .007f)), h * (.82f + .02f * cos(t * .013f)), m * .4f, Palette.INDIGO, .5f)
        listOf(.3f, .55f, .78f).forEachIndexed { i, y -> p.streak(c, w * (.5f + .2f * sin(t * .008f + i * 2)), h * y, m * .3f, Palette.MUTED, .05f, 0f, 3.4f, .22f) }
        p.additive(false)
        p.starfield(c, w, h, t, 45, .45f, d)
    }

    /** Small still thumbnail for the Settings grid. */
    fun thumbnail(id: String, wPx: Int, hPx: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(id, Canvas(bmp), wPx.toFloat(), hPx.toFloat(), 6f, Painter(), density)
        return bmp
    }
}

/** Near-black space with indigo nebula, drifting wisps, orbit rings, small planets and dust. */
class NebulaView(c: Context) : AmbientView(c) {
    private val p = Painter()
    /** 0.18 on Home keeps the orb in near pitch-black; other screens let the nebula come up. */
    var level = .18f
        set(v) { field = v; invalidate() }
    private val rnd = Random(42)
    private val filaments = listOf(
        floatArrayOf(.22f, .30f, .75f, .42f, -.5f, 2.2f), floatArrayOf(.78f, .58f, .70f, .30f, .6f, 2.6f), floatArrayOf(.55f, .44f, .55f, .16f, -.9f, 3.0f),
        floatArrayOf(.30f, .72f, .60f, .14f, .3f, 2.4f), floatArrayOf(.82f, .22f, .45f, .12f, -.2f, 2.8f), floatArrayOf(.15f, .55f, .40f, .10f, .9f, 2.0f),
        floatArrayOf(.62f, .86f, .55f, .30f, -.3f, 2.5f),
    ).map { it + floatArrayOf(rnd.nextFloat() * 6.28f, .012f + rnd.nextFloat() * .018f, .010f + rnd.nextFloat() * .016f) }
    private val filamentColors = listOf(Palette.VIOLET, Palette.VIOLET_2, Palette.MAGENTA, Palette.ROSE, Palette.COPPER, 0xFFE65100.toInt(), Palette.VIOLET)
    private val motes = List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), (rnd.nextFloat() - .5f) * .004f, -.002f - rnd.nextFloat() * .004f, rnd.nextFloat() * 6.28f) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val m = max(w, h); val t = time; val L = level; val d = resources.displayMetrics.density
        canvas.drawColor(Palette.PITCH)
        p.glow(canvas, w * .5f, h * .5f, m * .85f, Palette.INDIGO, .85f * L)
        p.glow(canvas, w * (.35f + sin(t * .02f) * .06f), h * .32f, m * .6f, Palette.INDIGO_2, .7f * L)
        p.additive(true)
        filaments.forEachIndexed { i, f ->
            val x = w * (f[0] + sin(t * f[7] + f[6]) * .12f); val y = h * (f[1] + cos(t * f[8] + f[6]) * .08f)
            p.streak(canvas, x, y, m * f[2] * .5f * (1 + .1f * sin(t * .03f + f[6])), filamentColors[i], f[3] * L, f[4] + sin(t * .01f + f[6]) * .25f, f[5], 1 / f[5] * 1.4f)
        }
        p.additive(false)
        val cx = w * .5f; val cy = h * .47f
        val voidAmt = ((1 - L) / .6f).coerceIn(0f, 1f)
        if (voidAmt > 0) p.glow(canvas, cx, cy, min(w, h) * .75f, Palette.PITCH, .95f * voidAmt)
        val ring = p.paint
        for ((rx, k, a, speed, ph, col) in listOf(
            Orbit(.62f, .34f, .13f, .05f, .4f, Palette.COPPER), Orbit(.86f, .30f, .09f, -.032f, 2.1f, Palette.HALO), Orbit(1.12f, .28f, .07f, .021f, 4.0f, Palette.ROSE))) {
            canvas.save(); canvas.translate(cx, cy); canvas.rotate(-18.3f)
            ring.style = Paint.Style.STROKE; ring.strokeWidth = .6f * d; ring.shader = null; ring.color = Palette.withAlpha(Palette.HALO, a * (.5f + .5f * L))
            canvas.drawOval(-w * rx, -w * rx * k, w * rx, w * rx * k, ring); ring.style = Paint.Style.FILL
            val ang = ph + t * speed; val px = cos(ang) * w * rx; val py = sin(ang) * w * rx * k
            p.glow(canvas, px, py, 14f * d, col, .5f)
            ring.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(px, py, 1.3f * d, ring)
            canvas.restore()
        }
        for (mo in motes) {
            val x = ((mo[0] + t * mo[3]) % 1f + 1f) % 1f * w; val y = ((mo[1] + t * mo[4]) % 1f + 1f) % 1f * h
            val tw = .55f + .45f * sin(t * .6f + mo[5])
            if (mo[2] > .82f) p.glow(canvas, x, y, (6 + mo[2] * 10) * d, Palette.HALO, .10f * tw)
            else p.fillRect(canvas, x, y, (.6f + mo[2] * 1.2f) * d, (.6f + mo[2] * 1.2f) * d, Palette.withAlpha(Palette.HALO, (.25f + mo[2] * .6f) * tw))
        }
    }
    private data class Orbit(val rx: Float, val k: Float, val a: Float, val speed: Float, val ph: Float, val col: Int)
}

/** Full-screen scenic view behind the session timer: a built-in scene or the person's picture. */
class SceneView(c: Context) : AmbientView(c) {
    private val p = Painter()
    private var sceneId = "quantum"
    private var picture: Bitmap? = null
    private val src = Rect(); private val dst = RectF()

    fun show(id: String, bitmap: Bitmap?) { sceneId = id; picture = bitmap; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val bmp = picture
        if (bmp != null) {
            canvas.drawColor(Palette.PITCH)
            val s = max(w / bmp.width, h / bmp.height); val iw = bmp.width * s; val ih = bmp.height * s
            src.set(0, 0, bmp.width, bmp.height); dst.set((w - iw) / 2, (h - ih) / 2, (w + iw) / 2, (h + ih) / 2)
            canvas.drawBitmap(bmp, src, dst, p.paint)
        } else ScenePainter.draw(sceneId, canvas, w, h, time, p, resources.displayMetrics.density)
        // Dim toward the edges so the timer stays readable.
        p.paint.shader = RadialGradient(w / 2, h * .46f, max(w, h) * .8f, intArrayOf(0x1A000000, 0x1A000000, 0xA8000000.toInt()), floatArrayOf(0f, .2f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p.paint); p.paint.shader = null
    }
}


package app.focusfriend.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.focusfriend.core.Durations
import app.focusfriend.core.SlideGesture
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Drives slow ambient animation at ~30 fps while a view is attached and visible.
 * Stops entirely when reduced motion is on (views then draw a still frame).
 */
abstract class AmbientView(c: Context) : View(c) {
    protected var time = 20f
        private set
    private var startNs = 0L
    private var lastFrameNs = 0L
    private var running = false
    /** Consecutive late frames, used by subclasses to lower their cost. */
    protected var lateFrames = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            if (now - lastFrameNs >= 32_000_000L) {
                val gap = (now - lastFrameNs) / 1_000_000L
                if (lastFrameNs != 0L && gap in 49..200) lateFrames++ else if (gap < 45) lateFrames = (lateFrames - 1).coerceAtLeast(0)
                lastFrameNs = now
                time = (now - startNs) / 1e9f
                invalidate()
            }
            postOnAnimation(this)
        }
    }

    fun setAnimating(on: Boolean) {
        val want = on && Motion.enabled
        if (want == running) { invalidate(); return }
        running = want
        if (want) { if (startNs == 0L) startNs = System.nanoTime(); lastFrameNs = 0L; postOnAnimation(tick) } else { removeCallbacks(tick); invalidate() }
    }

    override fun onDetachedFromWindow() { running = false; removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) { running = false; removeCallbacks(tick) }
    }
}

/**
 * The 3D quantum orb. API 33+: a volumetric AGSL shader (the same design as the browser
 * preview's WebGL shader). Older versions, or any shader failure: a polished 2D fallback.
 * Touch/hover raises [active]: the core grows and brightens.
 */
class OrbView(c: Context) : AmbientView(c) {
    var active = 0f
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) try { RuntimeShader(ORB_AGSL) } catch (e: RuntimeException) { null } else null
    private var steps = 20

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        // Battery/frame budget: sustained late frames lower the volume samples (20 → 14 → 10).
        if (lateFrames > 30 && steps > 10) { steps = if (steps > 14) 14 else 10; lateFrames = 0 }
        if (Build.VERSION.SDK_INT >= 33 && shader is RuntimeShader) {
            try {
                shader.setFloatUniform("uRes", w, h)
                shader.setFloatUniform("uTime", time)
                shader.setFloatUniform("uAct", active)
                shader.setFloatUniform("uSteps", steps.toFloat())
                paint.shader = shader
                canvas.drawRect(0f, 0f, w, h, paint)
                return
            } catch (e: RuntimeException) { /* fall through to 2D */ }
        }
        draw2D(canvas, w, h)
    }

    private val blobs = listOf(Palette.VIOLET_2, Palette.ROSE, 0xFF1E3AA8.toInt(), Palette.MAGENTA, Palette.VIOLET, Palette.ROSE)

    private fun draw2D(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2; val cy = h / 2
        val r = minOf(w, h) * .35f * (1 + .022f * sin(time * .698f)) * (1 + .055f * active)
        paint.blendMode = PLUS
        glow(canvas, cx, cy, r * 1.45f, Palette.VIOLET_2, .35f + .2f * active)
        paint.blendMode = null
        canvas.save()
        clip.reset(); clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW); canvas.clipPath(clip)
        paint.shader = RadialGradient(cx, cy, r, 0xFF0B1450.toInt(), 0xFF02030F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.blendMode = PLUS
        blobs.forEachIndexed { i, col ->
            val a = .05f + i * .013f; val b = .07f + i * .009f; val p = i * 1.3f
            glow(canvas, cx + sin(time * a + p) * r * .45f, cy + cos(time * b + p) * r * .45f, r * .8f, col, .5f)
        }
        glow(canvas, cx, cy, r * (.45f + .15f * active), Color.WHITE, .55f + .3f * active)
        paint.blendMode = null
        paint.shader = RadialGradient(cx, cy, r, intArrayOf(0, 0, Palette.withAlpha(Palette.MAGENTA, .35f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        canvas.restore()
    }
    private val clip = android.graphics.Path()
    private fun glow(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        paint.shader = RadialGradient(x, y, r, intArrayOf(Palette.withAlpha(color, a), Palette.withAlpha(color, a * .45f), Palette.withAlpha(color, 0f)),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, paint)
    }

    companion object {
        // AGSL port of the browser's GLSL orb (prototype/focus-friend.html). Loop bound is constant;
        // uSteps lowers the sample count when frames run late.
        const val ORB_AGSL = """
uniform float2 uRes; uniform float uTime; uniform float uAct; uniform float uSteps;
float hash(float3 p){ p=fract(p*0.3183099+0.1); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float noise(float3 x){ float3 i=floor(x); float3 f=fract(x); f=f*f*(3.0-2.0*f);
  return mix(mix(mix(hash(i),hash(i+float3(1,0,0)),f.x),mix(hash(i+float3(0,1,0)),hash(i+float3(1,1,0)),f.x),f.y),
             mix(mix(hash(i+float3(0,0,1)),hash(i+float3(1,0,1)),f.x),mix(hash(i+float3(0,1,1)),hash(i+float3(1,1,1)),f.x),f.y),f.z); }
float fbm(float3 p){ float s=0.0; float a=0.5; for(int i=0;i<3;i++){ s+=a*noise(p); p=p*2.03+float3(1.7,9.2,3.1); a*=0.5; } return s/0.875; }
float3x3 rY(float a){ float c=cos(a); float s=sin(a); return float3x3(c,0,s, 0,1,0, -s,0,c); }
float3x3 rX(float a){ float c=cos(a); float s=sin(a); return float3x3(1,0,0, 0,c,-s, 0,s,c); }
half4 main(float2 fc){
  const float3 DEEP=float3(0.008,0.012,0.08); const float3 COBALT=float3(0.035,0.07,0.34);
  const float3 VIOLET=float3(0.54,0.17,0.89); const float3 MAGENTA=float3(0.97,0.16,0.55);
  const float3 LAV=float3(0.88,0.76,0.99); const float3 HOT=float3(1.0,0.97,1.0);
  float2 uv=(fc*2.0-uRes)/uRes.y; uv.y=-uv.y;
  float t=uTime;
  float R=0.70*(1.0+0.022*sin(t*0.698))*(1.0+0.055*uAct);
  float r=length(uv); float d=max(r-R,0.0);
  float3 haloC=mix(VIOLET,MAGENTA,clamp(0.3+0.25*sin(t*0.21)+0.35*uAct,0.0,1.0));
  float halo=(exp(-d*10.0)*0.55+exp(-d*4.0)*0.22)*(0.85+0.6*uAct)*smoothstep(1.0,0.72,r);
  float3 col=haloC*halo; float alpha=halo;
  if(r<R+0.01){
    float rc=min(r,R-0.0001); float z=sqrt(R*R-rc*rc);
    float3 n=normalize(float3(uv,z));
    float3 sp=n*2.6+float3(0.0,0.0,t*0.06);
    float3 np=normalize(n+0.16*float3(fbm(sp)-0.5,fbm(sp+float3(5.2,1.3,2.7))-0.5,0.0));
    float3 ro=float3(uv,z)+(np-n)*0.22*R;
    float dt=2.0*z/uSteps;
    float3x3 m1=rY(t*0.045)*rX(0.4+0.25*sin(t*0.027));
    float3x3 m2=rY(-t*0.031+1.3)*rX(-0.3);
    float3 acc=float3(0.0); float T=1.0;
    for(int i=0;i<20;i++){
      if(float(i)>=uSteps) break;
      float3 q=(ro-float3(0.0,0.0,(float(i)+0.5)*dt))/R; float rr=length(q);
      float n1=fbm(m1*q*2.0+float3(0.0,t*0.02,0.0));
      float n2=noise(m2*q*3.4+float3(t*0.025));
      float fil=(pow(1.0-abs(n1*2.0-1.0),6.0)+0.7*pow(1.0-abs(n2*2.0-1.0),9.0))*smoothstep(1.05,0.25,rr);
      float core=exp(-rr*rr*(10.0-3.5*uAct))*(1.0+0.9*uAct);
      float3 c=mix(COBALT,VIOLET,smoothstep(0.05,0.45,fil));
      c=mix(c,MAGENTA,smoothstep(0.35,0.95,fil)*(0.55+0.45*sin(q.y*3.0+q.x*2.0+t*0.15)));
      c+=mix(LAV,HOT,smoothstep(0.3,1.1,core))*core*1.6;
      float a=clamp((fil*1.5+core*0.9)*dt*3.0,0.0,1.0);
      acc+=T*c*a*1.5; T*=1.0-a*0.6;
    }
    float3 inside=acc+T*mix(DEEP,COBALT,0.35+0.3*n.y);
    float fres=pow(1.0-max(np.z,0.0),2.6);
    inside+=mix(VIOLET,MAGENTA,0.5+0.5*sin(atan(uv.y,uv.x)*2.0+t*0.12))*fres*0.85;
    float3 L=normalize(float3(-0.45,0.55,0.75)); float3 H=normalize(L+float3(0.0,0.0,1.0));
    float nh=max(dot(np,H),0.0);
    float spec=pow(nh,140.0)*1.3+pow(nh,20.0)*0.08;
    float glint=pow(fbm(np*9.0+float3(t*0.08,0.0,-t*0.05)),11.0)*3.0*smoothstep(0.35,0.95,dot(np,L));
    inside+=HOT*(spec+glint);
    inside=1.0-exp(-inside*1.25);
    float e=smoothstep(R+0.004,R-0.006,r);
    col=mix(col,inside,e); alpha=mix(alpha,1.0,e);
  }
  return half4(half3(col),half(alpha));
}"""
    }
}

/**
 * The orbital dial around the orb: slowly turning micro ticks, a glowing arc on a
 * 60-minute face marked 15/30/45/60, and a breathing magenta ring.
 */
class DialView(c: Context) : AmbientView(c) {
    private var fraction = 0.25f
    private var activeMinutes = 15
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()
    private var anim: ValueAnimator? = null
    private val mono = Fonts.mono(c)
    // The face never changes shape, so its geometry is computed once (in the dial's 200×200 units).
    private val arcShader = SweepGradient(100f, 100f, intArrayOf(Palette.ROSE, Palette.MAGENTA, Palette.VIOLET_2, Palette.ROSE), null)
    private val ticks = FloatArray(120 * 4).also { t ->
        for (i in 0 until 120) {
            val a = i / 120.0 * Math.PI * 2; val r1 = if (i % 10 == 0) 88f else 90.5f
            t[4 * i] = 100 + sin(a).toFloat() * r1; t[4 * i + 1] = 100 - cos(a).toFloat() * r1
            t[4 * i + 2] = 100 + sin(a).toFloat() * 93f; t[4 * i + 3] = 100 - cos(a).toFloat() * 93f
        }
    }
    private val numbers = Durations.MINUTES.map { m ->
        val a = m / 60.0 * Math.PI * 2
        Triple(m, "$m", floatArrayOf(100 + sin(a).toFloat() * 72.5f, 100 - cos(a).toFloat() * 72.5f + 2.4f))
    }

    fun set(frac: Float, minutes: Int, animate: Boolean) {
        activeMinutes = minutes
        anim?.cancel()
        val target = frac.coerceIn(0f, 1f)
        if (animate && Motion.enabled) anim = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 700; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { fraction = it.animatedValue as Float; invalidate() }; start()
        } else { fraction = target; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 200f
        canvas.save(); canvas.translate(width / 2f - 100 * s, height / 2f - 100 * s); canvas.scale(s, s)
        p.style = Paint.Style.STROKE; p.shader = null
        p.color = Palette.withAlpha(Palette.HALO, .16f); p.strokeWidth = .6f; canvas.drawCircle(100f, 100f, 97f, p)
        // ticks: one revolution every 240 s
        canvas.save(); canvas.rotate(time * 1.5f, 100f, 100f)
        for (i in 0 until 120) {
            val major = i % 10 == 0
            p.color = Palette.withAlpha(Palette.HALO, if (major) .7f else .3f); p.strokeWidth = if (major) .7f else .35f
            canvas.drawLine(ticks[4 * i], ticks[4 * i + 1], ticks[4 * i + 2], ticks[4 * i + 3], p)
        }
        canvas.restore()
        p.color = Palette.withAlpha(Palette.INDIGO_2, .85f); p.strokeWidth = 2.2f; canvas.drawCircle(100f, 100f, 84f, p)
        arcRect.set(16f, 16f, 184f, 184f)
        if (fraction > .002f) {
            p.strokeWidth = 2.6f; p.strokeCap = Paint.Cap.ROUND
            p.shader = arcShader
            p.setShadowLayer(4f, 0f, 0f, Palette.MAGENTA)
            canvas.drawArc(arcRect, -90f, 360f * fraction, false, p)
            p.clearShadowLayer(); p.shader = null
            val a = fraction * Math.PI * 2
            p.style = Paint.Style.FILL; p.color = Color.WHITE
            canvas.drawCircle(100 + sin(a).toFloat() * 84f, 100 - cos(a).toFloat() * 84f, 2.2f, p)
            p.style = Paint.Style.STROKE
        }
        p.color = Palette.withAlpha(Palette.HALO, .09f); p.strokeWidth = .5f; canvas.drawCircle(100f, 100f, 77f, p)
        val breath = if (Motion.enabled) .22f + .53f * (.5f + .5f * sin(time * .698f)) else .5f
        p.color = Palette.withAlpha(Palette.MAGENTA, breath); p.strokeWidth = 1.1f; canvas.drawCircle(100f, 100f, 66f, p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.typeface = mono; p.textSize = 7f
        for ((m, label, at) in numbers) {
            p.color = if (m == activeMinutes) Palette.HALO else Palette.withAlpha(Palette.HALO, .5f)
            canvas.drawText(label, at[0], at[1], p)
        }
        canvas.restore()
    }
}

/** Warm-magenta rings that spread outward while the orb is touched or hovered. */
class RippleView(c: Context) : View(c) {
    private val rings = ArrayList<Float>()      // start times (s)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var spawning = false
    private val spawn = object : Runnable { override fun run() { if (spawning) { add(); postDelayed(this, 1500) } } }
    private val frame = object : Runnable { override fun run() { if (rings.isNotEmpty()) { invalidate(); postOnAnimation(this) } } }

    fun setActive(on: Boolean) {
        if (on == spawning || !Motion.enabled) { if (!on) spawning = false; return }
        spawning = on
        if (on) { removeCallbacks(spawn); post(spawn) }
    }
    private fun add() { rings += System.nanoTime() / 1e9f; removeCallbacks(frame); postOnAnimation(frame) }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime() / 1e9f
        rings.removeAll { now - it > 2.8f }
        val base = minOf(width, height) * .31f
        for (start in rings) {
            val k = ((now - start) / 2.8f).coerceIn(0f, 1f)
            val e = 1 - (1 - k) * (1 - k)
            p.strokeWidth = dp(1.2f); p.color = Palette.withAlpha(Palette.ROSE, .55f * (1 - k))
            p.setShadowLayer(dp(10f), 0f, 0f, Palette.withAlpha(Palette.MAGENTA, .5f * (1 - k)))
            canvas.drawCircle(width / 2f, height / 2f, base * (1 + 2.1f * e), p)
        }
    }
}

/**
 * Horizontal duration strip inside the orb: follows the finger, loops 15→30→45→60→15,
 * and snaps smoothly. Neighbours fade and shrink toward the edges.
 */
class CarouselView(c: Context) : View(c) {
    var position = Durations.DEFAULT_INDEX.toDouble()
        private set
    var onIndexChanged: ((Int) -> Unit)? = null
    private var index = Durations.DEFAULT_INDEX
    private var snap: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.mono(c); color = Color.WHITE; textAlign = Paint.Align.CENTER }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun setPosition(pos: Double) { snap?.cancel(); position = pos; update() }

    fun snapTo(target: Int) {
        snap?.cancel()
        val from = position; val dist = target - from
        if (!Motion.enabled || abs(dist) < 1e-3) { position = target.toDouble(); update(); return }
        snap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (380 + minOf(1.0, abs(dist)) * 220).toLong()
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener { position = from + dist * (it.animatedValue as Float); update() }
            start()
        }
    }

    fun reset() { snap?.cancel(); position = Durations.DEFAULT_INDEX.toDouble(); index = -1; update() }

    private fun update() {
        val i = Durations.indexAt(position.roundToInt())
        if (i != index) { index = i; onIndexChanged?.invoke(i) }
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dpi(170f), dpi(26f))

    override fun onDraw(canvas: Canvas) {
        val item = dp(SlideGesture.ITEM_WIDTH_DP)
        val base = position.roundToInt()
        p.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics)
        for (k in -2..2) {
            val i = base + k
            val x = ((i - position) * item).toFloat()
            val d = minOf(1f, abs(x) / (item * 1.7f))
            val edge = (1f - abs(x) / (width / 2f)).coerceIn(0f, 1f) // soft edge mask
            p.alpha = ((1 - d * .85f) * edge * 255).toInt().coerceIn(0, 255)
            canvas.save(); canvas.translate(width / 2f + x, height / 2f); val sc = 1 - d * .28f; canvas.scale(sc, sc)
            canvas.drawText("${Durations.minutesAt(i)}", 0f, p.textSize * .35f, p)
            canvas.restore()
        }
    }
}

/**
 * The Home Focus button: orb + dial + carousel. Tap starts Focus; a horizontal slide changes
 * the duration and never starts a session; vertical gestures do nothing.
 */
@SuppressLint("ViewConstructor")
class FocusButton(c: Context, private val onStart: () -> Unit, private val onDuration: (Int) -> Unit) : FrameLayout(c) {
    val orb = OrbView(c)
    val dial = DialView(c)
    private val ripples = RippleView(c)
    val carousel = CarouselView(c)
    var minutes = Durations.MINUTES[Durations.DEFAULT_INDEX]
        private set
    private var activeAnim: ValueAnimator? = null

    init {
        isFocusable = true; isClickable = true
        addView(ripples, LayoutParams(-1, -1))
        addView(orb, LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        addView(dial, LayoutParams(-1, -1))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(label(c, "Focus", 40f, Color.WHITE, Fonts.serif(c), Gravity.CENTER).apply { setShadowLayer(dp(14f), 0f, 0f, Palette.withAlpha(Palette.HALO, .55f)) })
            addView(carousel, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(6f) })
            addView(label(c, "MIN", 11f, Palette.HALO, Fonts.mono(c), Gravity.CENTER).apply { letterSpacing = .2f })
        }, LayoutParams(-2, -2, Gravity.CENTER))
        carousel.onIndexChanged = { i ->
            minutes = Durations.MINUTES[i]
            dial.set(minutes / 60f, minutes, animate = true)
            describe()
            onDuration(minutes)
        }
        carousel.reset()
    }

    override fun onMeasure(w: Int, h: Int) {
        val size = minOf(MeasureSpec.getSize(w), dpi(320f))
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(spec, spec)
        val orbSize = (size * .886f).toInt()
        orb.measure(MeasureSpec.makeMeasureSpec(orbSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(orbSize, MeasureSpec.EXACTLY))
    }

    fun resetDuration() { carousel.reset() }
    fun setAnimating(on: Boolean) { orb.setAnimating(on); dial.setAnimating(on) }

    private fun describe() {
        contentDescription = "Focus, $minutes minutes. Slide left or right, or use the arrow keys, to change the time."
    }

    private fun setActive(on: Boolean) {
        ripples.setActive(on)
        activeAnim?.cancel()
        if (!Motion.enabled) { orb.active = if (on) 1f else 0f; return }
        activeAnim = ValueAnimator.ofFloat(orb.active, if (on) 1f else 0f).apply { duration = 600; addUpdateListener { orb.active = it.animatedValue as Float }; start() }
    }

    // ---------- gesture: tap vs horizontal slide ----------
    private var downX = 0f; private var downY = 0f; private var startPos = 0.0
    private var axis: SlideGesture.Axis? = null
    private var tracker: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; axis = null; startPos = carousel.position
                carousel.setPosition(carousel.position)
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                parent?.requestDisallowInterceptTouchEvent(true)
                setActive(true)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dx = e.x - downX; val dy = e.y - downY
                if (axis == null && Math.hypot(dx.toDouble(), dy.toDouble()) > SlideGesture.TAP_SLOP_DP * density) axis = SlideGesture.axisOf(dx, dy)
                if (axis == SlideGesture.Axis.HORIZONTAL) carousel.setPosition(SlideGesture.positionWhileDragging(startPos, dx / density))
            }
            MotionEvent.ACTION_UP -> {
                setActive(false)
                val t = tracker; tracker = null
                when (axis) {
                    null -> { carousel.snapTo(carousel.position.roundToInt()); performClick() }       // a tap
                    SlideGesture.Axis.HORIZONTAL -> {
                        t?.computeCurrentVelocity(1)                                                    // px per ms
                        val itemsPerMs = -(t?.xVelocity ?: 0f) / (SlideGesture.ITEM_WIDTH_DP * density)
                        carousel.snapTo(SlideGesture.settle(startPos, carousel.position, itemsPerMs.toDouble()))
                    }
                    SlideGesture.Axis.VERTICAL -> carousel.snapTo(carousel.position.roundToInt())      // vertical: nothing
                }
                t?.recycle()
            }
            MotionEvent.ACTION_CANCEL -> { setActive(false); tracker?.recycle(); tracker = null; carousel.snapTo(carousel.position.roundToInt()) }
            MotionEvent.ACTION_HOVER_ENTER -> setActive(true)
            MotionEvent.ACTION_HOVER_EXIT -> setActive(false)
        }
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) setActive(true)
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) setActive(false)
        return super.onHoverEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); onStart(); return true }

    // Keyboard: ← previous, → next, Enter starts.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { carousel.snapTo(carousel.position.roundToInt() - 1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { carousel.snapTo(carousel.position.roundToInt() + 1); true }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { performClick(); true }
        else -> super.onKeyDown(keyCode, event)
    }

    // Screen readers: swipe up/down (scroll actions) changes the duration; double-tap starts.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { carousel.snapTo(carousel.position.roundToInt() + 1); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { carousel.snapTo(carousel.position.roundToInt() - 1); true }
        else -> super.performAccessibilityAction(action, arguments)
    }
}

/** The session timer: same orb and dial; the arc shrinks on the 60-minute face. Tap to end early. */
@SuppressLint("ViewConstructor")
class TimerButton(c: Context, private val onTap: () -> Unit) : FrameLayout(c) {
    val orb = OrbView(c)
    val dial = DialView(c)
    private val ripples = RippleView(c)
    private val minutesText = label(c, "15", 54f, Color.WHITE, Fonts.mono(c, 300), Gravity.CENTER)

    init {
        isFocusable = true; isClickable = true
        addView(ripples, LayoutParams(-1, -1))
        addView(orb, LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        addView(dial, LayoutParams(-1, -1))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(LinearLayout(c).apply {
                gravity = Gravity.BOTTOM
                addView(minutesText)
                addView(label(c, "min", 18f, Palette.HALO, Fonts.mono(c)).apply { setPadding(dpi(6f), 0, 0, dpi(8f)) })
            })
            addView(label(c, "TAP TO END EARLY", 11f, Palette.withAlpha(Palette.TEXT, .75f), Fonts.sans(c, 500), Gravity.CENTER).apply { letterSpacing = .16f })
        }, LayoutParams(-2, -2, Gravity.CENTER))
        setOnClickListener { onTap() }
        setOnTouchListener { _, e ->
            when (e.actionMasked) { MotionEvent.ACTION_DOWN -> active(true); MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> active(false) }
            false
        }
    }

    private fun active(on: Boolean) { ripples.setActive(on); orb.active = if (on) 1f else 0f }

    override fun onMeasure(w: Int, h: Int) {
        val size = minOf(MeasureSpec.getSize(w), dpi(320f))
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(spec, spec)
        val orbSize = (size * .886f).toInt()
        orb.measure(MeasureSpec.makeMeasureSpec(orbSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(orbSize, MeasureSpec.EXACTLY))
    }

    fun show(minutesLeft: Int, arc: Float, sessionMinutes: Int, animate: Boolean) {
        minutesText.text = "$minutesLeft"
        dial.set(arc, sessionMinutes, animate)
        contentDescription = "$minutesLeft ${if (minutesLeft == 1) "minute" else "minutes"} left. Tap to end early."
    }

    fun setAnimating(on: Boolean) { orb.setAnimating(on); dial.setAnimating(on) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { performClick(); true } else super.onKeyDown(keyCode, event)
}

/** Status and footer text styles used on the session screen. */
fun statusPill(c: Context, text: String, warn: Boolean): TextView =
    label(c, text.uppercase(), 10f, if (warn) Palette.WARN else Palette.HALO, Fonts.mono(c), Gravity.CENTER).apply {
        letterSpacing = .07f
        setPadding(dpi(16f), dpi(9f), dpi(16f), dpi(9f))
        background = GlassDrawable(c, 999f, prism = false)
    }

package app.focusfriend.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.AssetManager
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView

/** "Quantum Nebula Void" tokens — the same palette as the browser preview. */
object Palette {
    const val PITCH = 0xFF000000.toInt()
    const val OBSIDIAN = 0xFF08040C.toInt()
    const val INDIGO = 0xFF1E005B.toInt()
    const val INDIGO_2 = 0xFF2A085C.toInt()
    const val COPPER = 0xFFFF5400.toInt()
    const val ROSE = 0xFFF72585.toInt()
    const val MAGENTA = 0xFFD900FF.toInt()
    const val VIOLET = 0xFF6A0DAD.toInt()
    const val VIOLET_2 = 0xFF8A2BE2.toInt()
    const val HALO = 0xFFE0C3FC.toInt()
    const val PRISM_A = 0xFFFF007F.toInt()
    const val PRISM_B = 0xFF7B2CBF.toInt()
    const val TEXT = 0xFFF4ECFF.toInt()
    const val MUTED = 0xFFB7A6CC.toInt()
    const val FAINT = 0xFF8C7BA3.toInt()   // a touch lighter than the web token for 4.5:1 on panels
    const val WARN = 0xFFFFB38A.toInt()
    const val GLASS = 0x730A0514
    const val GLASS_LINE = 0x29E0C3FC

    fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

/** Bundled OFL fonts (assets/fonts). Falls back to system faces if a file is missing. */
object Fonts {
    private val cache = HashMap<String, Typeface>()
    private fun load(assets: AssetManager, file: String, weight: Int, fallback: Typeface): Typeface =
        cache.getOrPut("$file@$weight") {
            try { Typeface.Builder(assets, "fonts/$file").setFontVariationSettings("'wght' $weight").build() ?: fallback }
            catch (e: RuntimeException) { fallback }
        }
    /** Playfair Display: titles and quotes. */
    fun serif(c: Context, weight: Int = 400) = load(c.assets, "PlayfairDisplay.ttf", weight, Typeface.SERIF)
    fun serifItalic(c: Context) = load(c.assets, "PlayfairDisplay-Italic.ttf", 400, Typeface.create(Typeface.SERIF, Typeface.ITALIC))
    /** Plus Jakarta Sans: labels and UI. */
    fun sans(c: Context, weight: Int = 500) = load(c.assets, "PlusJakartaSans.ttf", weight, Typeface.SANS_SERIF)
    /** JetBrains Mono: numbers. */
    fun mono(c: Context, weight: Int = 400) = load(c.assets, "JetBrainsMono.ttf", weight, Typeface.MONOSPACE)
}

fun Context.dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dpi(v: Float) = dp(v).toInt()
fun View.dp(v: Float) = context.dp(v)
fun View.dpi(v: Float) = context.dpi(v)

/** Reduced motion: honour the system "Remove animations" setting everywhere. */
object Motion {
    val enabled get() = ValueAnimator.areAnimatorsEnabled()
    fun ms(v: Long) = if (enabled) v else 0L
}

/** Screen-reader state text (API 30+), falling back to content descriptions. */
fun View.stateText(state: String) {
    if (Build.VERSION.SDK_INT >= 30) stateDescription = state
}

fun label(c: Context, text: String, sizeSp: Float, color: Int = Palette.TEXT, face: Typeface = Fonts.sans(c), gravity: Int = Gravity.START) =
    TextView(c).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); setTextColor(color); typeface = face; this.gravity = gravity
        setLineSpacing(0f, 1.25f)
    }

fun eyebrow(c: Context, text: String) = label(c, text.uppercase(), 11f, Palette.MUTED, Fonts.sans(c, 600)).apply {
    letterSpacing = 0.2f; isAllCaps = false; accessibilityHeading(this)
}

fun accessibilityHeading(v: View) { v.isAccessibilityHeading = true }

/**
 * Dark glass panel with a thin glowing pink→purple edge (the "prism" border).
 * Drawn in code so it works at any size without nine-patches.
 */
class GlassDrawable(private val c: Context, private val radiusDp: Float = 20f, private val prism: Boolean = true) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GLASS }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = c.dp(1f) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = c.dp(6f) }
    private val rect = RectF()
    private var edgeShader: Shader? = null

    // The inset rectangle and the prism gradient depend only on the bounds, so they're rebuilt only when those change.
    override fun onBoundsChange(b: Rect) {
        rect.set(b.left + c.dp(1f), b.top + c.dp(1f), b.right - c.dp(1f), b.bottom - c.dp(1f))
        edgeShader = if (prism) LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Palette.PRISM_A, Palette.PRISM_B, Shader.TileMode.CLAMP) else null
    }

    override fun draw(canvas: Canvas) {
        val r = c.dp(radiusDp)
        canvas.drawRoundRect(rect, r, r, fill)
        if (prism) {
            val g = edgeShader
            glow.shader = g; glow.alpha = 38
            canvas.drawRoundRect(rect, r, r, glow)
            edge.shader = g
        } else {
            edge.shader = null; edge.color = Palette.GLASS_LINE
        }
        canvas.drawRoundRect(rect, r, r, edge)
    }
    override fun setAlpha(alpha: Int) { fill.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Pill buttons: primary rose→magenta glow, or quiet glass. */
fun pillButton(c: Context, text: String, primary: Boolean) = Button(c).apply {
    this.text = text
    isAllCaps = false
    typeface = Fonts.sans(c, 600)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
    setTextColor(if (primary) Color.WHITE else Palette.TEXT)
    minHeight = c.dpi(52f); minimumHeight = c.dpi(52f)
    stateListAnimator = null
    background = object : Drawable() {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF()
        var fillShader: Shader? = null
        override fun onBoundsChange(b: Rect) {
            fillShader = if (primary) LinearGradient(b.left.toFloat(), 0f, b.right.toFloat(), 0f, Palette.ROSE, Palette.MAGENTA, Shader.TileMode.CLAMP) else null
        }
        override fun draw(canvas: Canvas) {
            r.set(bounds); val rad = r.height() / 2
            if (primary) {
                p.shader = fillShader
                p.alpha = if (isEnabled) 255 else 100
            } else { p.shader = null; p.color = 0x8C0A0514.toInt() }
            canvas.drawRoundRect(r, rad, rad, p)
            if (!primary) {
                p.style = Paint.Style.STROKE; p.strokeWidth = c.dp(1f); p.color = Palette.GLASS_LINE
                canvas.drawRoundRect(r, rad, rad, p); p.style = Paint.Style.FILL
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    setOnTouchListener { v, e ->
        if (Motion.enabled) when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(.97f).scaleY(.97f).setDuration(120).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        }
        false
    }
}

/**
 * Ember switch: dark indigo track, glowing amber-white knob when ON. Announces as a
 * switch with ON/OFF state; state is never shown by colour alone (labels say ON/OFF).
 */
class EmberSwitch(c: Context, private val name: String) : View(c) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var trackShader: Shader? = null
    private var trackShaderW = -1

    init {
        isFocusable = true; isClickable = true
        contentDescription = name
        minimumWidth = c.dpi(58f); minimumHeight = c.dpi(48f)
        setOnClickListener { set(!checked, animate = true, notify = true) }
    }

    fun set(value: Boolean, animate: Boolean = false, notify: Boolean = false) {
        checked = value
        stateText(if (value) "On" else "Off")
        val target = if (value) 1f else 0f
        if (animate && Motion.enabled) ValueAnimator.ofFloat(pos, target).apply {
            duration = 450; addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start()
        } else { pos = target; invalidate() }
        if (notify) { onChange?.invoke(value); announceForAccessibility("$name ${if (value) "on" else "off"}") }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dpi(58f), dpi(48f))

    override fun onDraw(canvas: Canvas) {
        val top = (height - dp(32f)) / 2
        rect.set(0f, top, width.toFloat(), top + dp(32f))
        if (trackShaderW != width) { trackShader = LinearGradient(0f, 0f, width.toFloat(), 0f, Palette.INDIGO_2, Palette.PITCH, Shader.TileMode.CLAMP); trackShaderW = width }
        track.shader = trackShader
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, track)
        track.shader = null; track.style = Paint.Style.STROKE; track.strokeWidth = dp(1f)
        track.color = if (pos > .5f) Palette.withAlpha(Palette.ROSE, .7f) else Palette.GLASS_LINE
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, track); track.style = Paint.Style.FILL
        val cx = rect.left + dp(16f) + pos * (rect.width() - dp(32f)); val cy = rect.centerY(); val r = dp(12f)
        if (pos > 0f) {
            knob.shader = RadialGradient(cx, cy, r * 2.4f, Palette.withAlpha(Palette.COPPER, .55f * pos), 0, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 2.4f, knob)
        }
        knob.shader = if (pos > .5f) RadialGradient(cx - r * .2f, cy - r * .25f, r, intArrayOf(Color.WHITE, 0xFFFFE3CC.toInt(), 0xFFFF9A5A.toInt(), Palette.COPPER), floatArrayOf(0f, .4f, .75f, 1f), Shader.TileMode.CLAMP)
        else RadialGradient(cx - r * .2f, cy - r * .3f, r, 0xFF8B7AA3.toInt(), 0xFF4A3B5E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, knob)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"; info.isCheckable = true; info.isChecked = checked
    }
}

/** "Plus" blending for glows, like the web preview's `lighter` composite. */
val PLUS: BlendMode = BlendMode.PLUS

fun ellipsize(tv: TextView) { tv.maxLines = 1; tv.ellipsize = TextUtils.TruncateAt.END }

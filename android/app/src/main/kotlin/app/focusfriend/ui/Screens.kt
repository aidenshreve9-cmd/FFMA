package app.focusfriend.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.focusfriend.core.Quote

/** Home: the orb is the only main control. Settings gear top-right. No search here, ever. */
@SuppressLint("ViewConstructor")
class HomeScreen(c: Context, onStart: () -> Unit, onSettings: () -> Unit) : FrameLayout(c) {
    private val hint = label(c, "Slide the button to change the time", 13.5f, Palette.MUTED, Fonts.sans(c, 500), Gravity.CENTER)
    private val durationLive = label(c, "", 1f, Color.TRANSPARENT).apply { accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE }
    private var hintShown = true
    val focus = FocusButton(c, onStart = { hideHint(); onStart() }, onDuration = { m ->
        if (hintShown && isShown) hideHint()
        durationLive.text = "$m minutes"
    })
    val gear = ImageButton(c).apply {
        contentDescription = "Settings"
        setImageDrawable(GearDrawable(c))
        background = GlassDrawable(c, 999f, prism = false)
        setOnClickListener { onSettings() }
    }

    init {
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(focus, LinearLayout.LayoutParams(dpi(320f), dpi(320f)))
            addView(hint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(30f) })
            addView(durationLive, LinearLayout.LayoutParams(1, 1))
        }, LayoutParams(-1, -2, Gravity.CENTER))
        addView(gear, LayoutParams(dpi(48f), dpi(48f), Gravity.TOP or Gravity.END).apply { setMargins(0, dpi(14f), dpi(20f), 0) })
    }

    private fun hideHint() {
        if (!hintShown) return
        hintShown = false
        hint.animate().alpha(0f).setDuration(Motion.ms(800)).start()
    }

    /** Every return to Home starts at 15 minutes (the duration is never stored). */
    fun reset() { focus.resetDuration() }
}

/** Session: scenic view behind the timer, honest status line, sound · scene footer. */
@SuppressLint("ViewConstructor")
class SessionScreen(c: Context, onTimerTap: () -> Unit) : FrameLayout(c) {
    val scene = SceneView(c)
    val timer = TimerButton(c, onTimerTap)
    private val status = statusPill(c, "", warn = false)
    private val footer = label(c, "", 11f, Palette.withAlpha(Palette.HALO, .62f), Fonts.mono(c), Gravity.CENTER).apply { letterSpacing = .08f }
    val live = label(c, "", 1f, Color.TRANSPARENT).apply { accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE }

    init {
        addView(scene, LayoutParams(-1, -1))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(20f), dpi(18f), dpi(20f), dpi(22f))
            addView(status, LinearLayout.LayoutParams(-2, -2))
            addView(FrameLayout(c).apply { addView(timer, LayoutParams(dpi(320f), dpi(320f), Gravity.CENTER)) }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(-1, -2))
            addView(live, LinearLayout.LayoutParams(1, 1))
        }, LayoutParams(-1, -1))
    }

    /** The "silenced" line appears only when Do Not Disturb is really active. */
    fun setStatus(silencing: Boolean) {
        status.text = (if (silencing) "Notifications silenced · Emergency calls unaffected" else "Do Not Disturb is off · notifications not silenced").uppercase()
        status.setTextColor(if (silencing) Palette.HALO else Palette.WARN)
    }
    fun setFooter(sound: String, scene: String) { footer.text = if (sound.isEmpty()) scene else "$sound · $scene" }
}

/** Done: quote card, author, Done button. */
@SuppressLint("ViewConstructor")
class DoneScreen(c: Context, onDone: () -> Unit) : FrameLayout(c) {
    val orb = OrbView(c)
    private val title = label(c, "DONE", 11f, Palette.MUTED, Fonts.mono(c, 500), Gravity.CENTER).apply { letterSpacing = .24f; accessibilityHeading(this) }
    private val quote = label(c, "", 24f, Palette.TEXT, Fonts.serifItalic(c), Gravity.CENTER)
    private val author = label(c, "", 13f, Palette.MUTED, Fonts.sans(c), Gravity.CENTER).apply { letterSpacing = .06f }
    val doneButton = pillButton(c, "Done", primary = true).apply { setOnClickListener { onDone() } }

    init {
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpi(20f), 0, dpi(20f), 0)
            addView(orb, LinearLayout.LayoutParams(dpi(150f), dpi(150f)))
            addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(8f) })
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                background = GlassDrawable(c); setPadding(dpi(24f), dpi(28f), dpi(24f), dpi(24f))
                addView(quote); addView(author, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14f) })
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(24f) })
            addView(doneButton, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(26f) })
        }, LayoutParams(-1, -2, Gravity.CENTER))
    }

    fun show(natural: Boolean, q: Quote) {
        title.text = if (natural) "DONE" else "SESSION ENDED"
        title.contentDescription = if (natural) "Done" else "Session ended"
        quote.text = "“${q.text}”"
        author.text = "— ${q.author}"
    }
}

/** Screen transitions: gentle fade + zoom + blur; Settings slides from the side. Immediate with reduced motion. */
object Transitions {
    fun swap(from: View?, to: View, sideways: Int = 0) {
        to.visibility = View.VISIBLE
        if (from == null || from === to) { to.alpha = 1f; return }
        if (!Motion.enabled) { from.visibility = View.GONE; to.alpha = 1f; to.translationX = 0f; to.scaleX = 1f; to.scaleY = 1f; return }
        from.animate().cancel(); to.animate().cancel()
        val w = to.width.takeIf { it > 0 } ?: to.resources.displayMetrics.widthPixels
        if (sideways != 0) {
            to.translationX = sideways * w * .14f; to.alpha = 0f
            to.animate().translationX(0f).alpha(1f).setDuration(560).start()
            from.animate().translationX(-sideways * w * .10f).alpha(0f).setDuration(420).setListener(hideAfter(from)).start()
        } else {
            to.alpha = 0f; to.scaleX = .94f; to.scaleY = .94f
            blur(to, 12f)
            to.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(620)
                .setUpdateListener { blur(to, 12f * (1 - it.animatedFraction)) }.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { blur(to, 0f); to.animate().setUpdateListener(null).setListener(null).setStartDelay(0) }
                }).start()
            from.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(480)
                .setUpdateListener { blur(from, 10f * it.animatedFraction) }.setListener(hideAfter(from)).start()
        }
    }

    private fun hideAfter(v: View) = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(a: Animator) {
            v.visibility = View.GONE; v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; blur(v, 0f)
            v.animate().setListener(null).setUpdateListener(null)
        }
    }

    private fun blur(v: View, radius: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        v.setRenderEffect(if (radius < .5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }
}

/**
 * Sheets and dialogs drawn over the app: they rise in and sink away. Escape/Back closes
 * the top one. Focus moves into the sheet and returns when it closes.
 */
class SheetHost(private val c: Context, private val root: FrameLayout) {
    private var scrim: FrameLayout? = null
    private var onDismiss: (() -> Unit)? = null
    private var returnFocus: View? = null
    val isOpen get() = scrim != null

    fun show(title: String, body: List<View>, actions: List<View>, centered: Boolean = false, onDismiss: (() -> Unit)? = null) {
        close(immediate = true)
        returnFocus = root.findFocus()
        this.onDismiss = onDismiss
        val sheet = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(c, 28f)
            setPadding(c.dpi(20f), c.dpi(24f), c.dpi(20f), c.dpi(20f))
            addView(label(c, title, 24f, Palette.TEXT, Fonts.serif(c)).apply { accessibilityHeading(this) })
            body.forEach { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dpi(12f) }) }
            actions.forEach { addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = c.dpi(10f) }) }
            isClickable = true
            accessibilityPaneTitle = title
        }
        val s = FrameLayout(c).apply {
            setBackgroundColor(0x8C000000.toInt())
            isClickable = true
            setOnClickListener { dismiss() }
            addView(ScrollView(c).apply { isFillViewport = false; addView(sheet) },
                FrameLayout.LayoutParams(-1, -2, if (centered) Gravity.CENTER else Gravity.BOTTOM).apply { if (centered) setMargins(c.dpi(20f), 0, c.dpi(20f), 0) })
        }
        root.addView(s, FrameLayout.LayoutParams(-1, -1))
        scrim = s
        if (Motion.enabled) {
            s.alpha = 0f; s.animate().alpha(1f).setDuration(320).start()
            sheet.translationY = c.dp(40f); sheet.scaleX = .97f; sheet.scaleY = .97f
            sheet.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(560).start()
        }
        (actions.firstOrNull { it.isEnabled } ?: sheet).let { first -> first.post { first.requestFocus(); first.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) } }
    }

    /** User-initiated dismiss (scrim tap, Back, Escape). */
    fun dismiss() { val cb = onDismiss; close(); cb?.invoke() }

    fun close(immediate: Boolean = false) {
        val s = scrim ?: return
        scrim = null; onDismiss = null
        val focusBack = returnFocus; returnFocus = null
        val remove = { (s.parent as? ViewGroup)?.removeView(s); focusBack?.takeIf { it.isShown }?.requestFocus() }
        if (immediate || !Motion.enabled) { remove(); return }
        val sheet = (s.getChildAt(0) as ViewGroup).getChildAt(0)
        sheet.animate().translationY(c.dp(30f)).scaleX(.97f).scaleY(.97f).alpha(0f).setDuration(320).start()
        s.animate().alpha(0f).setDuration(340).withEndAction { remove() }.start()
    }
}

/** Non-blocking message: fades up, then down. Never intercepts taps. Announced politely. */
class Toaster(private val c: Context, root: FrameLayout) {
    private val view = label(c, "", 14f, Palette.TEXT, Fonts.sans(c), Gravity.CENTER).apply {
        background = GlassDrawable(c, 999f, prism = false)
        setPadding(c.dpi(18f), c.dpi(11f), c.dpi(18f), c.dpi(11f))
        visibility = View.GONE; isClickable = false; isFocusable = false
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable {
        if (!Motion.enabled) { view.visibility = View.GONE; return@Runnable }
        view.animate().alpha(0f).translationY(c.dp(8f)).setDuration(320).withEndAction { view.visibility = View.GONE }.start()
    }

    init { root.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = c.dpi(24f) }) }

    fun show(msg: String) {
        handler.removeCallbacks(hide)
        view.animate().cancel()
        view.text = msg
        view.bringToFront()
        if (view.visibility != View.VISIBLE && Motion.enabled) { view.alpha = 0f; view.translationY = c.dp(12f); view.animate().alpha(1f).translationY(0f).setDuration(380).start() }
        else { view.alpha = 1f; view.translationY = 0f }
        view.visibility = View.VISIBLE
        handler.postDelayed(hide, 2800)
    }
}

/** The gear icon, drawn in code (the app ships no generated R resources for UI). */
class GearDrawable(private val c: Context) : android.graphics.drawable.Drawable() {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = c.dp(1.6f); color = Palette.MUTED; strokeCap = android.graphics.Paint.Cap.ROUND
    }
    override fun getIntrinsicWidth() = c.dpi(20f)
    override fun getIntrinsicHeight() = c.dpi(20f)
    override fun draw(canvas: android.graphics.Canvas) {
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = c.dp(6.5f)
        canvas.drawCircle(cx, cy, c.dp(3f), p)
        canvas.drawCircle(cx, cy, r, p)
        for (i in 0 until 8) {
            val a = Math.PI / 4 * i
            canvas.drawLine(cx + (r * Math.cos(a)).toFloat(), cy + (r * Math.sin(a)).toFloat(), cx + ((r + c.dp(2.6f)) * Math.cos(a)).toFloat(), cy + ((r + c.dp(2.6f)) * Math.sin(a)).toFloat(), p)
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** Screen-reader-only text used for polite announcements that shouldn't interrupt. */
fun TextView.announcePolitely(msg: String) { text = ""; post { text = msg } }

package app.focusfriend.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/** 15 / 30 / 45 / 60, looping both ways. The selection is never persisted: every launch starts at 15. */
object Durations {
    val MINUTES = listOf(15, 30, 45, 60)
    const val DEFAULT_INDEX = 0

    fun indexAt(position: Int) = position.mod(MINUTES.size)
    fun minutesAt(position: Int) = MINUTES[indexAt(position)]
    fun next(index: Int) = indexAt(index + 1)
    fun previous(index: Int) = indexAt(index - 1)
}

/**
 * Horizontal slide on the orb. Sliding left moves to the next duration (content moves
 * right-to-left), sliding right to the previous one. Vertical gestures do nothing.
 */
object SlideGesture {
    enum class Axis { HORIZONTAL, VERTICAL }

    /** px of finger travel before a touch counts as a slide rather than a tap. */
    const val TAP_SLOP_DP = 7f
    /** px of travel per step (in dp). */
    const val ITEM_WIDTH_DP = 58f
    /** Items per millisecond; faster than this counts as a flick. */
    const val FLICK_VELOCITY = 0.0035

    fun axisOf(dx: Float, dy: Float) = if (abs(dx) > abs(dy)) Axis.HORIZONTAL else Axis.VERTICAL

    /** Carousel position while dragging: dragging left (negative dx) increases the position. */
    fun positionWhileDragging(startPosition: Double, dxDp: Float) = startPosition - dxDp / ITEM_WIDTH_DP

    /**
     * Where a slide settles. The drag decides; a quick flick that didn't reach the next
     * value moves exactly one step; velocity never adds a step on top of the drag.
     * [velocity] is in items/ms, positive toward the next duration.
     */
    fun settle(startPosition: Double, position: Double, velocity: Double): Int {
        val base = startPosition.roundToInt()
        val dragged = position.roundToInt()
        return if (dragged == base && abs(velocity) > FLICK_VELOCITY) base + sign(velocity).toInt() else dragged
    }
}

/**
 * A running session, based on a fixed end timestamp rather than a counter, so it stays
 * correct across backgrounding, process death (when restored) and delayed frames.
 */
class FocusSession(val minutes: Int, val startedAtMs: Long) {
    init { require(minutes in Durations.MINUTES) { "unsupported duration" } }

    val endAtMs: Long = startedAtMs + minutes * 60_000L
    private var finished = false

    fun remainingMs(nowMs: Long) = (endAtMs - nowMs).coerceAtLeast(0L)
    fun isOver(nowMs: Long) = nowMs >= endAtMs

    /** Minutes only, rounded up: shows "1 min" until the final second. */
    fun minutesLeft(nowMs: Long): Int = maxOf(1, ceil(remainingMs(nowMs) / 60_000.0).toInt())

    /** Arc on the shared 60-minute clock face (1.0 = full circle). */
    fun arcFraction(nowMs: Long) = (remainingMs(nowMs) / 3_600_000.0).coerceIn(0.0, 1.0)

    /** Completion and cleanup run exactly once, whichever path (natural or early) gets there first. */
    fun finishOnce(): Boolean { if (finished) return false; finished = true; return true }
    val isFinished get() = finished
}

data class Quote(val text: String, val author: String)

object Quotes {
    val APPROVED = listOf(
        Quote("Each man lives only this present, this momentary thing.", "Marcus Aurelius"),
        Quote("Do not imagine this, that you will recover it when you choose.", "Epictetus"),
        Quote("To learn, and in due time to practise what you have learned — is that not a pleasure?", "Confucius"),
        Quote("Learning without thought is dark; thought without learning is perilous.", "Confucius"),
        Quote("Stillness should be guarded with unwearying vigour.", "Laozi"),
        Quote("Hold fast to these few things only.", "Marcus Aurelius"),
        Quote("To learn without tiring of it, to teach others without wearying.", "Confucius"),
        Quote("Be quick in what must be done and careful in what you say.", "Confucius"),
        Quote("Is there any part of life excepted, to which attention does not extend?", "Epictetus"),
        Quote("The task at hand.", "Marcus Aurelius"),
    )
    fun random(rng: Random = Random) = APPROVED[rng.nextInt(APPROVED.size)]
}

/** Built-in content. Ids match the browser preview so saved choices mean the same thing. */
object Catalog {
    data class Sound(val id: String, val name: String, val kind: String, val gain: Float)
    data class Scene(val id: String, val name: String)

    val SOUNDS = listOf(
        Sound("white", "White Noise", "FLAT", 0.50f),
        Sound("pink", "Pink Noise", "SOFT 1/F", 0.62f),
        Sound("brown", "Brown Noise", "DEEP 1/F²", 0.70f),
        Sound("green", "Green Noise", "~500 HZ", 1.30f),
        Sound("grey", "Grey Noise", "EVEN EAR", 0.55f),
        Sound("blue", "Blue Noise", "BRIGHT", 0.30f),
        Sound("violet", "Violet Noise", "AIRY", 0.20f),
        Sound("black", "Black Noise", "SUB-BASS", 2.20f),
    )
    val SCENES = listOf(
        Scene("quantum", "Quantum Nebula"), Scene("galaxy", "Spiral Galaxy"), Scene("horizon", "Event Horizon"),
        Scene("aurora", "Aurora Veil"), Scene("dust", "Cosmic Dust"), Scene("nursery", "Stellar Nursery"),
        Scene("web", "Dark Matter Web"), Scene("void", "Ethereal Void"),
    )
    const val RANDOM = "random"
    const val DEFAULT_SOUND = "white"
    const val DEFAULT_SCENE = "quantum"
    const val USER_SOUND_PREFIX = "snd:"
    const val USER_PICTURE_PREFIX = "img:"

    /** Random picks fresh each session from built-ins plus the person's own files. */
    fun pickSound(choice: String, userSoundIds: List<String>, rng: Random = Random): String =
        if (choice != RANDOM) choice else (SOUNDS.map { it.id } + userSoundIds).random(rng)

    fun pickScene(choice: String, userPictureIds: List<String>, rng: Random = Random): String =
        if (choice != RANDOM) choice else (SCENES.map { it.id } + userPictureIds).random(rng)
}

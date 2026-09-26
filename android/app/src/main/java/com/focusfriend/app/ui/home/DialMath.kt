package com.focusfriend.app.ui.home

import com.focusfriend.app.model.Catalog
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The dial's rules, kept free of Android so they can be unit-tested. */
object DialMath {
    /** Touches at least this far out (share of the dial's radius) set the time. */
    const val RING_INNER = 0.68f

    /** Distance from the centre (1 = the dial's edge) and compass bearing in degrees (0 at the top, clockwise). */
    fun polar(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Pair<Float, Float> {
        val dx = x - centerX
        val dy = y - centerY
        val deg = ((Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())) + 360) % 360).toFloat()
        return hypot(dx, dy) / radius to deg
    }

    /** 6° per minute. The top of the ring is 60. */
    fun minutesAt(deg: Float): Int {
        val m = (deg / 6f).roundToInt()
        return if (m == 0 || m == 60) 60 else m
    }

    /** A drag never jumps across the top: pushing past 60 holds at 60, pulling back past 1 holds at 1. */
    fun follow(current: Int, deg: Float): Int {
        val m = minutesAt(deg)
        return when {
            current >= 50 && m <= 10 -> Catalog.MAX_MINUTES
            current <= 10 && m >= 50 -> Catalog.MIN_MINUTES
            else -> m
        }.coerceIn(Catalog.MIN_MINUTES, Catalog.MAX_MINUTES)
    }
}

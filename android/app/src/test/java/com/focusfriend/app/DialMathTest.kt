package com.focusfriend.app

import com.focusfriend.app.ui.home.DialMath
import org.junit.Assert.assertEquals
import org.junit.Test

class DialMathTest {
    @Test fun sixDegreesPerMinute() {
        assertEquals(60, DialMath.minutesAt(0f))
        assertEquals(1, DialMath.minutesAt(6f))
        assertEquals(15, DialMath.minutesAt(90f))
        assertEquals(30, DialMath.minutesAt(180f))
        assertEquals(60, DialMath.minutesAt(359f))
    }

    @Test fun neverJumpsAcrossTheTop() {
        assertEquals(60, DialMath.follow(58, 6f))
        assertEquals(1, DialMath.follow(2, 354f))
        assertEquals(31, DialMath.follow(30, 186f))
    }

    @Test fun polarUsesCompassBearing() {
        val (d, deg) = DialMath.polar(100f, 0f, 100f, 100f, 100f)
        assertEquals(1f, d, 0.001f)
        assertEquals(0f, deg, 0.001f)
        assertEquals(90f, DialMath.polar(200f, 100f, 100f, 100f, 100f).second, 0.001f)
    }
}

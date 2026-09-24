package app.focusfriend.core

import kotlin.random.Random
import kotlin.test.*

class DurationsTest {
    @Test fun loopsBothWays() {
        assertEquals(listOf(15, 30, 45, 60), Durations.MINUTES)
        assertEquals(15, Durations.minutesAt(Durations.DEFAULT_INDEX))
        assertEquals(0, Durations.next(3), "60 → 15")
        assertEquals(3, Durations.previous(0), "15 → 60")
        assertEquals(15, Durations.minutesAt(4)); assertEquals(60, Durations.minutesAt(-1))
    }

    @Test fun slideDirectionAndAxis() {
        assertEquals(SlideGesture.Axis.VERTICAL, SlideGesture.axisOf(10f, 40f))
        assertEquals(SlideGesture.Axis.HORIZONTAL, SlideGesture.axisOf(-40f, 10f))
        // sliding left (negative dx) moves toward the next duration
        assertTrue(SlideGesture.positionWhileDragging(0.0, -70f) > 1.0)
        assertTrue(SlideGesture.positionWhileDragging(0.0, 70f) < -1.0)
    }

    @Test fun flickIsExactlyOneStep() {
        assertEquals(1, SlideGesture.settle(0.0, 0.3, 0.02), "short fast flick → one step")
        assertEquals(-1, SlideGesture.settle(0.0, -0.3, -0.02))
        assertEquals(1, SlideGesture.settle(0.0, 1.2, 0.05), "velocity never adds a step on top of the drag")
        assertEquals(0, SlideGesture.settle(0.0, 0.3, 0.0001), "slow short drag snaps back")
        assertEquals(2, SlideGesture.settle(0.0, 2.2, 0.0), "a long drag can cross two values")
    }
}

class FocusSessionTest {
    private val t0 = 1_000_000L

    @Test fun fixedEndTimestamp() {
        val s = FocusSession(30, t0)
        assertEquals(t0 + 30 * 60_000L, s.endAtMs)
        assertEquals(30, s.minutesLeft(t0))
        assertEquals(30, s.minutesLeft(t0 + 1), "rounds up")
        assertEquals(29, s.minutesLeft(t0 + 60_000))
        assertEquals(1, s.minutesLeft(s.endAtMs - 1), "shows 1 min until the final second")
        assertEquals(1, s.minutesLeft(s.endAtMs + 10_000), "never 0 or negative on screen")
        assertTrue(s.isOver(s.endAtMs)); assertFalse(s.isOver(s.endAtMs - 1))
        assertEquals(0.5, s.arcFraction(t0), 1e-9)
        assertEquals(0.0, s.arcFraction(s.endAtMs + 5))
    }

    @Test fun accurateAfterLeavingAndReturning() {
        val s = FocusSession(15, t0)
        // Nothing ticks while away; the remaining time is derived from the clock on return.
        assertEquals(5, s.minutesLeft(t0 + 10 * 60_000 + 1))
    }

    @Test fun finishRunsOnce() {
        val s = FocusSession(15, t0)
        assertTrue(s.finishOnce()); assertFalse(s.finishOnce()); assertTrue(s.isFinished)
    }

    @Test fun rejectsUnsupportedDuration() { assertFailsWith<IllegalArgumentException> { FocusSession(20, t0) } }
}

class CatalogAndQuotesTest {
    @Test fun eightSoundsAndScenes() {
        assertEquals(listOf("White Noise", "Pink Noise", "Brown Noise", "Green Noise", "Grey Noise", "Blue Noise", "Violet Noise", "Black Noise"),
            Catalog.SOUNDS.map { it.name })
        assertEquals(listOf("Quantum Nebula", "Spiral Galaxy", "Event Horizon", "Aurora Veil", "Cosmic Dust", "Stellar Nursery", "Dark Matter Web", "Ethereal Void"),
            Catalog.SCENES.map { it.name })
        assertEquals("white", Catalog.DEFAULT_SOUND); assertEquals("quantum", Catalog.DEFAULT_SCENE)
    }

    @Test fun randomPicksFromBuiltInsPlusUserFiles() {
        val seen = (0 until 400).map { Catalog.pickSound(Catalog.RANDOM, listOf("snd:a"), Random(it)) }.toSet()
        assertEquals(Catalog.SOUNDS.map { it.id }.toSet() + "snd:a", seen)
        assertEquals("pink", Catalog.pickSound("pink", listOf("snd:a")))
        val scenes = (0 until 400).map { Catalog.pickScene(Catalog.RANDOM, listOf("img:x"), Random(it)) }.toSet()
        assertTrue("img:x" in scenes && scenes.size == 9)
    }

    @Test fun tenApprovedQuotesWithAuthors() {
        assertEquals(10, Quotes.APPROVED.size)
        assertTrue(Quotes.APPROVED.all { it.author.isNotBlank() && it.text.isNotBlank() })
        assertTrue(Quotes.random(Random(3)) in Quotes.APPROVED)
    }
}

class SettingsTest {
    private var n = 0
    private val id = { "id" + (n++) }

    @Test fun defaults() {
        val s = FocusSettings()
        assertEquals("white", s.sound); assertEquals("quantum", s.scene)
        assertTrue(s.alarmSafety, "Alarm Safety defaults ON"); assertFalse(s.trustedContactsOn, "Trusted Contacts defaults OFF")
        assertFalse(FocusSettings::class.java.declaredFields.any { it.name.contains("duration", true) }, "duration is never persisted")
    }

    @Test fun contactBookRules() {
        val book = TrustedContactBook()
        val added = book.add("Grandma", "+1 780 555 1234", id)
        assertIs<TrustedContactBook.AddResult.Added>(added)
        assertEquals("17805551234", added.contact.normalized)
        assertEquals("+1 780 555 1234", added.contact.number)
        assertEquals(TrustedContactBook.AddResult.Duplicate, book.add("Gran", "780-555-1234", id), "duplicate across formats")
        assertEquals(TrustedContactBook.AddResult.InvalidNumber, book.add("X", "call me", id))
        assertEquals(TrustedContactBook.AddResult.InvalidNumber, book.add("X", "12", id))
        repeat(49) { book.add("P$it", "2505550${(100 + it)}", id) }
        assertEquals(50, book.size)
        assertEquals(TrustedContactBook.AddResult.Full, book.add("Late", "250 555 9999", id))
        assertNotNull(book.remove(added.contact.id)); assertNull(book.remove(added.contact.id))
    }

    @Test fun validationFallsBackAndRepairs() {
        val s = FocusSettings(sound = "snd:gone", scene = "dusk",
            contacts = listOf(TrustedContact("a", "", "780 555 1234", ""), TrustedContact("b", "Dup", "17805551234", ""), TrustedContact("", "No id", "123", "")))
            .validated(emptySet(), emptySet())
        assertEquals("white", s.sound); assertEquals("quantum", s.scene)
        assertEquals(1, s.contacts.size, "duplicate and id-less entries dropped")
        assertEquals("780 555 1234", s.contacts[0].name, "blank name repaired")
        assertEquals("7805551234", s.contacts[0].normalized)
        assertEquals("snd:mine", FocusSettings(sound = "snd:mine").validated(setOf("snd:mine"), emptySet()).sound)
    }

    @Test fun policyFollowsSwitches() {
        val c = TrustedContact("a", "Grandma", "780 555 1234", "7805551234")
        val off = InterruptionPolicy.from(FocusSettings(contacts = listOf(c)))
        assertFalse(off.allowTrustedContacts); assertTrue(off.allowAlarms); assertTrue(off.allowRepeatCallers); assertTrue(off.allowEmergencyAlerts)
        assertEquals(15, off.repeatCallerWindowMinutes)
        val on = InterruptionPolicy.from(FocusSettings(trustedContactsOn = true, alarmSafety = false, contacts = listOf(c)))
        assertEquals(listOf("7805551234"), on.trustedNumbers); assertFalse(on.allowAlarms)
    }
}

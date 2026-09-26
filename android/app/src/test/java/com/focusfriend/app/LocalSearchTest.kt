package com.focusfriend.app

import com.focusfriend.app.model.Catalog
import com.focusfriend.app.search.FieldKind
import com.focusfriend.app.search.LocalSearch
import com.focusfriend.app.search.MatchType
import com.focusfriend.app.search.TextNormalizer
import com.focusfriend.app.search.boundedDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {
    private fun sounds() = LocalSearch().apply {
        define("sounds", mapOf("title" to FieldKind.TITLE))
        load("sounds", Catalog.SOUNDS.map { Triple(it.id, it.name, mapOf("title" to it.name)) })
    }

    private fun contacts() = LocalSearch().apply {
        define("contacts", mapOf("name" to FieldKind.NAME, "phone" to FieldKind.PHONE))
        load("contacts", listOf(
            Triple("1", "Mary O'Brien", mapOf("name" to "Mary O'Brien", "phone" to "+1 780 555 1234")),
            Triple("2", "Sam Lee", mapOf("name" to "Sam Lee", "phone" to "(403) 555-9876")),
        ))
    }

    @Test fun exactBeatsPrefixAndTokens() {
        val r = sounds().search("sounds", "white noise")
        assertEquals("white", r.first().record.id)
        assertEquals(MatchType.EXACT, r.first().matchType)
    }

    @Test fun tokenPrefixFindsEveryNoise() {
        val r = sounds().search("sounds", "noi")
        assertEquals(8, r.size)
    }

    @Test fun prefixRanksAboveSubstring() {
        val r = sounds().search("sounds", "bl")
        assertEquals(listOf("blue", "black"), r.map { it.record.id }.sorted().reversed())
    }

    @Test fun fuzzyNeedsLongerWords() {
        assertTrue(sounds().search("sounds", "whte").isEmpty())
        assertEquals("violet", sounds().search("sounds", "violte").first().record.id)
    }

    @Test fun didYouMeanOffersOneStrongGuess() {
        val s = sounds()
        assertEquals("white", s.suggest("sounds", "whte nise")?.id)
        assertNull(s.suggest("sounds", "zzzzzz"))
    }

    @Test fun phoneNumbersMatchAcrossFormats() {
        val c = contacts()
        assertEquals("1", c.search("contacts", "7805551234").first().record.id)
        assertEquals(MatchType.PHONE_FULL, c.search("contacts", "780-555-1234").first().matchType)
        assertEquals("2", c.search("contacts", "555 98").first().record.id)
        assertTrue(TextNormalizer.phonesMatch("17805551234", "7805551234"))
        assertFalse(TextNormalizer.phonesMatch("4035551234", "7805551234"))
        assertFalse(TextNormalizer.phonesMatch("123", "7805550123"))
    }

    @Test fun namesIgnoreApostrophes() {
        assertEquals("1", contacts().search("contacts", "obrien").first().record.id)
    }

    @Test fun emptyOrPunctuationQueriesFindNothing() {
        assertTrue(sounds().search("sounds", "   ").isEmpty())
        assertTrue(sounds().search("sounds", "!!").isEmpty())
    }

    @Test fun distanceIsBounded() {
        assertEquals(1, boundedDistance("violte", "violet", 2))
        assertEquals(3, boundedDistance("abc", "xyzxyz", 2))
    }
}

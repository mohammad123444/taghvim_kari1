package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test fun namesAreSortedAlphabetically() {
        val sorted = listOf("علی", "بهرام", "احمد").sortedAlphabetically { it }
        assertEquals(listOf("احمد", "بهرام", "علی"), sorted)
    }

    @Test fun arabicLettersMatchPersianOnes() {
        assertEquals(normalizeFa("علی"), normalizeFa("علي"))
        assertEquals(normalizeFa("کریم"), normalizeFa("كريم"))
    }
}

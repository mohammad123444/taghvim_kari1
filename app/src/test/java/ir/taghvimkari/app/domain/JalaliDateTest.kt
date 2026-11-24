package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliDateTest {

    @Test fun nowruzDatesMatchKnownValues() {
        assertEquals(JalaliDate(1400, 1, 1), JalaliDate.from(LocalDate.of(2021, 3, 21)))
        assertEquals(JalaliDate(1403, 1, 1), JalaliDate.from(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate.from(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1405, 1, 1), JalaliDate.from(LocalDate.of(2026, 3, 21)))
    }

    @Test fun specificDay() {
        assertEquals(JalaliDate(1405, 8, 27), JalaliDate.from(LocalDate.of(2026, 11, 18)))
        assertEquals(LocalDate.of(2026, 11, 18), JalaliDate(1405, 8, 27).toLocalDate())
    }

    @Test fun lastDayOfLeapAndNormalYear() {
        assertEquals(JalaliDate(1402, 12, 29), JalaliDate.from(LocalDate.of(2024, 3, 19)))
        assertEquals(LocalDate.of(2025, 3, 20), JalaliDate(1403, 12, 30).toLocalDate())
        assertEquals(JalaliDate(1404, 12, 29), JalaliDate.from(LocalDate.of(2026, 3, 20)))
    }

    @Test fun roundTripOverManyYears() {
        var d = LocalDate.of(2000, 1, 1)
        val end = LocalDate.of(2060, 1, 1)
        while (d.isBefore(end)) {
            assertEquals(d, JalaliDate.from(d).toLocalDate())
            d = d.plusDays(1)
        }
    }

    @Test fun leapYears() {
        assertTrue(JalaliDate.isLeapYear(1399))
        assertTrue(JalaliDate.isLeapYear(1403))
        assertFalse(JalaliDate.isLeapYear(1404))
        assertFalse(JalaliDate.isLeapYear(1405))
        assertEquals(30, JalaliDate.monthLength(1403, 12))
        assertEquals(29, JalaliDate.monthLength(1405, 12))
        assertEquals(31, JalaliDate.monthLength(1405, 6))
        assertEquals(30, JalaliDate.monthLength(1405, 7))
    }

    @Test fun parseAcceptsPersianAndLatinDigits() {
        val expected = JalaliDate(1405, 8, 27)
        assertEquals(expected, JalaliDate.parse("1405/08/27"))
        assertEquals(expected, JalaliDate.parse("۱۴۰۵/۰۸/۲۷"))
        assertEquals(expected, JalaliDate.parse(" 1405-8-27 "))
    }

    @Test fun parseRejectsInvalidInput() {
        assertNull(JalaliDate.parse(""))
        assertNull(JalaliDate.parse("1405/13/01"))
        assertNull(JalaliDate.parse("1405/07/31"))
        assertNull(JalaliDate.parse("1405/12/30")) // 1405 is not a leap year
        assertNull(JalaliDate.parse("1405/08"))
        assertNull(JalaliDate.parse("abc/de/fg"))
        assertNull(JalaliDate.parse("99999/01/01"))
    }

    @Test fun displayUsesPersianDigits() {
        assertEquals("۱۴۰۵/۰۸/۲۷", JalaliDate(1405, 8, 27).display())
        assertEquals("چهارشنبه ۲۷ آبان ۱۴۰۵", JalaliDate.longText(LocalDate.of(2026, 11, 18)))
    }
}

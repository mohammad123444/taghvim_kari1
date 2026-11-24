package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidaysTest {

    private fun daysInYear(year: Int): Int = (1..12).sumOf { Holidays.forMonth(year, it).size }

    @Test fun officialCalendarsHaveTheAnnouncedNumberOfHolidays() {
        assertEquals(25, daysInYear(1405))
        assertEquals(27, daysInYear(1406))
    }

    @Test fun monthsWithoutHolidaysAreEmpty() {
        // 1405: no holiday in Ordibehesht, Mehr and Azar.
        assertTrue(Holidays.forMonth(1405, 2).isEmpty())
        assertTrue(Holidays.forMonth(1405, 7).isEmpty())
        assertTrue(Holidays.forMonth(1405, 9).isEmpty())
    }

    @Test fun solarAndReligiousOccasionsShareADay() {
        val khordad = Holidays.forMonth(1405, 3)
        assertEquals(setOf(6, 14, 15), khordad.keys)
        assertTrue(khordad.getValue(14).contains("غدیر"))
        assertTrue(khordad.getValue(14).contains("امام خمینی"))
        assertTrue(Holidays.forMonth(1405, 1).getValue(1).contains("نوروز"))
    }

    @Test fun otherYearsOnlyHaveFixedHolidays() {
        assertFalse(Holidays.hasReligiousData(1410))
        assertEquals(setOf(14, 15), Holidays.forMonth(1410, 3).keys)
        assertTrue(Holidays.hasReligiousData(1405))
    }
}

package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftEngineTest {

    // 2026-11-14 is a Saturday (Persian weekday 0).
    private val saturday = LocalDate.of(2026, 11, 14)

    private fun person(pattern: Pattern, start: LocalDate = saturday, weekday: Int = 0) = Person(
        id = "1", name = "test", workType = WorkTypes.default, employment = Employment.OFFICIAL,
        pattern = pattern, startDate = start, startWeekday = weekday
    )

    @Test fun persianWeekdayMapping() {
        assertEquals(0, ShiftEngine.persianWeekday(LocalDate.of(2026, 11, 14))) // Saturday
        assertEquals(1, ShiftEngine.persianWeekday(LocalDate.of(2026, 11, 15))) // Sunday
        assertEquals(4, ShiftEngine.persianWeekday(LocalDate.of(2026, 11, 18))) // Wednesday
        assertEquals(6, ShiftEngine.persianWeekday(LocalDate.of(2026, 11, 20))) // Friday
    }

    @Test fun workRestAlternatesWeekly() {
        val p = person(Pattern.WORK_REST)
        assertEquals(Shift.WORK, ShiftEngine.shift(p, saturday))
        assertEquals(Shift.WORK, ShiftEngine.shift(p, saturday.plusDays(6)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(7)))
        assertEquals(Shift.WORK, ShiftEngine.shift(p, saturday.plusDays(14)))
    }

    @Test fun dayNightRestCycle() {
        val p = person(Pattern.DAY_NIGHT_REST)
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday))
        assertEquals(Shift.NIGHT, ShiftEngine.shift(p, saturday.plusDays(7)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(14)))
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday.plusDays(21)))
    }

    @Test fun twoWeeksDayOneWeekRest() {
        val p = person(Pattern.DAY2_REST1)
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday.plusDays(13)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(14)))
    }

    @Test fun dayNightTwoRestWeeks() {
        val p = person(Pattern.DAY_NIGHT_REST2)
        assertEquals(Shift.NIGHT, ShiftEngine.shift(p, saturday.plusDays(7)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(14)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(21)))
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday.plusDays(28)))
    }

    @Test fun beforeStartIsRest() {
        assertEquals(Shift.REST, ShiftEngine.shift(person(Pattern.WORK_REST), saturday.minusDays(1)))
    }

    @Test fun cycleAnchorsToFirstChosenWeekdayOnOrAfterStart() {
        // Start on Saturday but weeks begin on Monday (index 2): Sat/Sun are still before the anchor.
        val p = person(Pattern.WORK_REST, start = saturday, weekday = 2)
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday))
        assertEquals(Shift.WORK, ShiftEngine.shift(p, saturday.plusDays(2)))
    }

    @Test fun officialDaysAreSaturdayToWednesday() {
        val p = person(Pattern.OFFICIAL_DAYS)
        assertEquals(Shift.WORK, ShiftEngine.shift(p, LocalDate.of(2026, 11, 14))) // Sat
        assertEquals(Shift.WORK, ShiftEngine.shift(p, LocalDate.of(2026, 11, 18))) // Wed
        assertEquals(Shift.REST, ShiftEngine.shift(p, LocalDate.of(2026, 11, 19))) // Thu
        assertEquals(Shift.REST, ShiftEngine.shift(p, LocalDate.of(2026, 11, 20))) // Fri
    }

    @Test fun officialHolidayIsRest() {
        val p = person(Pattern.OFFICIAL_DAYS)
        val holiday = LocalDate.of(2026, 11, 16)
        assertEquals(Shift.REST, ShiftEngine.shift(p, holiday, setOf(holiday)))
    }

    @Test fun workingDaysInRange() {
        val p = person(Pattern.WORK_REST)
        val days = ShiftEngine.workingDays(p, saturday, saturday.plusDays(13))
        assertEquals(7, days.size)
        assertTrue(days.all { it.second == Shift.WORK })
    }

    @Test fun workTypeLabelsUsePersianDigitsEverywhere() {
        assertEquals("نوع کاری ۱", WorkTypes.default)
        assertEquals("نوع کاری ۳", WorkTypes.normalize("نوع کاری 3"))
        assertEquals(9, WorkTypes.options.size)
    }

    @Test fun statusCountsWorkAndRestStreaks() {
        val p = person(Pattern.WORK_REST)
        val first = ShiftEngine.status(p, saturday)
        assertTrue(first.working)
        assertEquals(1, first.elapsedDays)
        assertEquals(7, first.daysToChange)

        val last = ShiftEngine.status(p, saturday.plusDays(6))
        assertEquals(7, last.elapsedDays)
        assertEquals(1, last.daysToChange)

        val rest = ShiftEngine.status(p, saturday.plusDays(10))
        assertTrue(!rest.working)
        assertEquals(4, rest.elapsedDays)
        assertEquals(4, rest.daysToChange)
    }

    @Test fun statusBeforeStartIsNotStarted() {
        val p = person(Pattern.WORK_REST)
        val s = ShiftEngine.status(p, saturday.minusDays(3))
        assertTrue(!s.started)
        assertTrue(!s.working)
        assertEquals(3, s.daysToChange)
    }

    @Test fun dayNightWorkStreakSpansBothWeeks() {
        val p = person(Pattern.DAY_NIGHT_REST)
        val s = ShiftEngine.status(p, saturday.plusDays(8))
        assertEquals(Shift.NIGHT, s.shift)
        assertEquals(9, s.elapsedDays)
        assertEquals(6, s.daysToChange)
    }

    @Test fun twoWeeksDayTwoWeeksRest() {
        val p = person(Pattern.DAY2_REST2)
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday))
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday.plusDays(13)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(14)))
        assertEquals(Shift.REST, ShiftEngine.shift(p, saturday.plusDays(27)))
        assertEquals(Shift.DAY, ShiftEngine.shift(p, saturday.plusDays(28)))
        val s = ShiftEngine.status(p, saturday.plusDays(16))
        assertEquals(3, s.elapsedDays)
        assertEquals(12, s.daysToChange)
    }
}

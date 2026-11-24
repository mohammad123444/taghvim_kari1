package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportsTest {

    private fun entry(person: String, reward: Double, punishment: Double) = ReportEntry(
        id = person + reward + punishment, personId = person, personName = person,
        date = LocalDate.of(2026, 11, 23), title = "t", rewardHours = reward,
        punishmentHours = punishment, notes = ""
    )

    @Test fun totalsAddUpRewardAndPunishment() {
        val t = listOf(entry("a", 2.0, 0.0), entry("a", 0.0, 1.5), entry("b", 1.0, 0.5)).totals()
        assertEquals(3, t.count)
        assertEquals(3.0, t.reward, 0.0001)
        assertEquals(2.0, t.punishment, 0.0001)
        assertEquals(1.0, t.net, 0.0001)
    }

    @Test fun hoursAreFormattedWithPersianDigits() {
        assertEquals("۲", formatHours(2.0))
        assertEquals("۱٫۵", formatHours(1.5))
        assertEquals("۰", formatHours(0.0))
    }

    @Test fun hoursAreParsedFromAnyDigitsAndSeparator() {
        assertEquals(2.0, parseHours("۲"), 0.0)
        assertEquals(1.5, parseHours("1.5"), 0.0)
        assertEquals(1.5, parseHours("۱٫۵"), 0.0)
        assertEquals(1.5, parseHours("1,5"), 0.0)
        assertEquals(0.0, parseHours(""), 0.0)
        assertEquals(0.0, parseHours("abc"), 0.0)
        assertEquals(0.0, parseHours("-3"), 0.0)
    }

    @Test fun periodIsInclusiveAndUnboundedWhenNull() {
        val period = ReportPeriod("p", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(true, period.contains(LocalDate.of(2026, 11, 1)))
        assertEquals(true, period.contains(LocalDate.of(2026, 11, 30)))
        assertEquals(false, period.contains(LocalDate.of(2026, 12, 1)))
        assertEquals(true, ReportPeriod("all", null, null).contains(LocalDate.of(1999, 1, 1)))
    }

    @Test fun mergingReportsKeepsExistingAndAddsNew() {
        val mine = entry("a", 2.0, 0.0)
        val theirs = entry("b", 1.0, 0.0)
        val m = mergeReports(listOf(mine), listOf(theirs, mine), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(1, m.added)
        assertEquals(1, m.unchanged)
        assertEquals(0, m.updated)
    }

    @Test fun mergingReportsUpdatesTheSameIdOnly() {
        val mine = entry("a", 2.0, 0.0)
        val edited = mine.copy(title = "changed")
        val m = mergeReports(listOf(mine), listOf(edited), emptyList())
        assertEquals(1, m.result.size)
        assertEquals("changed", m.result.first().title)
        assertEquals(1, m.updated)
    }
}

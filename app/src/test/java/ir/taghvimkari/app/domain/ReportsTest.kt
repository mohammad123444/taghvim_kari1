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

    private fun report(person: String, reporter: String, day: Int, id: String = "$person$reporter$day") = ReportEntry(
        id = id, personId = person, personName = person, date = LocalDate.of(2026, 11, day),
        title = "t", rewardHours = 1.0, punishmentHours = 0.0, notes = "", reporter = reporter
    )

    @Test fun mergingReportsAddsNewOnesAndKeepsExisting() {
        val mine = report("a", "رضا", 1)
        val theirs = report("b", "رضا", 1)
        val m = mergeReports(listOf(mine), listOf(theirs), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(1, m.added.size)
        assertEquals(0, m.duplicates.size)
    }

    @Test fun sameReporterPersonAndDateIsADuplicateAndIsNeverRegistered() {
        val mine = report("a", "رضا", 1, id = "mine")
        // different id and different text, but the same three: reporter, person, date
        val theirs = mine.copy(id = "theirs", title = "another text", rewardHours = 5.0)
        val m = mergeReports(listOf(mine), listOf(theirs), emptyList())
        assertEquals(listOf(mine), m.result)
        assertEquals(0, m.added.size)
        assertEquals(listOf(theirs), m.duplicates)
    }

    @Test fun aDifferenceInReporterPersonOrDateIsRegistered() {
        val mine = report("a", "رضا", 1)
        val m = mergeReports(
            listOf(mine),
            listOf(report("a", "علی", 1), report("b", "رضا", 1), report("a", "رضا", 2)),
            emptyList()
        )
        assertEquals(4, m.result.size)
        assertEquals(3, m.added.size)
        assertEquals(0, m.duplicates.size)
    }

    @Test fun receivingTheSameFileTwiceRegistersNothingTheSecondTime() {
        val file = listOf(report("a", "رضا", 1), report("b", "رضا", 2))
        val first = mergeReports(emptyList(), file, emptyList())
        val second = mergeReports(first.result, file, emptyList())
        assertEquals(2, first.added.size)
        assertEquals(0, second.added.size)
        assertEquals(2, second.duplicates.size)
        assertEquals(2, second.result.size)
    }

    @Test fun duplicatesInsideOneFileAreRegisteredOnce() {
        val one = report("a", "رضا", 1, id = "x")
        val m = mergeReports(emptyList(), listOf(one, one.copy(id = "y")), emptyList())
        assertEquals(1, m.result.size)
        assertEquals(1, m.duplicates.size)
    }

    @Test fun reporterNamesAreComparedLikeSearchedNames() {
        val mine = report("a", "علی", 1)
        val m = mergeReports(listOf(mine), listOf(report("a", "علي", 1, id = "z")), emptyList())
        assertEquals(1, m.duplicates.size)
    }

    @Test fun aDifferentReportWithAnIdAlreadyUsedGetsANewId() {
        val mine = report("a", "رضا", 1, id = "same")
        val theirs = report("b", "رضا", 1, id = "same")
        val m = mergeReports(listOf(mine), listOf(theirs), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(2, m.result.map { it.id }.toSet().size)
    }
}

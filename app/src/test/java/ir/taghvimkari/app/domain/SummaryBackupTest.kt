package ir.taghvimkari.app.domain

import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.Incoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SummaryBackupTest {

    private val person = Person(
        id = "p1", name = "علی", workType = "اپراتور", employment = Employment.OFFICIAL,
        pattern = Pattern.DAY_NIGHT_REST, startDate = LocalDate.of(2026, 11, 1), startWeekday = 1,
        shiftName = "B"
    )
    private val report = ReportEntry(
        id = "r1", personId = "p1", personName = "علی", date = LocalDate.of(2026, 11, 10),
        title = "تشویق", rewardHours = 2.0, punishmentHours = 0.0, notes = ""
    )
    private val evaluation = EvaluationEntry(
        id = "e1", personId = "p1", personName = "علی", evaluatorId = "p2", evaluatorName = "رضا",
        date = LocalDate.of(2026, 11, 1), scores = List(EvaluationItems.count) { 4 },
        dateTo = LocalDate.of(2026, 11, 30)
    )
    private val cell = SummaryCell("p1", "2026-11-01|2026-11-30", extra = -2.5, comment = "تأخیر", level = 3.0)

    @Test fun fullBackupKeepsEverythingIncludingSummary() {
        val text = DataExchange.encodeFull(listOf(person), listOf(report), listOf(evaluation), listOf(cell))
        val incoming = DataExchange.parse(text) as Incoming.Full
        assertEquals(listOf(person), incoming.data.people)
        assertEquals(listOf(report), incoming.data.reports)
        assertEquals(listOf(evaluation), incoming.data.evaluations)
        assertEquals(listOf(cell), incoming.data.summary)
    }

    @Test fun summaryFileRoundTrips() {
        val incoming = DataExchange.parse(DataExchange.encodeSummary(listOf(cell))) as Incoming.Summary
        assertEquals(listOf(cell), incoming.list)
    }

    @Test fun backupWithoutStaffIsStillValid() {
        val incoming = DataExchange.parse(DataExchange.encodeFull(emptyList(), emptyList(), emptyList(), listOf(cell))) as Incoming.Full
        assertTrue(incoming.data.people.isEmpty())
        assertEquals(listOf(cell), incoming.data.summary)
    }

    @Test fun emptyBackupIsRejected() {
        val text = DataExchange.encodeFull(emptyList(), emptyList(), emptyList(), emptyList())
        val failed = runCatching { DataExchange.parse(text) }.exceptionOrNull()
        assertTrue(failed is IllegalArgumentException)
    }

    @Test fun cellWithOnlyANumberKeepsTheOtherFieldsEmpty() {
        val only = SummaryCell("p1", "k", level = 2.0)
        val back = (DataExchange.parse(DataExchange.encodeSummary(listOf(only))) as Incoming.Summary).list.single()
        assertNull(back.extra)
        assertEquals("", back.comment)
        assertEquals(2.0, back.level!!, 0.0)
    }

    @Test fun mergeAddsUpdatesAndKeepsUnchanged() {
        val other = SummaryCell("p2", "k", extra = 1.0)
        val changed = cell.copy(extra = 5.0)
        val merge = mergeSummary(listOf(cell), listOf(changed, other, other.copy(personId = "p3")))
        assertEquals(2, merge.added)
        assertEquals(1, merge.updated)
        assertEquals(3, merge.result.size)
        assertEquals(5.0, merge.result.first { it.personId == "p1" }.extra!!, 0.0)
        assertEquals(1, mergeSummary(listOf(cell), listOf(cell)).unchanged)
    }

    @Test fun numbersAreFormattedAndParsed() {
        assertEquals("−۲٫۵", formatNumber(-2.5))
        assertEquals("+۴", formatSigned(4.0))
        assertEquals(-2.5, parseNumber("−۲٫۵")!!, 0.0)
        assertNull(parseNumber(""))
        assertNull(parseNumber("abc"))
    }
}

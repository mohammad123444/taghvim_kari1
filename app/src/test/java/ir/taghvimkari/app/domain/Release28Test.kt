package ir.taghvimkari.app.domain

import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.Incoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Release28Test {

    private fun person(id: String, name: String, number: String = "", shift: String = "A:2.1") =
        Person(
            id = id, name = name, workType = "x", employment = Employment.OFFICIAL, pattern = Pattern.DAY2_REST1,
            startDate = LocalDate.of(2026, 11, 7), startWeekday = 0, shiftName = shift, personnelNumber = number
        )

    private val report = ReportEntry(
        id = "r1", personId = "p1", personName = "علی", date = LocalDate.of(2026, 11, 10),
        title = "غیبت", rewardHours = 20.0, punishmentHours = 0.0, notes = "", reporter = "رضا"
    )

    // ---------------------------------------------------------------- personnel number

    @Test fun personnelNumbersAreComparedWithLatinDigits() {
        assertEquals("12345", normalizePersonnel("۱۲۳ ۴۵"))
        assertEquals("12345", normalizePersonnel(" 12345 "))
        assertEquals("", normalizePersonnel("  "))
    }

    @Test fun aNumberThatIsTakenIsAConflict() {
        val people = listOf(person("1", "علی", "۱۰۰۱"), person("2", "رضا", ""))
        val c = personnelConflicts(people, listOf("حسن" to "1001"))
        assertEquals(1, c.size)
        assertEquals("علی", c.first().owner)
        assertEquals(0, c.first().index)
    }

    @Test fun editingAPersonDoesNotConflictWithHisOwnNumber() {
        val people = listOf(person("1", "علی", "1001"))
        assertTrue(personnelConflicts(people, listOf("علی" to "۱۰۰۱"), exceptId = "1").isEmpty())
    }

    @Test fun blankNumbersNeverConflict() {
        val people = listOf(person("1", "علی", ""), person("2", "رضا", ""))
        assertTrue(personnelConflicts(people, listOf("الف" to "", "ب" to "  ")).isEmpty())
    }

    @Test fun theSameNumberTwiceInOneFormIsAConflict() {
        // a leader and two members typed together: the third repeats the second's number
        val c = personnelConflicts(emptyList(), listOf("سرشیفت" to "1", "الف" to "2", "ب" to "۲"))
        assertEquals(listOf(2), c.map { it.index })
        assertEquals("الف", c.first().owner)
    }

    @Test fun aMembersLineMayEndWithHisNumber() {
        assertEquals("علی رضایی" to "12345", splitNameAndNumber("علی رضایی ۱۲۳۴۵"))
        assertEquals("علی رضایی" to "12345", splitNameAndNumber("علی رضایی - 12345"))
        assertEquals("علی رضایی" to "", splitNameAndNumber("علی رضایی"))
        assertEquals("علی" to "", splitNameAndNumber("علی"))
    }

    @Test fun personnelNumberSurvivesSavingAndFiles() {
        val p = person("1", "علی", "۱۰۰۱")
        // stored with Latin digits
        val back = (DataExchange.parse(DataExchange.encodePeople(listOf(p.copy(personnelNumber = "1001")))) as Incoming.People).people.first()
        assertEquals("1001", back.personnelNumber)
        // an old file without the field has no number
        assertEquals("", person("2", "رضا").personnelNumber)
    }

    @Test fun aReceivedStaffListDropsAPersonWithATakenNumber() {
        val a = person("1", "علی", "1001")
        val b = person("2", "حسین", "۱۰۰۱", shift = "B:2.1")   // different name and shift, same number
        val c = person("3", "رضا", "1002")
        val (kept, dropped) = dedupePeople(listOf(a, b, c))
        assertEquals(listOf("1", "3"), kept.map { it.id })
        assertEquals(1, dropped.size)
        assertTrue(dropped.first().startsWith("حسین"))
    }

    // ---------------------------------------------------------------- final opinion of a superior

    @Test fun withoutAFinalOpinionTheSuggestionCounts() {
        assertFalse(report.hasFinal)
        assertEquals(20.0, report.rewardHours, 0.0)
        assertEquals(20.0, listOf(report).totals().reward, 0.0)
    }

    @Test fun aChangedFinalOpinionBecomesTheCriterion() {
        val decided = report.withFinal("مدیر", reward = 10.0, punishment = 0.0)
        assertTrue(decided.hasFinal)
        assertTrue(decided.changedByFinal)
        assertEquals(10.0, decided.rewardHours, 0.0)
        assertEquals(20.0, decided.originalReward, 0.0)
        assertEquals(10.0, listOf(decided).totals().reward, 0.0)
    }

    @Test fun anUnchangedFinalOpinionKeepsTheSuggestion() {
        val decided = report.withFinal("مدیر", reward = 20.0, punishment = 0.0)
        assertTrue(decided.hasFinal)
        assertFalse(decided.changedByFinal)
        assertEquals(20.0, decided.rewardHours, 0.0)
        assertNull(decided.suggestedReward)
    }

    @Test fun changingTheFinalOpinionAgainKeepsTheFirstSuggestion() {
        val twice = report.withFinal("مدیر", 10.0, 0.0).withFinal("مدیر", 5.0, 0.0)
        assertEquals(5.0, twice.rewardHours, 0.0)
        assertEquals(20.0, twice.originalReward, 0.0)
        // back to the suggested number: no longer "changed"
        val same = twice.withFinal("مدیر", 20.0, 0.0)
        assertFalse(same.changedByFinal)
    }

    @Test fun takingTheFinalOpinionBackRestoresTheSuggestion() {
        val back = report.withFinal("مدیر", 10.0, 3.0).withoutFinal()
        assertEquals(report, back)
    }

    @Test fun finalOpinionSurvivesSavingAndFiles() {
        val decided = report.withFinal("مدیر", 10.0, 3.0)
        val incoming = DataExchange.parse(DataExchange.encodeReports(listOf(decided))) as Incoming.Reports
        assertEquals(listOf(decided), incoming.list)
        // a report without any opinion reads back unchanged
        val plain = DataExchange.parse(DataExchange.encodeReports(listOf(report))) as Incoming.Reports
        assertEquals(listOf(report), plain.list)
    }

    @Test fun aReceivedFinalOpinionIsAppliedToTheExistingReportOnly() {
        val mine = report.copy(title = "عنوان من")
        val theirs = report.copy(id = "other", title = "عنوان آنها").withFinal("مدیر", 10.0, 0.0)
        val m = mergeReports(listOf(mine), listOf(theirs), emptyList())
        assertEquals(0, m.duplicates.size)
        assertEquals(1, m.decided.size)
        assertEquals(1, m.result.size)
        val r = m.result.first()
        assertEquals("r1", r.id)
        assertEquals("عنوان من", r.title)       // nothing else of the existing report changes
        assertEquals(10.0, r.rewardHours, 0.0)
        assertEquals(20.0, r.originalReward, 0.0)
        assertEquals("مدیر", r.finalBy)
    }

    @Test fun aReceivedFinalOpinionNeverReplacesAnExistingOne() {
        val mine = report.withFinal("مدیر الف", 5.0, 0.0)
        val theirs = report.copy(id = "other").withFinal("مدیر ب", 1.0, 0.0)
        val m = mergeReports(listOf(mine), listOf(theirs), emptyList())
        assertEquals(1, m.duplicates.size)
        assertTrue(m.decided.isEmpty())
        assertEquals(5.0, m.result.first().rewardHours, 0.0)
    }

    @Test fun reportTextMentionsTheFinalOpinion() {
        val decided = report.withFinal("مدیر", 10.0, 0.0)
        val text = buildReportText("همه", "همه", listOf(decided)) { it.personName }
        assertTrue(text.contains("نظر نهایی: مدیر"))
        assertTrue(text.contains("پیشنهاد اولیه"))
    }
}

package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Release25Test {

    private fun entry(
        id: String, person: String = "p", evaluator: String = "e",
        from: LocalDate, to: LocalDate, score: Int = 4, draftItems: Int = 0
    ) = EvaluationEntry(
        id = id, personId = person, personName = "نام", evaluatorId = evaluator, evaluatorName = "ارزیاب",
        date = from, dateTo = to,
        scores = List(EvaluationItems.count) { if (it < draftItems) 0 else score }
    )

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    // ------------------------------------------------------------ text size

    @Test fun thereAreThreeTextSizesAndNormalIsTheDefault() {
        assertEquals(listOf("کوچک", "معمولی", "بزرگ"), TextSize.entries.map { it.title })
        assertEquals(TextSize.NORMAL, TextSize.fromId(null))
        assertEquals(TextSize.NORMAL, TextSize.fromId("nonsense"))
        assertEquals(TextSize.LARGE, TextSize.fromId(TextSize.LARGE.id))
        assertEquals(1.0f, TextSize.NORMAL.factor, 0f)
    }

    @Test fun sizesGrowInOrder() {
        assertTrue(TextSize.SMALL.factor < TextSize.NORMAL.factor)
        assertTrue(TextSize.NORMAL.factor < TextSize.LARGE.factor)
    }

    // ------------------------------------------------------------ monthly backup reminder

    @Test fun noReminderWhenThereIsNothingToBackUp() {
        assertFalse(backupReminderDue(1000, null, null, hasData = false))
    }

    @Test fun reminderComesAtOnceIfNeverBackedUp() {
        assertTrue(backupReminderDue(1000, null, null, hasData = true))
    }

    @Test fun reminderComesAMonthAfterTheLastBackup() {
        assertFalse(backupReminderDue(1029, 1000, null, hasData = true))
        assertTrue(backupReminderDue(1030, 1000, null, hasData = true))
        assertTrue(backupReminderDue(1100, 1000, null, hasData = true))
    }

    @Test fun aClosedReminderStaysHiddenForAFewDays() {
        val until = 1000 + BACKUP_SNOOZE_DAYS
        assertFalse(backupReminderDue(1000, null, until, hasData = true))
        assertFalse(backupReminderDue(until - 1, null, until, hasData = true))
        assertTrue(backupReminderDue(until, null, until, hasData = true))
    }

    @Test fun daysSinceBackupIgnoresAClockThatWentBackwards() {
        assertEquals(12L, daysSinceBackup(1012, 1000))
        assertNull(daysSinceBackup(1000, null))
        assertNull(daysSinceBackup(990, 1000))
    }

    // ------------------------------------------------------------ working draft

    @Test fun aDraftKnowsWhoIsNextAndWhenItIsOver() {
        val draft = WorkingDraft("ev", 10, 20, listOf("a", "b", "c"))
        assertEquals("a", draft.currentPersonId)
        assertEquals(3, draft.remaining)
        val second = draft.advanced()!!
        assertEquals("b", second.currentPersonId)
        assertEquals(2, second.remaining)
        assertTrue(second.advanced()!!.isLast)
        assertNull(second.advanced()!!.advanced())
    }

    @Test fun movingOnForgetsTheTypedScoresOfThePreviousPerson() {
        val draft = WorkingDraft("ev", 10, 20, listOf("a", "b"), scores = "4".repeat(EvaluationItems.count))
        assertEquals("", draft.advanced()!!.scores)
    }

    @Test fun typedScoresAreOnlyUsedWhenTheyAreComplete() {
        val n = EvaluationItems.count
        assertNull(WorkingDraft("e", 1, 1, listOf("a")).typedScores(n))
        assertNull(WorkingDraft("e", 1, 1, listOf("a"), scores = "45").typedScores(n))
        assertNull(WorkingDraft("e", 1, 1, listOf("a"), scores = "9".repeat(n)).typedScores(n))
        val typed = WorkingDraft("e", 1, 1, listOf("a"), scores = "4".repeat(n - 1) + "0").typedScores(n)!!
        assertEquals(n, typed.size)
        assertEquals(0, typed.last())
        assertEquals(4, typed.first())
    }

    @Test fun aDraftSurvivesBeingSavedAndLoaded() {
        val draft = WorkingDraft("ev-1", 20000, 20029, listOf("p1", "p2", "p3"), index = 1, scores = "0".repeat(EvaluationItems.count))
        assertEquals(draft, WorkingDraft.decode(draft.encode()))
        val noScores = draft.copy(scores = "")
        assertEquals(noScores, WorkingDraft.decode(noScores.encode()))
    }

    @Test fun brokenSavedDraftsAreIgnored() {
        assertNull(WorkingDraft.decode(null))
        assertNull(WorkingDraft.decode(""))
        assertNull(WorkingDraft.decode("a|b|c"))
        assertNull(WorkingDraft.decode("ev|1|2|p1,p2|5|")) // index out of range
        assertNull(WorkingDraft.decode("ev|x|2|p1|0|"))
        assertNull(WorkingDraft.decode("|1|2|p1|0|"))
    }

    // ------------------------------------------------------------ one tap: score and move on

    @Test fun aTapChangesOnlyThatItemAndMovesToTheNextOne() {
        val before = "0".repeat(EvaluationItems.count)
        val after = setScoreAt(before, 2, 5)
        assertEquals('5', after[2])
        assertEquals(before.length, after.length)
        assertEquals(1, after.count { it != '0' })
        assertEquals(3, nextFocus(2, EvaluationItems.count))
    }

    @Test fun theLastItemStaysWhereItIs() {
        val last = EvaluationItems.count - 1
        assertEquals(last, nextFocus(last, EvaluationItems.count))
    }

    // ------------------------------------------------------------ copy from the previous period

    @Test fun copyTakesTheLatestCompleteEvaluationBeforeThisPeriodByTheSameEvaluator() {
        val old = entry("old", from = d(8, 1), to = d(8, 31), score = 2)
        val newer = entry("newer", from = d(9, 1), to = d(9, 30), score = 3)
        val otherEvaluator = entry("x", evaluator = "other", from = d(10, 1), to = d(10, 31), score = 5)
        val otherPerson = entry("y", person = "q", from = d(10, 1), to = d(10, 31), score = 5)
        val later = entry("later", from = d(12, 1), to = d(12, 31), score = 1)
        val all = listOf(old, newer, otherEvaluator, otherPerson, later)
        assertEquals("newer", previousEvaluation(all, "p", "e", d(11, 1))?.id)
    }

    @Test fun copyIgnoresDraftsAndEvaluationsOfTheSamePeriod() {
        val draft = entry("draft", from = d(9, 1), to = d(9, 30), score = 3, draftItems = 5)
        val overlapping = entry("same", from = d(10, 1), to = d(10, 31), score = 3)
        assertNull(previousEvaluation(listOf(draft, overlapping), "p", "e", d(10, 1)))
    }

    // ------------------------------------------------------------ undo of a deletion

    @Test fun anUndoneDeletionComesBackAtItsOldPlace() {
        val list = listOf("a", "c", "d")
        assertEquals(listOf("a", "b", "c", "d"), restoreAt(list, "b", 1) { it == "b" })
    }

    @Test fun undoWorksAtTheEndsAndWhenTheListGotShorter() {
        assertEquals(listOf("z", "a"), restoreAt(listOf("a"), "z", 0) { it == "z" })
        assertEquals(listOf("a", "z"), restoreAt(listOf("a"), "z", 7) { it == "z" })
        assertEquals(listOf("z"), restoreAt(emptyList(), "z", 3) { it == "z" })
    }

    @Test fun undoNeverCreatesADuplicate() {
        assertEquals(listOf("a", "b"), restoreAt(listOf("a", "b"), "b", 0) { it == "b" })
    }
}

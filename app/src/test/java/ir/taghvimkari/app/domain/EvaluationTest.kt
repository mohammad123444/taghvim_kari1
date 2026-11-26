package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EvaluationTest {

    private fun entry(score: Int) = EvaluationEntry(
        id = "e", personId = "p", personName = "علی", evaluatorId = "l", evaluatorName = "رضا",
        date = LocalDate.of(2026, 11, 23), scores = List(EvaluationItems.count) { score }
    )

    @Test fun thereAre23ItemsAndTheMaximumIs115() {
        assertEquals(23, EvaluationItems.count)
        assertEquals(115, EvaluationItems.maxTotal)
    }

    @Test fun finalScoreIsSumTimes45DividedBy115() {
        assertEquals(45.0, entry(5).finalScore, 1e-9)
        assertEquals(9.0, entry(1).finalScore, 1e-9) // 23 * 45 / 115
        assertEquals(98 * 45 / 115.0, Evaluations.finalScore(98), 1e-9)
    }

    @Test fun scoreIsFormattedWithPersianDigitsAndTwoDecimals() {
        assertEquals("۳۸٫۳۵", formatScore(98 * 45 / 115.0))
        assertEquals("۴۵٫۰۰", formatScore(45.0))
    }

    @Test fun levelsFollowThePercentageOf45() {
        assertEquals(EvaluationLevel.EXCELLENT, Evaluations.levelOf(45.0))
        assertEquals(EvaluationLevel.VERY_GOOD, Evaluations.levelOf(37.0))
        assertEquals(EvaluationLevel.GOOD, Evaluations.levelOf(32.0))
        assertEquals(EvaluationLevel.FAIR, Evaluations.levelOf(25.0))
        assertEquals(EvaluationLevel.WEAK, Evaluations.levelOf(9.0))
    }

    @Test fun averageOfNothingIsZero() {
        assertEquals(0.0, Evaluations.average(emptyList()), 0.0)
        assertEquals(27.0, Evaluations.average(listOf(entry(5), entry(1))), 1e-9)
    }

    @Test fun textContainsEveryItemAndTheFinalScore() {
        val text = buildEvaluationText(entry(4), "علی")
        assertTrue(text.contains("شناخت کار"))
        assertTrue(text.contains("نگرش سیستمی"))
        assertTrue(text.contains("نمرهٔ نهایی"))
    }

    private fun person(id: String, name: String) = Person(
        id = id, name = name, workType = "x", employment = Employment.OFFICIAL, pattern = Pattern.WORK_REST,
        startDate = LocalDate.of(2026, 1, 1), startWeekday = 0
    )

    private fun draft(id: String, done: Int, person: String = "p") = EvaluationEntry(
        id = id, personId = person, personName = "علی", evaluatorId = "l", evaluatorName = "رضا",
        date = LocalDate.of(2026, 11, 23),
        scores = List(EvaluationItems.count) { if (it < done) 4 else 0 }
    )

    @Test fun anEvaluationWithUnscoredItemsIsADraft() {
        assertTrue(!draft("d", 10).isComplete)
        assertEquals(10, draft("d", 10).doneCount)
        assertTrue(draft("d", EvaluationItems.count).isComplete)
    }

    @Test fun draftsAreIgnoredInAverages() {
        assertEquals(45.0, Evaluations.average(listOf(entry(5), draft("d", 3))), 1e-9)
    }

    @Test fun mergeAddsNewAndKeepsExisting() {
        val m = mergeEvaluations(listOf(entry(5)), listOf(draft("other", 5, person = "q")), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(1, m.added.size)
    }

    @Test fun sameEvaluatorPersonAndPeriodIsADuplicateAndIsNeverRegistered() {
        // different id and different scores, but the same evaluator, person and period
        val m = mergeEvaluations(listOf(draft("a", 5)), listOf(draft("b", 12)), emptyList())
        assertEquals(1, m.result.size)
        assertEquals(5, m.result.first().doneCount)
        assertEquals(1, m.duplicates.size)
        assertEquals(0, m.added.size)
    }

    @Test fun aDifferentEvaluatorIsNotADuplicate() {
        val m = mergeEvaluations(listOf(entry(5)), listOf(entry(5).copy(id = "x", evaluatorId = "other", evaluatorName = "علی")), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(1, m.added.size)
    }

    @Test fun mergeMatchesPeopleByNameWhenIdsDiffer() {
        val m = mergeEvaluations(emptyList(), listOf(draft("a", 5, person = "foreign")), listOf(person("mine", "علي")))
        assertEquals("mine", m.result.first().personId)
    }

    @Test fun oldEvaluationsHaveTheSameDayAsStartAndEndOfThePeriod() {
        val e = entry(4)
        assertEquals(e.date, e.dateTo)
    }

    @Test fun periodsAreListedNewestFirstWithoutDuplicates() {
        val a = entry(4).copy(id = "a", date = LocalDate.of(2026, 10, 1), dateTo = LocalDate.of(2026, 10, 30))
        val b = entry(4).copy(id = "b", date = LocalDate.of(2026, 11, 1), dateTo = LocalDate.of(2026, 11, 30))
        val c = b.copy(id = "c", personId = "q")
        val periods = evaluationPeriods(listOf(a, b, c))
        assertEquals(2, periods.size)
        assertEquals(LocalDate.of(2026, 11, 1), periods.first().first)
    }

    @Test fun sameDayInAnotherPeriodIsAnotherEvaluation() {
        val october = entry(5).copy(id = "a", date = LocalDate.of(2026, 10, 1), dateTo = LocalDate.of(2026, 10, 30))
        val november = entry(5).copy(id = "b", date = LocalDate.of(2026, 11, 1), dateTo = LocalDate.of(2026, 11, 30))
        val m = mergeEvaluations(listOf(october), listOf(november), emptyList())
        assertEquals(2, m.result.size)
        assertEquals(1, m.added.size)
    }

    @Test fun mergingEvaluationsFromSeveralEvaluatorsKeepsEverything() {
        val mine = entry(5).copy(id = "m", evaluatorId = "e1", evaluatorName = "الف")
        val first = entry(4).copy(id = "x", evaluatorId = "e2", evaluatorName = "ب")
        val second = entry(3).copy(id = "y", evaluatorId = "e3", evaluatorName = "ج")
        val afterFirst = mergeEvaluations(listOf(mine), listOf(first), emptyList()).result
        val afterSecond = mergeEvaluations(afterFirst, listOf(second), emptyList()).result
        assertEquals(3, afterSecond.size)
        assertEquals(setOf("m", "x", "y"), afterSecond.map { it.id }.toSet())
    }

    @Test fun periodKeyDistinguishesPeriods() {
        val a = entry(5)
        assertTrue(a.periodKey != a.copy(dateTo = a.date.plusDays(5)).periodKey)
    }
}

package ir.taghvimkari.app.domain

import ir.taghvimkari.app.data.ReportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class Release281Test {
    private val suggested = ReportEntry(
        id = "r1", personId = "p1", personName = "علی", date = LocalDate.of(2026, 10, 20),
        title = "کار اضافه", rewardHours = 30.0, punishmentHours = 0.0, notes = "", reporter = "رضا"
    )

    @Test fun aSuggestionAloneIsNotCounted() {
        assertEquals(0, listOf(suggested).countedTotals().count)
        assertEquals(0.0, listOf(suggested).countedTotals().reward, 0.0)
    }

    @Test fun theFinalOpinionCountsInTheMonthItWasGiven() {
        val decided = suggested.withFinal("مدیر", 35.0, 0.0, on = LocalDate.of(2026, 11, 25))
        assertEquals(LocalDate.of(2026, 11, 25), decided.countDate)
        assertEquals(35.0, listOf(decided).countedTotals().reward, 0.0)
        assertEquals(LocalDate.of(2026, 10, 20), decided.date)
    }

    @Test fun aLaterCorrectionKeepsTheFirstFinalDay() {
        val first = suggested.withFinal("مدیر", 35.0, 0.0, on = LocalDate.of(2026, 11, 25))
        val again = first.withFinal("مدیر", 40.0, 0.0, on = LocalDate.of(2026, 12, 3))
        assertEquals(LocalDate.of(2026, 11, 25), again.finalDate)
    }

    @Test fun takingTheOpinionBackMakesItASuggestionAgain() {
        val back = suggested.withFinal("مدیر", 35.0, 0.0, on = LocalDate.of(2026, 11, 25)).withoutFinal()
        assertNull(back.finalDate)
        assertEquals(suggested.date, back.countDate)
        assertEquals(0, listOf(back).countedTotals().count)
    }

    @Test fun finalDateSurvivesSaving() {
        val decided = suggested.withFinal("مدیر", 35.0, 0.0, on = LocalDate.of(2026, 11, 25))
        val back = ReportStore.decode(ReportStore.encode(listOf(decided))).single()
        assertEquals(LocalDate.of(2026, 11, 25), back.finalDate)
    }

    @Test fun oldFinalOpinionsWithoutADayCountOnTheirOwnDate() {
        val old = suggested.copy(finalBy = "مدیر")
        assertEquals(suggested.date, old.countDate)
    }
}

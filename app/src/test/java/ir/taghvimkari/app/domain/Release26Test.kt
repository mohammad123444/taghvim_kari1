package ir.taghvimkari.app.domain

import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.Incoming
import ir.taghvimkari.app.data.Secure
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Release26Test {

    private fun person(id: String, name: String, shift: String = "A", pattern: Pattern = Pattern.WORK_REST, start: LocalDate = LocalDate.of(2026, 11, 7)) =
        Person(
            id = id, name = name, workType = "x", employment = Employment.OFFICIAL, pattern = pattern,
            startDate = start, startWeekday = ShiftEngine.persianWeekday(start), shiftName = shift
        )

    @Test fun searchNeedsThreeCharacters() {
        assertTrue(nameMatches("علی رضایی", ""))
        assertFalse(nameMatches("علی رضایی", "عل"))
        assertTrue(nameMatches("علی رضایی", "علی"))
        assertTrue(nameMatches("علی رضایی", "رضا"))
        assertFalse(nameMatches("علی رضایی", "حسن"))
        assertTrue(searchTooShort("عل"))
        assertFalse(searchTooShort("علی"))
    }

    @Test fun duplicatesInAReceivedStaffListAreLeftOut() {
        val a = person("1", "علی")
        val b = person("2", "علي")            // same name (Arabic yeh), same shift and employment
        val c = person("3", "رضا").copy(leaderId = "2")
        val (kept, dropped) = dedupePeople(listOf(a, b, c))
        assertEquals(listOf("1", "3"), kept.map { it.id })
        assertEquals(listOf("علي"), dropped)
        assertEquals("1", kept.last().leaderId) // his leader was a duplicate: points to the kept one
    }

    @Test fun sameNameInAnotherShiftIsNotADuplicate() {
        val (kept, dropped) = dedupePeople(listOf(person("1", "علی", "A"), person("2", "علی", "B")))
        assertEquals(2, kept.size)
        assertTrue(dropped.isEmpty())
    }

    @Test fun aTransferDoesNotRewriteThePast() {
        val p = person("1", "علی", "A", Pattern.WORK_REST, LocalDate.of(2026, 11, 7))
        val moveDay = LocalDate.of(2026, 12, 5)
        val moved = Transfer.move(p, "B", null, moveDay)
        assertEquals("B", moved.shiftName)
        assertEquals(1, moved.history.size)
        // Before the move day the old calendar is used.
        var d = LocalDate.of(2026, 11, 7)
        while (d.isBefore(moveDay)) {
            assertEquals(ShiftEngine.shift(p, d), ShiftEngine.shift(moved, d))
            d = d.plusDays(1)
        }
        // From the move day the new cycle starts: the first week is a working week.
        assertEquals(Shift.WORK, ShiftEngine.shift(moved, moveDay))
    }

    @Test fun swapExchangesShiftsAndKeepsRecordsOwnership() {
        val a = person("1", "علی", "A")
        val b = person("2", "رضا", "B", Pattern.DAY_NIGHT_REST)
        val (na, nb) = Transfer.swap(a, b, LocalDate.of(2026, 12, 1))
        assertEquals("B", na.shiftName)
        assertEquals("A", nb.shiftName)
        assertEquals("1", na.id)       // ids never change, so reports and evaluations stay with the person
        assertEquals(Pattern.DAY_NIGHT_REST, na.pattern)
    }

    @Test fun sentFilesAreEncryptedAndStillReadable() {
        val plain = DataExchange.encodePeople(listOf(person("1", "علی")))
        val sealed = JSONObject().put("app", "taghvim-kari").put("enc", 1).put("data", Secure.encrypt(plain)).toString()
        assertFalse(sealed.contains("علی"))
        val incoming = DataExchange.parse(sealed)
        assertTrue(incoming is Incoming.People)
        assertEquals("علی", (incoming as Incoming.People).people.first().name)
        // Plain files from older versions are still accepted.
        assertTrue(DataExchange.parse(plain) is Incoming.People)
        assertEquals(plain, Secure.decrypt(Secure.encrypt(plain)))
    }

    @Test fun similarReportsAreOnlyReplacedWithAReason() {
        val mine = ReportEntry("a", "p", "علی", LocalDate.of(2026, 11, 1), "قدیمی", 1.0, 0.0, "")
        val theirs = mine.copy(title = "جدید")
        val keep = mergeReports(listOf(mine), listOf(theirs), emptyList(), replaceSimilar = false)
        assertEquals(1, keep.conflicts)
        assertEquals("قدیمی", keep.result.first().title)
        val replaced = mergeReports(listOf(mine), listOf(theirs), emptyList(), replaceSimilar = true, reason = "اصلاح اشتباه")
        assertEquals("جدید", replaced.result.first().title)
        assertTrue(replaced.result.first().notes.contains("اصلاح اشتباه"))
    }

    @Test fun peopleHistorySurvivesSaving() {
        val moved = Transfer.move(person("1", "علی"), "C", null, LocalDate.of(2026, 12, 5))
        val text = DataExchange.encodePeople(listOf(moved))
        val back = (DataExchange.parse(text) as Incoming.People).people.first()
        assertEquals(1, back.history.size)
        assertEquals("A", back.history.first().shiftName)
    }
}

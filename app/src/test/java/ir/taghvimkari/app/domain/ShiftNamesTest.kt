package ir.taghvimkari.app.domain

import ir.taghvimkari.app.data.LocalStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftNamesTest {

    @Test fun theShiftNamesAreTheAgreedList() {
        assertEquals(
            listOf("A:2.1", "A:2.2", "B:2.1", "B:2.2", "C:2.1", "C:2.2", "D:2.2", "عادی کار"),
            SHIFT_NAMES
        )
    }

    @Test fun titles() {
        assertEquals("شیفت A:2.1", shiftTitle("A:2.1"))
        assertEquals("عادی کار", shiftTitle(ORDINARY_SHIFT))
        assertEquals("بدون شیفت", shiftTitle(""))
    }

    @Test fun oldLettersBecomeNewNamesByPattern() {
        assertEquals("A:2.1", migrateShiftName("A", Pattern.DAY2_REST1))
        assertEquals("A:2.2", migrateShiftName("A", Pattern.DAY2_REST2))
        assertEquals("C:2.1", migrateShiftName("C", Pattern.DAY2_REST1))
        assertEquals("B:2.2", migrateShiftName("B", Pattern.DAY2_REST2))
        // D only exists as 2.2
        assertEquals("D:2.2", migrateShiftName("D", Pattern.DAY2_REST1))
        assertEquals("D:2.2", migrateShiftName("D", Pattern.WORK_REST))
    }

    @Test fun anOldLetterWithAnotherPatternIsKeptNotLost() {
        assertEquals("B", migrateShiftName("B", Pattern.DAY_NIGHT_REST))
        assertTrue(isLegacyShift("B"))
        assertEquals(listOf("B"), shiftGroups(listOf("B", "A:2.1", "")).filter { isLegacyShift(it) })
    }

    @Test fun newNamesAndEmptyStayAndJunkIsDropped() {
        assertEquals("B:2.2", migrateShiftName("B:2.2", Pattern.WORK_REST))
        assertEquals(ORDINARY_SHIFT, migrateShiftName(ORDINARY_SHIFT, Pattern.OFFICIAL_DAYS))
        assertEquals("", migrateShiftName("", Pattern.DAY2_REST1))
        assertEquals("", migrateShiftName("zzz", Pattern.DAY2_REST1))
    }

    @Test fun groupsEndWithNoShiftAndOrderIsStable() {
        val groups = shiftGroups(emptyList())
        assertEquals(SHIFT_NAMES + "", groups)
        assertTrue(shiftOrder("A:2.1") < shiftOrder("A:2.2"))
        assertTrue(shiftOrder("A:2.2") < shiftOrder("B:2.1"))
        assertTrue(shiftOrder("D:2.2") < shiftOrder(ORDINARY_SHIFT))
        assertTrue(shiftOrder(ORDINARY_SHIFT) < shiftOrder(""))
        // an old letter sits with the names of its letter
        assertEquals(shiftOrder("B:2.1"), shiftOrder("B"))
    }

    @Test fun savedPeopleWithOldLettersAreMigratedWhenLoaded() {
        val start = LocalDate.of(2026, 11, 7)
        fun json(id: String, shift: String, pattern: Pattern) = JSONObject()
            .put("id", id).put("name", "نفر $id").put("employment", "OFFICIAL")
            .put("pattern", pattern.name).put("startDate", start.toString())
            .put("shiftName", shift)
        val array = JSONArray()
            .put(json("1", "A", Pattern.DAY2_REST1))
            .put(json("2", "A", Pattern.DAY2_REST2))
            .put(json("3", "D", Pattern.DAY2_REST1))
            .put(json("4", "", Pattern.OFFICIAL_DAYS))
        val people = LocalStore.decode(array)
        assertEquals(listOf("A:2.1", "A:2.2", "D:2.2", ""), people.map { it.shiftName })
        // saving and loading again changes nothing
        assertEquals(people.map { it.shiftName }, LocalStore.decode(LocalStore.encode(people)).map { it.shiftName })
        assertFalse(people.any { isLegacyShift(it.shiftName) })
    }

    @Test fun shiftNameChoosesThePattern() {
        assertEquals(Pattern.DAY2_REST1, patternForShift("A:2.1"))
        assertEquals(Pattern.DAY2_REST1, patternForShift("C:2.1"))
        assertEquals(Pattern.DAY2_REST2, patternForShift("D:2.2"))
        assertEquals(Pattern.DAY2_REST2, patternForShift("B:2.2"))
        assertEquals(Pattern.OFFICIAL_DAYS, patternForShift(ORDINARY_SHIFT))
        assertEquals(null, patternForShift(""))
        assertEquals(null, patternForShift("B"))
        // every standard name has a pattern
        assertTrue(SHIFT_NAMES.all { patternForShift(it) != null })
    }
}

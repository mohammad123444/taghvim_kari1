package ir.taghvimkari.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BucketsTest {

    // 2026-11-14 is a Saturday.
    private val saturday = LocalDate.of(2026, 11, 14)

    private fun person(
        pattern: Pattern,
        category: Category = Category.OPERATIONAL,
        name: String = "x",
        id: String = name,
        leader: Boolean = false,
        leaderId: String? = null,
        employment: Employment = Employment.OFFICIAL
    ) = Person(
        id = id, name = name, workType = WorkTypes.default, employment = employment,
        pattern = pattern, startDate = saturday, startWeekday = 0,
        isLeader = leader, leaderId = leaderId, category = category
    )

    @Test fun administrativeStaffOnlyHaveTwoStates() {
        val p = person(Pattern.WORK_REST, Category.ADMIN)
        assertEquals(Bucket.DAY, bucketOf(p, ShiftEngine.status(p, saturday)))
        assertEquals(Bucket.REST, bucketOf(p, ShiftEngine.status(p, saturday.plusDays(8))))
    }

    @Test fun operationalRestIsSplitIntoFirstAndSecondWeek() {
        val p = person(Pattern.DAY_NIGHT_REST2) // 1 week day, 1 week night, 2 weeks rest
        assertEquals(Bucket.DAY, bucketOf(p, ShiftEngine.status(p, saturday)))
        assertEquals(Bucket.NIGHT, bucketOf(p, ShiftEngine.status(p, saturday.plusDays(7))))
        assertEquals(Bucket.REST_WEEK1, bucketOf(p, ShiftEngine.status(p, saturday.plusDays(14))))
        assertEquals(Bucket.REST_WEEK1, bucketOf(p, ShiftEngine.status(p, saturday.plusDays(20))))
        assertEquals(Bucket.REST_WEEK2, bucketOf(p, ShiftEngine.status(p, saturday.plusDays(21))))
    }

    @Test fun notStartedPeopleAreShownInTheSecondRestWeek() {
        val p = person(Pattern.DAY_NIGHT_REST2)
        assertEquals(Bucket.REST_WEEK2, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(2))))
    }

    @Test fun remainingDaysDoNotCountToday() {
        // 14 working days (two day weeks): on day 10 -> 10 passed, 4 left.
        val p = person(Pattern.DAY2_REST1)
        val s = ShiftEngine.status(p, saturday.plusDays(9))
        assertEquals(10, s.elapsedDays)
        assertEquals(4, s.remainingDays)
        // Last working day: nothing left.
        assertEquals(0, ShiftEngine.status(p, saturday.plusDays(13)).remainingDays)
        // First rest day of a 7-day rest: 1 passed, 6 left.
        val rest = ShiftEngine.status(p, saturday.plusDays(14))
        assertEquals(1, rest.elapsedDays)
        assertEquals(6, rest.remainingDays)
    }

    @Test fun teamsAreListedLeaderFirstThenMembers() {
        val leader = person(Pattern.WORK_REST, name = "ب", id = "L", leader = true)
        val member = person(Pattern.WORK_REST, name = "الف", id = "M", leaderId = "L")
        val single = person(Pattern.WORK_REST, name = "پ", id = "S")
        val sorted = listOf(member, single, leader).sortedByTeam(mapOf("L" to "ب")) { it }
        assertEquals(listOf("L", "M", "S"), sorted.map { it.id })
    }

    @Test fun peopleAreListedByEmploymentThenAdminThenTeamsThenOthers() {
        val contractorTeam = person(Pattern.WORK_REST, name = "آ", id = "CT", employment = Employment.CONTRACTOR)
        val otherOfficial = person(Pattern.WORK_REST, name = "الف", id = "OO")
        val contractualAdmin = person(Pattern.WORK_REST, Category.ADMIN, name = "ب", id = "CA", employment = Employment.CONTRACTUAL)
        val leader = person(Pattern.WORK_REST, name = "ز", id = "L", leader = true)
        val member = person(Pattern.WORK_REST, name = "ی", id = "M", leaderId = "L")
        val officialAdmin = person(Pattern.WORK_REST, Category.ADMIN, name = "ی", id = "OA")
        val sorted = listOf(contractorTeam, otherOfficial, contractualAdmin, member, leader, officialAdmin).sortedByShiftName()
        // رسمی: اداری، سرشیفت و زیرمجموعه، سایر عملیاتی؛ بعد قراردادی؛ بعد پیمانکاری
        assertEquals(listOf("OA", "L", "M", "OO", "CA", "CT"), sorted.map { it.id })
    }

    @Test fun twoWeekRestSplitsDays1to7AndDays8to14() {
        // Both patterns with a two-week rest: rest starts after 2 weeks of work (DAY2_REST2)
        // or after a day week and a night week (DAY_NIGHT_REST2).
        for (pattern in listOf(Pattern.DAY2_REST2, Pattern.DAY_NIGHT_REST2)) {
            val p = person(pattern)
            val firstRestDay = saturday.plusDays(14)
            for (n in 1..7) {
                assertEquals("rest day $n of $pattern", Bucket.REST_WEEK1,
                    bucketOf(p, ShiftEngine.status(p, firstRestDay.plusDays((n - 1).toLong()))))
            }
            for (n in 8..14) {
                assertEquals("rest day $n of $pattern", Bucket.REST_WEEK2,
                    bucketOf(p, ShiftEngine.status(p, firstRestDay.plusDays((n - 1).toLong()))))
            }
        }
    }

    @Test fun notStartedPeopleAreSplitByRestDaysLeft() {
        val p = person(Pattern.DAY_NIGHT_REST2)
        // 14 .. 8 rest days left -> first rest week
        assertEquals(Bucket.REST_WEEK1, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(14))))
        assertEquals(Bucket.REST_WEEK1, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(8))))
        // 7 .. 1 left -> second rest week
        assertEquals(Bucket.REST_WEEK2, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(7))))
        assertEquals(Bucket.REST_WEEK2, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(1))))
    }

    @Test fun peopleWithShortRestAreJustResting() {
        // One-week-rest patterns never get "first/second rest week", started or not.
        for (pattern in listOf(Pattern.WORK_REST, Pattern.DAY2_REST1, Pattern.DAY_NIGHT_REST)) {
            val p = person(pattern)
            val restDay = saturday.plusDays((pattern.cycleWeeks - 1) * 7L + 3)
            val status = ShiftEngine.status(p, restDay)
            assertEquals("$pattern", Bucket.REST, bucketOf(p, status))
            assertEquals("$pattern not started", Bucket.REST, bucketOf(p, ShiftEngine.status(p, saturday.minusDays(10))))
        }
    }
}

package ir.taghvimkari.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Situation of one person on one date.
 * [elapsedDays] counts the current work/rest streak up to and including the date itself;
 * [daysToChange] is the number of days until the streak flips (1 = tomorrow).
 */
data class DayStatus(
    val shift: Shift,
    val working: Boolean,
    val started: Boolean,
    val elapsedDays: Int,
    val daysToChange: Int,
    /** 1 = first week of the rest period, 2 = second week; 0 when working or not applicable. */
    val restWeek: Int = 0
) {
    /** Days of the current work/rest stretch still to come after today (a 14-day stretch on day 10 -> 4). */
    val remainingDays: Int get() = daysToChange - 1
}

object ShiftEngine {

    private const val MAX_STREAK = 400

    /** Java DayOfWeek: Monday=1 ... Sunday=7. Persian weekday: Saturday=0 ... Friday=6. */
    fun persianWeekday(date: LocalDate): Int = (date.dayOfWeek.value + 1) % 7

    /** The person as he was on [date]: after a transfer, older dates still use the schedule he had then. */
    private fun effective(person: Person, date: LocalDate): Person {
        if (person.history.isEmpty()) return person
        val seg = person.history.sortedBy { it.until }.firstOrNull { date.isBefore(it.until) } ?: return person
        return person.copy(
            pattern = seg.pattern, startDate = seg.startDate, startWeekday = seg.startWeekday,
            shiftName = seg.shiftName, leaderId = seg.leaderId, history = emptyList()
        )
    }

    fun shift(source: Person, date: LocalDate, officialHolidays: Set<LocalDate> = emptySet()): Shift {
        val person = effective(source, date)
        if (person.pattern == Pattern.OFFICIAL_DAYS) {
            val isWorkday = persianWeekday(date) in 0..4 && date !in officialHolidays
            return if (isWorkday) Shift.WORK else Shift.REST
        }
        // The cycle starts on the first [startWeekday] on or after the chosen start date.
        val start = person.startDate
        val anchor = start.plusDays(((person.startWeekday - persianWeekday(start) + 7) % 7).toLong())
        val days = ChronoUnit.DAYS.between(anchor, date)
        if (days < 0) return Shift.REST
        val week = ((days / 7) % person.pattern.cycleWeeks).toInt()
        return when (person.pattern) {
            Pattern.DAY2_REST1, Pattern.DAY2_REST2 -> if (week < 2) Shift.DAY else Shift.REST
            Pattern.DAY_NIGHT_REST -> when (week) { 0 -> Shift.DAY; 1 -> Shift.NIGHT; else -> Shift.REST }
            Pattern.WORK_REST -> if (week == 0) Shift.WORK else Shift.REST
            Pattern.DAY_NIGHT_REST2 -> when (week) { 0 -> Shift.DAY; 1 -> Shift.NIGHT; else -> Shift.REST }
            Pattern.OFFICIAL_DAYS -> Shift.REST
        }
    }

    /** First day of the cycle, or null for the fixed official-days pattern. */
    fun anchor(person: Person): LocalDate? {
        if (person.pattern == Pattern.OFFICIAL_DAYS) return null
        val start = person.startDate
        return start.plusDays(((person.startWeekday - persianWeekday(start) + 7) % 7).toLong())
    }

    fun status(person: Person, date: LocalDate): DayStatus {
        val anchor = anchor(effective(person, date))
        if (anchor != null && date.isBefore(anchor)) {
            return DayStatus(Shift.REST, false, false, 0, ChronoUnit.DAYS.between(date, anchor).toInt())
        }
        val current = shift(person, date)
        val working = current != Shift.REST
        var elapsed = 1
        var d = date.minusDays(1)
        while (elapsed < MAX_STREAK &&
            (anchor == null || !d.isBefore(anchor)) &&
            (shift(person, d) != Shift.REST) == working
        ) {
            elapsed++
            d = d.minusDays(1)
        }
        var toChange = 1
        d = date.plusDays(1)
        while (toChange < MAX_STREAK && (shift(person, d) != Shift.REST) == working) {
            toChange++
            d = d.plusDays(1)
        }
        val restWeek = if (!working && effective(person, date).pattern != Pattern.OFFICIAL_DAYS) (elapsed - 1) / 7 + 1 else 0
        return DayStatus(current, working, true, elapsed, toChange, restWeek)
    }

    fun isWorking(person: Person, date: LocalDate): Boolean = shift(person, date) != Shift.REST

    /** Days in [from]..[to] (inclusive) on which the person is not resting. */
    fun workingDays(person: Person, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Shift>> {
        val result = ArrayList<Pair<LocalDate, Shift>>()
        var d = from
        while (!d.isAfter(to)) {
            val s = shift(person, d)
            if (s != Shift.REST) result.add(d to s)
            d = d.plusDays(1)
        }
        return result
    }
}

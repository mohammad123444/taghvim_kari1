package ir.taghvimkari.app.domain

import java.time.LocalDate

/**
 * Moving people between shifts. Rewards, punishments and evaluations belong to the person (by id), so they
 * stay with him unchanged. Only the work calendar changes: dates before [from] keep the old schedule
 * (saved in [Person.history]); from [from] on the person follows the new shift.
 */
object Transfer {

    private fun closeOld(p: Person, from: LocalDate): List<ScheduleSegment> =
        p.history + ScheduleSegment(from, p.pattern, p.startDate, p.startWeekday, p.shiftName, p.leaderId)

    /**
     * Moves [person] alone to shift [shiftName]. If [leader] is given he joins that team and takes the leader's
     * schedule; otherwise the pattern stays and the cycle starts on [from].
     */
    fun move(person: Person, shiftName: String, leader: Person?, from: LocalDate): Person {
        val history = closeOld(person, from)
        return if (leader != null) {
            person.copy(
                shiftName = shiftName,
                leaderId = leader.id,
                pattern = leader.pattern,
                startDate = leader.startDate,
                startWeekday = leader.startWeekday,
                history = history
            )
        } else {
            person.copy(
                shiftName = shiftName,
                leaderId = null,
                startDate = from,
                startWeekday = ShiftEngine.persianWeekday(from),
                history = history
            )
        }
    }

    /** [a] and [b] exchange their shifts (and teams, and schedules) from [from] on. */
    fun swap(a: Person, b: Person, from: LocalDate): Pair<Person, Person> {
        val ha = closeOld(a, from)
        val hb = closeOld(b, from)
        // A leader keeps leading: only plain members are re-pointed to the other team.
        val na = a.copy(
            shiftName = b.shiftName, leaderId = b.leaderId, pattern = b.pattern,
            startDate = b.startDate, startWeekday = b.startWeekday, history = ha
        )
        val nb = b.copy(
            shiftName = a.shiftName, leaderId = a.leaderId, pattern = a.pattern,
            startDate = a.startDate, startWeekday = a.startWeekday, history = hb
        )
        return na to nb
    }
}

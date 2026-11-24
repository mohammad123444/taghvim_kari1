package ir.taghvimkari.app.domain

/**
 * The groups used in reports. Administrative staff only have two states (روزکار / استراحت);
 * operational staff are split into day, night and the first/second rest week.
 */
enum class Bucket(val title: String) {
    DAY("روزکار"),
    NIGHT("شب‌کار"),
    REST("استراحت"),
    REST_WEEK1("هفته اول استراحت"),
    REST_WEEK2("هفته دوم استراحت");

    val working: Boolean get() = this == DAY || this == NIGHT
}

/** Only patterns with a two-week rest are split into "first" and "second" rest week. */
val Pattern.hasTwoRestWeeks: Boolean
    get() = this == Pattern.DAY2_REST2 || this == Pattern.DAY_NIGHT_REST2

fun bucketOf(person: Person, status: DayStatus): Bucket {
    if (person.category == Category.ADMIN) {
        return if (status.working) Bucket.DAY else Bucket.REST
    }
    return when {
        status.working -> if (status.shift == Shift.NIGHT) Bucket.NIGHT else Bucket.DAY
        // Everyone else who is resting is simply "استراحت".
        !person.pattern.hasTwoRestWeeks -> Bucket.REST
        // Cycle not begun yet: 8..14 rest days left = first rest week, 1..7 left = second (last) one.
        !status.started -> if (status.daysToChange >= 8) Bucket.REST_WEEK1 else Bucket.REST_WEEK2
        status.restWeek == 1 -> Bucket.REST_WEEK1
        else -> Bucket.REST_WEEK2
    }
}

/**
 * Lists everywhere show a team together: the shift leader first, immediately followed by
 * his members. People without a team come last.
 */
fun <T> List<T>.sortedByTeam(leaderNames: Map<String, String>, person: (T) -> Person): List<T> =
    sortedWith(
        compareBy<T>(
            { val p = person(it); if (p.isLeader || p.leaderId != null) 0 else 1 },
            { val p = person(it); if (p.isLeader) p.name else (p.leaderId?.let { id -> leaderNames[id] } ?: p.name) },
            { val p = person(it); p.leaderId ?: p.id },
            { if (person(it).isLeader) 0 else 1 },
            { person(it).name }
        )
    )

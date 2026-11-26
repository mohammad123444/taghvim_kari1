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

/** Shift order everywhere: A:2.1, A:2.2, B:2.1 ... D:2.2, عادی کار, then people without a shift. */
val Person.shiftRank: Int get() = shiftOrder(shiftName)

/** 0 = administrative staff, 1 = a shift leader or a member of a shift leader, 2 = other operational people. */
val Person.listGroup: Int
    get() = when {
        category == Category.ADMIN -> 0
        isLeader || leaderId != null -> 1
        else -> 2
    }

/**
 * The order of people in every list of the app. Employment first (رسمی، قراردادی، پیمانکاری); inside each:
 * administrative staff, then shift leaders each followed by his members (shift A, B, C, D), then the other
 * operational people. [leaderNames] maps a leader's id to his name so a team is kept together.
 */
fun <T> List<T>.sortedByTeam(leaderNames: Map<String, String>, person: (T) -> Person): List<T> {
    fun teamName(p: Person) = if (p.isLeader) p.name else (p.leaderId?.let { leaderNames[it] } ?: p.name)
    return sortedWith(
        compareBy<T>({ person(it).employment.ordinal }, { person(it).listGroup }, { person(it).shiftRank })
            .thenComparator { a, b -> compareNames(teamName(person(a)), teamName(person(b))) }
            .thenBy { person(it).let { p -> p.leaderId ?: p.id } }
            .thenBy { if (person(it).isLeader) 0 else 1 }
            .thenComparator { a, b -> compareNames(person(a).name, person(b).name) }
    )
}

/** People in the standard display order (see [sortedByTeam]); the leaders are taken from the list itself. */
fun List<Person>.sortedByShiftName(): List<Person> {
    val leaderNames = filter { it.isLeader }.associate { it.id to it.name }
    return sortedByTeam(leaderNames) { it }
}

/** Position of every person in the standard display order, to sort things that belong to people (records...). */
fun List<Person>.displayOrder(): Map<String, Int> =
    sortedByShiftName().mapIndexed { index, p -> p.id to index }.toMap()

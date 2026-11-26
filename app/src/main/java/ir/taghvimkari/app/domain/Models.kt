package ir.taghvimkari.app.domain

import java.time.LocalDate

enum class Employment(val title: String) {
    OFFICIAL("رسمی"),
    CONTRACTUAL("قراردادی"),
    CONTRACTOR("پیمانکاری")
}

/** اداری / عملیاتی */
enum class Category(val title: String) {
    ADMIN("اداری"),
    OPERATIONAL("عملیاتی")
}

enum class Pattern(val title: String, val cycleWeeks: Int) {
    DAY2_REST1("۲ هفته روزکار، ۱ هفته استراحت", 3),
    DAY2_REST2("۲ هفته روزکار، ۲ هفته استراحت", 4),
    DAY_NIGHT_REST("۱ هفته روز، ۱ هفته شب، ۱ هفته استراحت", 3),
    WORK_REST("۱ هفته کار، ۱ هفته استراحت", 2),
    DAY_NIGHT_REST2("۱ هفته روز، ۱ هفته شب، ۲ هفته استراحت", 4),
    OFFICIAL_DAYS("ایام رسمی (شنبه تا چهارشنبه)", 0)
}

enum class Shift(val title: String) {
    DAY("روزکار"),
    NIGHT("شب‌کار"),
    WORK("کار"),
    REST("استراحت")
}

/** [startDate] is a Gregorian date internally; the UI shows it as Jalali. */
data class Person(
    val id: String,
    val name: String,
    val workType: String,
    val employment: Employment,
    val pattern: Pattern,
    val startDate: LocalDate,
    /** 0 = Saturday ... 6 = Friday. The weekday on which each cycle week begins. */
    val startWeekday: Int,
    val active: Boolean = true,
    /** True for a shift leader (سرشیفت). */
    val isLeader: Boolean = false,
    /** Id of the leader this person belongs to, if any. */
    val leaderId: String? = null,
    val category: Category = Category.OPERATIONAL,
    /** May evaluate other people (ارزیاب). Shift leaders are evaluators by default. */
    val canEvaluate: Boolean = false,
    /** Shift name, one of [SHIFT_NAMES] (empty = no shift). Saves up to 2.7.1 used the plain letters "A".."D". */
    val shiftName: String = "",
    /**
     * Earlier schedules, kept when the person was moved to another shift: each one was valid for dates
     * BEFORE [ScheduleSegment.until]. The current fields above are valid from the latest move on.
     */
    val history: List<ScheduleSegment> = emptyList()
)

/** The schedule a person had until (not including) [until]; used so a transfer does not rewrite the past. */
data class ScheduleSegment(
    val until: LocalDate,
    val pattern: Pattern,
    val startDate: LocalDate,
    val startWeekday: Int,
    val shiftName: String,
    val leaderId: String?
)

/** The shift names offered when registering a person. «A:2.1» = shift A, 2 weeks of work + 1 week of rest; «A:2.2» = 2 + 2. */
val SHIFT_NAMES: List<String> = listOf("A:2.1", "A:2.2", "B:2.1", "B:2.2", "C:2.1", "C:2.2", "D:2.2", "عادی کار")

/** The shift that is not a rotating one (shown without the word «شیفت» in front). */
const val ORDINARY_SHIFT = "عادی کار"

/** «شیفت A:2.1», «عادی کار», or «بدون شیفت» for an empty name. */
fun shiftTitle(name: String): String = when {
    name.isBlank() -> "بدون شیفت"
    name == ORDINARY_SHIFT -> name
    else -> "شیفت $name"
}

/**
 * Brings a stored shift name to the current names. Old saves have a plain letter: with a "2 weeks work + 1 week rest" pattern it
 * becomes «X:2.1», with "2 + 2" it becomes «X:2.2» (D has only 2.2). A letter that fits neither is kept as it is, so no
 * person loses the shift; anything else unknown is dropped.
 */
fun migrateShiftName(raw: String, pattern: Pattern): String {
    val s = raw.trim()
    if (s in SHIFT_NAMES) return s
    if (s.length == 1 && s[0] in 'A'..'D') {
        val suffix = when (pattern) {
            Pattern.DAY2_REST1 -> "2.1"
            Pattern.DAY2_REST2 -> "2.2"
            else -> null
        }
        val candidate = if (suffix == null) null else "$s:$suffix"
        return when {
            candidate != null && candidate in SHIFT_NAMES -> candidate
            s == "D" -> "D:2.2"
            else -> s
        }
    }
    return ""
}

/** True for a name that is not one of [SHIFT_NAMES] but is kept (an old plain letter). */
fun isLegacyShift(name: String): Boolean = name.isNotBlank() && name !in SHIFT_NAMES

/** Every shift to show as a group: the standard names, then any old plain letters still in use, then «no shift» (""). */
fun shiftGroups(used: Collection<String>): List<String> =
    SHIFT_NAMES + used.filter { isLegacyShift(it) }.distinct().sorted() + ""

/** Position in the shift order; an old plain letter sits with the standard names of the same letter. */
fun shiftOrder(name: String): Int {
    val exact = SHIFT_NAMES.indexOf(name)
    if (exact >= 0) return exact
    if (name.isBlank()) return SHIFT_NAMES.size + 1
    val byLetter = SHIFT_NAMES.indexOfFirst { it.startsWith(name + ":") }
    return if (byLetter >= 0) byLetter else SHIFT_NAMES.size
}

/** Single source of truth for work-type labels (they used to be built in three places with different digits). */
object WorkTypes {
    const val ALL = "همه"
    const val OFFICIAL_DAYS = "ایام رسمی"

    val numbered: List<String> = (1..8).map { "نوع کاری ${it.toPersian()}" }
    val options: List<String> = numbered + OFFICIAL_DAYS
    val default: String = numbered.first()

    /** Old saves mixed Latin and Persian digits; normalise so filters match. */
    fun normalize(raw: String): String = raw.toPersianDigits()
}

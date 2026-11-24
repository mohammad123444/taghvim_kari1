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
    val canEvaluate: Boolean = false
)

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

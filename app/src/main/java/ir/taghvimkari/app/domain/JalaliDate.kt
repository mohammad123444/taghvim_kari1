package ir.taghvimkari.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Jalali (Solar Hijri) date using the 33-year arithmetic rule.
 * Checked against known Nowruz dates for 1400-1405; the rule can differ by one day
 * from the officially announced calendar in some far-future years.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {

    fun toLocalDate(): LocalDate {
        val jy = year + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + day +
            (if (month < 7) (month - 1) * 31 else (month - 7) * 30 + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val lengths = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 11 && gd > lengths[gm]) {
            gd -= lengths[gm]
            gm++
        }
        return LocalDate.of(gy, gm + 1, gd)
    }

    /** 1405/08/27 with Latin digits. */
    fun format(): String = String.format(Locale.ROOT, "%04d/%02d/%02d", year, month, day)

    /** ۱۴۰۵/۰۸/۲۷ */
    fun display(): String = format().toPersianDigits()

    companion object {
        val MONTH_NAMES = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        /** Saturday-first, matching [ShiftEngine.persianWeekday]. */
        val WEEKDAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

        private val G_D_M = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

        fun from(date: LocalDate): JalaliDate {
            val gy = date.year
            val gm = date.monthValue
            val gd = date.dayOfMonth
            val gy2 = if (gm > 2) gy + 1 else gy
            var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + G_D_M[gm - 1]
            var jy = -1595 + 33 * (days / 12053)
            days %= 12053
            jy += 4 * (days / 1461)
            days %= 1461
            if (days > 365) {
                jy += (days - 1) / 365
                days = (days - 1) % 365
            }
            val jm: Int
            val jd: Int
            if (days < 186) {
                jm = 1 + days / 31
                jd = 1 + days % 31
            } else {
                jm = 7 + (days - 186) / 30
                jd = 1 + (days - 186) % 30
            }
            return JalaliDate(jy, jm, jd)
        }

        fun isLeapYear(year: Int): Boolean {
            val a = JalaliDate(year, 1, 1).toLocalDate()
            val b = JalaliDate(year + 1, 1, 1).toLocalDate()
            return ChronoUnit.DAYS.between(a, b) == 366L
        }

        fun monthLength(year: Int, month: Int): Int = when {
            month <= 6 -> 31
            month <= 11 -> 30
            else -> if (isLeapYear(year)) 30 else 29
        }

        /** Accepts 1405/8/27, 1405-08-27, ۱۴۰۵/۰۸/۲۷. Returns null for anything invalid. */
        fun parse(text: String): JalaliDate? {
            val parts = text.trim().toLatinDigits().split('/', '-', '.')
            if (parts.size != 3) return null
            val y = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().toIntOrNull() ?: return null
            val d = parts[2].trim().toIntOrNull() ?: return null
            if (y !in 1200..1700 || m !in 1..12) return null
            if (d !in 1..monthLength(y, m)) return null
            return JalaliDate(y, m, d)
        }

        /** e.g. «چهارشنبه ۲۷ آبان ۱۴۰۵» */
        fun longText(date: LocalDate): String {
            val j = from(date)
            val weekday = WEEKDAY_NAMES[ShiftEngine.persianWeekday(date)]
            return "$weekday ${j.day.toPersian()} ${MONTH_NAMES[j.month - 1]} ${j.year.toPersian()}"
        }
    }
}

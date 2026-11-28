package ir.taghvimkari.app.domain

import java.util.Locale

/**
 * The hand-typed part of one row of the جمع‌بندی table, for one person and one period:
 * [extra] is the third column (a number with a [comment] explaining it), [level] the fourth.
 */
data class SummaryCell(
    val personId: String,
    val periodKey: String,
    val extra: Double? = null,
    val comment: String = "",
    val level: Double? = null
) {
    val isEmpty: Boolean get() = extra == null && comment.isBlank() && level == null
}

/** Every number in the app is a whole number: 4 -> "۴", 38.4 -> "۳۸", -2.5 -> "−۳" (halves go away from zero). */
fun roundWhole(value: Double): Long =
    if (value < 0) -Math.round(-value) else Math.round(value)

/** Left-to-right embedding: inside Persian text the sign then stays on the left of the digits, as "−۳". */
private const val LTR_START = "\u202A"
private const val LTR_END = "\u202C"

fun formatNumber(value: Double): String {
    val whole = roundWhole(value)
    val digits = Math.abs(whole).toString().toPersianDigits()
    return if (whole < 0) "$LTR_START−$digits$LTR_END" else digits
}

/** Like [formatNumber] but a positive number gets a «+» (on the left of the digits too). */
fun formatSigned(value: Double): String =
    if (roundWhole(value) > 0) "$LTR_START+${formatNumber(value)}$LTR_END" else formatNumber(value)

/** Reads a number typed with Persian or Latin digits, with an optional minus; it is rounded to a whole number. Blank or invalid gives null. */
fun parseNumber(text: String): Double? {
    val clean = text.trim().toLatinDigits()
        .replace('٫', '.').replace('،', '.').replace(',', '.').replace('/', '.')
        .replace('−', '-').replace('–', '-')
    if (clean.isBlank()) return null
    return clean.toDoubleOrNull()?.let { roundWhole(it).toDouble() }
}

data class SummaryMerge(val result: List<SummaryCell>, val added: Int, val updated: Int, val unchanged: Int)

/** Adds received جمع‌بندی cells to [current] without deleting anything; the same person and period is overwritten. */
fun mergeSummary(current: List<SummaryCell>, incoming: List<SummaryCell>): SummaryMerge {
    val out = current.toMutableList()
    var added = 0
    var updated = 0
    var unchanged = 0
    for (c in incoming) {
        if (c.isEmpty) continue
        val index = out.indexOfFirst { it.personId == c.personId && it.periodKey == c.periodKey }
        if (index < 0) {
            out.add(c)
            added++
        } else if (out[index] == c) {
            unchanged++
        } else {
            out[index] = c
            updated++
        }
    }
    return SummaryMerge(out, added, updated, unchanged)
}

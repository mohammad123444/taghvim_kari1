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

/** 4 -> "۴", -2.5 -> "−۲٫۵" (at most two decimals). */
fun formatNumber(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    val text = if (rounded == Math.floor(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
    return text.toPersianDigits().replace('.', '٫').replace('-', '−')
}

/** Like [formatNumber] but a positive number gets a «+». */
fun formatSigned(value: Double): String = if (value > 0.0) "+" + formatNumber(value) else formatNumber(value)

/** Reads a number typed with Persian or Latin digits, with an optional minus; blank or invalid gives null. */
fun parseNumber(text: String): Double? {
    val clean = text.trim().toLatinDigits()
        .replace('٫', '.').replace('،', '.').replace(',', '.').replace('/', '.')
        .replace('−', '-').replace('–', '-')
    if (clean.isBlank()) return null
    return clean.toDoubleOrNull()
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

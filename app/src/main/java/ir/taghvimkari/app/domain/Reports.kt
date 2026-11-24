package ir.taghvimkari.app.domain

import java.time.LocalDate
import java.util.Locale

/** One reward / punishment report about one person. Hours are decimal (1.5 = one and a half hours). */
data class ReportEntry(
    val id: String,
    val personId: String,
    /** Name at the time of writing; used if the person was deleted later. */
    val personName: String,
    val date: LocalDate,
    val title: String,
    val rewardHours: Double,
    val punishmentHours: Double,
    val notes: String
)

data class ReportTotals(val count: Int, val reward: Double, val punishment: Double) {
    val net: Double get() = reward - punishment
}

fun List<ReportEntry>.totals(): ReportTotals =
    ReportTotals(size, sumOf { it.rewardHours }, sumOf { it.punishmentHours })

/** 2 -> "۲", 1.5 -> "۱٫۵" */
fun formatHours(hours: Double): String {
    val rounded = Math.round(hours * 10) / 10.0
    val text = if (rounded == Math.floor(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", rounded)
    }
    return text.toPersianDigits().replace('.', '٫')
}

/** Reads hours typed by the user with Persian or Latin digits and any common decimal separator. */
fun parseHours(text: String): Double {
    val clean = text.trim().toLatinDigits()
        .replace('٫', '.').replace('،', '.').replace(',', '.').replace('/', '.')
    return (clean.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100000.0)
}

/** A plain-text report that can be sent through any messenger. */
fun buildReportText(
    periodLabel: String,
    scopeLabel: String,
    entries: List<ReportEntry>,
    nameOf: (ReportEntry) -> String
): String {
    val all = entries.totals()
    return buildString {
        appendLine("گزارش تشویق و تنبیه")
        appendLine("بازه: $periodLabel")
        appendLine("نفرات: $scopeLabel")
        appendLine(
            "تعداد گزارش: ${all.count.toPersian()} • تشویق: ${formatHours(all.reward)} ساعت • " +
                "تنبیه: ${formatHours(all.punishment)} ساعت • خالص: ${formatHours(all.net)} ساعت"
        )
        val byPerson = entries.groupBy { nameOf(it) }
        if (byPerson.size > 1) {
            appendLine()
            appendLine("به تفکیک نفر:")
            byPerson.entries.sortedByDescending { e -> e.value.totals().net }.forEach { (name, list) ->
                val t = list.totals()
                appendLine("• $name: تشویق ${formatHours(t.reward)} • تنبیه ${formatHours(t.punishment)} • خالص ${formatHours(t.net)}")
            }
        }
        appendLine()
        appendLine("جزئیات:")
        entries.sortedBy { it.date }.forEach { e ->
            val parts = mutableListOf(JalaliDate.from(e.date).display(), nameOf(e), e.title)
            if (e.rewardHours > 0) parts.add("تشویق ${formatHours(e.rewardHours)} ساعت")
            if (e.punishmentHours > 0) parts.add("تنبیه ${formatHours(e.punishmentHours)} ساعت")
            if (e.notes.isNotBlank()) parts.add(e.notes.trim())
            appendLine("- " + parts.joinToString(" | "))
        }
    }.trimEnd()
}

/** Period used by the report filters. [from]/[to] are inclusive; null means unbounded. */
data class ReportPeriod(val label: String, val from: LocalDate?, val to: LocalDate?) {
    fun contains(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
}

data class ReportMerge(val result: List<ReportEntry>, val added: Int, val updated: Int, val unchanged: Int)

/** Adds received reports to [current] without deleting anything; the same id is overwritten by the received copy. */
fun mergeReports(current: List<ReportEntry>, incoming: List<ReportEntry>, people: List<Person>): ReportMerge {
    val ids = people.map { it.id }.toSet()
    val out = current.toMutableList()
    var added = 0
    var updated = 0
    var unchanged = 0
    for (raw in incoming) {
        val e = if (raw.personId in ids) raw else {
            val match = people.firstOrNull { raw.personName.isNotBlank() && normalizeFa(it.name) == normalizeFa(raw.personName) }
            if (match != null) raw.copy(personId = match.id) else raw
        }
        val index = out.indexOfFirst { it.id == e.id }
        if (index < 0) {
            out.add(e)
            added++
        } else if (out[index] == e) {
            unchanged++
        } else {
            out[index] = e
            updated++
        }
    }
    return ReportMerge(out, added, updated, unchanged)
}

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
    val notes: String,
    /** Who wrote the report (ثبت‌کننده). Together with the person and the date it identifies a report. */
    val reporter: String = "",
    /**
     * The reporter's own suggestion, kept ONLY when a superior changed it (نظر نهایی). [rewardHours] / [punishmentHours]
     * always hold the hours that count (the criterion): the suggestion until a superior decides differently, then his
     * hours. null = the suggestion was not changed (so [rewardHours] / [punishmentHours] still are the suggestion).
     */
    val suggestedReward: Double? = null,
    val suggestedPunishment: Double? = null,
    /** The superior who gave the final opinion; blank = nobody has decided yet, the suggestion counts. */
    val finalBy: String = "",
    /** The day the superior gave the final opinion; the hours count in the period of this day. null on old data: [date] is used. */
    val finalDate: LocalDate? = null
) {
    /** The day this report counts in the summary: the final-opinion day, or (still only a suggestion) its own date. */
    val countDate: LocalDate get() = if (hasFinal) (finalDate ?: date) else date

    /** A superior has given his final opinion (even if he agreed with the suggestion). */
    val hasFinal: Boolean get() = finalBy.isNotBlank()

    /** The superior changed the suggested hours. */
    val changedByFinal: Boolean get() = suggestedReward != null || suggestedPunishment != null

    /** The reporter's reward hours as suggested (before any final opinion). */
    val originalReward: Double get() = suggestedReward ?: rewardHours

    /** The reporter's punishment hours as suggested (before any final opinion). */
    val originalPunishment: Double get() = suggestedPunishment ?: punishmentHours
}

/**
 * The final opinion (نظر نهایی) of a superior: [reward] / [punishment] become the hours that count. The reporter's
 * suggestion is kept beside them when it differs; when the superior leaves a number as it was, the suggestion stays.
 */
fun ReportEntry.withFinal(by: String, reward: Double, punishment: Double, on: LocalDate = LocalDate.now()): ReportEntry {
    val oldReward = originalReward
    val oldPunishment = originalPunishment
    return copy(
        rewardHours = reward,
        punishmentHours = punishment,
        suggestedReward = if (reward != oldReward) oldReward else null,
        suggestedPunishment = if (punishment != oldPunishment) oldPunishment else null,
        finalBy = by.trim(),
        // a later correction of the same opinion keeps its day, so a closed month does not change
        finalDate = finalDate ?: on
    )
}

/** Takes the final opinion back: the reporter's suggestion counts again. */
fun ReportEntry.withoutFinal(): ReportEntry = copy(
    rewardHours = originalReward,
    punishmentHours = originalPunishment,
    suggestedReward = null,
    suggestedPunishment = null,
    finalBy = "",
    finalDate = null
)

data class ReportTotals(val count: Int, val reward: Double, val punishment: Double) {
    val net: Double get() = reward - punishment
}

fun List<ReportEntry>.totals(): ReportTotals =
    ReportTotals(size, sumOf { it.rewardHours }, sumOf { it.punishmentHours })

/** Only reports with a final opinion count in the summary; a report without one is just a suggestion. */
fun List<ReportEntry>.countedTotals(): ReportTotals = filter { it.hasFinal }.totals()

/** Hours are whole numbers too: 2 -> "۲", 1.5 -> "۲". */
fun formatHours(hours: Double): String = formatNumber(hours)

/** Reads hours typed by the user with Persian or Latin digits; a decimal part is rounded to a whole number. */
fun parseHours(text: String): Double {
    val clean = text.trim().toLatinDigits()
        .replace('٫', '.').replace('،', '.').replace(',', '.').replace('/', '.')
    return roundWhole((clean.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100000.0)).toDouble()
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
        appendLine("سوابق تشویق و تنبیه")
        appendLine("بازه: $periodLabel")
        appendLine("نفرات: $scopeLabel")
        appendLine(
            "تعداد سوابق: ${all.count.toPersian()} • تشویق: ${formatHours(all.reward)} ساعت • " +
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
            if (e.reporter.isNotBlank()) parts.add("ثبت‌کننده: ${e.reporter.trim()}")
            if (e.rewardHours > 0) parts.add("تشویق ${formatHours(e.rewardHours)} ساعت")
            if (e.punishmentHours > 0) parts.add("تنبیه ${formatHours(e.punishmentHours)} ساعت")
            if (e.hasFinal) {
                parts.add(
                    "نظر نهایی: ${e.finalBy.trim()}" + if (e.changedByFinal) {
                        " (پیشنهاد اولیه: تشویق ${formatHours(e.originalReward)} • تنبیه ${formatHours(e.originalPunishment)})"
                    } else {
                        " (مطابق پیشنهاد)"
                    }
                )
            }
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

/**
 * Result of receiving reports. [added] were registered; [duplicates] were NOT: the same reporter had already
 * reported the same person on the same date (in the current data, or earlier in the same file).
 */
data class ReportMerge(
    val result: List<ReportEntry>,
    val added: List<ReportEntry>,
    val duplicates: List<ReportEntry>,
    /**
     * Existing reports that had no final opinion yet and got the one a received copy carries (نظر نهایی). Only the
     * final opinion is taken over: nothing else of the existing report changes. They are NOT in [duplicates].
     */
    val decided: List<ReportEntry> = emptyList()
)

/**
 * Adds received reports to [current] without deleting or changing anything. A received report is a duplicate, and is
 * never registered, when reporter + person + date are all the same as an existing (or earlier received) report;
 * if any of the three differs it is registered.
 */
fun mergeReports(current: List<ReportEntry>, incoming: List<ReportEntry>, people: List<Person>): ReportMerge {
    val who = PersonResolver(people)
    fun keyOf(e: ReportEntry) = Triple(normalizeFa(e.reporter), who.key(e.personId, e.personName), e.date)
    val seen = current.map(::keyOf).toHashSet()
    val usedIds = current.map { it.id }.toHashSet()
    val out = current.toMutableList()
    val added = mutableListOf<ReportEntry>()
    val duplicates = mutableListOf<ReportEntry>()
    val decided = mutableListOf<ReportEntry>()
    for (raw in incoming) {
        val e = raw.copy(personId = who.resolve(raw.personId, raw.personName))
        if (!seen.add(keyOf(e))) {
            // The same report that came back from a superior: take over his final opinion if this phone has none yet.
            val at = out.indexOfFirst { keyOf(it) == keyOf(e) }
            val mine = out.getOrNull(at)
            if (mine != null && !mine.hasFinal && e.hasFinal) {
                val updated = mine.copy(
                    rewardHours = e.rewardHours,
                    punishmentHours = e.punishmentHours,
                    suggestedReward = e.suggestedReward,
                    suggestedPunishment = e.suggestedPunishment,
                    finalBy = e.finalBy,
                    finalDate = e.finalDate
                )
                out[at] = updated
                decided.add(updated)
            } else {
                duplicates.add(raw)
            }
            continue
        }
        // A different report may carry an id that is already used here: it gets its own id.
        val saved = if (e.id in usedIds) e.copy(id = java.util.UUID.randomUUID().toString()) else e
        usedIds.add(saved.id)
        out.add(saved)
        added.add(saved)
    }
    return ReportMerge(out, added, duplicates, decided)
}

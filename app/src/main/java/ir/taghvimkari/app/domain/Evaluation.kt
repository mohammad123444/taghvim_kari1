package ir.taghvimkari.app.domain

import java.time.LocalDate
import java.util.Locale

/** The ارزیابی items. Each one is scored 1..5. */
object EvaluationItems {
    val all: List<String> = listOf(
        "شناخت کار",
        "سرعت انجام کار",
        "صحت و دقت",
        "کار تیمی",
        "خودآموزی",
        "خودکاری",
        "مدیریت زمان",
        "بهداشت و ایمنی",
        "تناسب پوشش",
        "فعالیت‌های اجتماعی",
        "انگیزه و پشتکار",
        "مسئولیت‌پذیری",
        "تفکر تحلیلی",
        "قدرت یادگیری",
        "انعطاف‌پذیری",
        "ابتکار",
        "قدرت تصمیم‌گیری",
        "انتقادپذیری",
        "خویشتن‌داری",
        "حسن رفتار",
        "قابلیت اعتماد",
        "نفوذ و تأثیرگذاری",
        "نگرش سیستمی"
    )
    const val MIN_SCORE = 1
    const val MAX_SCORE = 5

    /** Final score is (sum × 45) ÷ 115, i.e. out of 45. */
    const val FINAL_MAX = 45
    val count: Int get() = all.size
    val maxTotal: Int get() = all.size * MAX_SCORE
}

/** One evaluation of one person by an evaluator. [scores] has one value (1..5) per item, in item order. */
data class EvaluationEntry(
    val id: String,
    val personId: String,
    /** Name at the time of evaluation; used if the person is deleted later. */
    val personName: String,
    val evaluatorId: String,
    val evaluatorName: String,
    /** First day of the evaluation period (دورهٔ ارزیابی). */
    val date: LocalDate,
    val scores: List<Int>,
    /** Last day of the evaluation period; old saves had a single date, which is both start and end. */
    val dateTo: LocalDate = date,
    /** Why this evaluation replaced an earlier one (filled when a received file replaced a similar evaluation). */
    val note: String = ""
) {
    /** Identifies the evaluation period, e.g. to send only one period. */
    val periodKey: String get() = periodKeyOf(date, dateTo)

    val total: Int get() = scores.sum()
    val finalScore: Double get() = Evaluations.finalScore(total)

    /** Items already scored (0 means "not scored yet"). */
    val doneCount: Int get() = scores.count { it > 0 }

    /** An evaluation with unscored items is a draft that can be continued later. */
    val isComplete: Boolean get() = scores.size == EvaluationItems.count && scores.all { it > 0 }
}

fun periodKeyOf(from: LocalDate, to: LocalDate): String = "$from|$to"

/** "از ۱۴۰۵/۰۸/۰۱ تا ۱۴۰۵/۰۸/۳۰" (or just the date when the period is a single day). */
fun periodText(from: LocalDate, to: LocalDate): String =
    if (from == to) JalaliDate.from(from).display()
    else "از ${JalaliDate.from(from).display()} تا ${JalaliDate.from(to).display()}"

fun EvaluationEntry.periodText(): String = periodText(date, dateTo)

/** The distinct evaluation periods found in [entries], newest first. */
fun evaluationPeriods(entries: List<EvaluationEntry>): List<Pair<LocalDate, LocalDate>> =
    entries.map { it.date to it.dateTo }.distinct()
        .sortedWith(compareByDescending<Pair<LocalDate, LocalDate>> { it.first }.thenByDescending { it.second })

enum class EvaluationLevel(val title: String, val minFraction: Double) {
    EXCELLENT("عالی", 0.90),
    VERY_GOOD("خیلی خوب", 0.80),
    GOOD("خوب", 0.70),
    FAIR("قابل قبول", 0.50),
    WEAK("نیازمند بهبود", 0.0)
}

object Evaluations {
    /** Sum of all item scores × 45 ÷ 115 (the 115 is 23 items × 5). */
    fun finalScore(total: Int): Double =
        (total * EvaluationItems.FINAL_MAX).toDouble() / EvaluationItems.maxTotal

    fun levelOf(finalScore: Double): EvaluationLevel {
        val fraction = finalScore / EvaluationItems.FINAL_MAX
        // never throws: a NaN or negative score is simply the lowest level
        return EvaluationLevel.values().firstOrNull { fraction + 1e-9 >= it.minFraction } ?: EvaluationLevel.WEAK
    }

    /** Average final score of the complete evaluations only; drafts are ignored. */
    fun average(entries: List<EvaluationEntry>): Double {
        val done = entries.filter { it.isComplete }
        return if (done.isEmpty()) 0.0 else done.sumOf { it.finalScore } / done.size
    }
}

/**
 * Result of receiving evaluations. [added] were registered; [duplicates] were NOT: the same evaluator had already
 * evaluated the same person for the same period (in the current data, or earlier in the same file).
 */
data class EvaluationMerge(
    val result: List<EvaluationEntry>,
    val added: List<EvaluationEntry>,
    val duplicates: List<EvaluationEntry>
)

/**
 * Adds received evaluations to [current] without deleting or changing anything. A received evaluation is a
 * duplicate, and is never registered, when evaluator + person + period (from / to date) are all the same as an
 * existing (or earlier received) one; if any of them differs it is registered.
 */
fun mergeEvaluations(current: List<EvaluationEntry>, incoming: List<EvaluationEntry>, people: List<Person>): EvaluationMerge {
    val who = PersonResolver(people)
    fun keyOf(e: EvaluationEntry) = listOf(
        who.key(e.evaluatorId, e.evaluatorName), who.key(e.personId, e.personName), e.date, e.dateTo
    )
    val seen = current.map(::keyOf).toHashSet()
    val usedIds = current.map { it.id }.toHashSet()
    val out = current.toMutableList()
    val added = mutableListOf<EvaluationEntry>()
    val duplicates = mutableListOf<EvaluationEntry>()
    for (raw in incoming) {
        val e = raw.copy(
            personId = who.resolve(raw.personId, raw.personName),
            evaluatorId = who.resolve(raw.evaluatorId, raw.evaluatorName)
        )
        if (!seen.add(keyOf(e))) {
            duplicates.add(raw)
            continue
        }
        val saved = if (e.id in usedIds) e.copy(id = java.util.UUID.randomUUID().toString()) else e
        usedIds.add(saved.id)
        out.add(saved)
        added.add(saved)
    }
    return EvaluationMerge(out, added, duplicates)
}

/** 38.347 -> "۳۸" (scores are shown as whole numbers) */
fun formatScore(value: Double): String = formatNumber(value)

/** A plain-text evaluation that can be sent through any messenger. */
fun buildEvaluationText(entry: EvaluationEntry, nameOf: String): String = buildString {
    appendLine("ارزیابی $nameOf")
    appendLine("دورهٔ ارزیابی: ${entry.periodText()}")
    appendLine("ارزیاب: ${entry.evaluatorName}")
    appendLine()
    EvaluationItems.all.forEachIndexed { i, title ->
        val score = entry.scores.getOrElse(i) { 0 }
        appendLine("${(i + 1).toPersian()}. $title: ${if (score > 0) score.toPersian() else "—"}")
    }
    appendLine()
    if (!entry.isComplete) {
        append("ارزیابی ناتمام: ${entry.doneCount.toPersian()} از ${EvaluationItems.count.toPersian()} آیتم نمره گرفته است.")
        return@buildString
    }
    appendLine("جمع نمرات: ${entry.total.toPersian()} از ${EvaluationItems.maxTotal.toPersian()}")
    appendLine("جمع × ${EvaluationItems.FINAL_MAX.toPersian()} = ${(entry.total * EvaluationItems.FINAL_MAX).toPersian()}")
    appendLine("تقسیم بر ${EvaluationItems.maxTotal.toPersian()} = ${formatScore(entry.finalScore)}")
    append("نمرهٔ نهایی: ${formatScore(entry.finalScore)} از ${EvaluationItems.FINAL_MAX.toPersian()} (${Evaluations.levelOf(entry.finalScore).title})")
}

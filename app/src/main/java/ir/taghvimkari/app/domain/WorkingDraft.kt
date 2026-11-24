package ir.taghvimkari.app.domain

/**
 * The evaluation round the user is in the middle of ("score these people for this period").
 * It is saved on the phone after every tap, so leaving the tab or closing the app loses nothing.
 * [scores] are the not-yet-saved scores of the person at [index], one digit per item ('0' = not scored);
 * blank means "nothing typed yet" (the form then starts from the person's saved draft, if any).
 */
data class WorkingDraft(
    val evaluatorId: String,
    val fromEpoch: Long,
    val toEpoch: Long,
    val personIds: List<String>,
    val index: Int = 0,
    val scores: String = ""
) {
    val currentPersonId: String? get() = personIds.getOrNull(index)
    val remaining: Int get() = (personIds.size - index).coerceAtLeast(0)
    val isLast: Boolean get() = index + 1 >= personIds.size

    /** Moves on to the next person; null when this was the last one. */
    fun advanced(): WorkingDraft? = if (isLast) null else copy(index = index + 1, scores = "")

    /** Scores typed so far for the current person, or null when none were typed. */
    fun typedScores(count: Int): List<Int>? =
        scores.takeIf { it.length == count && it.all { c -> c in '0'..'5' } }?.map { it - '0' }

    /** One line, e.g. for the settings of the store. Ids are UUIDs, so '|' and ',' cannot appear in them. */
    fun encode(): String = listOf(evaluatorId, fromEpoch, toEpoch, personIds.joinToString(","), index, scores).joinToString("|")

    companion object {
        fun decode(text: String?): WorkingDraft? {
            val parts = text?.split("|") ?: return null
            if (parts.size != 6) return null
            val from = parts[1].toLongOrNull() ?: return null
            val to = parts[2].toLongOrNull() ?: return null
            val index = parts[4].toIntOrNull() ?: return null
            val ids = parts[3].split(",").filter { it.isNotBlank() }
            if (parts[0].isBlank() || ids.isEmpty() || index !in ids.indices) return null
            return WorkingDraft(parts[0], from, maxOf(from, to), ids, index, parts[5])
        }
    }
}

/** The latest complete evaluation of [personId] by [evaluatorId] whose period ended before [before] (to copy from). */
fun previousEvaluation(all: List<EvaluationEntry>, personId: String, evaluatorId: String, before: java.time.LocalDate): EvaluationEntry? =
    all.filter { it.personId == personId && it.evaluatorId == evaluatorId && it.isComplete && it.dateTo.isBefore(before) }
        .maxWithOrNull(compareBy<EvaluationEntry> { it.dateTo }.thenBy { it.date })

/** Puts [item] back where it was ([index] is clamped), for "undo delete". Does nothing if an equal id is already there. */
fun <T> restoreAt(list: List<T>, item: T, index: Int, sameAs: (T) -> Boolean): List<T> =
    if (list.any(sameAs)) list else list.toMutableList().also { it.add(index.coerceIn(0, it.size), item) }

/** Score string after applying one tap: item [i] gets [value]; the next item to look at is [nextFocus]. */
fun setScoreAt(scoreText: String, i: Int, value: Int): String = scoreText.substring(0, i) + value + scoreText.substring(i + 1)

fun nextFocus(i: Int, count: Int): Int = (i + 1).coerceAtMost(count - 1)

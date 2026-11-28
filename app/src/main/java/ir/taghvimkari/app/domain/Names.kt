package ir.taghvimkari.app.domain

import java.text.Collator
import java.util.Locale

/** Makes Arabic/Persian letter variants equal so searching "علي" finds "علی". */
fun normalizeFa(text: String): String = buildString {
    for (c in text) {
        when (c) {
            'ي', 'ى' -> append('ی')
            'ك' -> append('ک')
            '\u200c' -> append(' ')
            in '\u064B'..'\u065F' -> Unit // Arabic diacritics
            else -> append(c.lowercaseChar())
        }
    }
}.trim()

private val persianCollator: Collator by lazy {
    Collator.getInstance(Locale.forLanguageTag("fa")).apply { strength = Collator.SECONDARY }
}

/** Alphabetical comparison of two Persian names. */
fun compareNames(a: String, b: String): Int = persianCollator.compare(normalizeFa(a), normalizeFa(b))

/** Sorts by name in Persian alphabetical order. */
fun <T> List<T>.sortedAlphabetically(name: (T) -> String): List<T> =
    sortedWith { x, y -> compareNames(name(x), name(y)) }

/** A name search finds nothing until at least this many characters are typed. */
const val MIN_SEARCH_CHARS = 3

/**
 * True when [name] matches the typed [query]. A blank query matches everybody (no filter);
 * a query shorter than [MIN_SEARCH_CHARS] matches nobody.
 */
fun nameMatches(name: String, query: String): Boolean {
    val q = normalizeFa(query)
    if (q.isBlank()) return true
    if (q.length < MIN_SEARCH_CHARS) return false
    return normalizeFa(name).contains(q)
}

/** Hint shown while the typed text is too short to search. */
fun searchTooShort(query: String): Boolean {
    val q = normalizeFa(query)
    return q.isNotBlank() && q.length < MIN_SEARCH_CHARS
}

/**
 * Removes duplicate people from a received staff list (same id, or same name + shift + employment).
 * Returns the kept list and the names that were left out; leaders of removed duplicates are re-pointed.
 */
fun dedupePeople(list: List<Person>): Pair<List<Person>, List<String>> {
    val seenIds = HashSet<String>()
    val seenNumbers = HashMap<String, String>()
    val keyToId = HashMap<String, String>()
    val remap = HashMap<String, String>()
    val kept = ArrayList<Person>()
    val dropped = ArrayList<String>()
    for (p in list) {
        val key = normalizeFa(p.name) + "|" + p.shiftName + "|" + p.employment.name
        val sameKeyId = keyToId[key]
        if (!seenIds.add(p.id)) {
            dropped.add(p.name)
        } else if (sameKeyId != null) {
            remap[p.id] = sameKeyId
            dropped.add(p.name)
        } else {
            val number = normalizePersonnel(p.personnelNumber)
            val sameNumberId = if (number.isEmpty()) null else seenNumbers[number]
            if (sameNumberId != null) {
                // the same personnel number is a second registration of the same person
                remap[p.id] = sameNumberId
                dropped.add("${p.name} (شماره پرسنلی ${number.toPersianDigits()})")
            } else {
                if (number.isNotEmpty()) seenNumbers[number] = p.id
                keyToId[key] = p.id
                kept.add(p)
            }
        }
    }
    val fixed = kept.map { p -> p.leaderId?.let { id -> remap[id] }?.let { p.copy(leaderId = it) } ?: p }
    return fixed to dropped
}

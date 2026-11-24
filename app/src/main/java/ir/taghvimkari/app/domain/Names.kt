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

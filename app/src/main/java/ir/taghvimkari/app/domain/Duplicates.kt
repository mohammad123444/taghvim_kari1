package ir.taghvimkari.app.domain

/**
 * Tells who a received record is about / was written by, on THIS phone. A person is found by his id; when the id is
 * unknown here (the file came from another phone) by his name. Used to decide whether a received record is a duplicate.
 */
class PersonResolver(people: List<Person>) {
    private val list = people
    private val ids = people.map { it.id }.toSet()

    /** The id of the person on this phone, or [id] itself when nobody matches. */
    fun resolve(id: String, name: String): String =
        if (id in ids) id
        else list.firstOrNull { name.isNotBlank() && normalizeFa(it.name) == normalizeFa(name) }?.id ?: id

    /**
     * Same value for the same person whichever phone the record comes from. A person who is not in the staff list
     * (and was not matched by name) is told apart by his id, so two different people with the same name are never
     * mistaken for one.
     */
    fun key(id: String, name: String): String = resolve(id, name).let { if (it in ids) it else "id:$it" }
}

/**
 * A personnel number (شماره پرسنلی) the way it is stored and compared: Persian / Arabic digits become Latin digits and
 * all spaces and zero-width characters are removed, so «۱۲۳ ۴۵» and «12345» are the same number.
 */
fun normalizePersonnel(raw: String): String =
    raw.toLatinDigits().filterNot { it.isWhitespace() || it == '\u200c' || it == '\u200f' || it == '\u200e' }

/**
 * One personnel number that is already taken, found while registering people. [index] is the position of the
 * candidate in the list that was checked; [owner] is the name of the person who already has the number.
 */
data class PersonnelConflict(val index: Int, val number: String, val owner: String)

/**
 * Checks the personnel numbers of [candidates] (name to number) against [existing] people and against each other.
 * A person with [exceptId] (the one being edited) is not counted as an owner. Blank numbers are never a conflict.
 * Returns one entry per candidate whose number is taken: the number and the name of the person who has it.
 */
fun personnelConflicts(
    existing: List<Person>,
    candidates: List<Pair<String, String>>,
    exceptId: String? = null
): List<PersonnelConflict> {
    val owners = HashMap<String, String>()
    for (p in existing) {
        val n = normalizePersonnel(p.personnelNumber)
        if (n.isNotEmpty() && p.id != exceptId) owners.putIfAbsent(n, p.name)
    }
    val out = ArrayList<PersonnelConflict>()
    candidates.forEachIndexed { index, (name, raw) ->
        val n = normalizePersonnel(raw)
        if (n.isEmpty()) return@forEachIndexed
        val owner = owners[n]
        if (owner != null) out.add(PersonnelConflict(index, n, owner)) else owners[n] = name
    }
    return out
}

/**
 * Splits one line of the «افراد زیرمجموعه» box into the name and the personnel number: a last word made only of digits
 * is the number («علی رضایی ۱۲۳۴۵»). A line with no such word has no number.
 */
fun splitNameAndNumber(line: String): Pair<String, String> {
    val text = line.trim()
    val cut = text.lastIndexOfAny(charArrayOf(' ', '\t', '\u200c', '-', ':', '،', ','))
    if (cut < 0) return text to ""
    val last = text.substring(cut + 1)
    val latin = last.toLatinDigits()
    return if (latin.isNotEmpty() && latin.all { it in '0'..'9' }) {
        text.substring(0, cut).trim().trimEnd('-', ':', '،', ',').trim() to normalizePersonnel(last)
    } else {
        text to ""
    }
}

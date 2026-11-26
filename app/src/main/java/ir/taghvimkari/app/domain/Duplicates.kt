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

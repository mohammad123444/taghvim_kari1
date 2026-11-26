package ir.taghvimkari.app.data

import android.content.Context
import ir.taghvimkari.app.domain.Category
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.Pattern
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.migrateShiftName
import ir.taghvimkari.app.domain.ScheduleSegment
import ir.taghvimkari.app.domain.WorkTypes
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** SharedPreferences-backed storage. The JSON format is backward compatible with all earlier versions. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Person> {
        val array = runCatching { JSONArray(prefs.getString(KEY_PEOPLE, "[]")) }.getOrElse { JSONArray() }
        return decode(array)
    }

    fun save(people: List<Person>) {
        prefs.edit().putString(KEY_PEOPLE, encode(people).toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taghvim_kari"
        private const val KEY_PEOPLE = "people"

        fun decode(array: JSONArray): List<Person> = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val pattern = Pattern.valueOf(o.optString("pattern", Pattern.DAY_NIGHT_REST.name))
                val leader = o.optBoolean("isLeader", false)
                Person(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    workType = WorkTypes.normalize(o.optString("workType", WorkTypes.default)),
                    employment = Employment.valueOf(o.optString("employment", Employment.OFFICIAL.name)),
                    pattern = pattern,
                    startDate = LocalDate.parse(o.getString("startDate")),
                    startWeekday = o.optInt("startWeekday", 0).coerceIn(0, 6),
                    active = o.optBoolean("active", true),
                    isLeader = leader,
                    leaderId = o.optString("leaderId", "").ifBlank { null },
                    // Older saves have no category: fixed office days mean administrative, shifts mean operational.
                    category = runCatching { Category.valueOf(o.getString("category")) }.getOrElse {
                        if (pattern == Pattern.OFFICIAL_DAYS) Category.ADMIN else Category.OPERATIONAL
                    },
                    // Older saves have no such field: shift leaders were the evaluators.
                    canEvaluate = o.optBoolean("canEvaluate", leader),
                    shiftName = migrateShiftName(o.optString("shiftName", ""), pattern),
                    history = decodeHistory(o.optJSONArray("history"))
                )
            }.getOrNull()
        }

        private fun decodeHistory(array: JSONArray?): List<ScheduleSegment> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val o = array.getJSONObject(i)
                    val segPattern = Pattern.valueOf(o.getString("pattern"))
                    ScheduleSegment(
                        until = LocalDate.parse(o.getString("until")),
                        pattern = segPattern,
                        startDate = LocalDate.parse(o.getString("startDate")),
                        startWeekday = o.optInt("startWeekday", 0).coerceIn(0, 6),
                        shiftName = migrateShiftName(o.optString("shiftName", ""), segPattern),
                        leaderId = o.optString("leaderId", "").ifBlank { null }
                    )
                }.getOrNull()
            }
        }

        private fun encodeHistory(list: List<ScheduleSegment>): JSONArray = JSONArray().also { array ->
            list.forEach { h ->
                array.put(
                    JSONObject().apply {
                        put("until", h.until.toString())
                        put("pattern", h.pattern.name)
                        put("startDate", h.startDate.toString())
                        put("startWeekday", h.startWeekday)
                        put("shiftName", h.shiftName)
                        put("leaderId", h.leaderId ?: "")
                    }
                )
            }
        }

        fun encode(people: List<Person>): JSONArray {
            val array = JSONArray()
            people.forEach { p ->
                array.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("workType", p.workType)
                        put("employment", p.employment.name)
                        put("pattern", p.pattern.name)
                        put("startDate", p.startDate.toString())
                        put("startWeekday", p.startWeekday)
                        put("active", p.active)
                        put("isLeader", p.isLeader)
                        put("leaderId", p.leaderId ?: "")
                        put("category", p.category.name)
                        put("canEvaluate", p.canEvaluate)
                        put("shiftName", p.shiftName)
                        put("history", encodeHistory(p.history))
                    }
                )
            }
            return array
        }
    }
}

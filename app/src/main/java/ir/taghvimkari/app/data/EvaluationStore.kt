package ir.taghvimkari.app.data

import android.content.Context
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.EvaluationItems
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** SharedPreferences-backed storage of evaluations (ارزیابی). */
class EvaluationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<EvaluationEntry> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return decode(array)
    }

    fun save(entries: List<EvaluationEntry>) {
        prefs.edit().putString(KEY, encode(entries).toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taghvim_kari_evaluations"
        private const val KEY = "evaluations"

        fun encode(entries: List<EvaluationEntry>): JSONArray = JSONArray().also { array ->
            entries.forEach { e ->
                array.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("personId", e.personId)
                        put("personName", e.personName)
                        put("evaluatorId", e.evaluatorId)
                        put("evaluatorName", e.evaluatorName)
                        put("date", e.date.toString())
                        put("dateTo", e.dateTo.toString())
                        put("scores", JSONArray(e.scores))
                        put("note", e.note)
                    }
                )
            }
        }

        fun decode(array: JSONArray): List<EvaluationEntry> = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val raw = o.getJSONArray("scores")
                val from = LocalDate.parse(o.getString("date"))
                // Older saves have no "dateTo": the single date is the whole period.
                val to = o.optString("dateTo", "").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }?.takeIf { !it.isBefore(from) } ?: from
                EvaluationEntry(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    personName = o.optString("personName", ""),
                    evaluatorId = o.optString("evaluatorId", ""),
                    evaluatorName = o.optString("evaluatorName", ""),
                    date = from,
                    dateTo = to,
                    note = o.optString("note", ""),
                    scores = List(EvaluationItems.count) {
                        // 0 = not scored yet (draft)
                        raw.optInt(it, 0).coerceIn(0, EvaluationItems.MAX_SCORE)
                    }
                )
            }.getOrNull()
        }
    }
}

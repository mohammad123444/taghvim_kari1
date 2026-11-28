package ir.taghvimkari.app.data

import android.content.Context
import ir.taghvimkari.app.domain.ReportEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** SharedPreferences-backed storage of reward / punishment reports. */
class ReportStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ReportEntry> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return decode(array)
    }

    fun save(reports: List<ReportEntry>) {
        prefs.edit().putString(KEY, encode(reports).toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "taghvim_kari_reports"
        private const val KEY = "reports"

        fun encode(reports: List<ReportEntry>): JSONArray = JSONArray().also { array ->
            reports.forEach { r ->
                array.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("personId", r.personId)
                        put("personName", r.personName)
                        put("date", r.date.toString())
                        put("title", r.title)
                        put("rewardHours", r.rewardHours)
                        put("punishmentHours", r.punishmentHours)
                        put("notes", r.notes)
                        put("reporter", r.reporter)
                        r.suggestedReward?.let { put("suggestedReward", it) }
                        r.suggestedPunishment?.let { put("suggestedPunishment", it) }
                        put("finalBy", r.finalBy)
                    }
                )
            }
        }

        fun decode(array: JSONArray): List<ReportEntry> = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                ReportEntry(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    personName = o.optString("personName", ""),
                    date = LocalDate.parse(o.getString("date")),
                    title = o.optString("title", ""),
                    rewardHours = o.optDouble("rewardHours", 0.0),
                    punishmentHours = o.optDouble("punishmentHours", 0.0),
                    notes = o.optString("notes", ""),
                    reporter = o.optString("reporter", ""),
                    suggestedReward = if (o.has("suggestedReward")) o.optDouble("suggestedReward") else null,
                    suggestedPunishment = if (o.has("suggestedPunishment")) o.optDouble("suggestedPunishment") else null,
                    finalBy = o.optString("finalBy", "")
                )
            }.getOrNull()
        }
    }
}

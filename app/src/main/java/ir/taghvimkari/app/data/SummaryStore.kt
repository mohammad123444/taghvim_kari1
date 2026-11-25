package ir.taghvimkari.app.data

import android.content.Context
import ir.taghvimkari.app.domain.SummaryCell
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences-backed storage of the hand-typed cells of the جمع‌بندی table. */
class SummaryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<SummaryCell> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                SummaryCell(
                    personId = o.getString("personId"),
                    periodKey = o.getString("periodKey"),
                    extra = if (o.has("extra") && !o.isNull("extra")) o.getDouble("extra") else null,
                    comment = o.optString("comment", ""),
                    level = if (o.has("level") && !o.isNull("level")) o.getDouble("level") else null
                )
            }.getOrNull()
        }
    }

    fun save(cells: List<SummaryCell>) {
        val array = JSONArray()
        cells.forEach { c ->
            array.put(
                JSONObject().apply {
                    put("personId", c.personId)
                    put("periodKey", c.periodKey)
                    c.extra?.let { put("extra", it) }
                    put("comment", c.comment)
                    c.level?.let { put("level", it) }
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "taghvim_kari_summary"
        const val KEY = "cells"
    }
}

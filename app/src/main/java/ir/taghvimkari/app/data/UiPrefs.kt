package ir.taghvimkari.app.data

import android.content.Context
import ir.taghvimkari.app.domain.TextSize
import ir.taghvimkari.app.domain.WorkingDraft

/** Small settings kept on the phone: the text size and the evaluation round in progress (the working draft). */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("taghvim_kari_ui", Context.MODE_PRIVATE)

    var textSize: TextSize
        get() = TextSize.fromId(prefs.getString(KEY_SIZE, null))
        set(value) { prefs.edit().putString(KEY_SIZE, value.id).apply() }

    var workingDraft: WorkingDraft?
        get() = WorkingDraft.decode(prefs.getString(KEY_DRAFT, null))
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_DRAFT) else putString(KEY_DRAFT, value.encode()) }.apply() }

    private companion object {
        const val KEY_SIZE = "textSize"
        const val KEY_DRAFT = "workingDraft"
    }
}

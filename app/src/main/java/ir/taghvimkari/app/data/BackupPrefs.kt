package ir.taghvimkari.app.data

import android.content.Context

/** When the last backup was sent and until when the monthly reminder is hidden (both as epoch days). */
class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("taghvim_kari_backup", Context.MODE_PRIVATE)

    var lastBackupDay: Long?
        get() = prefs.getLong(KEY_LAST, -1L).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_LAST) else putLong(KEY_LAST, value) }.apply() }

    var snoozedUntilDay: Long?
        get() = prefs.getLong(KEY_SNOOZE, -1L).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_SNOOZE) else putLong(KEY_SNOOZE, value) }.apply() }

    private companion object {
        const val KEY_LAST = "last"
        const val KEY_SNOOZE = "snooze"
    }
}

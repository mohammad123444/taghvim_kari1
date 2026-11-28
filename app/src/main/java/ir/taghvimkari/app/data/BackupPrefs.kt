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

    /** The folder (a document-tree uri) chosen for the automatic weekly backup, or null when it is off. */
    var autoFolder: String?
        get() = prefs.getString(KEY_AUTO_FOLDER, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_AUTO_FOLDER) else putString(KEY_AUTO_FOLDER, value) }.apply() }

    /** Epoch day of the last automatic backup. */
    var lastAutoDay: Long?
        get() = prefs.getLong(KEY_AUTO_LAST, -1L).takeIf { it >= 0 }
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_AUTO_LAST) else putLong(KEY_AUTO_LAST, value) }.apply() }

    /** The automatic backup files made so far, newest first (one uri per line). */
    var autoFiles: List<String>
        get() = prefs.getString(KEY_AUTO_FILES, "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) { prefs.edit().putString(KEY_AUTO_FILES, value.joinToString("\n")).apply() }

    private companion object {
        const val KEY_LAST = "last"
        const val KEY_SNOOZE = "snooze"
        const val KEY_AUTO_FOLDER = "auto_folder"
        const val KEY_AUTO_LAST = "auto_last"
        const val KEY_AUTO_FILES = "auto_files"
    }
}

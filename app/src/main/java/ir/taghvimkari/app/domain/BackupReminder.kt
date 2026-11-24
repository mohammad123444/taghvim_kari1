package ir.taghvimkari.app.domain

/** A backup (sending the files) is asked for once a month. */
const val BACKUP_INTERVAL_DAYS = 30L

/** Closing the reminder hides it for this many days. */
const val BACKUP_SNOOZE_DAYS = 3L

/**
 * Whether the backup reminder is shown. All days are epoch days.
 * Nothing to back up -> never. Never backed up -> now. Otherwise [BACKUP_INTERVAL_DAYS] after the last backup.
 * A closed reminder stays hidden until [snoozedUntil].
 */
fun backupReminderDue(today: Long, lastBackup: Long?, snoozedUntil: Long?, hasData: Boolean): Boolean {
    if (!hasData) return false
    if (snoozedUntil != null && today < snoozedUntil) return false
    return lastBackup == null || today - lastBackup >= BACKUP_INTERVAL_DAYS
}

/** Whole days since [lastBackup], or null when there was none (or the clock went backwards). */
fun daysSinceBackup(today: Long, lastBackup: Long?): Long? = lastBackup?.let { (today - it).takeIf { d -> d >= 0 } }

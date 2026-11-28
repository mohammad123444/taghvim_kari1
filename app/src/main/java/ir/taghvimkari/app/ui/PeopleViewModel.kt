package ir.taghvimkari.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.taghvimkari.app.BuildConfig
import ir.taghvimkari.app.data.AutoBackup
import ir.taghvimkari.app.data.BackupPrefs
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.ExchangeData
import ir.taghvimkari.app.data.Incoming
import ir.taghvimkari.app.data.EvaluationStore
import ir.taghvimkari.app.data.SummaryStore
import ir.taghvimkari.app.domain.SummaryCell
import ir.taghvimkari.app.domain.SummaryMerge
import ir.taghvimkari.app.domain.mergeSummary
import ir.taghvimkari.app.data.ReportStore
import ir.taghvimkari.app.data.LocalStore
import ir.taghvimkari.app.data.UpdateChecker
import ir.taghvimkari.app.data.UpdateInfo
import ir.taghvimkari.app.data.UiPrefs
import ir.taghvimkari.app.data.UpdateResult
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.EvaluationMerge
import ir.taghvimkari.app.domain.ReportMerge
import ir.taghvimkari.app.domain.mergeEvaluations
import ir.taghvimkari.app.domain.mergeReports
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.Transfer
import ir.taghvimkari.app.domain.dedupePeople
import ir.taghvimkari.app.domain.AUTO_BACKUP_DAYS
import ir.taghvimkari.app.domain.BACKUP_SNOOZE_DAYS
import ir.taghvimkari.app.domain.ReportEntry
import ir.taghvimkari.app.domain.TextSize
import ir.taghvimkari.app.domain.WorkingDraft
import ir.taghvimkari.app.domain.backupReminderDue
import ir.taghvimkari.app.domain.restoreAt
import ir.taghvimkari.app.domain.withFinal
import ir.taghvimkari.app.domain.withoutFinal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: String?) : UpdateUi
    data class Failed(val reason: String) : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
}

/** A deletion that can still be taken back: the snackbar shows [message] with a "بازگرداندن" button that runs [restore]. */
class UndoAction(val id: Long, val message: String, val restore: () -> Unit)

class PeopleViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val reportStore = ReportStore(app)
    private val evaluationStore = EvaluationStore(app)
    private val summaryStore = SummaryStore(app)
    private val uiPrefs = UiPrefs(app)
    private val backupPrefs = BackupPrefs(app)
    private var checkInFlight = false
    private var undoCounter = 0L

    var people: List<Person> by mutableStateOf(store.load())
        private set

    var reports: List<ReportEntry> by mutableStateOf(reportStore.load())
        private set

    var evaluations: List<EvaluationEntry> by mutableStateOf(evaluationStore.load())
        private set

    /** Hand-typed cells of the جمع‌بندی table. */
    var summaryCells: List<SummaryCell> by mutableStateOf(summaryStore.load())
        private set

    /** Changes one person's typed cells for one period; a cell with nothing left in it is dropped. */
    fun updateSummary(personId: String, periodKey: String, change: (SummaryCell) -> SummaryCell) {
        val old = summaryCells.firstOrNull { it.personId == personId && it.periodKey == periodKey }
            ?: SummaryCell(personId, periodKey)
        val new = change(old)
        val rest = summaryCells.filterNot { it.personId == personId && it.periodKey == periodKey }
        summaryCells = if (new.isEmpty) rest else rest + new
        summaryStore.save(summaryCells)
    }

    /** Sets (value) or clears (null) the «تشویقی» number of the given people for one period, saved once. */
    fun setBonuses(values: Map<String, Double?>, periodKey: String) {
        var cells = summaryCells
        for ((id, value) in values) {
            val old = cells.firstOrNull { it.personId == id && it.periodKey == periodKey } ?: SummaryCell(id, periodKey)
            val new = old.copy(bonus = value)
            cells = cells.filterNot { it.personId == id && it.periodKey == periodKey } + (if (new.isEmpty) emptyList() else listOf(new))
        }
        summaryCells = cells
        summaryStore.save(summaryCells)
    }

    var updateUi: UpdateUi by mutableStateOf(UpdateUi.Idle)
        private set

    /** Update checking is off when the build has no GitHub repository configured. */
    val updatesEnabled: Boolean = BuildConfig.UPDATE_REPO.isNotBlank()

    /** Staff list received from a file, waiting for the user to confirm the replacement. */
    var pendingImport: ExchangeData? by mutableStateOf(null)
        private set

    /** Evaluations received from someone else, waiting for the user to confirm the merge. */
    var pendingEvaluations: List<EvaluationEntry>? by mutableStateOf(null)
        private set

    /** Reports received from someone else, waiting for the user to confirm the merge. */
    var pendingReports: List<ReportEntry>? by mutableStateOf(null)
        private set

    /** جمع‌بندی cells received from someone else, waiting for the user to confirm the merge. */
    var pendingSummary: List<SummaryCell>? by mutableStateOf(null)
        private set

    var importError: String? by mutableStateOf(null)
        private set

    /** Names that were left out of a received staff list because they were duplicates (shown as an alarm). */
    var duplicateAlarm: List<String>? by mutableStateOf(null)
        private set

    fun dismissDuplicateAlarm() {
        duplicateAlarm = null
    }

    // ------------------------------------------------------------------ text size
    var textSize: TextSize by mutableStateOf(uiPrefs.textSize)
        private set

    fun updateTextSize(size: TextSize) {
        textSize = size
        uiPrefs.textSize = size
    }

    // ------------------------------------------------------------------ app lock
    var lockEnabled: Boolean by mutableStateOf(uiPrefs.lockEnabled)
        private set

    /** True while the lock screen covers the app (at start when the lock is on, and after a while in the background). */
    var locked: Boolean by mutableStateOf(uiPrefs.lockEnabled)
        private set

    fun updateLockEnabled(on: Boolean) {
        lockEnabled = on
        uiPrefs.lockEnabled = on
        if (!on) locked = false
    }

    fun lockNow() {
        if (lockEnabled) locked = true
    }

    fun unlocked() {
        locked = false
    }

    // ------------------------------------------------------------------ undo
    /** The last deletion, offered for undo by the snackbar. Only the latest one can be taken back. */
    var undo: UndoAction? by mutableStateOf(null)
        private set

    private fun offerUndo(message: String, restore: () -> Unit) {
        undoCounter += 1
        undo = UndoAction(undoCounter, message, restore)
    }

    /** Called by the snackbar when it was dismissed or used (only if it is still the same deletion). */
    fun clearUndo(id: Long) {
        if (undo?.id == id) undo = null
    }

    // ------------------------------------------------------------------ working draft (evaluation round in progress)
    var workingDraft: WorkingDraft? by mutableStateOf(uiPrefs.workingDraft)
        private set

    private fun setDraft(draft: WorkingDraft?) {
        workingDraft = draft
        uiPrefs.workingDraft = draft
    }

    /** Starts a round: [personIds] are scored one after another for the period [fromEpoch]..[toEpoch]. */
    fun startDraft(evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) {
        if (personIds.isEmpty()) return
        val old = workingDraft
        val new = WorkingDraft(evaluatorId, fromEpoch, maxOf(fromEpoch, toEpoch), personIds)
        setDraft(new)
        // Starting another round (e.g. editing one finished evaluation) must not silently lose the unfinished one.
        if (old != null && old != new) offerUndo("ارزیابی ناتمام قبلی جایگزین شد") { setDraft(old) }
    }

    /** Remembers the scores typed so far for the current person (called after every tap). */
    fun updateDraftScores(scores: String) {
        workingDraft?.let { setDraft(it.copy(scores = scores)) }
    }

    /** The current person is finished (saved or skipped). Returns true when the whole round is over. */
    fun advanceDraft(): Boolean {
        val next = workingDraft?.advanced()
        setDraft(next)
        return next == null
    }

    /** Throws the round away; "بازگرداندن" brings it back. */
    fun discardDraft() {
        val old = workingDraft ?: return
        setDraft(null)
        offerUndo("ارزیابی ناتمام کنار گذاشته شد") { if (workingDraft == null) setDraft(old) }
    }

    // ------------------------------------------------------------------ backup reminder
    private var lastBackupDay: Long? by mutableStateOf(backupPrefs.lastBackupDay)
    private var snoozedUntilDay: Long? by mutableStateOf(backupPrefs.snoozedUntilDay)

    /** Epoch day of the last backup, or null. */
    val lastBackup: Long? get() = lastBackupDay

    /** True when the monthly backup reminder should be on screen. */
    val backupReminderVisible: Boolean
        get() = backupReminderDue(
            LocalDate.now().toEpochDay(), lastBackupDay, snoozedUntilDay,
            hasData = people.isNotEmpty() || reports.isNotEmpty() || evaluations.isNotEmpty()
        )

    /** The reminder was closed: it stays away for a few days. */
    fun snoozeBackupReminder() {
        val until = LocalDate.now().toEpochDay() + BACKUP_SNOOZE_DAYS
        snoozedUntilDay = until
        backupPrefs.snoozedUntilDay = until
    }

    /** Sends the backup: ONE file with staff, evaluations, reports and the جمع‌بندی cells. Returns false if nothing could be sent. */
    fun sendBackup(context: Context): Boolean {
        val ok = DataExchange.shareBackup(context, people, evaluations, reports, summaryCells)
        if (ok) {
            val today = LocalDate.now().toEpochDay()
            lastBackupDay = today
            backupPrefs.lastBackupDay = today
            snoozedUntilDay = null
            backupPrefs.snoozedUntilDay = null
        }
        return ok
    }

    // ------------------------------------------------------------------ automatic weekly backup
    /** The folder chosen for the automatic backup (null = off). */
    var autoBackupFolder: String? by mutableStateOf(backupPrefs.autoFolder)
        private set
    var lastAutoBackupDay: Long? by mutableStateOf(backupPrefs.lastAutoDay)
        private set

    /** True when the last automatic backup could not be written (the folder is gone or not allowed any more). */
    var autoBackupFailed: Boolean by mutableStateOf(false)
        private set
    private var autoBackupRunning = false

    fun setAutoBackupFolder(uri: Uri?) {
        autoBackupFolder = uri?.toString()
        backupPrefs.autoFolder = autoBackupFolder
        autoBackupFailed = false
        if (uri == null) {
            lastAutoBackupDay = null
            backupPrefs.lastAutoDay = null
        }
    }

    /** Makes the automatic backup when a folder is chosen and the last one is a week old (or [force]). */
    fun autoBackupIfDue(force: Boolean = false) {
        val folder = autoBackupFolder ?: return
        if (autoBackupRunning) return
        val today = LocalDate.now().toEpochDay()
        val last = lastAutoBackupDay
        if (!force && last != null && today - last in 0 until AUTO_BACKUP_DAYS) return
        if (people.isEmpty() && reports.isEmpty() && evaluations.isEmpty() && summaryCells.isEmpty()) return
        autoBackupRunning = true
        val app = getApplication<Application>()
        val sealed = DataExchange.seal(DataExchange.encodeFull(people, reports, evaluations, summaryCells))
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching { AutoBackup.write(app, folder, "taghvim-auto-backup-${LocalDate.now()}.json", sealed) }.getOrNull()
            }
            if (written == null) {
                autoBackupFailed = true
            } else {
                autoBackupFailed = false
                lastAutoBackupDay = today
                backupPrefs.lastAutoDay = today
                // an automatic backup counts as a backup: the monthly reminder starts over
                lastBackupDay = today
                backupPrefs.lastBackupDay = today
                snoozedUntilDay = null
                backupPrefs.snoozedUntilDay = null
                val all = listOf(written) + backupPrefs.autoFiles
                val keep = all.take(AutoBackup.KEEP)
                withContext(Dispatchers.IO) { all.drop(AutoBackup.KEEP).forEach { AutoBackup.delete(app, it) } }
                backupPrefs.autoFiles = keep
            }
            autoBackupRunning = false
        }
    }

    init {
        if (updatesEnabled) checkForUpdate(manual = false)
    }

    private fun commit() {
        store.save(people)
    }

    /** Reads a received file; nothing changes until the user confirms (see the pending* states). */
    fun offerImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { DataExchange.parse(DataExchange.readFile(getApplication(), uri)) }
            }
                .onSuccess { incoming ->
                    when (incoming) {
                        is Incoming.People -> pendingImport = ExchangeData(incoming.people, null, null)
                        is Incoming.Full -> pendingImport = incoming.data
                        is Incoming.Evaluations -> pendingEvaluations = incoming.list
                        is Incoming.Reports -> pendingReports = incoming.list
                        is Incoming.Summary -> pendingSummary = incoming.list
                    }
                }
                .onFailure { importError = it.message ?: "فایل معتبر نیست." }
        }
    }

    /**
     * Staff list: replaces the current list (after confirmation). Reports and evaluations that an old all-in-one
     * file may carry are merged into the existing ones, never replacing them.
     */
    fun confirmImport() {
        pendingImport?.let { data ->
            // A backup of an app with no staff must not wipe the staff list of this phone.
            if (data.people.isNotEmpty()) {
                // The new file replaces the list completely (no merging); duplicates inside it are left out.
                val (unique, dropped) = dedupePeople(data.people)
                people = unique
                commit()
                if (dropped.isNotEmpty()) duplicateAlarm = dropped
            }
            data.reports?.let {
                reports = mergeReports(reports, it, people).result
                reportStore.save(reports)
            }
            data.evaluations?.let {
                evaluations = mergeEvaluations(evaluations, it, people).result
                evaluationStore.save(evaluations)
            }
            data.summary?.let {
                summaryCells = mergeSummary(summaryCells, it).result
                summaryStore.save(summaryCells)
            }
        }
        pendingImport = null
    }

    /** The reporter of the last saved report; pre-filled in the next new report. */
    var lastReporter: String by mutableStateOf("")
        private set

    /** Adds a new report or replaces the one with the same id. */
    fun saveReport(entry: ReportEntry) {
        if (entry.reporter.isNotBlank()) lastReporter = entry.reporter
        reports = if (reports.any { it.id == entry.id }) {
            reports.map { if (it.id == entry.id) entry else it }
        } else {
            reports + entry
        }
        reportStore.save(reports)
    }

    /** Adds several new reports at once (one reward / punishment for many people). */
    fun saveReports(entries: List<ReportEntry>) {
        if (entries.isEmpty()) return
        entries.lastOrNull { it.reporter.isNotBlank() }?.let { lastReporter = it.reporter }
        val known = reports.map { it.id }.toHashSet()
        reports = reports + entries.filter { it.id !in known }
        reportStore.save(reports)
    }

    /** The superior who gave the last final opinion; pre-filled in the next one. */
    var lastFinalBy: String by mutableStateOf("")
        private set

    /**
     * The final opinion (نظر نهایی) of a superior on one report: [reward] / [punishment] are the hours that count from now
     * on. A number he leaves as the suggestion stays the suggestion.
     */
    fun setFinalOpinion(id: String, by: String, reward: Double, punishment: Double) {
        val old = reports.firstOrNull { it.id == id } ?: return
        if (by.isNotBlank()) lastFinalBy = by.trim()
        replaceReport(old.withFinal(by, reward, punishment))
    }

    /** Takes a final opinion back: the reporter's suggested hours count again. */
    fun clearFinalOpinion(id: String) {
        val old = reports.firstOrNull { it.id == id } ?: return
        replaceReport(old.withoutFinal())
    }

    /** Replaces the report with the same id without touching [lastReporter] (a superior's opinion is not a new report). */
    private fun replaceReport(entry: ReportEntry) {
        reports = reports.map { if (it.id == entry.id) entry else it }
        reportStore.save(reports)
    }

    fun deleteReport(id: String) {
        val index = reports.indexOfFirst { it.id == id }
        val removed = reports.getOrNull(index) ?: return
        reports = reports.filterNot { it.id == id }
        reportStore.save(reports)
        offerUndo("سابقهٔ «${removed.title}» حذف شد") {
            reports = restoreAt(reports, removed, index) { it.id == removed.id }
            reportStore.save(reports)
        }
    }

    /** Adds a new evaluation or replaces the one with the same id. */
    fun saveEvaluation(entry: EvaluationEntry) {
        evaluations = if (evaluations.any { it.id == entry.id }) {
            evaluations.map { if (it.id == entry.id) entry else it }
        } else {
            evaluations + entry
        }
        evaluationStore.save(evaluations)
    }

    fun deleteEvaluation(id: String) {
        val index = evaluations.indexOfFirst { it.id == id }
        val removed = evaluations.getOrNull(index) ?: return
        evaluations = evaluations.filterNot { it.id == id }
        evaluationStore.save(evaluations)
        offerUndo("ارزیابی «${removed.personName}» حذف شد") {
            evaluations = restoreAt(evaluations, removed, index) { it.id == removed.id }
            evaluationStore.save(evaluations)
        }
    }

    /** Deletes several evaluations at once (the filtered result list); one snackbar takes all of them back. */
    fun deleteEvaluations(ids: Set<String>) {
        val removed = evaluations.filter { it.id in ids }
        if (removed.isEmpty()) return
        evaluations = evaluations.filterNot { it.id in ids }
        evaluationStore.save(evaluations)
        offerUndo("${removed.size} ارزیابی حذف شد") {
            evaluations = evaluations + removed.filter { r -> evaluations.none { it.id == r.id } }
            evaluationStore.save(evaluations)
        }
    }

    /** What merging the received جمع‌بندی cells would do (shown before the user confirms). */
    fun previewSummaryMerge(incoming: List<SummaryCell>): SummaryMerge = mergeSummary(summaryCells, incoming)

    fun confirmSummaryImport() {
        pendingSummary?.let { incoming ->
            summaryCells = mergeSummary(summaryCells, incoming).result
            summaryStore.save(summaryCells)
        }
        pendingSummary = null
    }

    fun cancelSummaryImport() {
        pendingSummary = null
    }

    fun cancelImport() {
        pendingImport = null
    }

    /** What receiving the reports would do (checked before anything is registered): which are new, which duplicates. */
    fun previewReportMerge(incoming: List<ReportEntry>): ReportMerge =
        mergeReports(reports, incoming, people)

    /** Registers the new received reports; duplicates (same reporter, person and date) are never registered. */
    fun confirmReportImport() {
        pendingReports?.let { incoming ->
            reports = mergeReports(reports, incoming, people).result
            reportStore.save(reports)
        }
        pendingReports = null
    }

    fun cancelReportImport() {
        pendingReports = null
    }

    /** What receiving the evaluations would do (checked before anything is registered). */
    fun previewEvaluationMerge(incoming: List<EvaluationEntry>): EvaluationMerge =
        mergeEvaluations(evaluations, incoming, people)

    /** Registers the new received evaluations; duplicates (same evaluator, person and period) are never registered. */
    fun confirmEvaluationImport() {
        pendingEvaluations?.let { incoming ->
            evaluations = mergeEvaluations(evaluations, incoming, people).result
            evaluationStore.save(evaluations)
        }
        pendingEvaluations = null
    }

    fun cancelEvaluationImport() {
        pendingEvaluations = null
    }

    fun dismissImportError() {
        importError = null
    }

    /** Registers [head] and all [members] (who share the head's conditions) in one go. */
    fun add(head: Person, members: List<Person>) {
        people = people + head + members
        commit()
    }

    /**
     * Edits one person; any [members] typed during the edit are added as new people.
     * With [applyToMembers], only the conditions that changed in this edit are copied to the
     * leader's existing members, so their other individual settings stay untouched.
     */
    fun update(head: Person, members: List<Person>, applyToMembers: Boolean = false) {
        val old = people.firstOrNull { it.id == head.id }
        people = people.map { p ->
            when {
                p.id == head.id -> head
                applyToMembers && old != null && p.leaderId == head.id -> {
                    val dateChanged = head.startDate != old.startDate
                    p.copy(
                        employment = if (head.employment != old.employment) head.employment else p.employment,
                        category = if (head.category != old.category) head.category else p.category,
                        shiftName = if (head.shiftName != old.shiftName) head.shiftName else p.shiftName,
                        pattern = if (head.pattern != old.pattern) head.pattern else p.pattern,
                        startDate = if (dateChanged) head.startDate else p.startDate,
                        startWeekday = if (dateChanged) head.startWeekday else p.startWeekday
                    )
                }
                else -> p
            }
        } + members
        commit()
    }

    /** Active / inactive (left the unit): inactive people are hidden from evaluations and new records, never deleted. */
    fun setActive(id: String, active: Boolean) {
        people = people.map { if (it.id == id) it.copy(active = active) else it }
        commit()
    }

    /** Moves one person to another shift from [from] on; his records and evaluations stay with him. */
    fun moveToShift(id: String, shiftName: String, leaderId: String?, from: LocalDate) {
        val p = people.firstOrNull { it.id == id } ?: return
        val leader = leaderId?.let { lid -> people.firstOrNull { it.id == lid } }
        val moved = Transfer.move(p, shiftName, leader, from)
        people = people.map { if (it.id == id) moved else it }
        commit()
    }

    /** Two people exchange their shifts from [from] on. */
    fun swapShifts(aId: String, bId: String, from: LocalDate) {
        val a = people.firstOrNull { it.id == aId } ?: return
        val b = people.firstOrNull { it.id == bId } ?: return
        val (na, nb) = Transfer.swap(a, b, from)
        people = people.map {
            when (it.id) {
                aId -> na
                bId -> nb
                else -> it
            }
        }
        commit()
    }

    fun remove(id: String) {
        val index = people.indexOfFirst { it.id == id }
        val removed = people.getOrNull(index) ?: return
        people = people.filterNot { it.id == id }
        commit()
        offerUndo("«${removed.name}» حذف شد") {
            people = restoreAt(people, removed, index) { it.id == removed.id }
            commit()
        }
    }

    /** [manual] = the user tapped the button, so "up to date" and errors are reported; automatic checks stay silent. */
    fun checkForUpdate(manual: Boolean) {
        if (!updatesEnabled || checkInFlight) return
        checkInFlight = true
        if (manual) updateUi = UpdateUi.Checking
        viewModelScope.launch {
            val result = UpdateChecker.fetchLatest(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME)
            updateUi = when (result) {
                is UpdateResult.Available -> UpdateUi.Available(result.info)
                is UpdateResult.UpToDate -> if (manual) UpdateUi.UpToDate(result.latest) else UpdateUi.Idle
                is UpdateResult.Failed -> if (manual) UpdateUi.Failed(result.reason) else UpdateUi.Idle
            }
            checkInFlight = false
        }
    }

    fun dismissUpdate() {
        updateUi = UpdateUi.Idle
    }
}

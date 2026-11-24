package ir.taghvimkari.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.taghvimkari.app.BuildConfig
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.ExchangeData
import ir.taghvimkari.app.data.Incoming
import ir.taghvimkari.app.data.EvaluationStore
import ir.taghvimkari.app.data.ReportStore
import ir.taghvimkari.app.data.LocalStore
import ir.taghvimkari.app.data.UpdateChecker
import ir.taghvimkari.app.data.UpdateInfo
import ir.taghvimkari.app.data.UpdateInstaller
import ir.taghvimkari.app.data.UpdateResult
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.EvaluationMerge
import ir.taghvimkari.app.domain.ReportMerge
import ir.taghvimkari.app.domain.mergeEvaluations
import ir.taghvimkari.app.domain.mergeReports
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ReportEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: String?) : UpdateUi
    data class Failed(val reason: String) : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val percent: Int) : UpdateUi
    data class Downloaded(val path: String) : UpdateUi
    data class DownloadFailed(val info: UpdateInfo) : UpdateUi
}

class PeopleViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val reportStore = ReportStore(app)
    private val evaluationStore = EvaluationStore(app)
    private var checkInFlight = false

    var people: List<Person> by mutableStateOf(store.load())
        private set

    var reports: List<ReportEntry> by mutableStateOf(reportStore.load())
        private set

    var evaluations: List<EvaluationEntry> by mutableStateOf(evaluationStore.load())
        private set

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

    var importError: String? by mutableStateOf(null)
        private set

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
            people = data.people
            commit()
            data.reports?.let {
                reports = mergeReports(reports, it, people).result
                reportStore.save(reports)
            }
            data.evaluations?.let {
                evaluations = mergeEvaluations(evaluations, it, people).result
                evaluationStore.save(evaluations)
            }
        }
        pendingImport = null
    }

    /** Adds a new report or replaces the one with the same id. */
    fun saveReport(entry: ReportEntry) {
        reports = if (reports.any { it.id == entry.id }) {
            reports.map { if (it.id == entry.id) entry else it }
        } else {
            reports + entry
        }
        reportStore.save(reports)
    }

    fun deleteReport(id: String) {
        reports = reports.filterNot { it.id == id }
        reportStore.save(reports)
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
        evaluations = evaluations.filterNot { it.id == id }
        evaluationStore.save(evaluations)
    }

    fun cancelImport() {
        pendingImport = null
    }

    /** What merging the received reports would do (shown before the user confirms). */
    fun previewReportMerge(incoming: List<ReportEntry>): ReportMerge =
        mergeReports(reports, incoming, people)

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

    /** What merging the received evaluations would do (shown before the user confirms). */
    fun previewEvaluationMerge(incoming: List<EvaluationEntry>): EvaluationMerge =
        mergeEvaluations(evaluations, incoming, people)

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

    fun remove(id: String) {
        people = people.filterNot { it.id == id }
        commit()
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

    fun downloadUpdate(info: UpdateInfo) {
        val url = info.apkUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            updateUi = UpdateUi.DownloadFailed(info)
            return
        }
        updateUi = UpdateUi.Downloading(0)
        viewModelScope.launch {
            val file = UpdateInstaller.download(getApplication(), url) { p -> updateUi = UpdateUi.Downloading(p) }
            updateUi = if (file != null) UpdateUi.Downloaded(file.absolutePath) else UpdateUi.DownloadFailed(info)
        }
    }

    fun dismissUpdate() {
        updateUi = UpdateUi.Idle
    }
}

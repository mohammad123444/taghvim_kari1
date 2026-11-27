package ir.taghvimkari.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import ir.taghvimkari.app.data.DataExchange
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import ir.taghvimkari.app.domain.sortedByShiftName
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import ir.taghvimkari.app.domain.daysSinceBackup
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate

@Composable
fun TaghvimApp(viewModel: PeopleViewModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    // Settings is opened from the gear in the header; this remembers where to return to.
    var pageBeforeSettings by rememberSaveable { mutableStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Person?>(null) }
    var showCompare by rememberSaveable { mutableStateOf(false) }
    var transferringId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // While the keyboard is open the bottom bar is hidden and the content ends right above the keyboard,
    // so text boxes and their buttons are never covered by the keyboard or the phone's navigation buttons.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val snackbarHost = remember { SnackbarHostState() }
    var backupFailed by rememberSaveable { mutableStateOf(false) }

    // Every deletion (staff, report, evaluation) can be taken back from the snackbar.
    val undo = viewModel.undo
    LaunchedEffect(undo?.id) {
        if (undo != null) {
            val result = snackbarHost.showSnackbar(
                message = undo.message,
                actionLabel = "بازگرداندن",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) undo.restore()
            viewModel.clearUndo(undo.id)
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            AppHeader(
                title = PAGE_TITLES.getOrElse(page) { "" },
                settingsOpen = page == PAGE_SETTINGS,
                onSettings = {
                    if (page == PAGE_SETTINGS) {
                        page = pageBeforeSettings
                    } else {
                        pageBeforeSettings = page
                        page = PAGE_SETTINGS
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = { if (!imeVisible) AppNavigation(selected = page, onSelect = { page = it }) }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().imePadding()) {
            // Monthly backup reminder. Not shown while scoring an evaluation, so it never gets in the way there.
            if (viewModel.backupReminderVisible && page != PAGE_EVALUATION) {
                BackupReminderCard(
                    days = daysSinceBackup(LocalDate.now().toEpochDay(), viewModel.lastBackup),
                    failed = backupFailed,
                    onSend = { backupFailed = !viewModel.sendBackup(context) },
                    onLater = viewModel::snoozeBackupReminder
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> DashboardScreen(viewModel.people)
                1 -> PeopleScreen(
                    people = viewModel.people,
                    onAdd = { showAdd = true },
                    onEdit = { editing = it },
                    onCompare = { showCompare = true }
                )
                3 -> ReportsScreen(viewModel)
                PAGE_EVALUATION -> EvaluationScreen(viewModel)
                PAGE_SUMMARY -> SummaryScreen(viewModel)
                PAGE_EXCHANGE -> ExchangeScreen(viewModel)
                else -> SettingsScreen(viewModel)
            }
            }
        }
    }

    if (showCompare) CompareDialog(viewModel.people) { showCompare = false }

    BackHandler(enabled = page == PAGE_SETTINGS) { page = pageBeforeSettings }

    if (showAdd) {
        AddPersonDialog(
            onDismiss = { showAdd = false },
            onSave = { head, members, _ -> viewModel.add(head, members); showAdd = false }
        )
    }

    editing?.let { person ->
        AddPersonDialog(
            initial = person,
            memberCount = viewModel.people.count { it.leaderId == person.id },
            existingMembers = viewModel.people.filter { it.leaderId == person.id }.sortedByShiftName().map { it.name },
            onTransfer = { editing = null; transferringId = person.id },
            onDelete = { editing = null; viewModel.remove(person.id) },
            onDismiss = { editing = null },
            onSave = { head, members, apply -> viewModel.update(head, members, apply); editing = null }
        )
    }

    val transferring = transferringId?.let { id -> viewModel.people.firstOrNull { it.id == id } }
    if (transferring != null) {
        TransferDialog(
            person = transferring,
            people = viewModel.people,
            onMove = { shift, leaderId, from ->
                viewModel.moveToShift(transferring.id, shift, leaderId, from)
                transferringId = null
            },
            onSwap = { otherId, from ->
                viewModel.swapShifts(transferring.id, otherId, from)
                transferringId = null
            },
            onDismiss = { transferringId = null }
        )
    }

    viewModel.duplicateAlarm?.let { names ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateAlarm,
            title = { Text("هشدار: نام تکراری", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                ScrollText(
                    "در فایل دریافتی ${names.size.toPersian()} نام تکراری بود و ثبت نشد:\n" +
                        names.joinToString("\n") { "• $it" }
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDuplicateAlarm) { Text("باشه") } }
        )
    }

    UpdateDialog(
        state = viewModel.updateUi,
        onDismiss = viewModel::dismissUpdate,
        onDownload = viewModel::downloadUpdate
    )

    viewModel.pendingImport?.let { incoming ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("دریافت اطلاعات", fontWeight = FontWeight.Bold) },
            text = {
                val others = buildList {
                    incoming.reports?.let { add("${it.size.toPersian()} سابقه") }
                    incoming.evaluations?.let { add("${it.size.toPersian()} ارزیابی") }
                    incoming.summary?.let { add("${it.size.toPersian()} مورد جمع‌بندی") }
                }
                val hasPeople = incoming.people.isNotEmpty()
                ScrollText(
                    "در فایل دریافتی " + (listOfNotNull(
                        if (hasPeople) "${incoming.people.size.toPersian()} نفر" else null
                    ) + others).joinToString("، ") + " هست. " +
                        (if (hasPeople) {
                            "فهرست نیروهای فعلی این گوشی (${viewModel.people.size.toPersian()} نفر) کاملاً پاک و با این فایل جایگزین می‌شود؛ هیچ نامی از فهرست قبلی نمی‌ماند (ادغام انجام نمی‌شود) و نام‌های تکراری داخل فایل ثبت نمی‌شوند. "
                        } else "") +
                        (if (others.isNotEmpty()) {
                            "بقیهٔ موارد به اطلاعات فعلی اضافه می‌شود و چیزی از آن‌ها حذف نمی‌شود. "
                        } else "") +
                        "ادامه می‌دهید؟"
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text(if (incoming.people.isNotEmpty()) "پاک و جایگزین کن" else "اضافه کن", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("انصراف") } }
        )
    }

    viewModel.pendingReports?.let { incoming ->
        val merge = viewModel.previewReportMerge(incoming)
        ImportReviewDialog(
            title = "دریافت سوابق",
            what = "سابقه",
            total = incoming.size,
            newCount = merge.added.size,
            duplicateLines = merge.duplicates.map { e ->
                val by = if (e.reporter.isBlank()) "" else " — ثبت‌کننده: ${e.reporter.trim()}"
                "${e.personName} — ${JalaliDate.from(e.date).display()}$by"
            },
            rule = "سابقه‌ای تکراری است که ثبت‌کننده، نفر و تاریخ آن با یک سابقهٔ ثبت‌شده یکی باشد.",
            onConfirm = viewModel::confirmReportImport,
            onCancel = viewModel::cancelReportImport
        )
    }

    viewModel.pendingEvaluations?.let { incoming ->
        val merge = viewModel.previewEvaluationMerge(incoming)
        ImportReviewDialog(
            title = "دریافت ارزیابی‌ها",
            what = "ارزیابی",
            total = incoming.size,
            newCount = merge.added.size,
            duplicateLines = merge.duplicates.map { e ->
                "${e.personName} — ${e.periodText()} — ارزیاب: ${e.evaluatorName}"
            },
            rule = "ارزیابی‌ای تکراری است که ارزیاب، نفر و دورهٔ ارزیابی آن با یک ارزیابی ثبت‌شده یکی باشد.",
            onConfirm = viewModel::confirmEvaluationImport,
            onCancel = viewModel::cancelEvaluationImport
        )
    }

    viewModel.pendingSummary?.let { incoming ->
        val merge = viewModel.previewSummaryMerge(incoming)
        AlertDialog(
            onDismissRequest = viewModel::cancelSummaryImport,
            title = { Text("دریافت جمع‌بندی", fontWeight = FontWeight.Bold) },
            text = {
                ScrollText(
                    "در فایل دریافتی ${incoming.size.toPersian()} مورد هست: " +
                        "${merge.added.toPersian()} مورد جدید، ${merge.updated.toPersian()} مورد به‌روزشده و " +
                        "${merge.unchanged.toPersian()} مورد بدون تغییر. " +
                        "اطلاعات فعلی این گوشی حذف نمی‌شود و فقط این موارد به آن اضافه می‌شود. ادامه می‌دهید؟"
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmSummaryImport) { Text("اضافه کن", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelSummaryImport) { Text("انصراف") } }
        )
    }

    viewModel.importError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportError,
            title = { Text("فایل پذیرفته نشد", fontWeight = FontWeight.Bold) },
            text = { ScrollText(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissImportError) { Text("باشه") } }
        )
    }
}

private const val PAGE_EVALUATION = 4
private const val PAGE_SETTINGS = 5
private const val PAGE_SUMMARY = 6
private const val PAGE_EXCHANGE = 7

/** The blue header shows the title of the current page (indexes as in the bottom bar; 5 = settings). */
private val PAGE_TITLES = listOf("تقویم", "مدیریت نیروها", "مقایسه", "سوابق تشویق و تنبیه", "ارزیابی نیروها", "تنظیمات", "جمع‌بندی", "گزارش")

@Composable
private fun AppHeader(title: String, settingsOpen: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Navy, Purple)))
            .statusBarsPadding()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            Modifier.weight(1f),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "تنظیمات",
                tint = if (settingsOpen) Color(0xFFFFD54F) else Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

/** «یادآوری پشتیبان‌گیری»: closable, comes back after a few days if no backup was sent. */
@Composable
private fun BackupReminderCard(days: Long?, failed: Boolean, onSend: () -> Unit, onLater: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DC)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (days == null) "هنوز از اطلاعات پشتیبان نگرفته‌اید." else "${days.toInt().toPersian()} روز از آخرین پشتیبان‌گیری گذشته است.",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7A4B00)
            )
            if (failed) Text("ارسال انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSend, modifier = Modifier.weight(1f)) { Text("ارسال") }
                OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f)) { Text("بعداً") }
            }
        }
    }
}

@Composable
private fun AppNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Purple,
        selectedTextColor = Purple,
        indicatorColor = Purple.copy(alpha = .14f),
        unselectedIconColor = Color(0xFF8A88A3),
        unselectedTextColor = Color(0xFF8A88A3)
    )
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        NavigationBarItem(
            colors = navColors,
            selected = selected == 0, onClick = { onSelect(0) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) }, label = { Text("تقویم", maxLines = 1) }
        )
        NavigationBarItem(
            colors = navColors,
            selected = selected == 1, onClick = { onSelect(1) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) }, label = { Text("نیروها", maxLines = 1) }
        )
        NavigationBarItem(
            colors = navColors,
            selected = selected == 3, onClick = { onSelect(3) },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) }, label = { Text("سوابق", maxLines = 1) }
        )
        NavigationBarItem(
            colors = navColors,
            selected = selected == PAGE_EVALUATION, onClick = { onSelect(PAGE_EVALUATION) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) }, label = { Text("ارزیابی", maxLines = 1) }
        )
        NavigationBarItem(
            colors = navColors,
            selected = selected == PAGE_SUMMARY, onClick = { onSelect(PAGE_SUMMARY) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }, label = { Text("جمع‌بندی", maxLines = 1) }
        )
        NavigationBarItem(
            colors = navColors,
            selected = selected == PAGE_EXCHANGE, onClick = { onSelect(PAGE_EXCHANGE) },
            icon = { Icon(Icons.Default.Share, contentDescription = null) }, label = { Text("گزارش", maxLines = 1) }
        )
    }
}

/**
 * Received records / evaluations are checked BEFORE they are merged: the new ones are added to the current data,
 * the duplicates are listed here as a warning and are never registered. Nothing existing is changed or deleted.
 */
@Composable
private fun ImportReviewDialog(
    title: String,
    what: String,
    total: Int,
    newCount: Int,
    duplicateLines: List<String>,
    rule: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("در فایل دریافتی ${total.toPersian()} $what هست.")
                Text(
                    if (newCount > 0) "${newCount.toPersian()} $what جدید است و به اطلاعات فعلی اضافه می‌شود؛ چیزی از اطلاعات فعلی حذف یا عوض نمی‌شود."
                    else "هیچ $what جدیدی در این فایل نیست.",
                    fontWeight = FontWeight.Bold
                )
                if (duplicateLines.isNotEmpty()) {
                    Text(
                        "هشدار: ${duplicateLines.size.toPersian()} $what تکراری است و ثبت نمی‌شود:",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    duplicateLines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Text(rule, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (newCount > 0) {
                TextButton(onClick = onConfirm) { Text("ثبت ${newCount.toPersian()} $what جدید", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onCancel) { Text("باشه", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = if (newCount > 0) { { TextButton(onClick = onCancel) { Text("انصراف") } } } else null
    )
}

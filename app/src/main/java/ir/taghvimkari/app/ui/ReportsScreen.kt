package ir.taghvimkari.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ReportEntry
import ir.taghvimkari.app.domain.ReportPeriod
import ir.taghvimkari.app.domain.buildReportText
import ir.taghvimkari.app.domain.formatHours
import ir.taghvimkari.app.domain.parseHours
import ir.taghvimkari.app.domain.sortedAlphabetically
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

private val RewardGreen = Color(0xFF218838)
private val PunishRed = Color(0xFFD32F2F)
private const val ALL_PEOPLE = "همه نفرات"
private val PERIOD_LABELS = listOf("این ماه", "ماه قبل", "امسال", "همه", "بازه دلخواه")

/** Reward / punishment reports: write them down per person, then review any month, year or custom period. */
@Composable
fun ReportsScreen(viewModel: PeopleViewModel) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val todayJalali = remember { JalaliDate.from(today) }
    val people = viewModel.people
    val reports = viewModel.reports

    var mode by rememberSaveable { mutableStateOf(0) } // 0 this month, 1 last month, 2 this year, 3 all, 4 custom
    var customFrom by rememberSaveable { mutableStateOf(today.minusDays(29).toEpochDay()) }
    var customTo by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var personFilter by rememberSaveable { mutableStateOf("") }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }

    val labelled = remember(people) { uniqueLabels(people) }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val nameOf: (ReportEntry) -> String = { e -> nameById[e.personId] ?: e.personName }

    val period = remember(mode, customFrom, customTo) { periodFor(mode, todayJalali, customFrom, customTo) }
    val filtered = reports
        .filter { period.contains(it.date) && (personFilter.isBlank() || it.personId == personFilter) }
        .sortedWith(compareByDescending<ReportEntry> { it.date }.thenBy { nameOf(it) })
    val totals = filtered.totals()
    val filterLabel = labelled.firstOrNull { it.first.id == personFilter }?.second ?: ALL_PEOPLE
    val perPerson = if (personFilter.isBlank()) {
        filtered.groupBy { it.personId }
            .map { (id, list) -> Triple(id, nameOf(list.first()), list.totals()) }
            .sortedByDescending { it.third.net }
    } else {
        emptyList()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "گزارش تشویق و تنبیه",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Button(onClick = { showNew = true }, enabled = people.isNotEmpty()) { Text("+ ثبت گزارش") }
        }
        if (people.isEmpty()) {
            EmptyMessage("ابتدا در بخش «نیروها» افراد را ثبت کنید، بعد برایشان گزارش بنویسید.")
        }

        // ----- filters
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PERIOD_LABELS.forEachIndexed { index, label ->
                        val selected = mode == index
                        Surface(
                            color = if (selected) Purple else Purple.copy(alpha = .12f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { mode = index }
                        ) {
                            Text(
                                label,
                                Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                color = if (selected) Color.White else Purple,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                if (mode == 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) {
                            Text("از: ${JalaliDate.from(LocalDate.ofEpochDay(customFrom)).display()}")
                        }
                        OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                            Text("تا: ${JalaliDate.from(LocalDate.ofEpochDay(customTo)).display()}")
                        }
                    }
                }
                PersonSearchField(
                    label = "نفر (خالی = همه نفرات)",
                    options = labelled,
                    selectedId = personFilter,
                    onSelect = { personFilter = it }
                )
            }
        }

        // ----- send / receive the reports as a file (reports only)
        ExchangeBar(
            hint = "ارسال، گزارش‌های همین بازه و نفر (فیلتر بالا) را به‌صورت فایل می‌فرستد. گیرنده با «دریافت از فایل» آن‌ها را به گزارش‌های خودش اضافه می‌کند؛ چیزی حذف نمی‌شود.",
            sendLabel = "ارسال گزارش‌ها (${filtered.size.toPersian()})",
            sendEnabled = filtered.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.shareReports(context, filtered) },
            onReceive = { filePicker.launch(arrayOf("*/*")) }
        )

        // ----- summary
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    period.label + if (personFilter.isNotBlank()) " • $filterLabel" else "",
                    fontWeight = FontWeight.Bold,
                    color = Purple
                )
                Row {
                    HoursStat("تشویق", formatHours(totals.reward), RewardGreen, Modifier.weight(1f))
                    HoursStat("تنبیه", formatHours(totals.punishment), PunishRed, Modifier.weight(1f))
                    HoursStat(
                        "خالص",
                        (if (totals.net < 0) "−" else "") + formatHours(abs(totals.net)),
                        if (totals.net < 0) PunishRed else RewardGreen,
                        Modifier.weight(1f)
                    )
                }
                ShareBar(totals.reward, totals.punishment, height = 12.dp)
                Text(
                    "${totals.count.toPersian()} گزارش در این بازه",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        val text = buildReportText(period.label, filterLabel, filtered, nameOf)
                        shareText(context, text)
                    },
                    enabled = filtered.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ارسال گزارش (متن)") }
            }
        }

        // ----- per person
        if (perPerson.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("به تفکیک نفر", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                    perPerson.forEach { (id, name, t) ->
                        Column(Modifier.fillMaxWidth().clickable { personFilter = id }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                                Text(
                                    (if (t.net < 0) "−" else "") + formatHours(abs(t.net)) + " ساعت",
                                    fontWeight = FontWeight.Bold,
                                    color = if (t.net < 0) PunishRed else RewardGreen
                                )
                            }
                            ShareBar(t.reward, t.punishment, height = 8.dp)
                            Text(
                                "تشویق ${formatHours(t.reward)} • تنبیه ${formatHours(t.punishment)} • ${t.count.toPersian()} گزارش",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Text("برای دیدن گزارش‌های یک نفر، روی نامش بزنید.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ----- entries
        Text(
            "گزارش‌ها (${filtered.size.toPersian()})",
            fontWeight = FontWeight.Bold,
            color = Navy,
            style = MaterialTheme.typography.titleMedium
        )
        if (filtered.isEmpty()) {
            EmptyMessage("در این بازه گزارشی ثبت نشده است.")
        }
        filtered.forEach { e -> ReportCard(e, nameOf(e)) { editingId = e.id } }
    }

    // ----- dialogs
    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = LocalDate.ofEpochDay(if (picker == 1) customFrom else customTo),
            title = if (picker == 1) "تاریخ شروع بازه" else "تاریخ پایان بازه",
            onDismiss = { picker = 0 },
            onPick = { d ->
                if (picker == 1) {
                    customFrom = d.toEpochDay()
                    if (customTo < customFrom) customTo = customFrom
                } else {
                    customTo = d.toEpochDay()
                    if (customFrom > customTo) customFrom = customTo
                }
                picker = 0
            }
        )
    }

    if (showNew) {
        ReportEntryDialog(
            labelled = labelled,
            initial = null,
            presetPersonId = personFilter,
            onDismiss = { showNew = false },
            onSave = { viewModel.saveReport(it); showNew = false },
            onDelete = null
        )
    }

    val editing = editingId?.let { id -> reports.firstOrNull { it.id == id } }
    if (editing != null) {
        ReportEntryDialog(
            labelled = labelled,
            initial = editing,
            presetPersonId = "",
            onDismiss = { editingId = null },
            onSave = { viewModel.saveReport(it); editingId = null },
            onDelete = { deletingId = editing.id; editingId = null }
        )
    }

    val deleting = deletingId?.let { id -> reports.firstOrNull { it.id == id } }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("حذف گزارش", fontWeight = FontWeight.Bold) },
            text = { Text("گزارش «${deleting.title}» برای ${nameOf(deleting)} حذف شود؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteReport(deleting.id); deletingId = null }) {
                    Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun HoursStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text("$label (ساعت)", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
    }
}

/** A bar split between reward (green) and punishment (red) hours. */
@Composable
private fun ShareBar(reward: Double, punishment: Double, height: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0xFFE6E6EE))
    ) {
        if (reward > 0) Box(Modifier.weight(reward.toFloat()).fillMaxHeight().background(RewardGreen))
        if (punishment > 0) Box(Modifier.weight(punishment.toFloat()).fillMaxHeight().background(PunishRed))
    }
}

@Composable
private fun ReportCard(entry: ReportEntry, name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                Text(JalaliDate.from(entry.date).display(), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Text(entry.title)
            if (entry.rewardHours > 0 || entry.punishmentHours > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.rewardHours > 0) HoursChip("تشویق ${formatHours(entry.rewardHours)} ساعت", RewardGreen)
                    if (entry.punishmentHours > 0) HoursChip("تنبیه ${formatHours(entry.punishmentHours)} ساعت", PunishRed)
                }
            }
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HoursChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Add ([initial] == null) or edit one report. Editing also offers delete (confirmed by the caller). */
@Composable
private fun ReportEntryDialog(
    labelled: List<Pair<Person, String>>,
    initial: ReportEntry?,
    presetPersonId: String,
    onDismiss: () -> Unit,
    onSave: (ReportEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val today = remember { LocalDate.now() }
    var personId by rememberSaveable { mutableStateOf(initial?.personId ?: presetPersonId) }
    var dateEpoch by rememberSaveable { mutableStateOf((initial?.date ?: today).toEpochDay()) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var reward by rememberSaveable { mutableStateOf(initial?.rewardHours?.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var punishment by rememberSaveable { mutableStateOf(initial?.punishmentHours?.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpoch)

    FormDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "ثبت گزارش جدید" else "ویرایش گزارش",
        content = {
                PersonSearchField(
                    label = "نفر",
                    options = labelled,
                    selectedId = personId,
                    onSelect = { personId = it },
                    fallbackLabel = initial?.personName
                )
                if (showErrors && personId.isBlank()) {
                    Text("نفر را انتخاب کنید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تاریخ: ${JalaliDate.from(date).display()}")
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("گزارش (موضوع)") },
                    minLines = 2,
                    isError = showErrors && title.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reward, onValueChange = { reward = it },
                        label = { Text("ساعت تشویق") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = punishment, onValueChange = { punishment = it },
                        label = { Text("ساعت تنبیه") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("توضیحات") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
        },
        confirmButton = {
            Button(onClick = {
                if (personId.isNotBlank() && title.isNotBlank()) {
                    val person = labelled.firstOrNull { it.first.id == personId }?.first
                    onSave(
                        ReportEntry(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            personId = personId,
                            personName = person?.name ?: initial?.personName ?: "",
                            date = date,
                            title = title.trim(),
                            rewardHours = parseHours(reward),
                            punishmentHours = parseHours(punishment),
                            notes = notes.trim()
                        )
                    )
                } else {
                    showErrors = true
                }
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )

    if (showPicker) {
        JalaliDatePickerDialog(
            initial = date,
            title = "تاریخ گزارش",
            onDismiss = { showPicker = false },
            onPick = { dateEpoch = it.toEpochDay(); showPicker = false }
        )
    }
}

/** People in alphabetical order; equal names get a number so labels stay unique. */
internal fun uniqueLabels(people: List<Person>): List<Pair<Person, String>> {
    val counts = HashMap<String, Int>()
    return people.sortedAlphabetically { it.name }.map { p ->
        val n = (counts[p.name] ?: 0) + 1
        counts[p.name] = n
        p to if (n == 1) p.name else "${p.name} (${n.toPersian()})"
    }
}

private fun monthPeriod(year: Int, month: Int) = ReportPeriod(
    "${JalaliDate.MONTH_NAMES[month - 1]} ${year.toPersian()}",
    JalaliDate(year, month, 1).toLocalDate(),
    JalaliDate(year, month, JalaliDate.monthLength(year, month)).toLocalDate()
)

private fun periodFor(mode: Int, now: JalaliDate, customFrom: Long, customTo: Long): ReportPeriod = when (mode) {
    0 -> monthPeriod(now.year, now.month)
    1 -> if (now.month == 1) monthPeriod(now.year - 1, 12) else monthPeriod(now.year, now.month - 1)
    2 -> ReportPeriod(
        "سال ${now.year.toPersian()}",
        JalaliDate(now.year, 1, 1).toLocalDate(),
        JalaliDate(now.year, 12, JalaliDate.monthLength(now.year, 12)).toLocalDate()
    )
    3 -> ReportPeriod("همهٔ زمان‌ها", null, null)
    else -> {
        val from = LocalDate.ofEpochDay(customFrom)
        val to = LocalDate.ofEpochDay(customTo)
        ReportPeriod("از ${JalaliDate.from(from).display()} تا ${JalaliDate.from(to).display()}", from, to)
    }
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(send, "ارسال گزارش").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package ir.taghvimkari.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ReportEntry
import ir.taghvimkari.app.domain.ReportPeriod
import ir.taghvimkari.app.domain.ReportTotals
import ir.taghvimkari.app.domain.buildReportText
import ir.taghvimkari.app.domain.formatHours
import ir.taghvimkari.app.domain.normalizeFa
import ir.taghvimkari.app.domain.parseHours
import ir.taghvimkari.app.domain.displayOrder
import ir.taghvimkari.app.domain.shiftRank
import ir.taghvimkari.app.domain.sortedByShiftName
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

internal const val ALL_PEOPLE = "همه نفرات"
internal val PERIOD_LABELS = listOf("این ماه", "ماه قبل", "امسال", "همه", "بازه دلخواه")

/** Reward / punishment reports: write them down per person, then review any month, year or custom period. */
@Composable
fun ReportsScreen(viewModel: PeopleViewModel) {
    val today = remember { LocalDate.now() }
    val todayJalali = remember { JalaliDate.from(today) }
    val people = viewModel.people
    val reports = viewModel.reports

    var mode by rememberSaveable { mutableStateOf(0) } // 0 this month, 1 last month, 2 this year, 3 all, 4 custom
    // The custom period starts empty: the boxes only say «از» and «تا» until a date is chosen.
    var customFrom by rememberSaveable { mutableStateOf(NO_DATE) }
    var customTo by rememberSaveable { mutableStateOf(NO_DATE) }
    var openFolds by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var personFilter by rememberSaveable { mutableStateOf("") }
    // Searching the subject (موضوع) of the reports from the same box as the name.
    var subjectFilter by rememberSaveable { mutableStateOf("") }
    var finalId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }

    // Reports can be reviewed for everybody (also people who left); new records are only written for active people.
    val labelled = remember(people) { uniqueLabels(people).map { (p, n) -> p to if (p.active) n else "$n (غیرفعال)" } }
    val activeLabelled = remember(people) { uniqueLabels(people.filter { it.active }) }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val rankById = remember(people) { people.displayOrder() }
    val memberIds = remember(people) { people.filter { it.isMember }.map { it.id }.toSet() }
    val nameOf: (ReportEntry) -> String = { e -> nameById[e.personId] ?: e.personName }

    val period = remember(mode, customFrom, customTo) { periodFor(mode, todayJalali, customFrom, customTo) }
    val subjectQuery = normalizeFa(subjectFilter)
    val filtered = reports
        .filter {
            period.contains(it.date) && (personFilter.isBlank() || it.personId == personFilter) &&
                (subjectQuery.isEmpty() || normalizeFa(it.title).contains(subjectQuery))
        }
        .sortedWith(compareBy<ReportEntry> { rankById[it.personId] ?: Int.MAX_VALUE }.thenByDescending { it.date }.thenBy { nameOf(it) })
    val totals = filtered.totals()
    val filterLabel = labelled.firstOrNull { it.first.id == personFilter }?.second ?: ALL_PEOPLE
    val perPerson = if (personFilter.isBlank()) {
        filtered.groupBy { it.personId }
            .map { (id, list) -> Triple(id, nameOf(list.first()), list.totals()) }
            .sortedWith(compareBy<Triple<String, String, ReportTotals>> { rankById[it.first] ?: Int.MAX_VALUE }.thenByDescending { it.third.net })
    } else {
        emptyList()
    }

    val scroll = rememberScrollState()
    val narrowed = personFilter.isNotBlank() || subjectFilter.isNotBlank()
    // Showing one person (or one subject) must never trap the page: the phone's back button, and the bar below, show everybody again.
    BackHandler(enabled = narrowed) { personFilter = ""; subjectFilter = "" }
    LaunchedEffect(personFilter, subjectFilter) { scroll.scrollTo(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Two parts: «ثبت سابقه» (default) and «مشاهده سوابق» (period / person filters). The part we are in is blue.
            val filterOn = mode != 0 || personFilter.isNotBlank() || subjectFilter.isNotBlank()
            if (toolsOpen) {
                ActionButton(
                    "ثبت سابقه", { toolsOpen = false; showNew = true }, Modifier.weight(1f),
                    icon = Icons.Default.Add, filled = false, enabled = people.isNotEmpty()
                )
                ActionButton(
                    "مشاهده سوابق", { toolsOpen = false }, Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.List, chevron = true, dot = filterOn, container = Blue
                )
            } else {
                ActionButton(
                    "ثبت سابقه", { showNew = true }, Modifier.weight(1f),
                    icon = Icons.Default.Add, enabled = people.isNotEmpty(), container = Blue
                )
                ActionButton(
                    "مشاهده سوابق", { toolsOpen = true }, Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.List, filled = false, chevron = false, dot = filterOn
                )
            }
        }
        if (people.isEmpty()) {
            EmptyMessage("ابتدا در بخش «نیروها» افراد را ثبت کنید، بعد برایشان سابقه ثبت کنید.")
        }
        if (narrowed) {
            Surface(color = Purple.copy(alpha = .10f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (personFilter.isNotBlank()) "فقط سوابق «$filterLabel» نمایش داده می‌شود"
                        else "فقط سوابق با موضوع «${subjectFilter.trim()}» نمایش داده می‌شود",
                        Modifier.weight(1f),
                        color = Navy,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { personFilter = ""; subjectFilter = "" }) {
                        Text("نمایش همه", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ----- مشاهده سوابق: period and person filters, folded until needed
        if (toolsOpen) {
            FoldPanel {
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
                            Text(dateBoxText("از", customFrom))
                        }
                        OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                            Text(dateBoxText("تا", customTo))
                        }
                    }
                }
                PersonSearchField(
                    label = "نفر یا موضوع (خالی = همه)",
                    options = labelled,
                    selectedId = personFilter,
                    onSelect = { personFilter = it },
                    subjects = reports.map { it.title },
                    selectedSubject = subjectFilter,
                    onSelectSubject = { subjectFilter = it }
                )
            }
        }

        // ----- summary
        AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    period.label + (if (personFilter.isNotBlank()) " • $filterLabel" else "") +
                        (if (subjectFilter.isNotBlank()) " • موضوع: ${subjectFilter.trim()}" else ""),
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
                    "${totals.count.toPersian()} سابقه در این بازه",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
        }

        // ----- per person
        if (perPerson.isNotEmpty()) {
            FoldBox("به تفکیک نفر") {
                    perPerson.forEach { (id, name, t) ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                personFilter = id
                                // his reports are shown open, so the tap really shows them
                                openFolds = "k1,k2,k1-$id,k2-$id"
                            },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledName(name, id in memberIds, Modifier.weight(1f))
                                Text(
                                    (if (t.net < 0) "−" else "") + formatHours(abs(t.net)) + " ساعت",
                                    fontWeight = FontWeight.Bold,
                                    color = if (t.net < 0) PunishRed else RewardGreen
                                )
                            }
                            ShareBar(t.reward, t.punishment, height = 8.dp)
                            Text(
                                "تشویق ${formatHours(t.reward)} • تنبیه ${formatHours(t.punishment)} • ${t.count.toPersian()} سابقه",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
            }
        }

        // ----- entries
        Text(
            "سوابق (${filtered.size.toPersian()})",
            fontWeight = FontWeight.Bold,
            color = Navy,
            style = MaterialTheme.typography.titleMedium
        )
        if (filtered.isEmpty()) {
            EmptyMessage("در این بازه سابقه‌ای ثبت نشده است.")
        }
        val open = openFolds.split(",").toSet()
        fun flip(key: String) {
            openFolds = (if (key in open) open - key else open + key).filter { it.isNotEmpty() }.joinToString(",")
        }
        // Rewards and punishments are separate folds; inside each, one fold per person.
        listOf(
            Triple("تشویق‌ها", 1, RewardGreen),
            Triple("تنبیه‌ها", 2, PunishRed)
        ).forEach { (title, kind, accent) ->
            val list = filtered.filter { if (kind == 1) it.rewardHours > 0 else it.punishmentHours > 0 }
            if (list.isEmpty()) return@forEach
            val key = "k$kind"
            FoldSection(
                title = title, count = list.size, accent = accent, expanded = key in open, big = true,
                onToggle = { flip(key) }, unit = "سابقه"
            ) {
                list.groupBy { it.personId }.entries
                    .sortedWith(compareBy<Map.Entry<String, List<ReportEntry>>> { rankById[it.key] ?: Int.MAX_VALUE }.thenBy { nameOf(it.value.first()) })
                    .forEach { (personId, personList) ->
                        val personKey = "$key-$personId"
                        FoldSection(
                            title = nameOf(personList.first()), count = personList.size, accent = accent,
                            titleMember = personId in memberIds,
                            expanded = personKey in open, big = false,
                            onToggle = { flip(personKey) }, unit = "سابقه"
                        ) {
                            personList.forEach { e -> ReportCard(e, nameOf(e), e.personId in memberIds, kind, { finalId = e.id }) { editingId = e.id } }
                        }
                    }
            }
        }
        val neither = filtered.filter { it.rewardHours <= 0 && it.punishmentHours <= 0 }
        if (neither.isNotEmpty()) {
            val key = "k0"
            FoldSection(
                title = "بدون ساعت", count = neither.size, accent = Color.Gray, expanded = key in open, big = true,
                onToggle = { flip(key) }, unit = "سابقه"
            ) {
                neither.forEach { e -> ReportCard(e, nameOf(e), e.personId in memberIds, 0, { finalId = e.id }) { editingId = e.id } }
            }
        }
    }

    // ----- dialogs
    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = LocalDate.ofEpochDay((if (picker == 1) customFrom else customTo).let { if (it == NO_DATE) today.toEpochDay() else it }),
            title = if (picker == 1) "تاریخ شروع بازه" else "تاریخ پایان بازه",
            onDismiss = { picker = 0 },
            onPick = { d ->
                if (picker == 1) {
                    customFrom = d.toEpochDay()
                    if (customTo != NO_DATE && customTo < customFrom) customTo = customFrom
                } else {
                    customTo = d.toEpochDay()
                    if (customFrom != NO_DATE && customFrom > customTo) customFrom = customTo
                }
                picker = 0
            }
        )
    }

    if (showNew) {
        ReportEntryDialog(
            labelled = activeLabelled,
            initial = null,
            presetPersonId = personFilter,
            lastReporter = viewModel.lastReporter,
            knownReporters = reports.map { it.reporter },
            onDismiss = { showNew = false },
            onSave = { viewModel.saveReports(it); showNew = false },
            onDelete = null,
            onFinal = null
        )
    }

    val editing = editingId?.let { id -> reports.firstOrNull { it.id == id } }
    if (editing != null) {
        ReportEntryDialog(
            labelled = labelled,
            initial = editing,
            presetPersonId = "",
            lastReporter = viewModel.lastReporter,
            knownReporters = reports.map { it.reporter },
            onDismiss = { editingId = null },
            onSave = { list -> list.firstOrNull()?.let { viewModel.saveReport(it) }; editingId = null },
            onDelete = { viewModel.deleteReport(editing.id); editingId = null },
            onFinal = { editingId = null; finalId = editing.id }
        )
    }

    // ----- the final opinion (نظر نهایی) of a superior on one report
    val finalEntry = finalId?.let { id -> reports.firstOrNull { it.id == id } }
    if (finalEntry != null) {
        FinalOpinionDialog(
            entry = finalEntry,
            name = nameOf(finalEntry),
            lastFinalBy = viewModel.lastFinalBy,
            onDismiss = { finalId = null },
            onSave = { by, reward, punishment ->
                viewModel.setFinalOpinion(finalEntry.id, by, reward, punishment)
                finalId = null
            },
            onClear = { viewModel.clearFinalOpinion(finalEntry.id); finalId = null }
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
private fun ReportCard(entry: ReportEntry, name: String, member: Boolean, kind: Int, onFinal: () -> Unit, onClick: () -> Unit) {
    AppCard(onClick = onClick, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyledName(name, member, Modifier.weight(1f))
                Text(JalaliDate.from(entry.date).display(), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            // The title is the heading; the description of the violation below it is smaller and in another colour.
            Text(entry.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if ((kind != 2 && entry.rewardHours > 0) || (kind != 1 && entry.punishmentHours > 0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (kind != 2 && entry.rewardHours > 0) HoursChip("تشویق ${formatHours(entry.rewardHours)} ساعت", RewardGreen)
                    if (kind != 1 && entry.punishmentHours > 0) HoursChip("تنبیه ${formatHours(entry.punishmentHours)} ساعت", PunishRed)
                }
            }
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, color = Color(0xFF6B6B88), fontSize = AppText.small, fontWeight = FontWeight.Normal)
            }
            // Who gave the reward / punishment: always shown, in plain sight (old records without a name say so).
            val hasReward = entry.rewardHours > 0
            val hasPunishment = entry.punishmentHours > 0
            val role = when {
                hasReward && !hasPunishment -> "تشویق‌کننده"
                hasPunishment && !hasReward -> "تنبیه‌کننده"
                else -> "ثبت‌کننده (تشویق / تنبیه)"
            }
            val roleColor = when {
                hasReward && !hasPunishment -> RewardGreen
                hasPunishment && !hasReward -> PunishRed
                else -> Navy
            }
            val who = entry.reporter.trim()
            Surface(color = roleColor.copy(alpha = .09f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("👤 $role:", color = roleColor, fontSize = AppText.small)
                    Text(
                        if (who.isBlank()) "نامشخص" else who,
                        color = if (who.isBlank()) Color.Gray else roleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppText.body
                    )
                }
            }
            // The final opinion of a superior: his hours are the criterion; the reporter's suggestion stays visible.
            if (entry.hasFinal) {
                Surface(color = Purple.copy(alpha = .09f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("✅ نظر نهایی: ${entry.finalBy.trim()}", color = Purple, fontWeight = FontWeight.Bold, fontSize = AppText.body)
                        Text(
                            if (entry.changedByFinal) {
                                "پیشنهاد اولیه: تشویق ${formatHours(entry.originalReward)} • تنبیه ${formatHours(entry.originalPunishment)} ساعت" +
                                    " ← ملاک: تشویق ${formatHours(entry.rewardHours)} • تنبیه ${formatHours(entry.punishmentHours)} ساعت"
                            } else {
                                "مطابق پیشنهاد؛ همان ساعت پیشنهادی ملاک است."
                            },
                            color = Color(0xFF6B6B88),
                            fontSize = AppText.small
                        )
                    }
                }
            }
            TextButton(onClick = onFinal, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(if (entry.hasFinal) "ویرایش نظر نهایی" else "✍️ ثبت نظر نهایی (مافوق)", fontWeight = FontWeight.Bold)
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

/**
 * Add ([initial] == null) or edit one report. A NEW report can be written for several people at once: each one gets his own
 * report with the same subject, date, hours and description. The hours written here are the reporter's SUGGESTION; a superior
 * can give the final opinion later (see [FinalOpinionDialog]). Editing also offers delete (confirmed by the caller).
 */
@Composable
private fun ReportEntryDialog(
    labelled: List<Pair<Person, String>>,
    initial: ReportEntry?,
    presetPersonId: String,
    lastReporter: String,
    knownReporters: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<ReportEntry>) -> Unit,
    onDelete: (() -> Unit)?,
    onFinal: (() -> Unit)?
) {
    val today = remember { LocalDate.now() }
    // Editing one report: a single person. A new report: a list of people (ids joined with commas, so it survives rotation).
    var personId by rememberSaveable { mutableStateOf(initial?.personId ?: presetPersonId) }
    var pickedIds by rememberSaveable { mutableStateOf(presetPersonId.takeIf { id -> labelled.any { it.first.id == id } } ?: "") }
    var dateEpoch by rememberSaveable { mutableStateOf((initial?.date ?: today).toEpochDay()) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var reporter by rememberSaveable { mutableStateOf(initial?.reporter ?: lastReporter) }
    var reward by rememberSaveable { mutableStateOf(initial?.rewardHours?.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var punishment by rememberSaveable { mutableStateOf(initial?.punishmentHours?.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpoch)
    val picked = pickedIds.split(",").filter { it.isNotBlank() }
    val hasFinal = initial?.hasFinal == true

    FormDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "ثبت سابقهٔ جدید" else "ویرایش سابقه",
        icon = "📝",
        subtitle = if (initial == null) "تشویق یا تنبیه یک یا چند نفر را ثبت کنید" else "اطلاعات سابقه را تغییر دهید",
        content = {
            FormSection(if (initial == null) "نفرات و تاریخ" else "نفر و تاریخ", icon = "👤") {
                if (initial == null) {
                    // Many people: each chosen name is listed (tap ✕ to remove it); the box below adds another one.
                    picked.forEach { id ->
                        val entry = labelled.firstOrNull { it.first.id == id }
                        Surface(
                            color = Purple.copy(alpha = .08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                StyledName(entry?.second ?: "؟", entry?.first?.isMember == true, Modifier.weight(1f))
                                IconButton(onClick = { pickedIds = picked.filterNot { it == id }.joinToString(",") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "حذف از فهرست")
                                }
                            }
                        }
                    }
                    PersonSearchField(
                        label = if (picked.isEmpty()) "نفر" else "افزودن نفر دیگر",
                        options = labelled.filter { it.first.id !in picked },
                        selectedId = "",
                        onSelect = { if (it.isNotBlank() && it !in picked) pickedIds = (picked + it).joinToString(",") },
                        clearOnSelect = true
                    )
                    // A shift leader chosen: one tap adds all his members too.
                    picked.mapNotNull { id -> labelled.firstOrNull { it.first.id == id }?.first }.filter { it.isLeader }.forEach { leader ->
                        val missing = labelled.map { it.first }.filter { it.leaderId == leader.id && it.id !in picked }
                        if (missing.isNotEmpty()) {
                            TextButton(onClick = { pickedIds = (picked + missing.map { it.id }).joinToString(",") }) {
                                Text("➕ افزودن همهٔ زیرمجموعه‌های ${leader.name} (${missing.size.toPersian()} نفر)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (picked.size > 1) {
                        Text(
                            "${picked.size.toPersian()} نفر انتخاب شده‌اند؛ برای هر نفر یک سابقهٔ جداگانه با همین مشخصات ثبت می‌شود.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (showErrors && picked.isEmpty()) {
                        Text("حداقل یک نفر را انتخاب کنید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    PersonSearchField(
                        label = "نفر",
                        options = labelled,
                        selectedId = personId,
                        onSelect = { personId = it },
                        fallbackLabel = initial.personName
                    )
                    if (showErrors && personId.isBlank()) {
                        Text("نفر را انتخاب کنید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                FormDateField("تاریخ", JalaliDate.from(date).display()) { showPicker = true }
                FormTextField(
                    value = reporter, onValueChange = { reporter = it },
                    label = "چه کسی تشویق / تنبیه کرده است؟ (نام)",
                    isError = showErrors && initial == null && reporter.isBlank()
                )
                // Names offered while typing: people who gave earlier reports first, then every registered person.
                val typed = normalizeFa(reporter.trim())
                val suggestions = remember(typed, knownReporters, labelled) {
                    if (typed.isEmpty()) {
                        emptyList()
                    } else {
                        (knownReporters.map { it.trim() } + labelled.map { it.first.name.trim() })
                            .filter { it.isNotBlank() }
                            .distinctBy { normalizeFa(it) }
                            .filter { val n = normalizeFa(it); n.contains(typed) && n != typed }
                            .sortedBy { if (normalizeFa(it).startsWith(typed)) 0 else 1 }
                            .take(6)
                    }
                }
                if (suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { name ->
                            Surface(
                                color = Purple.copy(alpha = .08f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { reporter = name }
                            ) {
                                Text("👤 $name", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontWeight = FontWeight.Bold, color = Navy)
                            }
                        }
                    }
                }
            }
            FormSection("شرح سابقه", icon = "🗒") {
                FormTextField(
                    value = title, onValueChange = { title = it },
                    label = "سابقه (موضوع)",
                    singleLine = false, minLines = 2,
                    isError = showErrors && title.isBlank()
                )
                FormTextField(
                    value = notes, onValueChange = { notes = it },
                    label = "شرح تخلف / توضیحات",
                    singleLine = false, minLines = 2
                )
            }
            FormSection(if (hasFinal) "ساعت (طبق نظر نهایی)" else "ساعت پیشنهادی", icon = "⏱") {
                if (initial != null && hasFinal) {
                    // The hours now are the superior's: they change only through his final opinion.
                    Text(
                        "نظر نهایی «${initial.finalBy.trim()}» ثبت شده است: تشویق ${formatHours(initial.rewardHours)} • " +
                            "تنبیه ${formatHours(initial.punishmentHours)} ساعت." +
                            if (initial.changedByFinal) {
                                " (پیشنهاد اولیه: تشویق ${formatHours(initial.originalReward)} • تنبیه ${formatHours(initial.originalPunishment)})"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (onFinal != null) {
                        OutlinedButton(onClick = onFinal, shape = FormFieldShape, modifier = Modifier.fillMaxWidth()) {
                            Text("ویرایش نظر نهایی")
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormTextField(
                            value = reward, onValueChange = { reward = it },
                            label = "ساعت تشویق (پیشنهادی)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            accent = RewardGreen,
                            tint = RewardGreen.copy(alpha = .06f),
                            modifier = Modifier.weight(1f)
                        )
                        FormTextField(
                            value = punishment, onValueChange = { punishment = it },
                            label = "ساعت تنبیه (پیشنهادی)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            accent = PunishRed,
                            tint = PunishRed.copy(alpha = .06f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val rewardH = parseHours(reward)
                    val punishH = parseHours(punishment)
                    if (rewardH > 0 || punishH > 0) {
                        val net = rewardH - punishH
                        Text(
                            "خالص: " + when {
                                net > 0 -> "+${formatHours(net)}"
                                net < 0 -> "−${formatHours(-net)}"
                                else -> "۰"
                            } + " ساعت",
                            color = if (net >= 0) RewardGreen else PunishRed,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        "این ساعت‌ها پیشنهاد ثبت‌کننده است. مافوق می‌تواند بعد از دریافت، «نظر نهایی» بدهد؛ اگر عدد را عوض کند " +
                            "عدد او ملاک است و اگر عوض نکند همین عدد پیشنهادی ملاک می‌ماند.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            FormSaveButton("ذخیره", onClick = {
                val valid = title.isNotBlank() && if (initial == null) picked.isNotEmpty() && reporter.isNotBlank() else personId.isNotBlank()
                if (valid) {
                    if (initial == null) {
                        onSave(
                            picked.map { id ->
                                ReportEntry(
                                    id = UUID.randomUUID().toString(),
                                    personId = id,
                                    personName = labelled.firstOrNull { it.first.id == id }?.first?.name ?: "",
                                    date = date,
                                    title = title.trim(),
                                    rewardHours = parseHours(reward),
                                    punishmentHours = parseHours(punishment),
                                    notes = notes.trim(),
                                    reporter = reporter.trim()
                                )
                            }
                        )
                    } else {
                        val person = labelled.firstOrNull { it.first.id == personId }?.first
                        onSave(
                            listOf(
                                initial.copy(
                                    personId = personId,
                                    personName = person?.name ?: initial.personName,
                                    date = date,
                                    title = title.trim(),
                                    // with a final opinion the hours stay as the superior set them
                                    rewardHours = if (hasFinal) initial.rewardHours else parseHours(reward),
                                    punishmentHours = if (hasFinal) initial.punishmentHours else parseHours(punishment),
                                    notes = notes.trim(),
                                    reporter = reporter.trim()
                                )
                            )
                        )
                    }
                } else {
                    showErrors = true
                }
            })
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
            title = "تاریخ سابقه",
            onDismiss = { showPicker = false },
            onPick = { dateEpoch = it.toEpochDay(); showPicker = false }
        )
    }
}

/**
 * The final opinion (نظر نهایی) of a superior on a report. The reporter's suggestion is shown; the hours typed here become the
 * ones that count (تشویق / تنبیه). If the superior leaves the numbers as they are, the suggestion stays the criterion.
 */
@Composable
private fun FinalOpinionDialog(
    entry: ReportEntry,
    name: String,
    lastFinalBy: String,
    onDismiss: () -> Unit,
    onSave: (by: String, reward: Double, punishment: Double) -> Unit,
    onClear: () -> Unit
) {
    var by by rememberSaveable { mutableStateOf(entry.finalBy.ifBlank { lastFinalBy }) }
    var reward by rememberSaveable { mutableStateOf(entry.rewardHours.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var punishment by rememberSaveable { mutableStateOf(entry.punishmentHours.takeIf { it > 0 }?.let { formatHours(it) } ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    FormDialog(
        onDismiss = onDismiss,
        title = "نظر نهایی",
        icon = "✍️",
        subtitle = "$name • ${JalaliDate.from(entry.date).display()}",
        content = {
            FormSection("پیشنهاد ثبت‌کننده", icon = "📝") {
                Text(entry.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                if (entry.reporter.isNotBlank()) {
                    Text("ثبت‌کننده: ${entry.reporter.trim()}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HoursChip("تشویق ${formatHours(entry.originalReward)} ساعت", RewardGreen)
                    HoursChip("تنبیه ${formatHours(entry.originalPunishment)} ساعت", PunishRed)
                }
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, color = Color(0xFF6B6B88), style = MaterialTheme.typography.bodySmall)
                }
            }
            FormSection("نظر نهایی شما", icon = "✍️") {
                FormTextField(
                    value = by, onValueChange = { by = it },
                    label = "نام مافوق (ثبت‌کنندهٔ نظر نهایی)",
                    isError = showErrors && by.isBlank()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormTextField(
                        value = reward, onValueChange = { reward = it },
                        label = "ساعت تشویق",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accent = RewardGreen,
                        tint = RewardGreen.copy(alpha = .06f),
                        modifier = Modifier.weight(1f)
                    )
                    FormTextField(
                        value = punishment, onValueChange = { punishment = it },
                        label = "ساعت تنبیه",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accent = PunishRed,
                        tint = PunishRed.copy(alpha = .06f),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "اگر عدد را عوض نکنید، همان عدد پیشنهادی ملاک تشویق / تنبیه است؛ اگر عوض کنید، عدد شما ملاک می‌شود.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            FormSaveButton("ثبت نظر نهایی", onClick = {
                if (by.isNotBlank()) onSave(by.trim(), parseHours(reward), parseHours(punishment)) else showErrors = true
            })
        },
        dismissButton = {
            Row {
                if (entry.hasFinal) {
                    TextButton(onClick = onClear) { Text("حذف نظر نهایی", color = Color.Red, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}

/** People in alphabetical order; equal names get a number so labels stay unique. */
internal fun uniqueLabels(people: List<Person>): List<Pair<Person, String>> {
    val counts = HashMap<String, Int>()
    return people.sortedByShiftName().map { p ->
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

internal fun periodFor(mode: Int, now: JalaliDate, customFrom: Long, customTo: Long): ReportPeriod = when (mode) {
    0 -> monthPeriod(now.year, now.month)
    1 -> if (now.month == 1) monthPeriod(now.year - 1, 12) else monthPeriod(now.year, now.month - 1)
    2 -> ReportPeriod(
        "سال ${now.year.toPersian()}",
        JalaliDate(now.year, 1, 1).toLocalDate(),
        JalaliDate(now.year, 12, JalaliDate.monthLength(now.year, 12)).toLocalDate()
    )
    3 -> ReportPeriod("همهٔ زمان‌ها", null, null)
    else -> {
        val from = customFrom.takeIf { it != NO_DATE }?.let { LocalDate.ofEpochDay(it) }
        val to = customTo.takeIf { it != NO_DATE }?.let { LocalDate.ofEpochDay(it) }
        val label = when {
            from == null && to == null -> "بازهٔ دلخواه (همهٔ زمان‌ها)"
            from != null && to != null -> "از ${JalaliDate.from(from).display()} تا ${JalaliDate.from(to).display()}"
            from != null -> "از ${JalaliDate.from(from).display()}"
            else -> "تا ${JalaliDate.from(to!!).display()}"
        }
        ReportPeriod(label, from, to)
    }
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(send, "ارسال سوابق").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

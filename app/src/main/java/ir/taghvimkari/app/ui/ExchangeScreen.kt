package ir.taghvimkari.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.data.PdfGroup
import ir.taghvimkari.app.data.PdfRow
import ir.taghvimkari.app.data.SummaryPdf
import ir.taghvimkari.app.data.ShareBytes
import ir.taghvimkari.app.data.Xlsx
import ir.taghvimkari.app.domain.formatNumber
import ir.taghvimkari.app.domain.roundWhole
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.formatSigned
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ReportEntry
import ir.taghvimkari.app.domain.displayOrder
import ir.taghvimkari.app.domain.evaluationPeriods
import ir.taghvimkari.app.domain.formatHours
import ir.taghvimkari.app.domain.sortedByShiftName
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate
import kotlin.math.abs

/**
 * «گزارش» tab: the one place where staff, reward / punishment records and evaluations are sent and received.
 *
 *  - نفرات: everything is sent; the receiver's staff list is wiped and replaced completely by the file.
 *  - سوابق: all records, or the records of chosen people, or the records of a period; the receiver adds them to
 *    his own (nothing is replaced) and is warned about duplicates, which are never registered.
 *  - ارزیابی: all evaluations, or those of a period; same merge rule as records.
 * A duplicate is: same reporter + same person + same date (evaluations: same evaluator + person + period).
 */
@Composable
fun ExchangeScreen(viewModel: PeopleViewModel) {
    var section by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }
    val receive = { filePicker.launch(arrayOf("*/*")) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        SectionSwitch(
            listOf(
                "نفرات (${viewModel.people.size.toPersian()})",
                "سوابق (${viewModel.reports.size.toPersian()})",
                "ارزیابی (${viewModel.evaluations.size.toPersian()})",
                "جمع‌بندی"
            ),
            section
        ) { section = it }
        when (section) {
            0 -> PeopleExchange(viewModel.people, context, receive)
            1 -> ReportsExchange(viewModel.people, viewModel.reports, context, receive)
            2 -> EvaluationsExchange(viewModel.people, viewModel.evaluations, context, receive)
            else -> SummaryExchange(viewModel, context)
        }
    }
}

// ============================================================ نفرات

@Composable
private fun PeopleExchange(people: List<Person>, context: android.content.Context, onReceive: () -> Unit) {
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    ExStep(1, "چه چیزی ارسال می‌شود؟") {
        Text("همهٔ نفرات ثبت‌شده", fontWeight = FontWeight.Bold)
        BigCount(people.size, "نفر")
        if (people.isEmpty()) {
            Note("هنوز نیرویی ثبت نشده است.")
        } else {
            FoldBox("نمایش نام‌ها") {
                people.sortedByShiftName().forEach { StyledName("• ${it.name}", it.isMember) }
            }
        }
    }
    ExStep(2, "ارسال و دریافت") {
        SendReceiveButtons(
            sendLabel = "ارسال ${people.size.toPersian()} نفر",
            sendEnabled = people.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.sharePeople(context, people) },
            onReceive = onReceive
        )
        Note(
            "دریافت‌کننده: با وارد کردن این فایل، فهرست نیروهای فعلی او کاملاً پاک و با این فایل جایگزین می‌شود و هیچ اطلاعاتی از فهرست قبلی نمی‌ماند.",
            warning = true
        )
    }
}

// ============================================================ سوابق

@Composable
private fun ReportsExchange(
    people: List<Person>,
    reports: List<ReportEntry>,
    context: android.content.Context,
    onReceive: () -> Unit
) {
    val todayJalali = remember { JalaliDate.from(LocalDate.now()) }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 all, 1 chosen people, 2 a period
    var picked by rememberSaveable { mutableStateOf("") } // ids of the chosen people, joined by ","
    var addKey by rememberSaveable { mutableStateOf(0) }
    var periodMode by rememberSaveable { mutableStateOf(0) } // 0 this month, 1 last month, 2 this year, 4 custom
    var customFrom by rememberSaveable { mutableStateOf(NO_DATE) }
    var customTo by rememberSaveable { mutableStateOf(NO_DATE) }
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    val labelled = remember(people) { uniqueLabels(people).map { (p, n) -> p to if (p.active) n else "$n (غیرفعال)" } }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val memberIds = remember(people) { people.filter { it.isMember }.map { it.id }.toSet() }
    val order = remember(people) { people.displayOrder() }
    val pickedIds = picked.split(",").filter { it.isNotBlank() }
    val period = periodFor(periodMode, todayJalali, customFrom, customTo)
    val customMissing = periodMode == 4 && customFrom == NO_DATE && customTo == NO_DATE

    val selected = when (mode) {
        0 -> reports
        1 -> reports.filter { it.personId in pickedIds }
        else -> if (customMissing) emptyList() else reports.filter { period.contains(it.date) }
    }.sortedWith(compareBy<ReportEntry> { order[it.personId] ?: Int.MAX_VALUE }.thenByDescending { it.date })
    val ready = when (mode) {
        1 -> pickedIds.isNotEmpty()
        2 -> !customMissing
        else -> true
    }
    val totals = selected.totals()
    val perPerson = selected.groupBy { it.personId }
        .map { (id, list) -> Triple(id, nameById[id] ?: list.first().personName, list.size) }
        .sortedBy { order[it.first] ?: Int.MAX_VALUE }

    ExStep(1, "کدام سوابق ارسال شود؟") {
        ModeOption(mode == 0, "همهٔ سوابق", "تمام سوابق ثبت‌شده") { mode = 0 }
        ModeOption(mode == 1, "سوابق یک یا چند نفر", "نفرات را خودتان انتخاب می‌کنید") { mode = 1 }
        ModeOption(mode == 2, "سوابق یک بازهٔ زمانی", "فقط سوابقی که تاریخ‌شان در بازه است") { mode = 2 }

        if (mode == 1) {
            pickedIds.forEach { id ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Purple.copy(alpha = .08f)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledName(nameById[id] ?: "نامشخص", id in memberIds, Modifier.weight(1f))
                    IconButton(onClick = { picked = pickedIds.filterNot { it == id }.joinToString(",") }) {
                        Icon(Icons.Default.Clear, contentDescription = "حذف از انتخاب")
                    }
                }
            }
            key(addKey) {
                PersonSearchField(
                    label = if (pickedIds.isEmpty()) "افزودن نفر" else "افزودن نفر دیگر",
                    options = labelled.filter { it.first.id !in pickedIds },
                    selectedId = "",
                    onSelect = { id ->
                        if (id.isNotBlank()) {
                            picked = (pickedIds + id).joinToString(",")
                            addKey++
                        }
                    }
                )
            }
        }
        if (mode == 2) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("این ماه" to 0, "ماه قبل" to 1, "امسال" to 2, "بازهٔ دلخواه" to 4).forEach { (label, m) ->
                    ModeChip(label, periodMode == m) { periodMode = m }
                }
            }
            if (periodMode == 4) {
                DateRangeBoxes(customFrom, customTo) { f, t -> customFrom = f; customTo = t }
            }
        }
    }

    ExStep(2, "خلاصهٔ آنچه ارسال می‌شود") {
        Text(
            when (mode) {
                0 -> "همهٔ سوابق ثبت‌شده"
                1 -> if (pickedIds.isEmpty()) "هنوز نفری انتخاب نشده است" else "سوابق ${pickedIds.size.toPersian()} نفر انتخاب‌شده"
                else -> if (customMissing) "تاریخ شروع یا پایان بازه را انتخاب کنید" else "سوابق بازهٔ ${period.label}"
            },
            fontWeight = FontWeight.Bold
        )
        BigCount(selected.size, "سابقه", "از ${reports.size.toPersian()} سابقهٔ ثبت‌شده")
        if (selected.isNotEmpty()) {
            Note(
                "تشویق ${formatHours(totals.reward)} ساعت • تنبیه ${formatHours(totals.punishment)} ساعت • " +
                    "خالص ${if (totals.net < 0) "−" else ""}${formatHours(abs(totals.net))} ساعت"
            )
            if (perPerson.size > 1) {
                FoldBox("به تفکیک نفر") {
                    perPerson.forEach { (id, name, count) -> StyledName("• $name: ${count.toPersian()} سابقه", id in memberIds) }
                }
            }
        } else if (ready) {
            Note("با این انتخاب سابقه‌ای پیدا نشد.")
        }
    }

    ExStep(3, "ارسال و دریافت") {
        SendReceiveButtons(
            sendLabel = "ارسال ${selected.size.toPersian()} سابقه",
            sendEnabled = ready && selected.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.shareReports(context, selected) },
            onReceive = onReceive
        )
        Note(
            "دریافت‌کننده: این سوابق به سوابق خودش اضافه می‌شود و چیزی از اطلاعاتش حذف یا عوض نمی‌شود. " +
                "سابقه‌ای که ثبت‌کننده، نفر و تاریخ آن با یکی از سوابق او یکی باشد، تکراری است؛ ثبت نمی‌شود و به او هشدار داده می‌شود."
        )
    }
}

// ============================================================ ارزیابی

@Composable
private fun EvaluationsExchange(
    people: List<Person>,
    evaluations: List<EvaluationEntry>,
    context: android.content.Context,
    onReceive: () -> Unit
) {
    // «ارسال گزارش ارزیابی»: either the evaluations themselves (a file) or the advanced report (a short text).
    var view by rememberSaveable { mutableStateOf(0) }
    SectionSwitch(listOf("ارسال/دریافت ارزیابی‌ها", "گزارش پیشرفته"), view) { view = it }
    // Back from the advanced report returns to the send / receive part instead of leaving the app.
    BackHandler(enabled = view == 1) { view = 0 }
    if (view == 1) {
        AdvancedReportSection(people = people, evaluations = evaluations)
    } else {
        EvaluationFileExchange(people, evaluations, context, onReceive)
    }
}

@Composable
private fun EvaluationFileExchange(
    people: List<Person>,
    evaluations: List<EvaluationEntry>,
    context: android.content.Context,
    onReceive: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0 all, 1 a period
    var customFrom by rememberSaveable { mutableStateOf(NO_DATE) }
    var customTo by rememberSaveable { mutableStateOf(NO_DATE) }
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    val nameById = remember(people) { people.associate { it.id to it.name } }
    val memberIds = remember(people) { people.filter { it.isMember }.map { it.id }.toSet() }
    val order = remember(people) { people.displayOrder() }
    val periods = remember(evaluations) { evaluationPeriods(evaluations) }
    val periodLabels = remember(periods, evaluations, nameById) { periodLabelsWithEvaluators(periods, evaluations, nameById) }
    val customMissing = customFrom == NO_DATE && customTo == NO_DATE

    val selected = when (mode) {
        0 -> evaluations
        else -> if (customMissing) emptyList() else evaluations.filter { e ->
            // an evaluation belongs to the range when its evaluation period overlaps the range
            (customFrom == NO_DATE || e.dateTo.toEpochDay() >= customFrom) &&
                (customTo == NO_DATE || e.date.toEpochDay() <= customTo)
        }
    }.sortedWith(compareBy<EvaluationEntry> { order[it.personId] ?: Int.MAX_VALUE }.thenByDescending { it.date })
    val ready = mode == 0 || !customMissing
    val incomplete = selected.count { !it.isComplete }
    val perPerson = selected.groupBy { it.personId }
        .map { (id, list) -> Triple(id, nameById[id] ?: list.first().personName, list.size) }
        .sortedBy { order[it.first] ?: Int.MAX_VALUE }
    val rangeText = when {
        customFrom != NO_DATE && customTo != NO_DATE -> "از ${dateText(customFrom)} تا ${dateText(customTo)}"
        customFrom != NO_DATE -> "از ${dateText(customFrom)}"
        customTo != NO_DATE -> "تا ${dateText(customTo)}"
        else -> "" // no date chosen: nothing to convert (NO_DATE is not a real day and would crash)
    }

    ExStep(1, "کدام ارزیابی‌ها ارسال شود؟") {
        ModeOption(mode == 0, "همهٔ ارزیابی‌ها", "تمام ارزیابی‌های ثبت‌شده") { mode = 0 }
        ModeOption(mode == 1, "ارزیابی‌های یک بازهٔ زمانی", "ارزیابی‌هایی که دورهٔ آن‌ها با بازه همپوشانی دارد") { mode = 1 }
        if (mode == 1) {
            if (periods.isNotEmpty()) {
                val current = periods.indexOfFirst { it.first.toEpochDay() == customFrom && it.second.toEpochDay() == customTo }
                FilterDropdown("انتخاب از دوره‌های ثبت‌شده", periodLabels, periodLabels.getOrNull(current) ?: "—") { label ->
                    val i = periodLabels.indexOf(label)
                    if (i >= 0) {
                        customFrom = periods[i].first.toEpochDay()
                        customTo = periods[i].second.toEpochDay()
                    }
                }
            }
            DateRangeBoxes(customFrom, customTo) { f, t -> customFrom = f; customTo = t }
        }
    }

    ExStep(2, "خلاصهٔ آنچه ارسال می‌شود") {
        Text(
            when (mode) {
                0 -> "همهٔ ارزیابی‌های ثبت‌شده"
                else -> if (customMissing) "تاریخ شروع یا پایان بازه را انتخاب کنید" else "ارزیابی‌های بازهٔ $rangeText"
            },
            fontWeight = FontWeight.Bold
        )
        BigCount(selected.size, "ارزیابی", "از ${evaluations.size.toPersian()} ارزیابی ثبت‌شده")
        if (selected.isNotEmpty()) {
            if (incomplete > 0) Note("${incomplete.toPersian()} مورد ناتمام است.")
            if (perPerson.size > 1) {
                FoldBox("به تفکیک نفر") {
                    perPerson.forEach { (id, name, count) -> StyledName("• $name: ${count.toPersian()} ارزیابی", id in memberIds) }
                }
            }
        } else if (ready) {
            Note(if (evaluations.isEmpty()) "هنوز ارزیابی‌ای ثبت نشده است." else "با این انتخاب ارزیابی‌ای پیدا نشد.")
        }
    }

    ExStep(3, "ارسال و دریافت") {
        SendReceiveButtons(
            sendLabel = "ارسال ${selected.size.toPersian()} ارزیابی",
            sendEnabled = ready && selected.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.shareEvaluations(context, selected) },
            onReceive = onReceive
        )
        Note(
            "دریافت‌کننده: این ارزیابی‌ها به ارزیابی‌های خودش اضافه می‌شود و چیزی از اطلاعاتش حذف یا عوض نمی‌شود. " +
                "ارزیابی‌ای که ارزیاب، نفر و دورهٔ آن با یکی از ارزیابی‌های او یکی باشد، تکراری است؛ ثبت نمی‌شود و به او هشدار داده می‌شود."
        )
    }
}

// ============================================================ shared pieces

private fun dateText(epoch: Long): String = if (epoch == NO_DATE) "" else JalaliDate.from(LocalDate.ofEpochDay(epoch)).display()

/** A numbered step: every section reads top to bottom (what → summary → send / receive). */
@Composable
private fun ExStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Purple), contentAlignment = Alignment.Center) {
                Text(number.toPersian(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                title,
                Modifier.padding(start = 10.dp),
                fontWeight = FontWeight.Bold,
                color = Navy,
                style = MaterialTheme.typography.titleMedium
            )
        }
        content()
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) Purple else Color.Unspecified)
            Text(hint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (on) Purple else Purple.copy(alpha = .12f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (on) Color.White else Purple,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** «از» / «تا» date boxes with the Jalali date picker; [onChange] gets the new (from, to). */
@Composable
private fun DateRangeBoxes(from: Long, to: Long, onChange: (Long, Long) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    val today = remember { LocalDate.now() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) { Text(dateBoxText("از", from)) }
        OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) { Text(dateBoxText("تا", to)) }
    }
    if (picker != 0) {
        val current = if (picker == 1) from else to
        JalaliDatePickerDialog(
            initial = LocalDate.ofEpochDay(if (current == NO_DATE) today.toEpochDay() else current),
            title = if (picker == 1) "تاریخ شروع بازه" else "تاریخ پایان بازه",
            onDismiss = { picker = 0 },
            onPick = { d ->
                val e = d.toEpochDay()
                if (picker == 1) onChange(e, if (to != NO_DATE && to < e) e else to)
                else onChange(if (from != NO_DATE && from > e) e else from, e)
                picker = 0
            }
        )
    }
}

@Composable
private fun BigCount(count: Int, unit: String, of: String = "") {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(count.toPersian(), color = Purple, fontWeight = FontWeight.Bold, fontSize = 32.sp)
        Text(unit, Modifier.padding(bottom = 6.dp), color = Purple, fontWeight = FontWeight.Bold)
        if (of.isNotBlank()) Text(of, Modifier.padding(bottom = 6.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SendReceiveButtons(
    sendLabel: String,
    sendEnabled: Boolean,
    sendFailed: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSend, enabled = sendEnabled, modifier = Modifier.weight(1f)) { Text(sendLabel, maxLines = 1) }
        OutlinedButton(onClick = onReceive, modifier = Modifier.weight(1f)) { Text("دریافت فایل", maxLines = 1) }
    }
    if (sendFailed) Text("ارسال انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SectionSwitch(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Purple.copy(alpha = .12f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = selected == index
            Surface(
                color = if (on) Purple else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(index) }
            ) {
                Text(
                    label,
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    color = if (on) Color.White else Purple,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error else Color.Gray,
        fontWeight = if (warning) FontWeight.Bold else FontWeight.Normal
    )
}

// ============================================================ جمع‌بندی (PDF)

/** «جمع‌بندی» of a chosen period, sent as a PDF table (the same table as the جمع‌بندی page). */
@Composable
private fun SummaryExchange(viewModel: PeopleViewModel, context: android.content.Context) {
    var fromEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var toEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    // The employment kinds that go into the PDF; all three are on until the person turns some off.
    var pickedKinds by rememberSaveable { mutableStateOf(SUMMARY_ORDER.joinToString(",") { it.name }) }
    val chosenKinds = SUMMARY_ORDER.filter { it.name in pickedKinds.split(",") }
    val ready = fromEpoch != NO_DATE && toEpoch != NO_DATE

    val rows = if (!ready) emptyList() else {
        val from = LocalDate.ofEpochDay(fromEpoch)
        val to = LocalDate.ofEpochDay(toEpoch)
        viewModel.people.filter { it.active || showInactive }.sortedByShiftName().map { summaryRowFor(viewModel, it, from, to) }
    }
    val shownRows = rows.filter { it.person.employment in chosenKinds }

    ExStep(1, "کدام بازه؟") {
        DateRangeBoxes(fromEpoch, toEpoch) { f, t -> fromEpoch = f; toEpoch = t }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("نمایش غیرفعال‌ها (از واحد رفته‌ها)", Modifier.weight(1f))
            Switch(checked = showInactive, onCheckedChange = { showInactive = it })
        }
    }
    ExStep(2, "کدام وضعیت استخدامی؟") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUMMARY_ORDER.forEach { emp ->
                val count = rows.count { it.person.employment == emp }
                val on = emp in chosenKinds
                SelectChip(
                    text = if (ready) "${summaryTitle(emp)} (${count.toPersian()})" else summaryTitle(emp),
                    selected = on,
                    onClick = {
                        val next = if (on) chosenKinds - emp else chosenKinds + emp
                        pickedKinds = next.joinToString(",") { it.name }
                    }
                )
            }
        }
        if (chosenKinds.isEmpty()) Note("دست‌کم یک وضعیت استخدامی را انتخاب کنید.")
    }
    ExStep(3, "خلاصهٔ آنچه ارسال می‌شود") {
        if (!ready) {
            Note("تاریخ «از» و «تا» را انتخاب کنید.")
        } else {
            Text("جمع‌بندی ${periodText(LocalDate.ofEpochDay(fromEpoch), LocalDate.ofEpochDay(toEpoch))}", fontWeight = FontWeight.Bold)
            Text(
                if (chosenKinds.size == SUMMARY_ORDER.size) "همهٔ وضعیت‌های استخدامی"
                else chosenKinds.joinToString("، ") { summaryTitle(it) },
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
            BigCount(shownRows.size, "نفر")
            if (shownRows.isEmpty()) Note("نیرویی برای این بازه و وضعیت استخدامی پیدا نشد.")
        }
    }

    /** Makes and shares the PDF for the chosen employment kinds. */
    fun makePdf(kinds: List<Employment>) {
        val groups = kinds.mapNotNull { emp ->
            val group = rows.filter { it.person.employment == emp }
            if (group.isEmpty()) null else PdfGroup(
                title = summaryTitle(emp),
                rows = group.map { r ->
                    PdfRow(
                        name = r.person.name + (if (!r.person.active) " • غیرفعال" else ""),
                        total = formatNumber(r.total),
                        extra = r.cell?.extra?.let { formatSigned(it) } ?: "—",
                        level = r.cell?.level?.let { formatNumber(it) } ?: "—",
                        reward = formatSigned(r.reward),
                        evaluation = r.evaluation?.let { formatScore(it) } ?: "—"
                    )
                },
                average = formatNumber(group.sumOf { it.total } / group.size)
            )
        }
        failed = !SummaryPdf.share(
            context,
            periodText(LocalDate.ofEpochDay(fromEpoch), LocalDate.ofEpochDay(toEpoch)),
            groups
        )
    }

    /** The same table as an Excel file: one sheet, one row per person, real numbers (not text). */
    fun makeExcel() {
        val header = listOf("وضعیت استخدامی", "نام", "جمع", "ملاحظات", "رئیس", "تشویق / تنبیه", "ارزشیابی")
        val body = chosenKinds.flatMap { emp ->
            shownRows.filter { it.person.employment == emp }.map { r ->
                listOf<Any?>(
                    summaryTitle(emp),
                    r.person.name + (if (!r.person.active) " • غیرفعال" else ""),
                    roundWhole(r.total),
                    r.cell?.extra?.let { roundWhole(it) },
                    r.cell?.level?.let { roundWhole(it) },
                    roundWhole(r.reward),
                    r.evaluation?.let { roundWhole(it) }
                )
            }
        }
        failed = !ShareBytes.share(
            context,
            "taghvim-summary-${LocalDate.now()}.xlsx",
            Xlsx.MIME,
            Xlsx.build("جمع‌بندی", listOf(header) + body, listOf(18, 28, 10, 12, 10, 16, 12)),
            "ارسال جمع‌بندی (Excel)"
        )
    }

    ExStep(4, "خروجی PDF یا Excel") {
        ActionButton(
            "ساخت و ارسال PDF جمع‌بندی",
            { makePdf(chosenKinds) },
            Modifier.fillMaxWidth(),
            icon = Icons.Default.Share,
            enabled = ready && shownRows.isNotEmpty()
        )
        ActionButton(
            "ساخت و ارسال Excel جمع‌بندی",
            { makeExcel() },
            Modifier.fillMaxWidth(),
            icon = Icons.Default.Share,
            filled = false,
            enabled = ready && shownRows.isNotEmpty()
        )
        if (failed) Text("ساخت یا ارسال فایل انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Note("خروجی جمع‌بندی PDF یا Excel است و برای خواندن در برنامه دریافت نمی‌شود.")
    }
}

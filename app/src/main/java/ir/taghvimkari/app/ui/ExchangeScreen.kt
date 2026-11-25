package ir.taghvimkari.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ReportEntry
import ir.taghvimkari.app.domain.compareNames
import ir.taghvimkari.app.domain.evaluationPeriods
import ir.taghvimkari.app.domain.formatHours
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.periodKeyOf
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.searchTooShort
import ir.taghvimkari.app.domain.shiftRank
import ir.taghvimkari.app.domain.sortedByShiftName
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate
import kotlin.math.abs

private const val ALL_LABEL = "همه"

/**
 * «گزارش» tab: the one place where staff, reward / punishment records and evaluations are sent and received.
 * Each section has the same filters it had on its own page (name search / period / person / evaluator), and
 * says exactly what is going to be sent (how many items, which period, which person) before the button is pressed.
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
                "ارزیابی (${viewModel.evaluations.size.toPersian()})"
            ),
            section
        ) { section = it }
        when (section) {
            0 -> PeopleExchange(viewModel.people, context, receive)
            1 -> ReportsExchange(viewModel.people, viewModel.reports, context, receive)
            else -> EvaluationsExchange(viewModel.people, viewModel.evaluations, context, receive)
        }
    }
}

// ============================================================ نفرات

@Composable
private fun PeopleExchange(people: List<Person>, context: android.content.Context, onReceive: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sendFailed by rememberSaveable { mutableStateOf(false) }
    val searching = query.isNotBlank()
    val selected = if (searching) people.filter { nameMatches(it.name, query) } else people

    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("چه چیزی ارسال شود؟")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused(),
            label = { Text("جستجوی نام (خالی = همهٔ نفرات؛ حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف)") },
            singleLine = true
        )
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("آنچه ارسال می‌شود")
        Text(
            if (searching) "نفرات منطبق با «${query.trim()}»" else "همهٔ نفرات ثبت‌شده",
            fontWeight = FontWeight.Bold
        )
        Text("${selected.size.toPersian()} نفر از ${people.size.toPersian()} نفر", color = Purple, fontWeight = FontWeight.Bold)
        if (searchTooShort(query)) {
            Text("برای جستجو حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف بنویسید.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (selected.isEmpty() && searching) {
            Text("نیرویی پیدا نشد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        if (selected.isNotEmpty()) {
            FoldBox("نمایش نام‌ها") {
                selected.sortedByShiftName().forEach { Text("• ${it.name}") }
            }
        }
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExchangeRow(
            sendEnabled = selected.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.sharePeople(context, selected) },
            onReceive = onReceive
        )
        Note(
            "فایل دریافتی، فهرست نیروهای فعلی را کاملاً جایگزین می‌کند (ادغام نمی‌شود) و نام‌های تکراری حذف می‌شوند." +
                if (searching) " چون فقط بخشی از نفرات ارسال می‌شود، دستگاه گیرنده فقط همین نفرات را خواهد داشت." else ""
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
    val today = remember { LocalDate.now() }
    val todayJalali = remember { JalaliDate.from(today) }
    var mode by rememberSaveable { mutableStateOf(3) } // 0 this month, 1 last month, 2 this year, 3 all, 4 custom
    var customFrom by rememberSaveable { mutableStateOf(NO_DATE) }
    var customTo by rememberSaveable { mutableStateOf(NO_DATE) }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var personFilter by rememberSaveable { mutableStateOf("") }
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    val labelled = remember(people) { uniqueLabels(people).map { (p, n) -> p to if (p.active) n else "$n (غیرفعال)" } }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val rankById = remember(people) { people.associate { it.id to it.shiftRank } }
    val nameOf: (ReportEntry) -> String = { e -> nameById[e.personId] ?: e.personName }

    val period = remember(mode, customFrom, customTo) { periodFor(mode, todayJalali, customFrom, customTo) }
    val filtered = reports
        .filter { period.contains(it.date) && (personFilter.isBlank() || it.personId == personFilter) }
        .sortedWith(compareBy<ReportEntry> { rankById[it.personId] ?: 9 }.thenByDescending { it.date }.thenBy { nameOf(it) })
    val totals = filtered.totals()
    val personLabel = labelled.firstOrNull { it.first.id == personFilter }?.second ?: ALL_PEOPLE
    val perPerson = filtered.groupBy { it.personId }
        .map { (id, list) -> Triple(id, nameOf(list.first()), list.size) }
        .sortedWith(compareBy<Triple<String, String, Int>> { rankById[it.first] ?: 9 }.thenComparator { a, b -> compareNames(a.second, b.second) })

    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("چه چیزی ارسال شود؟")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PERIOD_LABELS.forEachIndexed { index, label ->
                val on = mode == index
                Surface(
                    color = if (on) Purple else Purple.copy(alpha = .12f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { mode = index }
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
        }
        if (mode == 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) { Text(dateBoxText("از", customFrom)) }
                OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) { Text(dateBoxText("تا", customTo)) }
            }
        }
        PersonSearchField(
            label = "نفر (خالی = همه نفرات)",
            options = labelled,
            selectedId = personFilter,
            onSelect = { personFilter = it }
        )
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("آنچه ارسال می‌شود")
        Text("بازه: ${period.label}", fontWeight = FontWeight.Bold)
        Text("نفر: $personLabel", fontWeight = FontWeight.Bold)
        Text("${totals.count.toPersian()} سابقه از ${reports.size.toPersian()} سابقهٔ ثبت‌شده", color = Purple, fontWeight = FontWeight.Bold)
        if (totals.count > 0) {
            Text(
                "تشویق ${formatHours(totals.reward)} ساعت • تنبیه ${formatHours(totals.punishment)} ساعت • " +
                    "خالص ${if (totals.net < 0) "−" else ""}${formatHours(abs(totals.net))} ساعت",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            FoldBox("به تفکیک نفر") {
                perPerson.forEach { (_, name, count) -> Text("• $name: ${count.toPersian()} سابقه") }
            }
        } else {
            Text("با این انتخاب سابقه‌ای پیدا نشد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExchangeRow(
            sendEnabled = filtered.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.shareReports(context, filtered) },
            onReceive = onReceive
        )
        Note("سوابق دریافتی به سوابق فعلی اضافه می‌شود و چیزی حذف نمی‌شود.")
    }

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
    var personFilter by rememberSaveable { mutableStateOf("") }
    var evaluatorFilter by rememberSaveable { mutableStateOf("") }
    var periodFilter by rememberSaveable { mutableStateOf("") } // key of the evaluation period, blank = all
    var sendFailed by rememberSaveable { mutableStateOf(false) }

    val labelled = remember(people) { uniqueLabels(people).map { (p, n) -> p to if (p.active) n else "$n (غیرفعال)" } }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val rankById = remember(people) { people.associate { it.id to it.shiftRank } }
    val nameOf: (EvaluationEntry) -> String = { e -> nameById[e.personId] ?: e.personName }
    val evaluatorList = remember(evaluations, people) {
        evaluations.map { it.evaluatorId }.filter { it.isNotBlank() }.distinct()
            .map { id -> id to (nameById[id] ?: evaluations.firstOrNull { it.evaluatorId == id }?.evaluatorName.orEmpty()) }
            .sortedWith(compareBy<Pair<String, String>> { rankById[it.first] ?: 9 }.thenComparator { a, b -> compareNames(a.second, b.second) })
    }
    val evaluatorLabels = remember(evaluatorList) { numberedLabels(evaluatorList.map { it.second }) }
    val periods = remember(evaluations) { evaluationPeriods(evaluations) }
    val periodLabels = remember(periods) { periods.map { periodText(it.first, it.second) } }
    val periodIndex = periods.indexOfFirst { periodKeyOf(it.first, it.second) == periodFilter }

    val filtered = evaluations
        .filter {
            (personFilter.isBlank() || it.personId == personFilter) &&
                (evaluatorFilter.isBlank() || it.evaluatorId == evaluatorFilter) &&
                (periodFilter.isBlank() || it.periodKey == periodFilter)
        }
        .sortedWith(compareBy<EvaluationEntry> { rankById[it.personId] ?: 9 }.thenByDescending { it.date }.thenBy { nameOf(it) })
    val periodShown = periodLabels.getOrNull(periodIndex) ?: ALL_LABEL
    val personShown = labelled.firstOrNull { it.first.id == personFilter }?.second ?: ALL_PEOPLE
    val evaluatorShown = evaluatorList.indexOfFirst { it.first == evaluatorFilter }.takeIf { it >= 0 }?.let { evaluatorLabels[it] } ?: ALL_LABEL
    val incomplete = filtered.count { !it.isComplete }
    val perPerson = filtered.groupBy { it.personId }
        .map { (id, list) -> Triple(id, nameOf(list.first()), list.size) }
        .sortedWith(compareBy<Triple<String, String, Int>> { rankById[it.first] ?: 9 }.thenComparator { a, b -> compareNames(a.second, b.second) })

    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("چه چیزی ارسال شود؟")
        if (periods.isNotEmpty()) {
            FilterDropdown("دورهٔ ارزیابی", listOf(ALL_LABEL) + periodLabels, periodShown) { label ->
                val i = periodLabels.indexOf(label)
                periodFilter = if (i >= 0) periodKeyOf(periods[i].first, periods[i].second) else ""
            }
        }
        PersonSearchField(
            label = "نفر (خالی = همه نفرات)",
            options = labelled,
            selectedId = personFilter,
            onSelect = { personFilter = it }
        )
        if (evaluatorList.isNotEmpty()) {
            FilterDropdown("ارزیاب", listOf(ALL_LABEL) + evaluatorLabels, evaluatorShown) { label ->
                evaluatorFilter = evaluatorList.getOrNull(evaluatorLabels.indexOf(label))?.first ?: ""
            }
        }
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("آنچه ارسال می‌شود")
        Text("دوره: $periodShown", fontWeight = FontWeight.Bold)
        Text("نفر: $personShown", fontWeight = FontWeight.Bold)
        Text("ارزیاب: $evaluatorShown", fontWeight = FontWeight.Bold)
        Text("${filtered.size.toPersian()} ارزیابی از ${evaluations.size.toPersian()} ارزیابی ثبت‌شده", color = Purple, fontWeight = FontWeight.Bold)
        if (filtered.isNotEmpty()) {
            if (incomplete > 0) {
                Text("${incomplete.toPersian()} مورد ناتمام است.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            FoldBox("به تفکیک نفر") {
                perPerson.forEach { (_, name, count) -> Text("• $name: ${count.toPersian()} ارزیابی") }
            }
        } else {
            Text(
                if (evaluations.isEmpty()) "هنوز ارزیابی‌ای ثبت نشده است." else "با این انتخاب ارزیابی‌ای پیدا نشد.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExchangeRow(
            sendEnabled = filtered.isNotEmpty(),
            sendFailed = sendFailed,
            onSend = { sendFailed = !DataExchange.shareEvaluations(context, filtered) },
            onReceive = onReceive
        )
        Note("ارزیابی‌های دریافتی به ارزیابی‌های فعلی اضافه می‌شود و چیزی حذف نمی‌شود.")
    }
}

// ============================================================ shared pieces

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
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Purple, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
}

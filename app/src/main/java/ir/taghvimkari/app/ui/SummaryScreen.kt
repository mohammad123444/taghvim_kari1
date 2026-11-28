package ir.taghvimkari.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.Evaluations
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.SummaryCell
import ir.taghvimkari.app.domain.formatNumber
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.formatSigned
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.searchTooShort
import ir.taghvimkari.app.domain.parseNumber
import ir.taghvimkari.app.domain.periodKeyOf
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.sortedByShiftName
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate

private val NAME_W = 112.dp
private val COL_W = 70.dp

private fun monthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> =
    JalaliDate(year, month, 1).toLocalDate() to
        JalaliDate(year, month, JalaliDate.monthLength(year, month)).toLocalDate()

/** Summary groups, in this order: پیمانکاری، رسمی، قرارداد مستقیم. */
internal val SUMMARY_ORDER = listOf(Employment.OFFICIAL, Employment.CONTRACTUAL, Employment.CONTRACTOR)

internal fun summaryTitle(e: Employment): String = if (e == Employment.CONTRACTUAL) "قرارداد مستقیم" else e.title

private fun employmentAccent(e: Employment): Color = when (e) {
    Employment.OFFICIAL -> Purple
    Employment.CONTRACTUAL -> Color(0xFF1E88A8)
    Employment.CONTRACTOR -> Color(0xFFEF8B24)
}

/** One row of the table, worked out for the chosen period. */
internal class SummaryRow(
    val person: Person,
    val evaluation: Double?,
    val reward: Double,
    val cell: SummaryCell?
) {
    val total: Double get() = (evaluation ?: 0.0) + reward + (cell?.extra ?: 0.0) + (cell?.level ?: 0.0) + (cell?.bonus ?: 0.0)
}

/** The row of one person for the period [from]..[to]: evaluation average, reward / punishment net and the typed cell. */
internal fun summaryRowFor(viewModel: PeopleViewModel, p: Person, from: LocalDate, to: LocalDate): SummaryRow {
    val key = periodKeyOf(from, to)
    val evals = viewModel.evaluations.filter { it.personId == p.id && it.isComplete && it.dateTo >= from && it.date <= to }
    val reports = viewModel.reports.filter { it.personId == p.id && it.date >= from && it.date <= to }
    return SummaryRow(
        person = p,
        evaluation = if (evals.isEmpty()) null else Evaluations.average(evals),
        reward = reports.totals().net,
        cell = viewModel.summaryCells.firstOrNull { it.personId == p.id && it.periodKey == key }
    )
}

/**
 * جمع‌بندی: for a period (e.g. اول تا آخر آبان) one row per person with five columns —
 * the evaluation score, the reward/punishment of the same dates (+ / −), a typed number with a comment,
 * a typed «سطح» number — and the sum of those four.
 */
@Composable
fun SummaryScreen(viewModel: PeopleViewModel) {
    val context = LocalContext.current
    val people = viewModel.people
    val today = LocalDate.now()
    val thisMonth = remember { JalaliDate.from(today) }
    // The boxes start empty: only «از» and «تا» are written until the dates are chosen.
    var fromEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var toEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var editPerson by rememberSaveable { mutableStateOf("") }
    var editColumn by rememberSaveable { mutableStateOf(0) } // 3 = ملاحظات (number with comment), 4 = رئیس, 5 = تشویقی
    var bulkBonus by rememberSaveable { mutableStateOf(false) } // «تشویقی» per group at once
    var foldedGroups by rememberSaveable { mutableStateOf("") } // employment kinds that are folded

    val hasPeriod = fromEpoch != NO_DATE && toEpoch != NO_DATE
    val from = LocalDate.ofEpochDay(if (fromEpoch == NO_DATE) today.toEpochDay() else fromEpoch)
    val to = LocalDate.ofEpochDay(if (toEpoch == NO_DATE) today.toEpochDay() else toEpoch)
    val key = periodKeyOf(from, to)

    val rows = (if (!hasPeriod) emptyList<Person>() else people.filter { it.active || showInactive })
        .sortedByShiftName()
        .filter { nameMatches(it.name, query) }
        .map { p -> summaryRowFor(viewModel, p, from, to) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بازهٔ جمع‌بندی", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(dateBoxText("از", fromEpoch), { picker = 1 }, Modifier.weight(1f), icon = Icons.Default.DateRange, filled = false)
                    ActionButton(dateBoxText("تا", toEpoch), { picker = 2 }, Modifier.weight(1f), icon = Icons.Default.DateRange, filled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("ماه جاری", {
                        val (a, b) = monthRange(thisMonth.year, thisMonth.month)
                        fromEpoch = a.toEpochDay(); toEpoch = b.toEpochDay()
                    })
                    QuickChip("ماه قبل", {
                        val y = if (thisMonth.month == 1) thisMonth.year - 1 else thisMonth.year
                        val m = if (thisMonth.month == 1) 12 else thisMonth.month - 1
                        val (a, b) = monthRange(y, m)
                        fromEpoch = a.toEpochDay(); toEpoch = b.toEpochDay()
                    })
                }
        }

        // The search stays folded until needed; the dot shows while a search is on.
        FoldBox("جستجوی پیشرفته", active = query.isNotBlank() || showInactive) {
            OutlinedTextField(
                shape = FormFieldShape, colors = formFieldColors(),
                value = query, onValueChange = { query = it },
                label = { Text("جستجوی نام (حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("نمایش غیرفعال‌ها (از واحد رفته‌ها)", Modifier.weight(1f))
                Switch(checked = showInactive, onCheckedChange = { showInactive = it })
            }
        }

        if (rows.isEmpty()) {
            EmptyMessage(
                when {
                    !hasPeriod -> "ابتدا تاریخ «از» و «تا» را انتخاب کنید."
                    people.none { it.active } -> "ابتدا در بخش «نیروها» افراد را ثبت کنید."
                    searchTooShort(query) -> "برای جستجو حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف بنویسید."
                    else -> "نیرویی پیدا نشد."
                }
            )
        } else {
            val tableWidth = NAME_W + COL_W * 6
            AppCard {
                Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                    SUMMARY_ORDER.forEach { emp ->
                        val group = rows.filter { it.person.employment == emp }
                        if (group.isEmpty()) return@forEach
                        val folded = emp.name in foldedGroups.split(",")
                        // group header: tap to fold / unfold
                        Row(
                            Modifier
                                .width(tableWidth)
                                .background(employmentAccent(emp).copy(alpha = .12f))
                                .clickable {
                                    val set = foldedGroups.split(",").filter { it.isNotEmpty() }.toSet()
                                    foldedGroups = (if (folded) set - emp.name else set + emp.name).joinToString(",")
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(summaryTitle(emp), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = employmentAccent(emp))
                            Text("${group.size.toPersian()} نفر", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                            Text(if (folded) "▼" else "▲", Modifier.padding(start = 10.dp), color = employmentAccent(emp))
                        }
                        if (!folded) {
                            Row(Modifier.background(Purple.copy(alpha = .06f))) {
                                SummaryCellBox(NAME_W, "نام", Purple, bold = true)
                                SummaryCellBox(COL_W, "جمع", Purple, bold = true)
                                SummaryCellBox(COL_W, "رئیس", Purple, bold = true)
                                SummaryCellBox(COL_W, "ملاحظات", Purple, bold = true)
                                SummaryCellBox(COL_W, "تشویقی ✎", RewardGreen, bold = true, onClick = { bulkBonus = true })
                                SummaryCellBox(COL_W, "تشویق / تنبیه", Purple, bold = true)
                                SummaryCellBox(COL_W, "ارزشیابی", Purple, bold = true)
                            }
                            group.forEach { r ->
                                Box(Modifier.width(tableWidth).height(1.dp).background(Color(0xFFEAE8F5)))
                                Row {
                                    SummaryCellBox(
                                        NAME_W,
                                        r.person.name + (if (r.person.shiftName.isNotBlank()) " (${r.person.shiftName})" else "") +
                                            (if (!r.person.active) " • غیرفعال" else ""),
                                        if (r.person.isMember) MemberNameColor else LeaderNameColor,
                                        bold = !r.person.isMember, align = TextAlign.Start,
                                        fontSize = if (r.person.isMember) 12.sp else 15.sp
                                    )
                                    SummaryCellBox(COL_W, formatNumber(r.total), Navy, bold = true, bg = Navy.copy(alpha = .08f))
                                    SummaryCellBox(
                                        COL_W, r.cell?.level?.let { formatNumber(it) } ?: "—",
                                        if (r.cell?.level == null) Color.Gray else Navy,
                                        bg = Purple.copy(alpha = .06f),
                                        onClick = { editPerson = r.person.id; editColumn = 4 }
                                    )
                                    SummaryCellBox(
                                        COL_W, r.cell?.extra?.let { formatSigned(it) } ?: "—",
                                        if (r.cell?.extra == null) Color.Gray else Navy,
                                        bg = Purple.copy(alpha = .06f),
                                        marker = r.cell?.comment?.isNotBlank() == true,
                                        onClick = { editPerson = r.person.id; editColumn = 3 }
                                    )
                                    SummaryCellBox(
                                        COL_W, r.cell?.bonus?.let { formatSigned(it) } ?: "—",
                                        if (r.cell?.bonus == null) Color.Gray else RewardGreen,
                                        bold = r.cell?.bonus != null,
                                        bg = RewardGreen.copy(alpha = .07f),
                                        onClick = { editPerson = r.person.id; editColumn = 5 }
                                    )
                                    SummaryCellBox(
                                        COL_W, formatSigned(r.reward),
                                        when {
                                            r.reward > 0 -> RewardGreen
                                            r.reward < 0 -> PunishRed
                                            else -> Color.Gray
                                        },
                                        bold = r.reward != 0.0
                                    )
                                    SummaryCellBox(COL_W, r.evaluation?.let { formatScore(it) } ?: "—", if (r.evaluation == null) Color.Gray else Navy)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "ستون‌های «رئیس»، «ملاحظات» و «تشویقی» را با لمس کردن پر کنید؛ در «ملاحظات» می‌توانید دلیلش را هم بنویسید. با لمس عنوان «تشویقی ✎» برای هر گروه (رسمی، پیمانکاری، قرارداد مستقیم) یک عدد را یکجا ثبت می‌کنید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    // Floating averages: stay on screen while scrolling, one per employment kind.
    if (rows.isNotEmpty()) FloatingAverages(rows, Modifier.align(Alignment.BottomCenter))
    }

    // ----- edit a typed cell
    val editRow = rows.firstOrNull { it.person.id == editPerson }
    if (editColumn != 0 && editRow != null) {
        val cell = editRow.cell
        val isExtra = editColumn == 3
        val isBonus = editColumn == 5
        val current: Double? = when (editColumn) { 3 -> cell?.extra; 5 -> cell?.bonus; else -> cell?.level }
        CellDialog(
            title = editRow.person.name + " • " + when (editColumn) { 3 -> "ملاحظات"; 5 -> "تشویقی"; else -> "رئیس" },
            number = current?.let { formatNumber(it) }.orEmpty(),
            comment = if (isExtra) cell?.comment.orEmpty() else null,
            canClear = cell != null && (current != null || (isExtra && cell.comment.isNotBlank())),
            onSave = { number, comment ->
                viewModel.updateSummary(editRow.person.id, key) {
                    when {
                        isExtra -> it.copy(extra = number, comment = comment.trim())
                        isBonus -> it.copy(bonus = number)
                        else -> it.copy(level = number)
                    }
                }
                editColumn = 0
            },
            onClear = {
                viewModel.updateSummary(editRow.person.id, key) {
                    when {
                        isExtra -> it.copy(extra = null, comment = "")
                        isBonus -> it.copy(bonus = null)
                        else -> it.copy(level = null)
                    }
                }
                editColumn = 0
            },
            onDismiss = { editColumn = 0 }
        )
    }

    // ----- «تشویقی» for everybody in the list at once
    if (bulkBonus) {
        BulkBonusDialog(
            rows = rows,
            onApply = { values ->
                viewModel.setBonuses(values, key)
                bulkBonus = false
            },
            onDismiss = { bulkBonus = false }
        )
    }

    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = if (picker == 1) from else to,
            title = if (picker == 1) "تاریخ شروع" else "تاریخ پایان",
            onDismiss = { picker = 0 },
            onPick = { d ->
                if (picker == 1) {
                    fromEpoch = d.toEpochDay()
                    if (toEpoch != NO_DATE && toEpoch < fromEpoch) toEpoch = fromEpoch
                } else {
                    toEpoch = d.toEpochDay()
                    if (fromEpoch != NO_DATE && fromEpoch > toEpoch) fromEpoch = toEpoch
                }
                picker = 0
            }
        )
    }
}

@Composable
private fun SummaryCellBox(
    width: Dp,
    text: String,
    color: Color,
    bold: Boolean = false,
    bg: Color = Color.Transparent,
    align: TextAlign = TextAlign.Center,
    marker: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .width(width)
            .height(50.dp)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            Modifier.fillMaxWidth(),
            textAlign = align,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            fontSize = fontSize
        )
        if (marker) {
            Text("●", Modifier.align(Alignment.TopStart), color = Purple, fontSize = 8.sp)
        }
    }
}

/**
 * «تشویقی» written per employment group in one go: a number for رسمی, another for پیمانکاری and another for
 * قرارداد مستقیم. A group whose box is left empty is not touched, so one group alone can be filled; «پاک» clears
 * the column of that group. Only the people shown in the table are changed.
 */
@Composable
private fun BulkBonusDialog(rows: List<SummaryRow>, onApply: (Map<String, Double?>) -> Unit, onDismiss: () -> Unit) {
    val groups = SUMMARY_ORDER.mapNotNull { emp -> rows.filter { it.person.employment == emp }.takeIf { it.isNotEmpty() }?.let { emp to it } }
    var texts by remember { mutableStateOf(mapOf<Employment, String>()) }
    var cleared by remember { mutableStateOf(setOf<Employment>()) }
    val allValid = groups.all { (emp, _) -> texts[emp].orEmpty().isBlank() || parseNumber(texts[emp].orEmpty()) != null }
    val anyChange = groups.any { (emp, _) -> emp in cleared || texts[emp].orEmpty().isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشویقی به تفکیک گروه", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "برای هر گروه یک عدد بنویسید تا برای همهٔ نفرات همان گروه ثبت شود. جعبهٔ خالی یعنی آن گروه دست نمی‌خورد؛ پس می‌توانید فقط برای یک گروه وارد کنید. بعداً هر نفر را جدا هم می‌توانید تغییر دهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                groups.forEach { (emp, list) ->
                    val text = texts[emp].orEmpty()
                    val hasValues = list.any { it.cell?.bonus != null }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${summaryTitle(emp)} (${list.size.toPersian()} نفر)", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = employmentAccent(emp))
                            if (hasValues) {
                                TextButton(onClick = { texts = texts + (emp to ""); cleared = cleared + emp }) {
                                    Text("پاک کردن", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        OutlinedTextField(
                            shape = FormFieldShape, colors = formFieldColors(),
                            value = text,
                            onValueChange = { texts = texts + (emp to it); cleared = cleared - emp },
                            label = { Text(if (emp in cleared) "مقدار این گروه پاک می‌شود" else "عدد (منفی هم می‌شود)") },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                            isError = text.isNotBlank() && parseNumber(text) == null,
                            modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val out = LinkedHashMap<String, Double?>()
                    groups.forEach { (emp, list) ->
                        val t = texts[emp].orEmpty()
                        when {
                            emp in cleared -> list.forEach { out[it.person.id] = null }
                            t.isNotBlank() -> list.forEach { out[it.person.id] = parseNumber(t) }
                        }
                    }
                    onApply(out)
                },
                enabled = allValid && anyChange
            ) { Text("ثبت", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** A number, and (for the «عدد» column) the comment that explains it. */
@Composable
private fun CellDialog(
    title: String,
    number: String,
    comment: String?,
    canClear: Boolean,
    onSave: (Double?, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var n by remember { mutableStateOf(number) }
    var c by remember { mutableStateOf(comment.orEmpty()) }
    val parsed = parseNumber(n)
    val valid = n.isBlank() || parsed != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    shape = FormFieldShape, colors = formFieldColors(),
                    value = n, onValueChange = { n = it },
                    label = { Text("عدد (منفی هم می‌شود)") }, singleLine = true,
                    // typed left-to-right so a minus stays on the left of the digits
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (comment != null) {
                    OutlinedTextField(
                        shape = FormFieldShape, colors = formFieldColors(),
                        value = c, onValueChange = { c = it },
                        label = { Text("توضیح ملاحظات") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed, c) }, enabled = valid) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (canClear) {
                    TextButton(onClick = onClear) { Text("پاک کردن", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}

/** «میانگین»: the average of the «جمع» column for پیمانکاری, رسمی and قرارداد مستقیم, floating over the table. */
@Composable
private fun FloatingAverages(rows: List<SummaryRow>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("میانگین جمع", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SUMMARY_ORDER.forEach { emp ->
                    val group = rows.filter { it.person.employment == emp }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(summaryTitle(emp), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(
                            if (group.isEmpty()) "—" else formatNumber(group.sumOf { it.total } / group.size),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

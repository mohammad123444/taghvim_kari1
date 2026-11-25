package ir.taghvimkari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.domain.Evaluations
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.SummaryCell
import ir.taghvimkari.app.domain.formatNumber
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.formatSigned
import ir.taghvimkari.app.domain.normalizeFa
import ir.taghvimkari.app.domain.parseNumber
import ir.taghvimkari.app.domain.periodKeyOf
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.sortedByShiftName
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.totals
import java.time.LocalDate

private val NAME_W = 112.dp
private val COL_W = 70.dp
private val SumGreen = Color(0xFF2E9E5B)
private val SumRed = Color(0xFFD64545)

private fun monthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> =
    JalaliDate(year, month, 1).toLocalDate() to
        JalaliDate(year, month, JalaliDate.monthLength(year, month)).toLocalDate()

/** One row of the table, worked out for the chosen period. */
private class SummaryRow(
    val person: Person,
    val evaluation: Double?,
    val reward: Double,
    val cell: SummaryCell?
) {
    val total: Double get() = (evaluation ?: 0.0) + reward + (cell?.extra ?: 0.0) + (cell?.level ?: 0.0)
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
    var fromEpoch by rememberSaveable { mutableStateOf(monthRange(thisMonth.year, thisMonth.month).first.toEpochDay()) }
    var toEpoch by rememberSaveable { mutableStateOf(monthRange(thisMonth.year, thisMonth.month).second.toEpochDay()) }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var editPerson by rememberSaveable { mutableStateOf("") }
    var editColumn by rememberSaveable { mutableStateOf(0) } // 3 = number with comment, 4 = سطح

    val from = LocalDate.ofEpochDay(fromEpoch)
    val to = LocalDate.ofEpochDay(toEpoch)
    val key = periodKeyOf(from, to)
    val q = normalizeFa(query)

    val rows = people.filter { it.active }
        .sortedByShiftName()
        .filter { q.isBlank() || normalizeFa(it.name).contains(q) }
        .map { p ->
            val evals = viewModel.evaluations.filter {
                it.personId == p.id && it.isComplete && it.dateTo >= from && it.date <= to
            }
            val reports = viewModel.reports.filter { it.personId == p.id && it.date >= from && it.date <= to }
            SummaryRow(
                person = p,
                evaluation = if (evals.isEmpty()) null else Evaluations.average(evals),
                reward = reports.totals().net,
                cell = viewModel.summaryCells.firstOrNull { it.personId == p.id && it.periodKey == key }
            )
        }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بازهٔ جمع‌بندی", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) {
                        Text("از: ${JalaliDate.from(from).display()}")
                    }
                    OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                        Text("تا: ${JalaliDate.from(to).display()}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val (a, b) = monthRange(thisMonth.year, thisMonth.month)
                            fromEpoch = a.toEpochDay(); toEpoch = b.toEpochDay()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("ماه جاری") }
                    TextButton(
                        onClick = {
                            val y = if (thisMonth.month == 1) thisMonth.year - 1 else thisMonth.year
                            val m = if (thisMonth.month == 1) 12 else thisMonth.month - 1
                            val (a, b) = monthRange(y, m)
                            fromEpoch = a.toEpochDay(); toEpoch = b.toEpochDay()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("ماه قبل") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("جستجوی نام") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (rows.isEmpty()) {
            EmptyMessage(
                if (people.none { it.active }) "ابتدا در بخش «نیروها» افراد را ثبت کنید." else "نیرویی پیدا نشد."
            )
        } else {
            val tableWidth = NAME_W + COL_W * 5
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                    Row(Modifier.background(Purple.copy(alpha = .12f))) {
                        SummaryCellBox(NAME_W, "نام", Purple, bold = true)
                        SummaryCellBox(COL_W, "ارزشیابی", Purple, bold = true)
                        SummaryCellBox(COL_W, "تشویق", Purple, bold = true)
                        SummaryCellBox(COL_W, "عدد", Purple, bold = true)
                        SummaryCellBox(COL_W, "سطح", Purple, bold = true)
                        SummaryCellBox(COL_W, "جمع", Purple, bold = true)
                    }
                    rows.forEach { r ->
                        Box(Modifier.width(tableWidth).height(1.dp).background(Color(0xFFEAE8F5)))
                        Row {
                            SummaryCellBox(
                                NAME_W,
                                r.person.name + if (r.person.shiftName.isNotBlank()) " (${r.person.shiftName})" else "",
                                Navy, bold = true, align = TextAlign.Start
                            )
                            SummaryCellBox(COL_W, r.evaluation?.let { formatScore(it) } ?: "—", if (r.evaluation == null) Color.Gray else Navy)
                            SummaryCellBox(
                                COL_W, formatSigned(r.reward),
                                when {
                                    r.reward > 0 -> SumGreen
                                    r.reward < 0 -> SumRed
                                    else -> Color.Gray
                                },
                                bold = r.reward != 0.0
                            )
                            SummaryCellBox(
                                COL_W, r.cell?.extra?.let { formatSigned(it) } ?: "—",
                                if (r.cell?.extra == null) Color.Gray else Navy,
                                bg = Purple.copy(alpha = .06f),
                                marker = r.cell?.comment?.isNotBlank() == true,
                                onClick = { editPerson = r.person.id; editColumn = 3 }
                            )
                            SummaryCellBox(
                                COL_W, r.cell?.level?.let { formatNumber(it) } ?: "—",
                                if (r.cell?.level == null) Color.Gray else Navy,
                                bg = Purple.copy(alpha = .06f),
                                onClick = { editPerson = r.person.id; editColumn = 4 }
                            )
                            SummaryCellBox(COL_W, formatNumber(r.total), Navy, bold = true, bg = Navy.copy(alpha = .08f))
                        }
                    }
                }
            }
            Text(
                "ستون «عدد» و «سطح» را با لمس کردن پر کنید؛ در «عدد» می‌توانید دلیلش را هم بنویسید و بعداً با لمس همان عدد ببینید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { shareText(context, buildSummaryText(from, to, rows)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ارسال") }
        }
    }

    // ----- edit a typed cell
    val editRow = rows.firstOrNull { it.person.id == editPerson }
    if (editColumn != 0 && editRow != null) {
        val cell = editRow.cell
        val isExtra = editColumn == 3
        CellDialog(
            title = editRow.person.name + " • " + if (isExtra) "عدد" else "سطح",
            number = (if (isExtra) cell?.extra else cell?.level)?.let { formatNumber(it) }.orEmpty(),
            comment = if (isExtra) cell?.comment.orEmpty() else null,
            canClear = cell != null && (if (isExtra) cell.extra != null || cell.comment.isNotBlank() else cell.level != null),
            onSave = { number, comment ->
                viewModel.updateSummary(editRow.person.id, key) {
                    if (isExtra) it.copy(extra = number, comment = comment.trim()) else it.copy(level = number)
                }
                editColumn = 0
            },
            onClear = {
                viewModel.updateSummary(editRow.person.id, key) {
                    if (isExtra) it.copy(extra = null, comment = "") else it.copy(level = null)
                }
                editColumn = 0
            },
            onDismiss = { editColumn = 0 }
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
                    if (toEpoch < fromEpoch) toEpoch = fromEpoch
                } else {
                    toEpoch = d.toEpochDay()
                    if (fromEpoch > toEpoch) fromEpoch = toEpoch
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
            fontSize = 13.sp
        )
        if (marker) {
            Text("●", Modifier.align(Alignment.TopStart), color = Purple, fontSize = 8.sp)
        }
    }
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
                    value = n, onValueChange = { n = it },
                    label = { Text("عدد (منفی هم می‌شود)") }, singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (comment != null) {
                    OutlinedTextField(
                        value = c, onValueChange = { c = it },
                        label = { Text("دلیل این عدد") },
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

private fun buildSummaryText(from: LocalDate, to: LocalDate, rows: List<SummaryRow>): String = buildString {
    appendLine("جمع‌بندی • ${periodText(from, to)}")
    appendLine("(ارزشیابی + تشویق + عدد + سطح = جمع)")
    rows.forEach { r ->
        appendLine()
        appendLine(
            "• ${r.person.name}" + if (r.person.shiftName.isNotBlank()) " (شیفت ${r.person.shiftName})" else ""
        )
        append("  ${r.evaluation?.let { formatScore(it) } ?: "۰"} + ${formatSigned(r.reward)}")
        append(" + ${formatSigned(r.cell?.extra ?: 0.0)} + ${formatNumber(r.cell?.level ?: 0.0)}")
        appendLine(" = ${formatNumber(r.total)}")
        val note = r.cell?.comment.orEmpty()
        if (note.isNotBlank()) appendLine("  توضیح عدد: $note")
    }
}.trim()

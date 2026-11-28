package ir.taghvimkari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.data.ShareBytes
import ir.taghvimkari.app.data.Xlsx
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.shiftTitle
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.Shift
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.sortedByTeam
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate

private const val MAX_DAYS = 400

/** Index of Sunday in the Saturday-first week used by [ShiftEngine.persianWeekday]. */
private const val SUNDAY = 1
private val DayColor = RewardGreen
private val NightColor = Color(0xFF4F46C8)
private val RestColor = Color(0xFFD9D9E6)
private val RestTextColor = Color(0xFF616161)
private val NAME_WIDTH = 96.dp

/** 0 = rest, 1 = day / work, 2 = night. */
private fun kindOf(person: Person, date: LocalDate): Int = when (ShiftEngine.shift(person, date)) {
    Shift.REST -> 0
    Shift.NIGHT -> 2
    else -> 1
}

private fun kindColor(kind: Int): Color = when (kind) {
    1 -> DayColor
    2 -> NightColor
    else -> RestColor
}

/** Run-length encoding of a person's status over the range: list of (kind, length in days). */
private fun runsOf(person: Person, from: LocalDate, days: Int): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var kind = -1
    var length = 0
    for (i in 0 until days) {
        val k = kindOf(person, from.plusDays(i.toLong()))
        if (k == kind) {
            length++
        } else {
            if (length > 0) out.add(kind to length)
            kind = k
            length = 1
        }
    }
    if (length > 0) out.add(kind to length)
    return out
}

/** The day-by-day sheet of the comparison: one row per day, one column per person, plus totals at the bottom. */
private fun compareSheet(chosen: List<Person>, from: LocalDate, days: Int): List<List<Any?>> {
    fun kindName(k: Int) = when (k) { 0 -> "استراحت"; 2 -> "شب‌کار"; else -> "کار" }
    val header = listOf<Any?>("تاریخ", "روز هفته") + chosen.map { it.name } + listOf("وضعیت جمعی")
    val rows = ArrayList<List<Any?>>()
    rows.add(header)
    val work = IntArray(chosen.size)
    val rest = IntArray(chosen.size)
    var allWork = 0
    var allRest = 0
    for (i in 0 until days) {
        val date = from.plusDays(i.toLong())
        val kinds = chosen.map { kindOf(it, date) }
        kinds.forEachIndexed { idx, k -> if (k == 0) rest[idx]++ else work[idx]++ }
        val together = when {
            chosen.size < 2 -> ""
            kinds.all { it != 0 } -> { allWork++; "همه سر کار" }
            kinds.all { it == 0 } -> { allRest++; "همه در استراحت" }
            else -> ""
        }
        rows.add(
            listOf<Any?>(JalaliDate.from(date).display(), JalaliDate.WEEKDAY_NAMES[ShiftEngine.persianWeekday(date)]) +
                kinds.map { kindName(it) } + listOf(together)
        )
    }
    rows.add(emptyList())
    rows.add(listOf<Any?>("جمع روزهای کار", "") + work.toList() + listOf(if (chosen.size >= 2) allWork else null))
    rows.add(listOf<Any?>("جمع روزهای استراحت", "") + rest.toList() + listOf(if (chosen.size >= 2) allRest else null))
    rows.add(listOf<Any?>("شیفت", "") + chosen.map { if (it.shiftName.isBlank()) "" else shiftTitle(it.shiftName) } + listOf(""))
    rows.add(listOf<Any?>("نوع همکاری", "") + chosen.map { "${it.employment.title} • ${it.category.title}" } + listOf(""))
    return rows
}

private data class CompareRow(val person: Person, val runs: List<Pair<Int, Int>>) {
    val workDays: Int get() = runs.filter { it.first != 0 }.sumOf { it.second }
    val restDays: Int get() = runs.filter { it.first == 0 }.sumOf { it.second }
}

/** Compare the work status of several people over one day, a few days or several months on a single page. */
@Composable
fun CompareScreen(people: List<Person>) {
    val today = remember { LocalDate.now() }
    // The name column grows with the text size so names and counts are not cut.
    val nameWidth = NAME_WIDTH * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    var selectedText by rememberSaveable { mutableStateOf("") }
    // The date boxes start empty (only «از» / «تا»).
    var fromEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var toEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 = closed, 1 = from, 2 = to
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val datesSet = fromEpoch != NO_DATE && toEpoch != NO_DATE
    val from = LocalDate.ofEpochDay(if (fromEpoch == NO_DATE) today.toEpochDay() else fromEpoch)
    val to = LocalDate.ofEpochDay(if (toEpoch == NO_DATE) today.toEpochDay() else toEpoch)
    val days = if (datesSet) (toEpoch - fromEpoch + 1).toInt() else 0
    val error: String? = when {
        !datesSet -> "تاریخ «از» و «تا» را انتخاب کنید."
        days < 1 -> "تاریخ پایان نباید قبل از تاریخ شروع باشد."
        days > MAX_DAYS -> "حداکثر طول بازه ${MAX_DAYS.toPersian()} روز است."
        else -> null
    }

    val active = people.filter { it.active }
    val leaderNames = active.filter { it.isLeader }.associate { it.id to it.name }
    val selectedIds = selectedText.split(",").filter { it.isNotBlank() }.toSet()
    val chosen = active.filter { it.id in selectedIds }.sortedByTeam(leaderNames) { it }

    val rows = remember(chosen, fromEpoch, toEpoch) {
        if (error != null) emptyList() else chosen.map { CompareRow(it, runsOf(it, from, days)) }
    }
    // Periods in which everybody works together / everybody rests together.
    val together = remember(chosen, fromEpoch, toEpoch) {
        if (error != null || chosen.size < 2) {
            null
        } else {
            val work = BooleanArray(days)
            val rest = BooleanArray(days)
            for (i in 0 until days) {
                val kinds = chosen.map { kindOf(it, from.plusDays(i.toLong())) }
                work[i] = kinds.all { it != 0 }
                rest[i] = kinds.all { it == 0 }
            }
            rangesOf(work, from) to rangesOf(rest, from)
        }
    }
    var togetherDialog by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 work, 2 rest

    // Week lengths inside the range. Work starts on Sundays, so the chart's dividers are drawn at each
    // Sunday (weekday index 1); the app's calendar itself stays Saturday-first. First/last weeks may be partial.
    val weekLengths = remember(fromEpoch, toEpoch) {
        val out = ArrayList<Int>()
        var length = 0
        for (i in 0 until days.coerceAtLeast(0)) {
            if (i > 0 && ShiftEngine.persianWeekday(from.plusDays(i.toLong())) == SUNDAY) {
                out.add(length)
                length = 0
            }
            length++
        }
        if (length > 0) out.add(length)
        out
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) {
                Text(dateBoxText("از", fromEpoch))
            }
            OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                Text(dateBoxText("تا", toEpoch))
            }
        }
        if (error != null) {
            Text(
                error,
                color = Color(0xFFB3261E),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        OutlinedButton(onClick = { showPeople = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (chosen.isEmpty()) "انتخاب نیروها" else "نیروهای انتخاب‌شده: ${chosen.size.toPersian()} نفر (ویرایش)")
        }

        if (error != null) {
            // the red message is shown under the date boxes
        } else if (rows.isEmpty()) {
            EmptyMessage("نیروها را انتخاب کنید تا وضعیت کاری‌شان در بازهٔ انتخابی کنار هم نمایش داده شود.")
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(DayColor, "روزکار / کار")
                        Legend(NightColor, "شب‌کار")
                        Legend(RestColor, "استراحت")
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Spacer(Modifier.width(nameWidth))
                        Text(JalaliDate.from(from).display(), Modifier.weight(1f), fontSize = 11.sp, color = Color.DarkGray)
                        Text("${days.toPersian()} روز", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            JalaliDate.from(to).display(),
                            Modifier.weight(1f),
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.End
                        )
                    }
                    if (days <= 14) {
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(nameWidth))
                            Row(Modifier.weight(1f)) {
                                for (i in 0 until days) {
                                    Text(
                                        JalaliDate.from(from.plusDays(i.toLong())).day.toPersian(),
                                        Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(nameWidth)) {
                                Text(
                                    if (row.person.isLeader) "★ ${row.person.name}" else row.person.name,
                                    fontSize = if (row.person.isMember) 11.sp else 13.sp,
                                    fontWeight = if (row.person.isMember) FontWeight.Normal else FontWeight.Bold,
                                    color = if (row.person.isMember) MemberNameColor else LeaderNameColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${row.workDays.toPersian()} کار • ${row.restDays.toPersian()} استراحت",
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                            Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp))) {
                                Row(Modifier.fillMaxSize()) {
                                    row.runs.forEach { (kind, length) ->
                                        Box(Modifier.weight(length.toFloat()).fillMaxHeight().background(kindColor(kind)))
                                    }
                                }
                                // A white divider at the start of every work week (Sunday).
                                if (days > 7) {
                                    Row(Modifier.fillMaxSize()) {
                                        weekLengths.forEachIndexed { index, length ->
                                            Box(Modifier.weight(length.toFloat()).fillMaxHeight()) {
                                                if (index > 0) {
                                                    Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            together?.let { (workRanges, restRanges) ->
                AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("همپوشانی برنامهٔ نیروهای انتخاب‌شده", color = Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TogetherTile("همه با هم سر کار", workRanges.sumOf { it.days }, DayColor, Modifier.weight(1f)) { togetherDialog = 1 }
                        TogetherTile("همه با هم در استراحت", restRanges.sumOf { it.days }, RestTextColor, Modifier.weight(1f)) { togetherDialog = 2 }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    exportFailed = !ShareBytes.share(
                        context,
                        "taghvim-compare-${LocalDate.now()}.xlsx",
                        Xlsx.MIME,
                        Xlsx.build("مقایسه نیروها", compareSheet(chosen, from, days), listOf(14, 12) + chosen.map { 16 } + listOf(18)),
                        "ارسال مقایسه نیروها (Excel)"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("خروجی Excel (ریز روزانه)") }
            if (exportFailed) {
                Text("ارسال انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (togetherDialog != 0 && together != null) {
        val ranges = if (togetherDialog == 1) together.first else together.second
        TogetherDialog(
            title = if (togetherDialog == 1) "همه با هم سر کار" else "همه با هم در استراحت",
            ranges = ranges,
            color = if (togetherDialog == 1) DayColor else RestTextColor,
            onDismiss = { togetherDialog = 0 }
        )
    }

    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = if (picker == 1) from else to,
            title = if (picker == 1) "تاریخ شروع بازه" else "تاریخ پایان بازه",
            onDismiss = { picker = 0 },
            onPick = { d ->
                if (picker == 1) {
                    fromEpoch = d.toEpochDay()
                    if (toEpoch != NO_DATE && toEpoch < fromEpoch) toEpoch = fromEpoch
                } else {
                    toEpoch = d.toEpochDay()
                }
                picker = 0
            }
        )
    }

    if (showPeople) {
        PeoplePickerDialog(
            people = active,
            initial = selectedIds,
            onDismiss = { showPeople = false },
            onConfirm = { selectedText = it.joinToString(","); showPeople = false }
        )
    }
}

/** A run of consecutive days. */
private data class DayRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
}

private fun rangesOf(flags: BooleanArray, from: LocalDate): List<DayRange> {
    val out = ArrayList<DayRange>()
    var startIndex = -1
    for (i in 0..flags.size) {
        val on = i < flags.size && flags[i]
        if (on && startIndex < 0) startIndex = i
        if (!on && startIndex >= 0) {
            out.add(DayRange(from.plusDays(startIndex.toLong()), from.plusDays((i - 1).toLong())))
            startIndex = -1
        }
    }
    return out
}

@Composable
private fun TogetherTile(label: String, days: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val muted = days == 0
    val tint = if (muted) Color.Gray else color
    Column(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = .10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(days.toPersian(), color = tint, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text("روز", color = tint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
        Text(if (muted) "موردی نیست" else "مشاهدهٔ بازه‌ها ‹", color = tint.copy(alpha = .75f), fontSize = 11.sp)
    }
}

@Composable
private fun TogetherDialog(title: String, ranges: List<DayRange>, color: Color, onDismiss: () -> Unit) {
    val total = ranges.sumOf { it.days }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(color))
                Text(title, fontWeight = FontWeight.Bold, color = Navy)
            }
        },
        text = {
            if (ranges.isEmpty()) {
                Text("در این بازه چنین روزی وجود ندارد.", color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "${total.toPersian()} روز در ${ranges.size.toPersian()} بازه",
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            color = color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(
                        Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ranges.forEach { r ->
                            val first = JalaliDate.from(r.start)
                            val text = if (r.days == 1) {
                                "${JalaliDate.WEEKDAY_NAMES[ShiftEngine.persianWeekday(r.start)]} ${first.display()}"
                            } else {
                                "${first.display()}  تا  ${JalaliDate.from(r.end).display()}"
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF4F3FA))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2B2B3A))
                                Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(50)) {
                                    Text(
                                        "${r.days.toPersian()} روز",
                                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = Purple, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text("  $label", fontSize = 11.sp, color = Color.DarkGray)
    }
}

@Composable
private fun PeoplePickerDialog(
    people: List<Person>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val leaderNames = remember(people) { people.filter { it.isLeader }.associate { it.id to it.name } }
    val shown = people.filter { nameMatches(it.name, query) }.sortedByTeam(leaderNames) { it }

    FormDialog(
        onDismiss = onDismiss,
        title = "انتخاب نیروها (${draft.size.toPersian()})",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    shape = FormFieldShape, colors = formFieldColors(),
                    value = query, onValueChange = { query = it },
                    label = { Text("جستجو (حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused()
                )
                Row {
                    TextButton(onClick = { draft = draft + shown.map { it.id } }) { Text("انتخاب همه") }
                    TextButton(onClick = { draft = emptySet() }) { Text("هیچ‌کدام") }
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    ShiftEmploymentFolds(items = shown, person = { it }, stateKey = "compare", forceOpen = query.isNotBlank()) { p ->
                        val checked = p.id in draft
                        val isMember = p.leaderId != null && !p.isLeader
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // only the tapped person is (un)ticked, never his whole team
                                    draft = if (checked) draft - p.id else draft + p.id
                                }
                                .padding(start = if (isMember) 18.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                PersonNameText(p)
                                Text(
                                    "${p.employment.title} • ${p.category.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

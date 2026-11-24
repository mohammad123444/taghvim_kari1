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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.EvaluationItems
import ir.taghvimkari.app.domain.EvaluationLevel
import ir.taghvimkari.app.domain.Evaluations
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.buildEvaluationText
import ir.taghvimkari.app.domain.evaluationPeriods
import ir.taghvimkari.app.domain.periodKeyOf
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.normalizeFa
import ir.taghvimkari.app.domain.sortedAlphabetically
import ir.taghvimkari.app.domain.sortedByTeam
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate
import java.util.UUID

private const val ALL = "همه"
private val DraftOrange = Color(0xFFEF8B24)

private fun scoreColor(score: Int): Color = when (score) {
    1 -> Color(0xFFD32F2F)
    2 -> Color(0xFFEF8B24)
    3 -> Color(0xFFD9A400)
    4 -> Color(0xFF7CB342)
    else -> Color(0xFF218838)
}

private fun levelColor(level: EvaluationLevel): Color = when (level) {
    EvaluationLevel.EXCELLENT -> Color(0xFF218838)
    EvaluationLevel.VERY_GOOD -> Color(0xFF7CB342)
    EvaluationLevel.GOOD -> Color(0xFF1976D2)
    EvaluationLevel.FAIR -> Color(0xFFEF8B24)
    EvaluationLevel.WEAK -> Color(0xFFD32F2F)
}

/**
 * Evaluations: anyone can be the evaluator (ارزیاب) for an evaluation period (from date .. to date), tick the
 * people to evaluate (ticking a shift leader ticks his whole team), score each one — unfinished evaluations are
 * saved as drafts and can be continued later. Results can be filtered and sent per evaluation period.
 */
@Composable
fun EvaluationScreen(viewModel: PeopleViewModel) {
    val people = viewModel.people
    val evaluations = viewModel.evaluations
    var mode by rememberSaveable { mutableStateOf(0) } // 0 new evaluation, 1 results
    var queueText by rememberSaveable { mutableStateOf("") }
    var queueIndex by rememberSaveable { mutableStateOf(0) }
    var sessionEvaluator by rememberSaveable { mutableStateOf("") }
    var sessionDate by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var sessionDateTo by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var sendFailed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }

    fun startSession(evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) {
        sessionEvaluator = evaluatorId
        sessionDate = fromEpoch
        sessionDateTo = maxOf(fromEpoch, toEpoch)
        queueText = personIds.joinToString(",")
        queueIndex = 0
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ارزیابی نیروها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
        val drafts = evaluations.count { !it.isComplete }
        ChoiceChips(
            listOf(
                "ارزیابی جدید",
                "نتایج (${evaluations.size.toPersian()})" + if (drafts > 0) " • ناتمام ${drafts.toPersian()}" else ""
            ),
            mode
        ) { mode = it }
        if (mode == 0) {
            NewEvaluationSection(
                people = people,
                evaluations = evaluations,
                onStart = ::startSession
            )
        } else {
            ResultsSection(
                people = people,
                evaluations = evaluations,
                sendFailed = sendFailed,
                onOpen = { detailId = it },
                onResume = { e -> startSession(e.evaluatorId, listOf(e.personId), e.date.toEpochDay(), e.dateTo.toEpochDay()) },
                onSend = { list -> sendFailed = !DataExchange.shareEvaluations(context, list) },
                onReceive = { picker.launch(arrayOf("*/*")) }
            )
        }
    }

    // ----- scoring one person after another
    val queue = queueText.split(",").filter { it.isNotBlank() }
    val currentId = queue.getOrNull(queueIndex)
    if (currentId != null) {
        val person = people.firstOrNull { it.id == currentId }
        val advance = {
            if (queueIndex + 1 >= queue.size) {
                queueText = ""
                queueIndex = 0
                mode = 1
            } else {
                queueIndex += 1
            }
        }
        if (person == null) {
            LaunchedEffect(currentId) { advance() }
        } else {
            val date = LocalDate.ofEpochDay(sessionDate)
            val dateTo = LocalDate.ofEpochDay(sessionDateTo)
            val existing = evaluations.firstOrNull {
                it.personId == person.id && it.evaluatorId == sessionEvaluator && it.date == date && it.dateTo == dateTo
            }
            val evaluatorName = people.firstOrNull { it.id == sessionEvaluator }?.name ?: existing?.evaluatorName ?: ""
            EvaluationFormDialog(
                personName = person.name,
                evaluatorName = evaluatorName,
                position = queueIndex + 1,
                total = queue.size,
                date = date,
                dateTo = dateTo,
                key = person.id + sessionEvaluator + sessionDate + "-" + sessionDateTo,
                initialScores = existing?.scores?.joinToString("") ?: "0".repeat(EvaluationItems.count),
                onCancel = { queueText = ""; queueIndex = 0 },
                onSave = { scores ->
                    viewModel.saveEvaluation(
                        EvaluationEntry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            personId = person.id,
                            personName = person.name,
                            evaluatorId = sessionEvaluator,
                            evaluatorName = evaluatorName,
                            date = date,
                            dateTo = dateTo,
                            scores = scores
                        )
                    )
                    advance()
                }
            )
        }
    }

    // ----- detail table
    val detail = detailId?.let { id -> evaluations.firstOrNull { it.id == id } }
    if (detail != null) {
        val name = people.firstOrNull { it.id == detail.personId }?.name ?: detail.personName
        EvaluationDetailDialog(
            entry = detail,
            name = name,
            onShare = { shareText(context, buildEvaluationText(detail, name)) },
            onEdit = {
                detailId = null
                startSession(detail.evaluatorId, listOf(detail.personId), detail.date.toEpochDay(), detail.dateTo.toEpochDay())
            },
            onDelete = { deletingId = detail.id; detailId = null },
            onDismiss = { detailId = null }
        )
    }
    val deleting = deletingId?.let { id -> evaluations.firstOrNull { it.id == id } }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("حذف ارزیابی", fontWeight = FontWeight.Bold) },
            text = {
                val name = people.firstOrNull { it.id == deleting.personId }?.name ?: deleting.personName
                Text("ارزیابی «$name» (${deleting.periodText()}) حذف شود؟ این کار قابل بازگشت نیست.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEvaluation(deleting.id); deletingId = null }) {
                    Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("انصراف") } }
        )
    }
}

// ============================================================ new evaluation

@Composable
private fun NewEvaluationSection(
    people: List<Person>,
    evaluations: List<EvaluationEntry>,
    onStart: (evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) -> Unit
) {
    var evaluatorId by rememberSaveable { mutableStateOf("") }
    var fromEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var toEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var selectedText by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to

    // Everybody can evaluate: the evaluator is chosen from all active people.
    val evaluatorOptions = remember(people) { uniqueLabels(people.filter { it.active }) }
    val evaluator = people.firstOrNull { it.id == evaluatorId && it.active }
    val fromDate = LocalDate.ofEpochDay(fromEpoch)
    val toDate = LocalDate.ofEpochDay(toEpoch)

    // Anyone active except the evaluator himself; a team is shown together (leader first).
    val leaderNames = remember(people) { people.filter { it.isLeader }.associate { it.id to it.name } }
    val candidates = remember(people, evaluatorId) { people.filter { it.active && it.id != evaluatorId } }
    val q = normalizeFa(query)
    val shown = candidates
        .filter { q.isBlank() || normalizeFa(it.name).contains(q) }
        .sortedByTeam(leaderNames) { it }
    val candidateIds = candidates.map { it.id }.toSet()
    val selected = selectedText.split(",").filter { it in candidateIds }.toSet()
    val chosen = candidates.filter { it.id in selected }.sortedAlphabetically { it.name }

    fun draftOf(p: Person): EvaluationEntry? = evaluations.firstOrNull {
        it.personId == p.id && it.evaluatorId == evaluatorId && it.date == fromDate && it.dateTo == toDate
    }

    StepBox(1, "چه کسی ارزیابی می‌کند؟", "ارزیاب را انتخاب کنید و بازهٔ زمانی (دورهٔ) ارزیابی را مشخص کنید.", true) {
        if (people.none { it.active }) {
            Text(
                "ابتدا در بخش «نیروها» افراد را ثبت کنید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            PersonSearchField(
                label = "ارزیاب (همهٔ نیروها می‌توانند ارزیابی کنند)",
                options = evaluatorOptions,
                selectedId = evaluatorId,
                onSelect = { evaluatorId = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) {
                    Text("از: ${JalaliDate.from(fromDate).display()}")
                }
                OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                    Text("تا: ${JalaliDate.from(toDate).display()}")
                }
            }
            Text(
                "دورهٔ ارزیابی: ${periodText(fromDate, toDate)}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    StepBox(
        2, "چه کسانی ارزیابی شوند؟",
        "نفرات را تیک بزنید. با تیک نام سرشیفت، همهٔ زیرمجموعه‌های او هم انتخاب می‌شوند و می‌توانید تکی برشان دارید.",
        evaluator != null
    ) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            TextButton(onClick = { selectedText = (selected + shown.map { it.id }).joinToString(",") }) { Text("همهٔ نتایج") }
            TextButton(onClick = {
                selectedText = candidates.filter { draftOf(it)?.isComplete == false }.joinToString(",") { it.id }
            }) { Text("فقط ناتمام‌ها") }
            TextButton(onClick = { selectedText = "" }) { Text("هیچ‌کدام") }
        }
        if (shown.isEmpty()) {
            Text("نیرویی پیدا نشد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        shown.forEach { p ->
            val isMember = p.leaderId != null && !p.isLeader
            val teamIds: Set<String> = if (p.isLeader) {
                (candidates.filter { it.leaderId == p.id }.map { it.id } + p.id).filter { it in candidateIds }.toSet()
            } else {
                setOf(p.id)
            }
            val checked = teamIds.isNotEmpty() && teamIds.all { it in selected }
            val draft = draftOf(p)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        selectedText = (if (checked) selected - teamIds else selected + teamIds).joinToString(",")
                    }
                    .padding(start = if (isMember) 18.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(if (p.isLeader) "${p.name} • سرشیفت (با زیرمجموعه)" else p.name, fontWeight = FontWeight.Bold, color = Navy)
                    val status = when {
                        draft == null -> "${p.employment.title} • ${p.category.title}"
                        draft.isComplete -> "در این دوره ارزیابی شده (${formatScore(draft.finalScore)})"
                        else -> "ناتمام: ${draft.doneCount.toPersian()} از ${EvaluationItems.count.toPersian()} آیتم"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draft != null && !draft.isComplete) DraftOrange else Color.Gray
                    )
                }
            }
        }
    }

    if (evaluator != null) {
        val resuming = chosen.count { draftOf(it)?.isComplete == false }
        Button(
            onClick = { onStart(evaluator.id, chosen.map { it.id }, fromEpoch, toEpoch) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (chosen.isEmpty()) "ابتدا نفرات را تیک بزنید"
                else "شروع ارزیابی (${chosen.size.toPersian()} نفر" +
                    (if (resuming > 0) "، ${resuming.toPersian()} ادامه" else "") + ")"
            )
        }
    }

    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = if (picker == 1) fromDate else toDate,
            title = if (picker == 1) "تاریخ شروع دوره" else "تاریخ پایان دوره",
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
private fun StepBox(number: Int, title: String, hint: String, enabled: Boolean, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (enabled) Purple else Color(0xFFBDBBD6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(number.toPersian(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    title,
                    Modifier.padding(start = 10.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Navy else Color.Gray,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(hint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            if (enabled) content()
        }
    }
}

@Composable
private fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, label ->
            val on = selected == index
            Surface(
                color = if (on) Purple else Purple.copy(alpha = .12f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onSelect(index) }
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
}

/** Equal names get a number so each label is unique. */
private fun numberedLabels(names: List<String>): List<String> {
    val counts = HashMap<String, Int>()
    return names.map { n ->
        val k = (counts[n] ?: 0) + 1
        counts[n] = k
        if (k == 1) n else "$n (${k.toPersian()})"
    }
}

// ============================================================ scoring form

@Composable
private fun EvaluationFormDialog(
    personName: String,
    evaluatorName: String,
    position: Int,
    total: Int,
    date: LocalDate,
    dateTo: LocalDate,
    key: String,
    initialScores: String,
    onCancel: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    val count = EvaluationItems.count
    // One character per item: '0' = not scored yet, '1'..'5' = the score.
    var scoreText by rememberSaveable(key) { mutableStateOf(initialScores.padEnd(count, '0').take(count)) }
    val scores = scoreText.map { it - '0' }
    val done = scores.count { it > 0 }
    val complete = done == count
    val sum = scores.sum()

    // Drawn inside the app's content area (between the header and the bottom bar), not in a separate window,
    // so the buttons are always above the phone's own navigation buttons.
    BackHandler(onBack = onCancel)
    run {
        Surface(Modifier.fillMaxSize(), color = AppBackground) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().background(Navy).padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(personName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "نفر ${position.toPersian()} از ${total.toPersian()} • دوره: ${periodText(date, dateTo)}" +
                            if (evaluatorName.isNotBlank()) " • ارزیاب: $evaluatorName" else "",
                        color = Color(0xFFDAD9FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "برای هر آیتم از ۱ (کمترین) تا ۵ (بیشترین) نمره بدهید. اگر وقت نداشتید، «ذخیرهٔ ناتمام» را بزنید و بعداً ادامه دهید.",
                        color = Color(0xFFDAD9FF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EvaluationItems.all.forEachIndexed { i, title ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${(i + 1).toPersian()}. $title", fontWeight = FontWeight.Bold, color = Navy)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (value in EvaluationItems.MIN_SCORE..EvaluationItems.MAX_SCORE) {
                                        val on = scores[i] == value
                                        Surface(
                                            color = if (on) scoreColor(value) else Color(0xFFF1EFFB),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    scoreText = scoreText.substring(0, i) + value + scoreText.substring(i + 1)
                                                }
                                        ) {
                                            Text(
                                                value.toPersian(),
                                                Modifier.padding(vertical = 9.dp).fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                color = if (on) Color.White else Navy,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (complete) {
                            val final = Evaluations.finalScore(sum)
                            Text(
                                "جمع: ${sum.toPersian()} از ${EvaluationItems.maxTotal.toPersian()} • نمرهٔ نهایی: " +
                                    "${formatScore(final)} از ${EvaluationItems.FINAL_MAX.toPersian()}",
                                fontWeight = FontWeight.Bold,
                                color = levelColor(Evaluations.levelOf(final))
                            )
                        } else {
                            Text(
                                "${done.toPersian()} از ${count.toPersian()} آیتم نمره گرفته است؛ ${(count - done).toPersian()} آیتم مانده.",
                                color = Color.DarkGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(onClick = { onSave(scores) }, enabled = complete, modifier = Modifier.fillMaxWidth()) {
                            Text(if (position < total) "ذخیره و نفر بعدی" else "ذخیره و پایان")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onSave(scores) },
                                enabled = done > 0 && !complete,
                                modifier = Modifier.weight(1f)
                            ) { Text("ذخیرهٔ ناتمام") }
                            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("انصراف") }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ results

@Composable
private fun ResultsSection(
    people: List<Person>,
    evaluations: List<EvaluationEntry>,
    sendFailed: Boolean,
    onOpen: (String) -> Unit,
    onResume: (EvaluationEntry) -> Unit,
    onSend: (List<EvaluationEntry>) -> Unit,
    onReceive: () -> Unit
) {
    var personFilter by rememberSaveable { mutableStateOf("") }
    var evaluatorFilter by rememberSaveable { mutableStateOf("") }
    var periodFilter by rememberSaveable { mutableStateOf("") } // key of the evaluation period, blank = all

    val labelled = remember(people) { uniqueLabels(people) }
    val evaluatorIds = remember(evaluations) { evaluations.map { it.evaluatorId }.filter { it.isNotBlank() }.distinct() }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val evaluatorList = remember(evaluations, people) {
        evaluatorIds
            .map { id -> id to (nameById[id] ?: evaluations.firstOrNull { it.evaluatorId == id }?.evaluatorName.orEmpty()) }
            .sortedAlphabetically { it.second }
    }
    val evaluatorLabels = remember(evaluatorList) { numberedLabels(evaluatorList.map { it.second }) }
    val nameOf: (EvaluationEntry) -> String = { e -> nameById[e.personId] ?: e.personName }
    val periods = remember(evaluations) { evaluationPeriods(evaluations) }
    val periodLabels = remember(periods) { periods.map { periodText(it.first, it.second) } }
    val periodIndex = periods.indexOfFirst { periodKeyOf(it.first, it.second) == periodFilter }

    val filtered = evaluations
        .filter {
            (personFilter.isBlank() || it.personId == personFilter) &&
                (evaluatorFilter.isBlank() || it.evaluatorId == evaluatorFilter) &&
                (periodFilter.isBlank() || it.periodKey == periodFilter)
        }
        .sortedWith(compareByDescending<EvaluationEntry> { it.date }.thenBy { nameOf(it) })

    // ----- send / receive (evaluations only; staff and reports have their own send buttons)
    ExchangeBar(
        hint = "ارسال، ارزیابی‌های فهرست پایین (با فیلتر فعلی، مثلاً فقط یک دورهٔ ارزیابی) را به‌صورت فایل می‌فرستد. " +
            "گیرنده با «دریافت از فایل» آن‌ها را به ارزیابی‌های خودش اضافه (ادغام) می‌کند؛ هیچ ارزیابی قبلی حذف نمی‌شود.",
        sendLabel = if (periodFilter.isBlank()) "ارسال همه (${filtered.size.toPersian()})" else "ارسال این دوره (${filtered.size.toPersian()})",
        sendEnabled = filtered.isNotEmpty(),
        sendFailed = sendFailed,
        onSend = { onSend(filtered) },
        onReceive = onReceive
    )

    // ----- filters
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (periods.isNotEmpty()) {
                FilterDropdown(
                    "دورهٔ ارزیابی",
                    listOf(ALL) + periodLabels,
                    periodLabels.getOrNull(periodIndex) ?: ALL
                ) { label ->
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
                FilterDropdown(
                    "ارزیاب",
                    listOf(ALL) + evaluatorLabels,
                    evaluatorList.indexOfFirst { it.first == evaluatorFilter }.takeIf { it >= 0 }?.let { evaluatorLabels[it] } ?: ALL
                ) { label ->
                    evaluatorFilter = evaluatorList.getOrNull(evaluatorLabels.indexOf(label))?.first ?: ""
                }
            }
        }
    }

    if (filtered.isEmpty()) {
        EmptyMessage(
            if (evaluations.isEmpty()) "هنوز ارزیابی‌ای ثبت نشده است. از «ارزیابی جدید» شروع کنید."
            else "با این فیلتر ارزیابی‌ای پیدا نشد."
        )
        return
    }

    val drafts = filtered.filter { !it.isComplete }
    val done = filtered.filter { it.isComplete }

    // ----- drafts to continue
    if (drafts.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ارزیابی‌های ناتمام (${drafts.size.toPersian()})",
                    fontWeight = FontWeight.Bold,
                    color = DraftOrange,
                    style = MaterialTheme.typography.titleMedium
                )
                drafts.forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { onResume(e) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nameOf(e), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                            Text("ادامه", color = Purple, fontWeight = FontWeight.Bold)
                        }
                        ScoreBar(e.doneCount.toDouble() / EvaluationItems.count, DraftOrange, 8)
                        Text(
                            "${e.doneCount.toPersian()} از ${EvaluationItems.count.toPersian()} آیتم • ${e.periodText()}" +
                                if (e.evaluatorName.isNotBlank()) " • ارزیاب: ${e.evaluatorName}" else "",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    if (done.isEmpty()) return

    // ----- overall summary
    val average = Evaluations.average(done)
    val level = Evaluations.levelOf(average)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("میانگین نمرهٔ نهایی", color = Purple, fontWeight = FontWeight.Bold)
            ScoreBadge(average, big = true)
            ScoreBar(average / EvaluationItems.FINAL_MAX, levelColor(level), 12)
            Text(
                "${done.size.toPersian()} ارزیابی کامل • سطح: ${level.title}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // ----- per person
    if (personFilter.isBlank()) {
        val perPerson = done.groupBy { it.personId }
            .map { (id, list) -> Triple(id, nameOf(list.first()), list) }
            .sortedByDescending { Evaluations.average(it.third) }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("میانگین هر نفر", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                perPerson.forEach { (id, name, list) ->
                    val avg = Evaluations.average(list)
                    val lvl = Evaluations.levelOf(avg)
                    Column(Modifier.fillMaxWidth().clickable { personFilter = id }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                            Text(
                                "${formatScore(avg)} از ${EvaluationItems.FINAL_MAX.toPersian()}",
                                fontWeight = FontWeight.Bold,
                                color = levelColor(lvl)
                            )
                        }
                        ScoreBar(avg / EvaluationItems.FINAL_MAX, levelColor(lvl), 8)
                        Text(
                            "${list.size.toPersian()} ارزیابی • ${lvl.title}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text("برای دیدن ارزیابی‌های یک نفر، روی نامش بزنید.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // ----- entries
    Text(
        "ارزیابی‌ها (${done.size.toPersian()})",
        fontWeight = FontWeight.Bold,
        color = Navy,
        style = MaterialTheme.typography.titleMedium
    )
    done.forEach { e ->
        val lvl = Evaluations.levelOf(e.finalScore)
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nameOf(e), fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            e.periodText() +
                                if (e.evaluatorName.isNotBlank()) " • ارزیاب: ${e.evaluatorName}" else "",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        formatScore(e.finalScore),
                        color = levelColor(lvl),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                ScoreBar(e.finalScore / EvaluationItems.FINAL_MAX, levelColor(lvl), 8)
                Text(
                    "${lvl.title} • جمع ${e.total.toPersian()} از ${EvaluationItems.maxTotal.toPersian()} • برای دیدن جدول بزنید",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Double, big: Boolean) {
    val color = levelColor(Evaluations.levelOf(score))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            formatScore(score),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 44.sp else 28.sp
        )
        Text(
            "  از ${EvaluationItems.FINAL_MAX.toPersian()}",
            color = Color.Gray,
            modifier = Modifier.padding(bottom = if (big) 8.dp else 4.dp)
        )
    }
}

@Composable
private fun ScoreBar(fraction: Double, color: Color, heightDp: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape((heightDp / 2).dp))
            .background(Color(0xFFE6E6EE))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

// ============================================================ detail table

@Composable
private fun EvaluationDetailDialog(
    entry: EvaluationEntry,
    name: String,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val final = entry.finalScore
    val level = Evaluations.levelOf(final)
    val color = levelColor(level)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    entry.periodText() +
                        if (entry.evaluatorName.isNotBlank()) " • ارزیاب: ${entry.evaluatorName}" else "",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )

                // final number
                Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("نمرهٔ نهایی", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        ScoreBadge(final, big = true)
                        ScoreBar(final / EvaluationItems.FINAL_MAX, color, 10)
                        Text(level.title, color = color, fontWeight = FontWeight.Bold)
                    }
                }

                // table
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF6F5FC))) {
                    Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("#", Modifier.weight(0.12f), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("آیتم", Modifier.weight(0.68f), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("نمره", Modifier.weight(0.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                    EvaluationItems.all.forEachIndexed { i, title ->
                        val score = entry.scores.getOrElse(i) { 0 }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (i % 2 == 0) Color.White else Color(0xFFF1EFFB))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((i + 1).toPersian(), Modifier.weight(0.12f), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text(title, Modifier.weight(0.68f), style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(0.2f), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape).background(scoreColor(score)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(score.toPersian(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(Color(0xFFE6E4F7)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("جمع نمرات", Modifier.weight(0.8f), fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            "${entry.total.toPersian()} از ${EvaluationItems.maxTotal.toPersian()}",
                            Modifier.weight(0.4f),
                            fontWeight = FontWeight.Bold,
                            color = Navy,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // how the number is computed
                Surface(color = Color(0xFFF1EFFB), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("نحوهٔ محاسبه", fontWeight = FontWeight.Bold, color = Purple, style = MaterialTheme.typography.labelLarge)
                        Text("جمع نمرات = ${entry.total.toPersian()}")
                        Text("${entry.total.toPersian()} × ${EvaluationItems.FINAL_MAX.toPersian()} = ${(entry.total * EvaluationItems.FINAL_MAX).toPersian()}")
                        Text(
                            "${(entry.total * EvaluationItems.FINAL_MAX).toPersian()} ÷ ${EvaluationItems.maxTotal.toPersian()} = ${formatScore(final)}",
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) { Text("ویرایش") }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onShare) { Text("ارسال") }
            }
        }
    )
}

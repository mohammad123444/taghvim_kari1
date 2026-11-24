package ir.taghvimkari.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import ir.taghvimkari.app.domain.WorkingDraft
import ir.taghvimkari.app.domain.nextFocus
import ir.taghvimkari.app.domain.previousEvaluation
import ir.taghvimkari.app.domain.setScoreAt
import ir.taghvimkari.app.domain.sortedByTeam
import kotlinx.coroutines.launch
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
 * saved as drafts and can be continued later. The round in progress (people, period and the scores typed so far)
 * is saved after every tap, so it survives a tab change or closing the app and is continued from a card on top.
 */
@Composable
fun EvaluationScreen(viewModel: PeopleViewModel) {
    val people = viewModel.people
    val evaluations = viewModel.evaluations
    val draft = viewModel.workingDraft
    var mode by rememberSaveable { mutableStateOf(0) } // 0 new evaluation, 1 results
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var sendFailed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }

    fun startSession(evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) {
        viewModel.startDraft(evaluatorId, personIds, fromEpoch, toEpoch)
        formOpen = true
    }

    // The form must not pop up by itself when a round was thrown away and a new one starts later.
    LaunchedEffect(draft == null) { if (draft == null) formOpen = false }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (draft != null) {
            ResumeCard(
                draft = draft,
                personName = people.firstOrNull { it.id == draft.currentPersonId }?.name.orEmpty(),
                onResume = { formOpen = true },
                onDiscard = { viewModel.discardDraft() }
            )
        }
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
    if (draft != null && formOpen) {
        val person = people.firstOrNull { it.id == draft.currentPersonId }
        val finish = {
            formOpen = false
            mode = 1
        }
        if (person == null) {
            // This person was deleted since the round started: skip him.
            LaunchedEffect(draft.index, draft.currentPersonId) { if (viewModel.advanceDraft()) finish() }
        } else {
            val date = LocalDate.ofEpochDay(draft.fromEpoch)
            val dateTo = LocalDate.ofEpochDay(draft.toEpoch)
            val existing = evaluations.firstOrNull {
                it.personId == person.id && it.evaluatorId == draft.evaluatorId && it.date == date && it.dateTo == dateTo
            }
            val previous = remember(evaluations, person.id, draft.evaluatorId, date) {
                previousEvaluation(evaluations, person.id, draft.evaluatorId, date)
            }
            val evaluatorName = people.firstOrNull { it.id == draft.evaluatorId }?.name ?: existing?.evaluatorName ?: ""
            val start = draft.typedScores(EvaluationItems.count)?.joinToString("")
                ?: existing?.scores?.joinToString("")
                ?: "0".repeat(EvaluationItems.count)
            EvaluationFormDialog(
                personName = person.name,
                evaluatorName = evaluatorName,
                position = draft.index + 1,
                total = draft.personIds.size,
                date = date,
                dateTo = dateTo,
                key = person.id + draft.evaluatorId + draft.fromEpoch + "-" + draft.toEpoch,
                initialScores = start,
                previous = previous,
                onScoresChange = { viewModel.updateDraftScores(it) },
                // Closing only pauses: the round stays and is continued from the card on top.
                onCancel = { formOpen = false },
                onSave = { scores ->
                    viewModel.saveEvaluation(
                        EvaluationEntry(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            personId = person.id,
                            personName = person.name,
                            evaluatorId = draft.evaluatorId,
                            evaluatorName = evaluatorName,
                            date = date,
                            dateTo = dateTo,
                            scores = scores
                        )
                    )
                    if (viewModel.advanceDraft()) finish()
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
            // Deleted at once; the snackbar offers «بازگرداندن».
            onDelete = { viewModel.deleteEvaluation(detail.id); detailId = null },
            onDismiss = { detailId = null }
        )
    }
}

/** «ادامهٔ ارزیابی ناتمام»: shown on top while a round is saved on the phone. */
@Composable
private fun ResumeCard(draft: WorkingDraft, personName: String, onResume: () -> Unit, onDiscard: () -> Unit) {
    val typed = draft.typedScores(EvaluationItems.count)?.count { it > 0 }
    Card(
        colors = CardDefaults.cardColors(containerColor = Purple.copy(alpha = .10f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onResume)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ادامهٔ ارزیابی ناتمام", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (personName.isNotBlank()) "$personName • " else "") +
                    (if (typed != null) "${typed.toPersian()} از ${EvaluationItems.count.toPersian()} آیتم • " else "") +
                    "${draft.remaining.toPersian()} نفر مانده • ${periodText(LocalDate.ofEpochDay(draft.fromEpoch), LocalDate.ofEpochDay(draft.toEpoch))}",
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("ادامه") }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("کنار گذاشتن") }
            }
        }
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

    StepBox(1, "چه کسی ارزیابی می‌کند؟", "", true) {
        if (people.none { it.active }) {
            Text(
                "ابتدا در بخش «نیروها» افراد را ثبت کنید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            PersonSearchField(
                label = "ارزیاب",
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
        }
    }

    StepBox(
        2, "چه کسانی ارزیابی شوند؟",
        "با تیک نام سرشیفت، همهٔ زیرمجموعه‌های او هم انتخاب می‌شوند و می‌توانید تکی برشان دارید.",
        evaluator != null
    ) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
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
            if (hint.isNotBlank()) Text(hint, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
private fun ScoreButtons(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (value in EvaluationItems.MIN_SCORE..EvaluationItems.MAX_SCORE) {
            val on = selected == value
            Surface(
                color = if (on) scoreColor(value) else Color(0xFFF1EFFB),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onPick(value) }
            ) {
                Text(
                    value.toPersian(),
                    Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = if (on) Color.White else Navy,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
    previous: EvaluationEntry?,
    onScoresChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    val count = EvaluationItems.count
    // One character per item: '0' = not scored yet, '1'..'5' = the score.
    var scoreText by rememberSaveable(key) { mutableStateOf(initialScores.padEnd(count, '0').take(count)) }
    // The item being scored now; after each tap the list moves on to the next one.
    var focus by rememberSaveable(key) { mutableStateOf(scoreText.indexOf('0').coerceAtLeast(0)) }
    var fillValue by rememberSaveable { mutableStateOf(4) }
    var pendingText by remember { mutableStateOf<String?>(null) } // new scores waiting for the OK to replace the old ones
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val scores = scoreText.map { it - '0' }
    val done = scores.count { it > 0 }
    val complete = done == count
    val sum = scores.sum()

    // Reopening a half-scored form: start at the item that is next.
    LaunchedEffect(key) { if (focus > 0) listState.scrollToItem(focus + 1) }

    fun change(text: String) {
        scoreText = text
        onScoresChange(text)
    }

    // Replacing already given scores (fill all / copy) asks first; filling an empty form does not.
    fun replaceAll(text: String) {
        if (done > 0 && text != scoreText) {
            pendingText = text
        } else {
            change(text)
            focus = 0
            scope.launch { listState.animateScrollToItem(1) }
        }
    }

    // Drawn inside the app's content area (between the header and the bottom bar), not in a separate window,
    // so the buttons are always above the phone's own navigation buttons.
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize(), color = AppBackground) {
        Column(Modifier.fillMaxSize()) {
            // ----- fixed header with the progress («۱۲ از ۲۳»)
            Column(
                Modifier.fillMaxWidth().background(Navy).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(personName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    "نفر ${position.toPersian()} از ${total.toPersian()} • دوره: ${periodText(date, dateTo)}" +
                        if (evaluatorName.isNotBlank()) " • ارزیاب: $evaluatorName" else "",
                    color = Color(0xFFDAD9FF),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${done.toPersian()} از ${count.toPersian()}",
                        Modifier.padding(end = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Box(Modifier.weight(1f)) { ScoreBar(done.toDouble() / count, Color(0xFF7CB342), 8) }
                }
                Text(
                    "اگر وقت نداشتید، «ذخیرهٔ ناتمام» را بزنید و بعداً ادامه دهید.",
                    color = Color(0xFFDAD9FF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ----- quick fill and copy
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("همهٔ آیتم‌ها =", fontWeight = FontWeight.Bold, color = Navy)
                            ScoreButtons(fillValue, { fillValue = it })
                            Button(
                                onClick = { replaceAll(fillValue.toString().repeat(count)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("پر کردن همه با ${fillValue.toPersian()}") }
                            OutlinedButton(
                                onClick = { previous?.let { replaceAll(it.scores.joinToString("")) } },
                                enabled = previous != null,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (previous != null) "کپی از دورهٔ قبل (${periodText(previous.date, previous.dateTo)})" else "دورهٔ قبلی برای کپی نیست") }
                        }
                    }
                }
                itemsIndexed(EvaluationItems.all) { i, title ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = if (i == focus) BorderStroke(2.dp, Purple) else null
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${(i + 1).toPersian()}. $title", fontWeight = FontWeight.Bold, color = Navy)
                            ScoreButtons(scores[i], { value ->
                                change(setScoreAt(scoreText, i, value))
                                // On to the next item.
                                focus = nextFocus(i, count)
                                scope.launch { listState.animateScrollToItem(focus + 1) }
                            })
                        }
                    }
                }
            }

            // ----- bottom bar: never taller than a part of the screen, scrolls if the text is large
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
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
                        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("بستن") }
                    }
                }
            }
        }
    }

    pendingText?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingText = null },
            title = { Text("جایگزینی نمره‌ها", fontWeight = FontWeight.Bold) },
            text = { ScrollText("نمرهٔ ${done.toPersian()} آیتم که تا اینجا داده‌اید با نمره‌های تازه جایگزین می‌شود. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = {
                    change(text)
                    focus = 0
                    scope.launch { listState.animateScrollToItem(1) }
                    pendingText = null
                }) { Text("جایگزین کن", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingText = null }) { Text("انصراف") } }
        )
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
                    "${lvl.title} • جمع ${e.total.toPersian()} از ${EvaluationItems.maxTotal.toPersian()}",
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

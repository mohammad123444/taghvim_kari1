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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import ir.taghvimkari.app.domain.Category
import ir.taghvimkari.app.domain.Employment
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
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.normalizeFa
import ir.taghvimkari.app.domain.searchTooShort
import ir.taghvimkari.app.domain.shiftGroups
import ir.taghvimkari.app.domain.shiftTitle
import ir.taghvimkari.app.domain.compareNames
import ir.taghvimkari.app.domain.displayOrder
import ir.taghvimkari.app.domain.shiftRank
import ir.taghvimkari.app.domain.sortedByShiftName
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
    1 -> PunishRed
    2 -> Color(0xFFEF8B24)
    3 -> Color(0xFFD9A400)
    4 -> Color(0xFF7CB342)
    else -> RewardGreen
}

private fun levelColor(level: EvaluationLevel): Color = when (level) {
    EvaluationLevel.EXCELLENT -> RewardGreen
    EvaluationLevel.VERY_GOOD -> Color(0xFF7CB342)
    EvaluationLevel.GOOD -> Color(0xFF1976D2)
    EvaluationLevel.FAIR -> Color(0xFFEF8B24)
    EvaluationLevel.WEAK -> PunishRed
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
    var returnMode by rememberSaveable { mutableStateOf(0) }      // the page to go back to when a round ends
    var finishedCount by rememberSaveable { mutableStateOf(0) }   // > 0 shows «خاتمه ارزیابی»
    var clearSignal by rememberSaveable { mutableStateOf(0) }     // clears the ticked people on the new-evaluation page
    val context = LocalContext.current

    fun startSession(evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) {
        viewModel.startDraft(evaluatorId, personIds, fromEpoch, toEpoch)
        returnMode = mode
        formOpen = true
    }

    // The form must not pop up by itself when a round was thrown away and a new one starts later.
    LaunchedEffect(draft == null) { if (draft == null) formOpen = false }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
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
        TwoBoxTabs(
            listOf(
                "ارزیابی جدید",
                "مشاهده ارزیابی (${evaluations.size.toPersian()})" + if (drafts > 0) " • ناتمام ${drafts.toPersian()}" else ""
            ),
            mode
        ) { mode = it }
        if (mode == 0) {
            NewEvaluationSection(
                people = people,
                evaluations = evaluations,
                clearSignal = clearSignal,
                onStart = ::startSession
            )
        } else {
            ResultsSection(
                people = people,
                evaluations = evaluations,
                onOpen = { detailId = it },
                onResume = { e -> startSession(e.evaluatorId, listOf(e.personId), e.date.toEpochDay(), e.dateTo.toEpochDay()) },
                onDelete = { ids -> viewModel.deleteEvaluations(ids.toSet()) }
            )
        }
    }

    // ----- «خاتمه ارزیابی»: shown when the last chosen person was done, then back to the page we came from
    if (finishedCount > 0) {
        AlertDialog(
            onDismissRequest = { finishedCount = 0 },
            title = { Text("خاتمه ارزیابی", fontWeight = FontWeight.Bold) },
            text = { Text("ارزیابی ${finishedCount.toPersian()} نفر پایان یافت.") },
            confirmButton = { TextButton(onClick = { finishedCount = 0 }) { Text("تأیید") } }
        )
    }

    // ----- scoring one person after another
    if (draft != null && formOpen) {
        val person = people.firstOrNull { it.id == draft.currentPersonId }
        val total = draft.personIds.size
        val finish = {
            formOpen = false
            mode = returnMode
            clearSignal += 1
            finishedCount = total
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
    clearSignal: Int,
    onStart: (evaluatorId: String, personIds: List<String>, fromEpoch: Long, toEpoch: Long) -> Unit
) {
    var evaluatorId by rememberSaveable { mutableStateOf("") }
    // The date boxes start empty (only «از» / «تا»).
    var fromEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var toEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var leaderPrompt by rememberSaveable { mutableStateOf("") } // leader whose tick asks «alone or with his team?»
    var selectedText by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to
    var openGroups by rememberSaveable { mutableStateOf("") } // keys of the unfolded groups
    LaunchedEffect(clearSignal) { if (clearSignal > 0) selectedText = "" }
    val doneCount = remember(evaluations) { evaluations.filter { it.isComplete }.groupingBy { it.personId }.eachCount() }

    // Everybody can evaluate: the evaluator is chosen from all active people.
    val evaluatorOptions = remember(people) { uniqueLabels(people.filter { it.active }) }
    val evaluator = people.firstOrNull { it.id == evaluatorId && it.active }
    val datesChosen = fromEpoch != NO_DATE && toEpoch != NO_DATE
    val fromDate = LocalDate.ofEpochDay(if (fromEpoch == NO_DATE) LocalDate.now().toEpochDay() else fromEpoch)
    val toDate = LocalDate.ofEpochDay(if (toEpoch == NO_DATE) LocalDate.now().toEpochDay() else toEpoch)

    // Anyone active except the evaluator himself; a team is shown together (leader first).
    val leaderNames = remember(people) { people.filter { it.isLeader }.associate { it.id to it.name } }
    val candidates = remember(people, evaluatorId) { people.filter { it.active && it.id != evaluatorId } }
    val q = normalizeFa(query)
    val shown = candidates
        .filter { nameMatches(it.name, query) }
        .sortedByTeam(leaderNames) { it }
    val candidateIds = candidates.map { it.id }.toSet()
    val selected = selectedText.split(",").filter { it in candidateIds }.toSet()
    val chosen = candidates.filter { it.id in selected }.sortedByShiftName()

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
                    Text(dateBoxText("از", fromEpoch))
                }
                OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                    Text(dateBoxText("تا", toEpoch))
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
            shape = FormFieldShape, colors = formFieldColors(),
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام (حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused()
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { selectedText = (selected + shown.map { it.id }).joinToString(",") }) { Text("انتخاب همه") }
            TextButton(onClick = {
                selectedText = candidates.filter { draftOf(it)?.isComplete == false }.joinToString(",") { it.id }
            }) { Text("فقط ناتمام‌ها") }
            TextButton(onClick = { selectedText = "" }) { Text("هیچ‌کدام") }
        }
        if (shown.isEmpty()) {
            Text(
                if (searchTooShort(query)) "برای جستجو حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف بنویسید." else "نیرویی پیدا نشد.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        val open = openGroups.split(",").toSet()
        // Folds by shift name (A, B, C, D, none) and, inside, by employment.
        shiftGroups(shown.map { it.shiftName }).forEach { shiftKey ->
            Employment.entries.forEach { emp ->
                val members = shown.filter { it.shiftName == shiftKey && it.employment == emp }
                if (members.isEmpty()) return@forEach
                val key = "$shiftKey-${emp.name}"
                val expanded = key in open || q.isNotBlank()
                FoldGroup(
                    title = shiftTitle(shiftKey) + " • ${emp.title}",
                    accent = employmentColor(emp),
                    total = members.size,
                    selectedCount = members.count { it.id in selected },
                    expanded = expanded,
                    onToggle = { openGroups = (if (key in open) open - key else open + key).joinToString(",") },
                    onSelectAll = { allOn ->
                        val ids = members.map { it.id }
                        selectedText = (if (allOn) selected - ids.toSet() else selected + ids).joinToString(",")
                    }
                ) {
                    members.forEach { p ->
                        val isMember = p.leaderId != null && !p.isLeader
                        val checked = p.id in selected
                        val hasMembers = p.isLeader && candidates.any { it.leaderId == p.id }
                        val draft = draftOf(p)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    when {
                                        checked -> selectedText = (selected - p.id).joinToString(",")
                                        // Ticking a shift leader asks: only him, or with all his members?
                                        hasMembers -> leaderPrompt = p.id
                                        else -> selectedText = (selected + p.id).joinToString(",")
                                    }
                                }
                                .padding(start = if (isMember) 18.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                PersonNameText(p)
                                val status = when {
                                    draft == null -> if (p.shiftName.isNotBlank()) shiftTitle(p.shiftName) else ""
                                    draft.isComplete -> "در این دوره ارزیابی شده (${formatScore(draft.finalScore)})"
                                    else -> "ناتمام: ${draft.doneCount.toPersian()} از ${EvaluationItems.count.toPersian()} آیتم"
                                }
                                if (status.isNotBlank()) {
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (draft != null && !draft.isComplete) DraftOrange else Color.Gray
                                    )
                                }
                            }
                            CountBadge(doneCount[p.id] ?: 0)
                        }
                    }
                }
            }
        }
    }

    if (evaluator != null) {
        val resuming = chosen.count { draftOf(it)?.isComplete == false }
        Button(
            onClick = { onStart(evaluator.id, chosen.map { it.id }, fromEpoch, toEpoch) },
            enabled = chosen.isNotEmpty() && datesChosen,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (!datesChosen) "ابتدا تاریخ «از» و «تا» را انتخاب کنید"
                else if (chosen.isEmpty()) "ابتدا نفرات را تیک بزنید"
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
                    if (toEpoch != NO_DATE && toEpoch < fromEpoch) toEpoch = fromEpoch
                } else {
                    toEpoch = d.toEpochDay()
                    if (fromEpoch != NO_DATE && fromEpoch > toEpoch) fromEpoch = toEpoch
                }
                picker = 0
            }
        )
    }

    // ----- tick of a shift leader: only him, or with all his members
    val promptLeader = candidates.firstOrNull { it.id == leaderPrompt }
    if (promptLeader != null) {
        val teamIds = candidates.filter { it.leaderId == promptLeader.id }.map { it.id }
        AlertDialog(
            onDismissRequest = { leaderPrompt = "" },
            title = { Text("انتخاب سرشیفت", fontWeight = FontWeight.Bold) },
            text = { Text("«${promptLeader.name}» سرشیفت است. فقط خودش ارزیابی شود یا با همهٔ ${teamIds.size.toPersian()} نفر زیرمجموعه‌اش؟") },
            confirmButton = {
                TextButton(onClick = {
                    selectedText = (selected + promptLeader.id + teamIds).joinToString(",")
                    leaderPrompt = ""
                }) { Text("با همهٔ زیرمجموعه", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        selectedText = (selected + promptLeader.id).joinToString(",")
                        leaderPrompt = ""
                    }) { Text("فقط خودش") }
                    TextButton(onClick = { leaderPrompt = "" }) { Text("انصراف") }
                }
            }
        )
    }
}

private fun employmentColor(e: Employment): Color = when (e) {
    Employment.OFFICIAL -> Purple
    Employment.CONTRACTUAL -> Color(0xFF1E88A8)
    Employment.CONTRACTOR -> DraftOrange
}

/** A foldable group: coloured header with its title, «ticked/total» and a select-all box; [content] shows when unfolded. */
@Composable
private fun FoldGroup(
    title: String,
    accent: Color,
    total: Int,
    selectedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelectAll: (currentlyAll: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val all = total > 0 && selectedCount == total
    Surface(color = accent.copy(alpha = .08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TriStateCheckbox(
                    state = when {
                        all -> ToggleableState.On
                        selectedCount > 0 -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { onSelectAll(all) },
                    enabled = total > 0
                )
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = accent)
                Text(if (expanded) "▲" else "▼", Modifier.padding(start = 10.dp), color = accent)
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    if (total == 0) {
                        Text("نیرویی در این گروه نیست.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    }
                    content()
                }
            }
        }
    }
}

/** How many finished evaluations a person has, as a small pill. */
@Composable
private fun CountBadge(count: Int) {
    val color = if (count > 0) Purple else Color.Gray
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (count > 0) {
                Text(count.toPersian(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(" ارزیابی", color = color, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("بدون ارزیابی", color = color, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StepBox(number: Int, title: String, hint: String, enabled: Boolean, content: @Composable () -> Unit) {
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Two equal boxes that fill the whole width (ارزیابی جدید / نتایج). */
@Composable
private fun TwoBoxTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val on = selected == index
            Surface(
                color = if (on) Purple else Purple.copy(alpha = .12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(index) }
            ) {
                Text(
                    label,
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
                    textAlign = TextAlign.Center,
                    color = if (on) Color.White else Purple,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleSmall
                )
            }
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

/** «از … تا … • ارزیاب: …»: each evaluation period in the filter also names who evaluated in it. */
internal fun periodLabelsWithEvaluators(
    periods: List<Pair<LocalDate, LocalDate>>,
    evaluations: List<EvaluationEntry>,
    nameById: Map<String, String>
): List<String> = periods.map { (from, to) ->
    val names = evaluations.filter { it.date == from && it.dateTo == to }
        .map { nameById[it.evaluatorId] ?: it.evaluatorName }
        .filter { it.isNotBlank() }
        .distinct()
    periodText(from, to) + if (names.isEmpty()) "" else " • ارزیاب: " + names.joinToString("، ")
}

/** Equal names get a number so each label is unique. */
internal fun numberedLabels(names: List<String>): List<String> {
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
                verticalArrangement = Arrangement.spacedBy(CardGap)
            ) {
                // ----- quick fill and copy
                item {
                    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                itemsIndexed(EvaluationItems.all) { i, title ->
                    AppCard(border = if (i == focus) BorderStroke(2.dp, Purple) else null, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSave(scores) }, enabled = complete, modifier = Modifier.weight(1f)) {
                            Text("ذخیره")
                        }
                        OutlinedButton(
                            onClick = { onSave(scores) },
                            enabled = done > 0 && !complete,
                            modifier = Modifier.weight(1f)
                        ) { Text("ذخیرهٔ ناتمام") }
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
    onOpen: (String) -> Unit,
    onResume: (EvaluationEntry) -> Unit,
    onDelete: (List<String>) -> Unit
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var flippedGroups by rememberSaveable { mutableStateOf("") } // evaluator groups folded/unfolded against the default
    var personFilter by rememberSaveable { mutableStateOf("") }
    var evaluatorFilter by rememberSaveable { mutableStateOf("") }
    var periodFilter by rememberSaveable { mutableStateOf("") } // key of the evaluation period, blank = all

    val labelled = remember(people) { uniqueLabels(people).map { (p, n) -> p to if (p.active) n else "$n (غیرفعال)" } }
    val evaluatorIds = remember(evaluations) { evaluations.map { it.evaluatorId }.filter { it.isNotBlank() }.distinct() }
    val nameById = remember(people) { people.associate { it.id to it.name } }
    val rankById = remember(people) { people.displayOrder() }
    val memberIds = remember(people) { people.filter { it.isMember }.map { it.id }.toSet() }
    val evaluatorList = remember(evaluations, people) {
        evaluatorIds
            .map { id -> id to (nameById[id] ?: evaluations.firstOrNull { it.evaluatorId == id }?.evaluatorName.orEmpty()) }
            .sortedWith(compareBy<Pair<String, String>> { rankById[it.first] ?: Int.MAX_VALUE }.thenComparator { a, b -> compareNames(a.second, b.second) })
    }
    val evaluatorLabels = remember(evaluatorList) { numberedLabels(evaluatorList.map { it.second }) }
    val nameOf: (EvaluationEntry) -> String = { e -> nameById[e.personId] ?: e.personName }
    val periods = remember(evaluations) { evaluationPeriods(evaluations) }
    val periodLabels = remember(periods, evaluations, nameById) { periodLabelsWithEvaluators(periods, evaluations, nameById) }
    val periodIndex = periods.indexOfFirst { periodKeyOf(it.first, it.second) == periodFilter }

    val filtered = evaluations
        .filter {
            (personFilter.isBlank() || it.personId == personFilter) &&
                (evaluatorFilter.isBlank() || it.evaluatorId == evaluatorFilter) &&
                (periodFilter.isBlank() || it.periodKey == periodFilter)
        }
        .sortedWith(compareBy<EvaluationEntry> { rankById[it.personId] ?: Int.MAX_VALUE }.thenByDescending { it.date }.thenBy { nameOf(it) })

    // ----- filters and delete: folded until needed, a dot shows while a filter is on
    FoldBox(
        "فیلتر",
        active = periodFilter.isNotBlank() || personFilter.isNotBlank() || evaluatorFilter.isNotBlank()
    ) {
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
            // delete sits under all the filters and works on the filtered list
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = filtered.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("حذف") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف ارزیابی‌ها", fontWeight = FontWeight.Bold) },
            text = { Text("${filtered.size.toPersian()} ارزیابی از لیست فعلی حذف شود؟ بعد از حذف می‌توانید با «بازگرداندن» آن‌ها را برگردانید.") },
            confirmButton = {
                TextButton(onClick = { onDelete(filtered.map { it.id }); confirmDelete = false }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
        )
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
        AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ارزیابی‌های ناتمام (${drafts.size.toPersian()})",
                    fontWeight = FontWeight.Bold,
                    color = DraftOrange,
                    style = MaterialTheme.typography.titleMedium
                )
                drafts.forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { onResume(e) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StyledName(nameOf(e), e.personId in memberIds, Modifier.weight(1f))
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

    if (done.isEmpty()) return

    // ----- per person
    if (personFilter.isBlank()) {
        val perPerson = done.groupBy { it.personId }
            .map { (id, list) -> Triple(id, nameOf(list.first()), list) }
            .sortedWith(compareBy<Triple<String, String, List<EvaluationEntry>>> { rankById[it.first] ?: Int.MAX_VALUE }.thenByDescending { Evaluations.average(it.third) })
        FoldBox("میانگین هر نفر") {
                perPerson.forEach { (id, name, list) ->
                    val avg = Evaluations.average(list)
                    val lvl = Evaluations.levelOf(avg)
                    Column(Modifier.fillMaxWidth().clickable { personFilter = id }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StyledName(name, id in memberIds, Modifier.weight(1f))
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

    // ----- entries, one foldable group per evaluator (his name on top)
    Text(
        "ارزیابی‌ها (${done.size.toPersian()})",
        fontWeight = FontWeight.Bold,
        color = Navy,
        style = MaterialTheme.typography.titleMedium
    )
    val groups = done.groupBy { it.evaluatorId }
        .map { (id, list) ->
            val name = nameById[id] ?: list.firstOrNull { it.evaluatorName.isNotBlank() }?.evaluatorName.orEmpty()
            Triple(id, name.ifBlank { "ارزیاب نامشخص" }, list)
        }
        .sortedWith(compareBy<Triple<String, String, List<EvaluationEntry>>> { rankById[it.first] ?: Int.MAX_VALUE }.thenComparator { a, b -> compareNames(a.second, b.second) })
    val flipped = flippedGroups.split(",").toSet()
    groups.forEach { (id, evaluatorName, list) ->
        val key = id.ifBlank { "-" }
        val expanded = (groups.size == 1) != (key in flipped)
        val avg = Evaluations.average(list)
        AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { flippedGroups = (if (key in flipped) flipped - key else flipped + key).joinToString(",") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ارزیاب: $evaluatorName", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${list.size.toPersian()} ارزیابی • میانگین ${formatScore(avg)}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(if (expanded) "▲" else "▼", color = Purple)
                }
                if (expanded) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        list.forEach { e ->
                            val lvl = Evaluations.levelOf(e.finalScore)
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F6FD)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            StyledName(nameOf(e), e.personId in memberIds)
                                            Text(e.periodText(), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
                }
        }
    }
}

// ============================================================ advanced report

private data class PersonAverage(val person: Person, val average: Double, val count: Int)
private data class BreakdownRow(val label: String, val average: Double, val count: Int)

/** Small chips, several can be on; every chip has a key, the ticked keys are kept as "a,b,c". */
@Composable
private fun MultiChips(title: String, options: List<Pair<String, String>>, selected: String, onChange: (String) -> Unit) {
    val on = selected.split(",").toSet()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        // At most 4 chips per line, so many shift names wrap onto more lines instead of being squeezed.
        val perLine = if (options.size <= 4) options.size.coerceAtLeast(1) else 4
        options.chunked(perLine).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { (key, label) ->
                    val isOn = key in on
                    Surface(
                        color = if (isOn) Purple else Purple.copy(alpha = .10f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                            onChange((if (isOn) on - key else on + key).filter { it.isNotEmpty() }.joinToString(","))
                        }
                    ) {
                        Text(
                            label,
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = if (isOn) Color.White else Purple,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                // keep every chip the same width on a shorter last line
                repeat(perLine - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BreakdownBlock(title: String, rows: List<BreakdownRow>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        rows.forEach { r ->
            val color = levelColor(Evaluations.levelOf(r.average))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.width(104.dp), color = Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { ScoreBar(r.average / EvaluationItems.FINAL_MAX, color, 8) }
                Text(formatScore(r.average), color = color, fontWeight = FontWeight.Bold)
                Text(" (${r.count.toPersian()})", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Evaluation report on demand: choose the dates, the employment kind, the type and the shifts, and get
 * one short summary where the final number comes first.
 */
@Composable
internal fun AdvancedReportSection(people: List<Person>, evaluations: List<EvaluationEntry>) {
    val context = LocalContext.current
    val today = LocalDate.now().toEpochDay()
    // The first dates are remembered so the box can tell when the person has changed them (a filter is on).
    // Empty date boxes mean "no limit": only «از» / «تا» are shown until a date is chosen.
    val defaultFrom = NO_DATE
    val defaultTo = NO_DATE
    var fromEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var toEpoch by rememberSaveable { mutableStateOf(NO_DATE) }
    var empText by rememberSaveable { mutableStateOf(Employment.entries.joinToString(",") { it.name }) }
    var catText by rememberSaveable { mutableStateOf(Category.entries.joinToString(",") { it.name }) }
    // Shift chips: the standard names, any old plain letter still in use, and «بدون» (key "-").
    val shiftKeys = remember(people) { shiftGroups(people.map { it.shiftName }).map { it.ifBlank { "-" } } }
    var shiftText by rememberSaveable { mutableStateOf(shiftKeys.joinToString(",")) }
    var picker by rememberSaveable { mutableStateOf(0) } // 0 closed, 1 from, 2 to

    val fromDate = if (fromEpoch == NO_DATE) (evaluations.minOfOrNull { it.date } ?: LocalDate.now()) else LocalDate.ofEpochDay(fromEpoch)
    val toDate = if (toEpoch == NO_DATE) (evaluations.maxOfOrNull { it.dateTo } ?: LocalDate.now()) else LocalDate.ofEpochDay(toEpoch)
    val emps = empText.split(",").toSet()
    val cats = catText.split(",").toSet()
    val shifts = shiftText.split(",").toSet()
    val byId = remember(people) { people.associateBy { it.id } }

    val matched = evaluations.filter { e ->
        val p = byId[e.personId]
        e.isComplete &&
            e.dateTo >= fromDate && e.date <= toDate &&
            p != null &&
            p.employment.name in emps && p.category.name in cats &&
            p.shiftName.ifBlank { "-" } in shifts
    }
    val advancedOrder = remember(people) { people.displayOrder() }
    val average = Evaluations.average(matched)
    val level = Evaluations.levelOf(average)
    val perPerson = matched.groupBy { it.personId }
        .mapNotNull { (id, list) -> byId[id]?.let { PersonAverage(it, Evaluations.average(list), list.size) } }
        .sortedWith(compareBy<PersonAverage> { advancedOrder[it.person.id] ?: Int.MAX_VALUE }.thenByDescending { it.average })

    fun rowsBy(groups: List<Pair<String, (Person) -> Boolean>>) = groups.mapNotNull { (label, test) ->
        val list = matched.filter { e -> byId[e.personId]?.let(test) == true }
        if (list.isEmpty()) null else BreakdownRow(label, Evaluations.average(list), list.size)
    }
    val byEmployment = rowsBy(Employment.entries.map { emp -> emp.title to { p: Person -> p.employment == emp } })
    val byCategory = rowsBy(Category.entries.map { cat -> cat.title to { p: Person -> p.category == cat } })
    val byShift = rowsBy(
        shiftGroups(people.map { it.shiftName }).map { s -> shiftTitle(s) to { p: Person -> p.shiftName == s } }
    )

    // ----- what to include: folded until needed, a dot shows while any choice differs from the start
    val filterOn = fromEpoch != defaultFrom || toEpoch != defaultTo ||
        emps != Employment.entries.map { it.name }.toSet() ||
        cats != Category.entries.map { it.name }.toSet() ||
        shifts != shiftKeys.toSet()
    FoldBox("فیلتر گزارش", active = filterOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.weight(1f)) {
                    Text(dateBoxText("از", fromEpoch))
                }
                OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.weight(1f)) {
                    Text(dateBoxText("تا", toEpoch))
                }
            }
            MultiChips("وضعیت استخدامی", Employment.entries.map { it.name to it.title }, empText) { empText = it }
            MultiChips("نوع", Category.entries.map { it.name to it.title }, catText) { catText = it }
            MultiChips("شیفت", shiftKeys.map { it to (if (it == "-") "بدون" else it) }, shiftText) { shiftText = it }
    }

    if (matched.isEmpty()) {
        EmptyMessage(
            if (evaluations.none { it.isComplete }) "هنوز ارزیابی کاملی ثبت نشده است."
            else "با این انتخاب‌ها ارزیابی کاملی پیدا نشد. بازه یا گروه‌ها را تغییر دهید."
        )
    } else {
        // ----- the final number
        AppCard(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("نمرهٔ نهایی", color = Purple, fontWeight = FontWeight.Bold)
                ScoreBadge(average, big = true)
                ScoreBar(average / EvaluationItems.FINAL_MAX, levelColor(level), 12)
                Text(
                    "سطح: ${level.title} • ${matched.size.toPersian()} ارزیابی • ${perPerson.size.toPersian()} نفر",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(periodText(fromDate, toDate), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }

        // ----- split by kind
        AppCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BreakdownBlock("وضعیت استخدامی", byEmployment)
                BreakdownBlock("نوع", byCategory)
                BreakdownBlock("شیفت", byShift)
        }

        // ----- every person, shift A first
        AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نمرهٔ نهایی هر نفر", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
                perPerson.forEach { pa ->
                    val color = levelColor(Evaluations.levelOf(pa.average))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            StyledName(pa.person.name, pa.person.isMember)
                            Text(
                                (if (pa.person.shiftName.isNotBlank()) shiftTitle(pa.person.shiftName) + " • " else "") +
                                    "${pa.person.employment.title} • ${pa.person.category.title} • ${pa.count.toPersian()} ارزیابی",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(formatScore(pa.average), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                }
        }

        Button(
            onClick = {
                shareText(
                    context,
                    buildAdvancedText(
                        fromDate, toDate, average, level.title, matched.size, perPerson,
                        listOf("وضعیت استخدامی" to byEmployment, "نوع" to byCategory, "شیفت" to byShift)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ارسال گزارش") }
    }

    // ----- the score of one person, period after period
    TrendSection(people, evaluations)

    if (picker != 0) {
        JalaliDatePickerDialog(
            initial = if (picker == 1) fromDate else toDate,
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

private fun buildAdvancedText(
    from: LocalDate,
    to: LocalDate,
    average: Double,
    levelTitle: String,
    evaluationCount: Int,
    perPerson: List<PersonAverage>,
    blocks: List<Pair<String, List<BreakdownRow>>>
): String = buildString {
    appendLine("گزارش ارزیابی • ${periodText(from, to)}")
    appendLine("نمرهٔ نهایی: ${formatScore(average)} از ${EvaluationItems.FINAL_MAX.toPersian()} ($levelTitle)")
    appendLine("${evaluationCount.toPersian()} ارزیابی • ${perPerson.size.toPersian()} نفر")
    blocks.filter { it.second.isNotEmpty() }.forEach { (title, rows) ->
        appendLine()
        appendLine(title)
        rows.forEach { appendLine("• ${it.label}: ${formatScore(it.average)} (${it.count.toPersian()})") }
    }
    appendLine()
    appendLine("هر نفر")
    perPerson.forEach { appendLine("• ${it.person.name}: ${formatScore(it.average)}") }
}.trim()

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
                .fillMaxWidth(fraction.toFloat().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) })
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

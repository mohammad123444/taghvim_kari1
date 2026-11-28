package ir.taghvimkari.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ORDINARY_SHIFT
import ir.taghvimkari.app.domain.shiftGroups
import ir.taghvimkari.app.domain.shiftTitle
import ir.taghvimkari.app.domain.sortedByTeam
import ir.taghvimkari.app.domain.toPersian
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Colour and size of names everywhere in the app: a member (زیرمجموعه — somebody who belongs to a shift leader)
 * is smaller and in another colour; everybody else (shift leaders and people without a leader) is shown like a leader.
 */
val LeaderNameColor = Color(0xFF6A1B9A)
val MemberNameColor = Color(0xFF455A64)

val Person.isMember: Boolean get() = leaderId != null && !isLeader

@Composable
fun PersonNameText(person: Person, modifier: Modifier = Modifier) {
    if (person.isMember) {
        Text(person.name, modifier, color = MemberNameColor, fontSize = AppText.body)
    } else {
        Text(
            if (person.isLeader) "${person.name} • سرشیفت" else person.name,
            modifier,
            fontWeight = FontWeight.Bold,
            color = LeaderNameColor,
            fontSize = AppText.title
        )
    }
}

/** A name inside lists and cards, styled by [member]: members smaller and in another colour, the rest like a leader. */
@Composable
fun StyledName(
    name: String,
    member: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    if (member) {
        Text(name, modifier, color = MemberNameColor, fontSize = AppText.body, maxLines = maxLines)
    } else {
        Text(name, modifier, color = LeaderNameColor, fontWeight = FontWeight.Bold, fontSize = AppText.title, maxLines = maxLines)
    }
}

/** When this text box gets focus the screen scrolls so the box (and what opens under it) is not hidden by the keyboard. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.keepVisibleWhenFocused(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) scope.launch {
                delay(300)
                requester.bringIntoView()
            }
        }
}

private val SHIFT_ACCENT = mapOf(
    "A" to Color(0xFF3F51B5),
    "B" to Color(0xFF00897B),
    "C" to Color(0xFFEF6C00),
    "D" to Color(0xFF8E24AA),
    ORDINARY_SHIFT to Color(0xFF546E7A)
)

private fun toggled(open: Set<String>, key: String): String =
    (if (key in open) open - key else open + key).filter { it.isNotEmpty() }.joinToString(",")

/** A coloured, tappable header with a count; [content] is shown only while unfolded. */
@Composable
fun FoldSection(
    title: String,
    count: Int,
    accent: Color,
    expanded: Boolean,
    big: Boolean,
    onToggle: () -> Unit,
    unit: String = "نفر",
    /** Non-null when the title is a person's name: then it is styled as a member (true) or like a leader (false). */
    titleMember: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = accent.copy(alpha = if (big) .10f else .06f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = if (big) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (titleMember != null) {
                    StyledName(title, titleMember, Modifier.weight(1f))
                } else {
                    Text(
                        title,
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                    )
                }
                Text("${count.toPersian()} $unit", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(if (expanded) "  ▲" else "  ▼", color = accent)
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * People in folds: first by shift name (A:2.1 ... D:2.2, عادی کار, then «بدون شیفت»), inside each by employment
 * (رسمی / قراردادی / پیمانکاری). A shift leader comes first in his group, followed by his members.
 * Folded by default; [forceOpen] opens everything (e.g. while searching).
 */
@Composable
fun <T> ShiftEmploymentFolds(
    items: List<T>,
    person: (T) -> Person,
    stateKey: String,
    forceOpen: Boolean = false,
    row: @Composable (T) -> Unit
) {
    var openKeys by rememberSaveable(stateKey) { mutableStateOf("") }
    val open = openKeys.split(",").toSet()
    val leaderNames = remember(items) { items.map(person).filter { it.isLeader }.associate { it.id to it.name } }
    Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
        shiftGroups(items.map { person(it).shiftName }).forEach { shift ->
            val inShift = items.filter { person(it).shiftName == shift }
            if (inShift.isEmpty()) return@forEach
            val shiftKey = "s$shift"
            FoldSection(
                title = shiftTitle(shift),
                count = inShift.size,
                accent = SHIFT_ACCENT[shift.substringBefore(':')] ?: Color.Gray,
                expanded = forceOpen || shiftKey in open,
                big = true,
                onToggle = { openKeys = toggled(open, shiftKey) }
            ) {
                Employment.entries.forEach { emp ->
                    val group = inShift.filter { person(it).employment == emp }.sortedByTeam(leaderNames, person)
                    if (group.isEmpty()) return@forEach
                    val empKey = "$shiftKey-${emp.name}"
                    FoldSection(
                        title = emp.title,
                        count = group.size,
                        accent = employmentTint(emp),
                        expanded = forceOpen || empKey in open,
                        big = false,
                        onToggle = { openKeys = toggled(open, empKey) }
                    ) {
                        group.forEach { item -> row(item) }
                    }
                }
            }
        }
    }
}

fun employmentTint(e: Employment): Color = when (e) {
    Employment.OFFICIAL -> Blue
    Employment.CONTRACTUAL -> Green
    Employment.CONTRACTOR -> Orange
}

/** A date box with no chosen date shows only «از» / «تا» (no default date is filled in). */
const val NO_DATE = Long.MIN_VALUE

fun dateBoxText(prefix: String, epoch: Long): String =
    if (epoch == NO_DATE) prefix else "$prefix: ${JalaliDate.from(java.time.LocalDate.ofEpochDay(epoch)).display()}"

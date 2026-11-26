package ir.taghvimkari.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.shiftTitle
import ir.taghvimkari.app.domain.DayStatus
import ir.taghvimkari.app.domain.bucketOf
import ir.taghvimkari.app.domain.toPersian

@Composable
fun EmptyMessage(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        textAlign = TextAlign.Center,
        color = Color.Gray
    )
}

@Composable
fun FilterDropdown(label: String, options: List<String>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onChange(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun PersonCard(person: Person, status: DayStatus? = null, leaderName: String? = null, onClick: (() -> Unit)? = null) {
    val employmentColor = when (person.employment) {
        Employment.OFFICIAL -> Blue
        Employment.CONTRACTUAL -> Green
        Employment.CONTRACTOR -> Orange
    }
    AppCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PersonNameText(person)
                    if (!person.active) {
                        Text("غیرفعال (از واحد رفته)", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
                    }
                    if (leaderName != null) {
                        Text("زیرمجموعهٔ $leaderName", style = MaterialTheme.typography.bodySmall, color = Purple)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = employmentColor.copy(alpha = .13f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            person.employment.title,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = employmentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(color = Purple.copy(alpha = .10f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            person.category.title + if (person.shiftName.isNotBlank()) " • " + shiftTitle(person.shiftName) else "",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Purple,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (status != null) {
                val bucket = bucketOf(person, status)
                Spacer(Modifier.height(8.dp))
                // Label, shift and counts together in one box at the bottom of the card.
                Surface(color = Color(0xFFF1EFFB), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "وضعیت:",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Text(
                                bucket.title,
                                color = if (bucket.working) Green else Color.DarkGray,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            statusLine(status),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

/** One-line Persian summary of a [DayStatus]: how long the current work/rest stretch has lasted and how long remains. */
fun statusLine(status: DayStatus): String {
    val left = status.remainingDays
    return when {
        // Today counts as a day that has passed: day 10 of 14 -> "10 passed, 4 left".
        !status.started -> "${left.toPersian()} روز استراحت مانده"
        status.working -> "${status.elapsedDays.toPersian()} روز از کار گذشته • " +
            if (left > 0) "${left.toPersian()} روز کار مانده" else "امروز آخرین روز کار است"
        else -> "${status.elapsedDays.toPersian()} روز از استراحت گذشته • " +
            if (left > 0) "${left.toPersian()} روز استراحت مانده" else "امروز آخرین روز استراحت است"
    }
}

/**
 * A dialog with text boxes / buttons that always stays above the on-screen keyboard and the phone's own
 * navigation buttons: the dialog window is edge-to-edge and the content is padded by the system bars and the
 * keyboard. The body scrolls; the buttons stay pinned below it, so they can always be tapped while typing.
 */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // The dialog's own window may report no system-bar insets (edge-to-edge, targetSdk 35), which put the
    // buttons under the phone's navigation bar. So the bar sizes are read here, from the app window, and
    // applied as fixed padding; the keyboard still pushes the dialog up through the ime inset.
    val bars = WindowInsets.systemBars
    val density = LocalDensity.current
    val barInsets = WindowInsets(0, bars.getTop(density), 0, bars.getBottom(density))
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(barInsets.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        title,
                        Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { content() }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Dialog body text that scrolls instead of being cut off when the text size is large. */
@Composable
fun ScrollText(text: String) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(text) }
}

/**
 * The one white card of the app: corner 18dp, a light shadow and 14dp inside. Every page uses this instead of
 * its own Card, so cards look the same everywhere. Put the gap between cards in the parent with [CardGap].
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = border
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/**
 * The button that opens / closes a folded box («گزارش‌گیری ▼»). When [active] (a filter is on) a small dot shows
 * beside the title, with the words «فیلتر فعال» too when [showLabel] is on and there is room.
 */
@Composable
fun FoldToggle(
    title: String,
    open: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(if (open) "$title ▲" else "$title ▼")
        if (active) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
            if (showLabel) {
                Spacer(Modifier.width(4.dp))
                Text("فیلتر فعال", color = Orange, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** The card that opens under a [FoldToggle]: the same spacing for every folded box. */
@Composable
fun FoldPanel(content: @Composable ColumnScope.() -> Unit) {
    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/**
 * A box for filters, search and send / receive that stays folded (closed) until the title is tapped.
 * Use it on every page instead of showing these tools all the time. Pass [active] = true while any filter
 * inside is on, so the person still sees that the list is filtered.
 */
@Composable
fun FoldBox(
    title: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CardGap)) {
        FoldToggle(title, open, active, onClick = { open = !open }, modifier = Modifier.fillMaxWidth())
        if (open) FoldPanel(content = content)
    }
}

/** The send / receive buttons of a section, without a card around them, so they sit inside a [FoldBox]; [onDelete] adds «حذف». */
@Composable
fun ExchangeRow(
    sendEnabled: Boolean,
    sendFailed: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSend, enabled = sendEnabled, modifier = Modifier.weight(1f)) { Text("ارسال") }
            OutlinedButton(onClick = onReceive, modifier = Modifier.weight(1f)) { Text("دریافت") }
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = deleteEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text("حذف") }
            }
        }
        if (sendFailed) {
            Text("ارسال انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

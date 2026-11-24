package ir.taghvimkari.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.Category
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Pattern
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.WorkTypes
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate
import java.util.UUID

/**
 * Add ([initial] == null) or edit one person.
 * For a shift leader, names typed in the members box are created with the same
 * employment, pattern and start date, so nothing has to be repeated per person.
 */
@Composable
fun AddPersonDialog(
    initial: Person? = null,
    /** Number of existing members when editing a leader. */
    memberCount: Int = 0,
    /** Only offered when editing; the caller asks for confirmation before really deleting. */
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (head: Person, members: List<Person>, applyToMembers: Boolean) -> Unit
) {
    val today = remember { LocalDate.now() }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var employment by remember { mutableStateOf(initial?.employment ?: Employment.OFFICIAL) }
    var category by remember { mutableStateOf(initial?.category ?: Category.OPERATIONAL) }
    var applyToMembers by rememberSaveable { mutableStateOf(true) }
    var pattern by remember { mutableStateOf(initial?.pattern ?: Pattern.DAY_NIGHT_REST) }
    var startEpochDay by rememberSaveable { mutableStateOf((initial?.startDate ?: today).toEpochDay()) }
    var isLeader by rememberSaveable { mutableStateOf(initial?.isLeader ?: false) }
    var membersText by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val startDate = LocalDate.ofEpochDay(startEpochDay)
    val memberNames = membersText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    FormDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "ثبت نیروی جدید" else "ویرایش نیرو",
        content = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(if (isLeader) "نام سرشیفت" else "نام و نام خانوادگی") },
                    singleLine = true, isError = showErrors && name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                FilterDropdown("وضعیت استخدامی", Employment.entries.map { it.title }, employment.title) { v ->
                    employment = Employment.entries.first { it.title == v }
                }
                FilterDropdown("نوع نیرو", Category.entries.map { it.title }, category.title) { v ->
                    category = Category.entries.first { it.title == v }
                }
                FilterDropdown("الگوی شیفت", Pattern.entries.map { it.title }, pattern.title) { v ->
                    pattern = Pattern.entries.first { it.title == v }
                }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تاریخ شروع: ${JalaliDate.from(startDate).display()}")
                }
                Text(
                    JalaliDate.longText(startDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("این فرد سرشیفت است", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = isLeader, onCheckedChange = { isLeader = it })
                }
                if (initial != null && initial.isLeader && memberCount > 0) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "اعمال تغییرات روی ${memberCount.toPersian()} نفر زیرمجموعه",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Switch(checked = applyToMembers, onCheckedChange = { applyToMembers = it })
                    }
                }
                if (isLeader) {
                    OutlinedTextField(
                        value = membersText, onValueChange = { membersText = it },
                        label = { Text("افراد زیرمجموعه (هر نفر در یک خط)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (memberNames.isNotEmpty()) {
                        Text(
                            "${memberNames.size.toPersian()} نفر با همین شرایط ثبت می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    // Keep the stored weekday when editing without changing the date, so the schedule does not shift.
                    val weekday = if (initial != null && startDate == initial.startDate) {
                        initial.startWeekday
                    } else {
                        ShiftEngine.persianWeekday(startDate)
                    }
                    val head = Person(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        workType = initial?.workType ?: WorkTypes.default,
                        employment = employment,
                        pattern = pattern,
                        startDate = startDate,
                        startWeekday = weekday,
                        active = initial?.active ?: true,
                        isLeader = isLeader,
                        leaderId = initial?.leaderId,
                        category = category
                    )
                    val members = if (isLeader) {
                        memberNames.map { n ->
                            Person(
                                id = UUID.randomUUID().toString(),
                                name = n,
                                workType = WorkTypes.default,
                                employment = employment,
                                pattern = pattern,
                                startDate = startDate,
                                startWeekday = weekday,
                                leaderId = head.id,
                                category = category
                            )
                        }
                    } else {
                        emptyList()
                    }
                    onSave(head, members, initial != null && initial.isLeader && memberCount > 0 && applyToMembers)
                } else {
                    showErrors = true
                }
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )

    if (showPicker) {
        JalaliDatePickerDialog(
            initial = startDate,
            title = "انتخاب تاریخ شروع",
            onDismiss = { showPicker = false },
            onPick = { startEpochDay = it.toEpochDay(); showPicker = false }
        )
    }
}

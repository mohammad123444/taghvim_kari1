package ir.taghvimkari.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import ir.taghvimkari.app.domain.SHIFT_NAMES
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.WorkTypes
import ir.taghvimkari.app.domain.patternForShift
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
    /** Names of the existing members when editing a leader; always listed under the leader. */
    existingMembers: List<String> = emptyList(),
    /** Opens the transfer (جابجایی شیفت) dialog; only offered when editing. */
    onTransfer: (() -> Unit)? = null,
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
    var pattern by remember { mutableStateOf(initial?.pattern ?: patternForShift(SHIFT_NAMES.first()) ?: Pattern.DAY_NIGHT_REST) }
    var shiftName by rememberSaveable { mutableStateOf(if (initial == null) SHIFT_NAMES.first() else initial.shiftName) }
    var startEpochDay by rememberSaveable { mutableStateOf((initial?.startDate ?: today).toEpochDay()) }
    var isLeader by rememberSaveable { mutableStateOf(initial?.isLeader ?: false) }
    var membersText by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(initial?.active ?: true) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val startDate = LocalDate.ofEpochDay(startEpochDay)
    val memberNames = membersText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    FormDialog(
        onDismiss = onDismiss,
        title = if (initial == null) "ثبت نیروی جدید" else "ویرایش نیرو",
        icon = "👤",
        subtitle = if (initial == null) "مشخصات و برنامهٔ کاری را وارد کنید" else "اطلاعات را تغییر دهید و ذخیره کنید",
        content = {
            FormSection("مشخصات", icon = "🪪") {
                FormTextField(
                    value = name, onValueChange = { name = it },
                    label = if (isLeader) "نام سرشیفت" else "نام و نام خانوادگی",
                    isError = showErrors && name.isBlank()
                )
                FormChoiceChips("وضعیت استخدامی", Employment.entries.map { it.title }, employment.title) { v ->
                    employment = Employment.entries.first { it.title == v }
                }
                FormChoiceChips("نوع نیرو", Category.entries.map { it.title }, category.title, perLine = 2) { v ->
                    category = Category.entries.first { it.title == v }
                }
            }
            FormSection("شیفت و برنامهٔ کاری", icon = "🗓") {
                FormChoiceChips("نام شیفت", SHIFT_NAMES, shiftName, perLine = 4) { v ->
                    shiftName = v
                    // the pattern follows the shift (A:2.1 → ۲ هفته روزکار + ۱ استراحت ...); it can still be changed below
                    patternForShift(v)?.let { pattern = it }
                }
                FormDropdown("الگوی شیفت", Pattern.entries.map { it.title }, pattern.title) { v ->
                    pattern = Pattern.entries.first { it.title == v }
                }
                Text(
                    "با انتخاب شیفت، الگو خودکار تنظیم می‌شود؛ در صورت نیاز می‌توانید تغییرش دهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                FormDateField("تاریخ شروع", JalaliDate.from(startDate).display(), JalaliDate.longText(startDate)) { showPicker = true }
                if (initial != null && onTransfer != null) {
                    OutlinedButton(onClick = onTransfer, shape = FormFieldShape, modifier = Modifier.fillMaxWidth()) { Text("جابجایی به شیفت دیگر") }
                }
            }
            FormSection("نقش و وضعیت", icon = "⚙️") {
                FormSwitchRow("این فرد سرشیفت است", null, isLeader) { isLeader = it }
                if (isLeader) {
                    FormTextField(
                        value = membersText, onValueChange = { membersText = it },
                        label = "افراد زیرمجموعه (هر نفر در یک خط)",
                        singleLine = false, minLines = 3
                    )
                    if (memberNames.isNotEmpty()) {
                        Text(
                            "${memberNames.size.toPersian()} نفر با همین شرایط ثبت می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                FormSwitchRow(
                    if (active) "فعال" else "غیرفعال",
                    "اگر از واحد رفته یا دیگر در سازمان نیست، غیرفعال کنید؛ در ارزیابی و ثبت سابقهٔ جدید نمایش داده نمی‌شود اما اطلاعاتش حذف نمی‌شود و در گزارش‌ها هست.",
                    active
                ) { active = it }
            }
            if (initial != null && initial.isLeader && existingMembers.isNotEmpty()) {
                // The members always stay listed right under their shift leader.
                FormSection("زیرمجموعه‌های ${initial.name}", icon = "👥") {
                    existingMembers.forEach { n ->
                        Text("• $n", Modifier.padding(start = 6.dp), color = MemberNameColor, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (memberCount > 0) {
                        FormSwitchRow("اعمال تغییرات روی ${memberCount.toPersian()} نفر زیرمجموعه", null, applyToMembers) { applyToMembers = it }
                    }
                }
            } else if (initial != null && initial.isLeader && memberCount > 0) {
                FormSwitchRow("اعمال تغییرات روی ${memberCount.toPersian()} نفر زیرمجموعه", null, applyToMembers) { applyToMembers = it }
            }
        },
        confirmButton = {
            FormSaveButton("ذخیره", onClick = {
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
                        active = active,
                        isLeader = isLeader,
                        leaderId = initial?.leaderId,
                        category = category,
                        canEvaluate = initial?.canEvaluate ?: false,
                        shiftName = shiftName
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
                                category = category,
                                shiftName = shiftName
                            )
                        }
                    } else {
                        emptyList()
                    }
                    onSave(head, members, initial != null && initial.isLeader && memberCount > 0 && applyToMembers)
                } else {
                    showErrors = true
                }
            })
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = Color.Red, fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نیرو", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "آیا مطمئنی که «${name.trim().ifBlank { initial?.name.orEmpty() }}» حذف شود؟" +
                        if (initial?.isLeader == true && memberCount > 0) " زیرمجموعه‌های او حذف نمی‌شوند." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("بله، حذف شود", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("نه") } }
        )
    }

    if (showPicker) {
        JalaliDatePickerDialog(
            initial = startDate,
            title = "انتخاب تاریخ شروع",
            onDismiss = { showPicker = false },
            onPick = { startEpochDay = it.toEpochDay(); showPicker = false }
        )
    }
}

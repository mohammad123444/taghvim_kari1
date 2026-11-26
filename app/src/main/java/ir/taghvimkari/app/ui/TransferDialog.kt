package ir.taghvimkari.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.SHIFT_NAMES
import ir.taghvimkari.app.domain.shiftTitle
import java.time.LocalDate

private const val NO_LEADER = "بدون سرشیفت (الگوی فعلی ادامه می‌یابد)"

/**
 * Moves [person] to another shift, either alone or by swapping places with another person.
 * His rewards, punishments and evaluations stay under his name; only his work calendar changes,
 * from the chosen date on (earlier dates keep the old calendar).
 */
@Composable
fun TransferDialog(
    person: Person,
    people: List<Person>,
    onMove: (shiftName: String, leaderId: String?, from: LocalDate) -> Unit,
    onSwap: (otherId: String, from: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var swapMode by rememberSaveable { mutableStateOf(false) }
    var targetShift by rememberSaveable { mutableStateOf(SHIFT_NAMES.firstOrNull { it != person.shiftName } ?: SHIFT_NAMES.first()) }
    var leaderId by rememberSaveable { mutableStateOf("") }
    var otherId by rememberSaveable { mutableStateOf("") }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpoch)

    val leaders = remember(people, targetShift) {
        uniqueLabels(people.filter { it.active && it.isLeader && it.id != person.id && it.shiftName == targetShift })
    }
    val others = remember(people) { uniqueLabels(people.filter { it.active && it.id != person.id }) }
    val leaderLabel = leaders.firstOrNull { it.first.id == leaderId }?.second ?: NO_LEADER

    FormDialog(
        title = "جابجایی ${person.name}",
        onDismiss = onDismiss,
        content = {
            Text(
                "سوابق تشویق، تنبیه و ارزیابی به نام همین فرد می‌ماند و تغییری نمی‌کند؛ فقط تقویم کاری او از تاریخ جابجایی بر اساس شیفت جدید حساب می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!swapMode) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("جابجایی تنها") }
                    OutlinedButton(onClick = { swapMode = true }, modifier = Modifier.weight(1f)) { Text("با شخص دیگر") }
                } else {
                    OutlinedButton(onClick = { swapMode = false }, modifier = Modifier.weight(1f)) { Text("جابجایی تنها") }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("با شخص دیگر") }
                }
            }
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("تاریخ جابجایی: ${JalaliDate.from(date).display()}")
            }
            if (!swapMode) {
                Text(
                    "اکنون: " + shiftTitle(person.shiftName),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                FilterDropdown("شیفت مقصد", SHIFT_NAMES, targetShift) { v -> targetShift = v; leaderId = "" }
                FilterDropdown("سرشیفت مقصد", listOf(NO_LEADER) + leaders.map { it.second }, leaderLabel) { v ->
                    leaderId = leaders.firstOrNull { it.second == v }?.first?.id ?: ""
                }
            } else {
                PersonSearchField(
                    label = "جابجا با چه کسی؟",
                    options = others,
                    selectedId = otherId,
                    onSelect = { otherId = it }
                )
                Text(
                    "هر دو نفر شیفت و تقویم کاری یکدیگر را از تاریخ بالا می‌گیرند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (swapMode) onSwap(otherId, date) else onMove(targetShift, leaderId.ifBlank { null }, date)
                },
                enabled = !swapMode || otherId.isNotBlank()
            ) { Text("انجام جابجایی", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )

    if (showPicker) {
        JalaliDatePickerDialog(
            initial = date,
            title = "تاریخ جابجایی",
            onDismiss = { showPicker = false },
            onPick = { dateEpoch = it.toEpochDay(); showPicker = false }
        )
    }
}

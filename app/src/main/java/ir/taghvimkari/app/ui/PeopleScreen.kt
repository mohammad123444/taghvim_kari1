package ir.taghvimkari.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.sortedByTeam
import java.time.LocalDate

@Composable
fun PeopleScreen(
    people: List<Person>,
    onAdd: () -> Unit,
    onEdit: (Person) -> Unit,
    sendFailed: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val isToday = date == LocalDate.now()
    val leaderNames = people.filter { it.isLeader }.associate { it.id to it.name }
    // Each shift leader comes first, immediately followed by his members.
    val filtered = people
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
        .sortedByTeam(leaderNames) { it }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("جستجو بر اساس نام") },
                singleLine = true
            )
            HelpTip("برای ثبت نیروی تازه، دکمهٔ + را بزنید. برای ویرایش یا حذف، روی هر نفر بزنید.")
            FilledIconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "افزودن نیرو") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                Text("وضعیت در تاریخ: ${JalaliDate.from(date).display()}")
            }
            if (!isToday) {
                TextButton(onClick = { dateEpoch = LocalDate.now().toEpochDay() }) { Text("امروز") }
            }
        }
        Text(
            JalaliDate.longText(date),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) EmptyMessage("نیرویی پیدا نشد.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ExchangeBar(
                    warning = "اطلاعات دریافتی، پس از تأیید، جایگزین فهرست نیروهای این گوشی می‌شود.",
                    help = "فقط اطلاعات ثبت نیروها (نیروها، سرشیفت‌ها و زیرمجموعه‌ها) ارسال می‌شود؛ ارزیابی و گزارش‌ها جدا و از بخش خودشان ارسال می‌شوند.",
                    sendLabel = "ارسال اطلاعات نیروها",
                    sendEnabled = people.isNotEmpty(),
                    sendFailed = sendFailed,
                    onSend = onSend,
                    onReceive = onReceive
                )
            }
            items(filtered, key = { it.id }) { p ->
                Row(
                    Modifier.padding(start = if (p.leaderId != null && !p.isLeader) 20.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        PersonCard(p, ShiftEngine.status(p, date), leaderName = p.leaderId?.let { leaderNames[it] }, onClick = { onEdit(p) })
                    }
                }
            }
        }
    }

    if (showPicker) {
        JalaliDatePickerDialog(
            initial = date,
            title = "انتخاب تاریخ",
            onDismiss = { showPicker = false },
            onPick = { dateEpoch = it.toEpochDay(); showPicker = false }
        )
    }
}

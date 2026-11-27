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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
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
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.searchTooShort
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate

@Composable
fun PeopleScreen(
    people: List<Person>,
    onAdd: () -> Unit,
    onEdit: (Person) -> Unit,
    onCompare: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val isToday = date == LocalDate.now()
    val leaderNames = people.filter { it.isLeader }.associate { it.id to it.name }
    // Names are searched only after three characters; the folds open by themselves while searching.
    val searching = query.isNotBlank()
    val filtered = people.filter { nameMatches(it.name, query) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(CardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("افزودن نیرو", onAdd, Modifier.weight(1f), icon = Icons.Default.Add)
            ActionButton("مقایسه نیروها", onCompare, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.List, filled = false)
        }
        // The name search is a plain box on the page, not inside a fold.
        OutlinedTextField(
            shape = FormFieldShape, colors = formFieldColors(),
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().keepVisibleWhenFocused(),
            label = { Text("جستجو بر اساس نام (حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف)") },
            singleLine = true
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }
        if (filtered.isEmpty()) {
            EmptyMessage(if (searchTooShort(query)) "برای جستجو حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف بنویسید." else "نیرویی پیدا نشد.")
        }
        // Folded by shift name (A, B, C, D) and, inside, by employment; each shift leader first, then his members.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ShiftEmploymentFolds(items = filtered, person = { it }, stateKey = "people", forceOpen = searching) { p ->
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

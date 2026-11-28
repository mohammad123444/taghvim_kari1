package ir.taghvimkari.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.formatHours
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.normalizeFa
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.searchTooShort
import ir.taghvimkari.app.domain.toPersian

/**
 * «جست‌وجوی سراسری»: type a name and see the person; tap him / her to see every evaluation and every
 * report (سابقه) in one place. Text typed in a report's title or notes is found too.
 */
@Composable
fun GlobalSearchDialog(viewModel: PeopleViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var personId by rememberSaveable { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; personId = "" },
                        label = { Text("جست‌وجوی نام (دست‌کم سه حرف)") },
                        singleLine = true,
                        shape = FormFieldShape,
                        colors = formFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "بستن") }
                }
                val person = viewModel.people.firstOrNull { it.id == personId }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (person != null) PersonDetail(viewModel, person, onBack = { personId = "" })
                    else SearchResults(viewModel, query, onPick = { personId = it })
                }
            }
        }
    }
}

@Composable
private fun SearchResults(viewModel: PeopleViewModel, query: String, onPick: (String) -> Unit) {
    when {
        query.isBlank() -> Hint("نام یک نفر را بنویسید تا ارزیابی‌ها و سوابق او یک‌جا دیده شود.")
        searchTooShort(query) -> Hint("دست‌کم سه حرف بنویسید.")
        else -> {
            val people = viewModel.people.filter { nameMatches(it.name, query) }
                .sortedWith { a, b -> ir.taghvimkari.app.domain.compareNames(a.name, b.name) }
            val q = normalizeFa(query)
            // reports whose title or notes contain the text (people already found are not repeated)
            val textHits = viewModel.reports
                .filter { normalizeFa(it.title).contains(q) || normalizeFa(it.notes).contains(q) }
                .sortedByDescending { it.date }
                .take(30)
            if (people.isEmpty() && textHits.isEmpty()) Hint("چیزی پیدا نشد.")
            people.forEach { p ->
                AppCard(onClick = { onPick(p.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        p.name + if (!p.active) " (غیرفعال)" else "",
                        fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium
                    )
                    val evals = viewModel.evaluations.count { it.personId == p.id }
                    val reps = viewModel.reports.count { it.personId == p.id }
                    Text(
                        "${p.employment.title} • ${evals.toPersian()} ارزیابی • ${reps.toPersian()} سابقه",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (textHits.isNotEmpty()) {
                Text("سوابقی که این متن در آن‌ها آمده", fontWeight = FontWeight.Bold, color = Purple)
                textHits.forEach { r ->
                    AppCard(onClick = { r.personId.takeIf { id -> viewModel.people.any { it.id == id } }?.let(onPick) },
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.personName, fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            "${JalaliDate.from(r.date).display()} • ${r.title}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonDetail(viewModel: PeopleViewModel, person: Person, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
        Text("‹ بازگشت به نتایج", color = Purple, fontWeight = FontWeight.Bold)
    }
    Text(person.name + if (!person.active) " (غیرفعال)" else "", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleLarge)
    Text(
        person.employment.title + if (person.shiftName.isNotBlank()) " • ${person.shiftName}" else "",
        color = Color.Gray, style = MaterialTheme.typography.bodySmall
    )

    val evals = viewModel.evaluations.filter { it.personId == person.id }.sortedByDescending { it.date }
    Text("ارزیابی‌ها (${evals.size.toPersian()})", fontWeight = FontWeight.Bold, color = Purple)
    if (evals.isEmpty()) Hint("ارزیابی‌ای ثبت نشده است.")
    evals.forEach { e ->
        AppCard(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.periodText(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (e.isComplete) formatScore(e.finalScore) else "ناتمام",
                    fontWeight = FontWeight.Bold,
                    color = if (e.isComplete) Navy else Orange
                )
            }
            if (e.evaluatorName.isNotBlank()) {
                Text("ارزیاب: ${e.evaluatorName}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    val reports = viewModel.reports.filter { it.personId == person.id }.sortedByDescending { it.date }
    Text("سوابق (${reports.size.toPersian()})", fontWeight = FontWeight.Bold, color = Purple)
    if (reports.isEmpty()) Hint("سابقه‌ای ثبت نشده است.")
    reports.forEach { r ->
        AppCard(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${JalaliDate.from(r.date).display()} • ${r.title}", fontWeight = FontWeight.Bold)
            Text(
                "تشویق ${formatHours(r.rewardHours)} ساعت • تنبیه ${formatHours(r.punishmentHours)} ساعت",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
            if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
}

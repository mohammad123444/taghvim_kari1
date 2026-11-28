package ir.taghvimkari.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.MIN_SEARCH_CHARS
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.nameMatches
import ir.taghvimkari.app.domain.normalizeFa

/**
 * Type part of a name and the matching full names are suggested right below, in alphabetical order.
 * [options] must already be in the order to show. [selectedId] is blank when nobody is chosen.
 *
 * When [subjects] is given (the report subjects that exist) the same box also searches the subject: matching subjects are
 * suggested under the names, and the typed text itself can be used as a subject search. A chosen subject is reported
 * through [onSelectSubject] (and shown in [selectedSubject]); choosing a person clears the subject and the other way round.
 * With [clearOnSelect] the box empties after a person is picked, so another one can be added (many-people forms).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonSearchField(
    label: String,
    options: List<Pair<Person, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null,
    subjects: List<String>? = null,
    selectedSubject: String = "",
    onSelectSubject: (String) -> Unit = {},
    clearOnSelect: Boolean = false
) {
    val selectedLabel = options.firstOrNull { it.first.id == selectedId }?.second
        ?: fallbackLabel?.takeIf { selectedId.isNotBlank() && it.isNotBlank() }
        ?: selectedSubject.takeIf { subjects != null && it.isNotBlank() }
    var query by rememberSaveable { mutableStateOf(selectedLabel ?: "") }
    var focused by remember { mutableStateOf(false) }
    // What was in the box before «پاک کردن»: shown as «بازگرداندن» so nothing is lost by a wrong tap.
    var undoText by remember { mutableStateOf("") }
    var undoId by remember { mutableStateOf("") }
    var undoSubject by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // A choice made elsewhere (or restored) shows its full name in the box; a choice removed elsewhere («نمایش همه») empties it.
    var shownLabel by remember { mutableStateOf(selectedLabel) }
    LaunchedEffect(selectedId, selectedLabel) {
        if (selectedLabel != null) query = selectedLabel
        else if (shownLabel != null && query == shownLabel) query = ""
        shownLabel = selectedLabel
    }

    val q = normalizeFa(query)
    val showing = focused && query != selectedLabel
    // No name is listed until at least three characters are typed (also when the box is empty).
    val tooShort = q.length < MIN_SEARCH_CHARS
    val suggestions = if (!showing || tooShort) {
        emptyList()
    } else {
        options.filter { nameMatches(it.second, query) }
    }
    // Subjects that contain the typed text (each subject once), after the names.
    val subjectSuggestions = if (subjects == null || !showing || tooShort) {
        emptyList()
    } else {
        subjects.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { normalizeFa(it) }
            .filter { normalizeFa(it).contains(q) && normalizeFa(it) != q }
            .take(6)
    }

    // The field and its suggestions are scrolled into view, so the keyboard never hides them.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(showing, query, suggestions.size, subjectSuggestions.size) {
        if (showing) {
            delay(250)
            requester.bringIntoView()
        }
    }

    Column(modifier.bringIntoViewRequester(requester)) {
        OutlinedTextField(
            shape = FormFieldShape, colors = formFieldColors(),
            value = query,
            // Typing does not throw the chosen person away: the choice stays until another name is picked
            // (or the box is cleared), and leaving the box unfinished brings the chosen name back.
            onValueChange = {
                query = it
                undoText = ""
            },
            label = { Text(label) },
            placeholder = { Text(if (subjects != null) "بخشی از نام یا موضوع را بنویسید" else "بخشی از نام را بنویسید") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        undoText = query
                        undoId = selectedId
                        undoSubject = selectedSubject
                        query = ""
                        onSelect("")
                        if (subjects != null) onSelectSubject("")
                    }) { Icon(Icons.Default.Clear, contentDescription = "پاک کردن") }
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                val left = focused && !it.isFocused
                focused = it.isFocused
                if (left && selectedLabel != null && query != selectedLabel) query = selectedLabel
            }
        )
        if (query.isEmpty() && undoText.isNotBlank()) {
            TextButton(onClick = {
                query = undoText
                if (undoId.isNotBlank()) onSelect(undoId)
                if (undoSubject.isNotBlank()) onSelectSubject(undoSubject)
                undoText = ""
            }) { Text("↩ بازگرداندن «${undoText.trim()}»") }
        }
        if (showing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                if (suggestions.isEmpty() && subjects == null) {
                    Text(
                        if (tooShort) "حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف از نام را بنویسید." else "نامی با این حروف پیدا نشد.",
                        Modifier.padding(12.dp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (tooShort) {
                    Text(
                        "حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف از نام یا موضوع را بنویسید.",
                        Modifier.padding(12.dp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        suggestions.forEachIndexed { index, (person, name) ->
                            if (index > 0) HorizontalDivider(color = Color(0xFFEDEBF7))
                            StyledName(
                                name,
                                person.isMember,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = if (clearOnSelect) "" else name
                                        undoText = ""
                                        if (subjects != null) onSelectSubject("")
                                        onSelect(person.id)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                        if (subjects != null) {
                            fun pickSubject(text: String) {
                                query = text
                                undoText = ""
                                onSelect("")
                                onSelectSubject(text)
                                focusManager.clearFocus()
                            }
                            if (suggestions.isNotEmpty()) HorizontalDivider(color = Color(0xFFEDEBF7))
                            // the typed text itself, searched as a subject
                            Text(
                                "🔍 جستجوی موضوع «${query.trim()}»",
                                Modifier.fillMaxWidth().clickable { pickSubject(query.trim()) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                color = Purple,
                                fontWeight = FontWeight.Bold
                            )
                            subjectSuggestions.forEach { subject ->
                                HorizontalDivider(color = Color(0xFFEDEBF7))
                                Text(
                                    "📝 $subject",
                                    Modifier.fillMaxWidth().clickable { pickSubject(subject) }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    color = Navy
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

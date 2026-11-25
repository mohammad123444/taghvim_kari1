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
import ir.taghvimkari.app.domain.searchTooShort

/**
 * Type part of a name and the matching full names are suggested right below, in alphabetical order.
 * [options] must already be in the order to show. [selectedId] is blank when nobody is chosen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonSearchField(
    label: String,
    options: List<Pair<Person, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fallbackLabel: String? = null
) {
    val selectedLabel = options.firstOrNull { it.first.id == selectedId }?.second
        ?: fallbackLabel?.takeIf { selectedId.isNotBlank() && it.isNotBlank() }
    var query by rememberSaveable { mutableStateOf(selectedLabel ?: "") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // A choice made elsewhere (or restored) shows its full name in the box.
    LaunchedEffect(selectedId, selectedLabel) {
        if (selectedLabel != null) query = selectedLabel
    }

    val q = normalizeFa(query)
    val showing = focused && query != selectedLabel
    // Nothing is found until three characters are typed.
    val tooShort = searchTooShort(query)
    val suggestions = if (!showing || tooShort) {
        emptyList()
    } else if (q.isBlank()) {
        options
    } else {
        options.filter { nameMatches(it.second, query) }
    }

    // The field and its suggestions are scrolled into view, so the keyboard never hides them.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(showing, query, suggestions.size) {
        if (showing) {
            delay(250)
            requester.bringIntoView()
        }
    }

    Column(modifier.bringIntoViewRequester(requester)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (selectedId.isNotBlank()) onSelect("")
            },
            label = { Text(label) },
            placeholder = { Text("بخشی از نام را بنویسید") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onSelect("")
                    }) { Icon(Icons.Default.Clear, contentDescription = "پاک کردن") }
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
        if (showing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        if (tooShort) "حداقل ${MIN_SEARCH_CHARS.toPersian()} حرف از نام را بنویسید." else "نامی با این حروف پیدا نشد.",
                        Modifier.padding(12.dp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(
                        Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
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
                                        query = name
                                        onSelect(person.id)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

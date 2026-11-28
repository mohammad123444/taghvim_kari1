package ir.taghvimkari.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

/** Building blocks of the entry forms (ثبت نیرو، ثبت سابقه): grouped sections, rounded fields, one look everywhere. */

val FormFieldShape = RoundedCornerShape(14.dp)
private val FormBorder = Color(0xFFD5D2E8)

@Composable
fun formFieldColors(accent: Color = Purple, tint: Color = Color.White) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = if (tint == Color.White) FormBorder else accent.copy(alpha = .45f),
    focusedLabelColor = accent,
    cursorColor = accent,
    focusedContainerColor = tint,
    unfocusedContainerColor = tint,
    errorContainerColor = tint
)

/** A white rounded group with a small coloured bar and a title; the fields of one topic go inside. */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    accent: Color = Purple,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E4F4)),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) { Text(icon, fontSize = AppText.body) }
                } else {
                    Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, color = Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF0EEF8)))
            content()
        }
    }
}

/** Pill buttons to pick one value; [perLine] pills per line (4 for the shifts, 3 for employment). */
@Composable
fun FormChoiceChips(label: String, options: List<String>, selected: String, perLine: Int = 3, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(horizontal = 4.dp))
        options.chunked(perLine).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { option ->
                    val on = option == selected
                    Surface(
                        color = if (on) Purple else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (on) Purple else FormBorder),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onChange(option) }
                    ) {
                        Text(
                            option,
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            color = if (on) Color.White else Navy,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                repeat(perLine - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The main button of a form (ذخیره). */
@Composable
fun FormSaveButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Purple),
        contentPadding = PaddingValues(horizontal = 28.dp),
        modifier = Modifier.height(46.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    accent: Color = Purple,
    tint: Color = Color.White
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine, minLines = minLines, isError = isError,
        keyboardOptions = keyboardOptions,
        shape = FormFieldShape,
        colors = formFieldColors(accent, tint),
        modifier = modifier.fillMaxWidth()
    )
}

/** A read-only box that looks like a field and reacts to a tap (the keyboard does not open). */
@Composable
private fun PickerBox(label: String, value: String, trailing: String, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true,
            trailingIcon = { Text(trailing, color = Purple, modifier = Modifier.padding(end = 4.dp)) },
            shape = FormFieldShape,
            colors = formFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clip(FormFieldShape).clickable(onClick = onClick))
    }
}

/** A choice list that looks like the other fields: label on the border, the chosen value inside, ▾ at the end. */
@Composable
fun FormDropdown(label: String, options: List<String>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PickerBox(label, value, "▾") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}

/** A date box: the date inside, an optional small line under it (e.g. the long Jalali date). */
@Composable
fun FormDateField(label: String, dateText: String, caption: String? = null, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PickerBox(label, dateText, "📅", onClick)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** A soft rounded row with a title, an optional explanation and a switch. */
@Composable
fun FormSwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(color = Purple.copy(alpha = .06f), shape = FormFieldShape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Navy)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

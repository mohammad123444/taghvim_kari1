package ir.taghvimkari.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.domain.Holidays
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate

private val FridayRed = Color(0xFFD32F2F)
private val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/**
 * Monthly Jalali calendar (Saturday first). The layout direction is RTL app-wide,
 * so the first column is drawn on the right.
 * [badge] may return a tiny label (e.g. a head-count) shown under a day number.
 */
@Composable
fun JalaliMonthCalendar(
    year: Int,
    month: Int,
    onMonthChange: (year: Int, month: Int) -> Unit,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Lists the month's official holidays under the grid. */
    showOccasions: Boolean = false,
    badge: ((LocalDate) -> String?)? = null
) {
    val first = JalaliDate(year, month, 1).toLocalDate()
    val offset = ShiftEngine.persianWeekday(first)
    val length = JalaliDate.monthLength(year, month)
    val rows = (offset + length + 6) / 7
    val holidays = remember(year, month) { Holidays.forMonth(year, month) }
    var yearMenu by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    val goPrev = { if (month == 1) onMonthChange(year - 1, 12) else onMonthChange(year, month - 1) }
    val goNext = { if (month == 12) onMonthChange(year + 1, 1) else onMonthChange(year, month + 1) }

    // Swipe to change month. The layout is right-to-left, so the next month is on the left:
    // dragging right brings the next month in, dragging left brings the previous one back.
    Column(
        modifier.pointerInput(year, month) {
            detectHorizontalDragGestures(
                onDragStart = { dragTotal = 0f },
                onDragEnd = {
                    if (dragTotal > SWIPE_THRESHOLD) goNext() else if (dragTotal < -SWIPE_THRESHOLD) goPrev()
                    dragTotal = 0f
                },
                onDragCancel = { dragTotal = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragTotal += amount
                }
            )
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { goPrev() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ماه قبل") }

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TextButton(onClick = { yearMenu = true }) {
                    Text(
                        "${JalaliDate.MONTH_NAMES[month - 1]} ${year.toPersian()}",
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    for (y in (year - 10)..(year + 10)) {
                        DropdownMenuItem(
                            text = { Text(y.toPersian(), fontWeight = if (y == year) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { onMonthChange(y, month); yearMenu = false }
                        )
                    }
                }
            }

            IconButton(onClick = { goNext() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "ماه بعد") }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            WEEKDAY_SHORT.forEachIndexed { i, name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (i == 6) FridayRed else Color.Gray,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    if (day !in 1..length) {
                        Box(Modifier.weight(1f).height(cellHeight()))
                    } else {
                        val date = JalaliDate(year, month, day).toLocalDate()
                        DayCell(
                            day = day,
                            isFriday = c == 6 || holidays.containsKey(day),
                            isToday = date == today,
                            isSelected = date == selected,
                            badge = badge?.invoke(date),
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (showOccasions) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (holidays.isNotEmpty()) {
                    Text(
                        "تعطیلات رسمی ${JalaliDate.MONTH_NAMES[month - 1]}",
                        fontWeight = FontWeight.Bold,
                        color = FridayRed,
                        style = MaterialTheme.typography.labelLarge
                    )
                    holidays.entries.sortedBy { it.key }.forEach { (day, name) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${day.toPersian()} ${JalaliDate.MONTH_NAMES[month - 1]}",
                                Modifier.width(84.dp),
                                color = FridayRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(name, Modifier.weight(1f), color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text("این ماه تعطیل رسمی ندارد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (!Holidays.hasReligiousData(year)) {
                    Text(
                        "تعطیلات مذهبی این سال هنوز ثبت نشده است؛ فقط تعطیلات ثابت نمایش داده می‌شود.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Horizontal drag distance (px) that counts as a month swipe. */
private const val SWIPE_THRESHOLD = 120f

private val CELL_HEIGHT = 62.dp

/** The day cells grow with the text size (phone setting × the app's text-size choice). */
@Composable
private fun cellHeight(): androidx.compose.ui.unit.Dp = CELL_HEIGHT * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)

@Composable
private fun DayCell(
    day: Int,
    isFriday: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = when {
        isSelected -> Color.White
        isFriday -> FridayRed
        else -> Color(0xFF222244)
    }
    // The day number and the head-count live together in one box; the day number is the larger one.
    Box(
        modifier
            .padding(2.dp)
            .height(cellHeight() - 4.dp)
            .clip(shape)
            .background(if (isSelected) Purple else Color(0xFFF1EFFB))
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(1.5.dp, Purple), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toPersian(),
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (badge != null) {
                Text(
                    badge,
                    color = if (isSelected) Color(0xFFE6E3FF) else Purple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Tap a day and it is chosen immediately. */
@Composable
fun JalaliDatePickerDialog(
    initial: LocalDate,
    title: String,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val start = JalaliDate.from(initial)
    var year by rememberSaveable { mutableStateOf(start.year) }
    var month by rememberSaveable { mutableStateOf(start.month) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            JalaliMonthCalendar(
                year = year,
                month = month,
                onMonthChange = { y, m -> year = y; month = m },
                today = today,
                selected = initial,
                onSelect = onPick
            )
        },
        confirmButton = { TextButton(onClick = { onPick(today) }) { Text("امروز") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

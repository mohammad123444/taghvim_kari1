package ir.taghvimkari.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.EvaluationItems
import ir.taghvimkari.app.domain.Evaluations
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.formatNumber
import ir.taghvimkari.app.domain.formatScore
import ir.taghvimkari.app.domain.roundWhole
import ir.taghvimkari.app.domain.formatSigned
import ir.taghvimkari.app.domain.periodText
import ir.taghvimkari.app.domain.toPersianDigits
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The score of one person in one evaluation period, with the average of everybody for the same period. */
private class TrendPoint(val from: LocalDate, val to: LocalDate, val value: Double, val everyone: Double, val count: Int)

/**
 * «روند نمرهٔ هر نفر»: pick a person and see his / her final score period after period as a line,
 * next to the average of everyone in the same periods. Oldest period on the right (Persian reading order).
 */
@Composable
internal fun TrendSection(people: List<Person>, evaluations: List<EvaluationEntry>) {
    val complete = remember(evaluations) { evaluations.filter { it.isComplete } }
    val withData = remember(people, complete) {
        val ids = complete.map { it.personId }.toSet()
        people.filter { it.id in ids }
    }
    if (withData.isEmpty()) return

    var personId by rememberSaveable { mutableStateOf("") }
    // false = compare with everybody, true = only with people of the same employment kind (رسمی / قرارداد مستقیم / پیمانکاری)
    var sameGroup by rememberSaveable { mutableStateOf(false) }
    val labelled = remember(withData) { uniqueLabels(withData) }
    val person = withData.firstOrNull { it.id == personId }
    val points = remember(personId, sameGroup, complete, people) {
        if (personId.isBlank()) emptyList() else {
            val groupIds = people.filter { p -> p.employment == person?.employment }.map { it.id }.toSet()
            val pool = if (!sameGroup) complete else complete.filter { it.personId in groupIds || it.personId == personId }
            complete.filter { it.personId == personId }
                .groupBy { it.date to it.dateTo }
                .map { (period, list) ->
                    TrendPoint(
                        from = period.first,
                        to = period.second,
                        value = Evaluations.average(list),
                        everyone = Evaluations.average(pool.filter { it.date == period.first && it.dateTo == period.second }),
                        count = list.size
                    )
                }
                .sortedBy { it.from.toEpochDay() }
        }
    }

    AppCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("روند نمرهٔ هر نفر", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleMedium)
        PersonSearchField(label = "نفر", options = labelled, selectedId = personId, onSelect = { personId = it })
        if (personId.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مقایسه با:", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
                SelectChip("همهٔ نفرات", selected = !sameGroup, onClick = { sameGroup = false })
                SelectChip(person?.employment?.title?.let { "هم‌گروه ($it)" } ?: "هم‌گروه", selected = sameGroup, onClick = { sameGroup = true })
            }
        }
        when {
            personId.isBlank() ->
                Text("نفر را انتخاب کنید تا نمرهٔ او در دوره‌های مختلف دیده شود.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            points.isEmpty() ->
                Text("برای این نفر ارزیابی کاملی ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            else -> TrendChart(points, if (sameGroup) "میانگین هم‌گروه" else "میانگین همه")
        }
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>, groupLabel: String) {
    val n = points.size
    val all = points.map { it.value } + points.map { it.everyone }
    val maxScore = EvaluationItems.FINAL_MAX.toDouble()
    var lo = floor((all.minOrNull() ?: 0.0) - 2).coerceAtLeast(0.0)
    var hi = ceil((all.maxOrNull() ?: maxScore) + 2).coerceAtMost(maxScore)
    if (hi - lo < 6) {
        hi = min(maxScore, lo + 6)
        lo = max(0.0, hi - 6)
    }
    val line = Purple
    val gray = Color(0xFF9E9BB5)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = maxOf(60.dp, maxWidth / n)
        Column(Modifier.horizontalScroll(rememberScrollState()).width(cell * n)) {
            // the score of each period, above its point
            Row {
                points.forEach { p ->
                    Text(
                        formatScore(p.value), Modifier.width(cell), textAlign = TextAlign.Center,
                        color = Navy, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val w = size.width
                val h = size.height
                val pad = 12.dp.toPx()
                // the first (oldest) period is on the right, like the text
                fun x(i: Int) = w * (1f - (i + 0.5f) / n)
                fun y(v: Double) = pad + (h - 2 * pad) * (1f - ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f))
                repeat(4) { k ->
                    val yy = pad + (h - 2 * pad) * k / 3f
                    drawLine(Color(0xFFEDEBF7), Offset(0f, yy), Offset(w, yy), strokeWidth = 1.dp.toPx())
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                for (i in 0 until n - 1) {
                    drawLine(gray, Offset(x(i), y(points[i].everyone)), Offset(x(i + 1), y(points[i + 1].everyone)), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                    drawLine(line, Offset(x(i), y(points[i].value)), Offset(x(i + 1), y(points[i + 1].value)), strokeWidth = 3.dp.toPx())
                }
                for (i in 0 until n) {
                    drawCircle(gray, radius = 3.5.dp.toPx(), center = Offset(x(i), y(points[i].everyone)))
                    drawCircle(line, radius = 6.dp.toPx(), center = Offset(x(i), y(points[i].value)))
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x(i), y(points[i].value)))
                }
            }
            // the period of each point (month and year of its start)
            Row {
                points.forEach { p ->
                    val j = JalaliDate.from(p.from)
                    Column(Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(JalaliDate.MONTH_NAMES[j.month - 1], color = Color.Gray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(j.year.toString().toPersianDigits(), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("● نمرهٔ این نفر", color = line, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("┄ $groupLabel", color = gray, style = MaterialTheme.typography.labelMedium)
    }
    if (n >= 2) {
        val change = points.last().value - points.first().value
        val last = points.last().value - points[n - 2].value
        Text(
            "تغییر از اولین تا آخرین دوره: ${formatSigned(change)}  •  نسبت به دورهٔ قبل: ${formatSigned(last)}",
            color = if (last >= 0) RewardGreen else PunishRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
    run {
        val gap = points.last().value - points.last().everyone
        val verdict = when {
            roundWhole(gap) > 0L -> "${formatNumber(gap)} امتیاز بالاتر از $groupLabel"
            roundWhole(gap) < 0L -> "${formatNumber(-gap)} امتیاز پایین‌تر از $groupLabel"
            else -> "برابر با $groupLabel"
        }
        Text(
            "در آخرین دوره: $verdict",
            color = if (gap >= 0) RewardGreen else PunishRed,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        "آخرین دوره: ${periodText(points.last().from, points.last().to)} • محور عمودی از ${formatScore(lo)} تا ${formatScore(hi)}",
        color = Color.Gray,
        style = MaterialTheme.typography.labelSmall
    )
}

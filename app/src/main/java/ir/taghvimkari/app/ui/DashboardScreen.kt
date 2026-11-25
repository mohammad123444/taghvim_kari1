package ir.taghvimkari.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import ir.taghvimkari.app.domain.Bucket
import ir.taghvimkari.app.domain.Category
import ir.taghvimkari.app.domain.DayStatus
import ir.taghvimkari.app.domain.Employment
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.Shift
import ir.taghvimkari.app.domain.ShiftEngine
import ir.taghvimkari.app.domain.bucketOf
import ir.taghvimkari.app.domain.hasTwoRestWeeks
import ir.taghvimkari.app.domain.sortedByTeam
import ir.taghvimkari.app.domain.toPersian
import java.time.LocalDate

private val PresentGreen = Color(0xFF218838)
private val NightIndigo = Color(0xFF4F46C8)
private val RestGray = Color(0xFF757575)

private fun bucketColor(bucket: Bucket): Color = when (bucket) {
    Bucket.DAY -> PresentGreen
    Bucket.NIGHT -> NightIndigo
    else -> RestGray
}

@Composable
fun DashboardScreen(people: List<Person>) {
    val today = remember { LocalDate.now() }
    val todayJalali = remember { JalaliDate.from(today) }
    var viewYear by rememberSaveable { mutableStateOf(todayJalali.year) }
    var viewMonth by rememberSaveable { mutableStateOf(todayJalali.month) }
    var selectedEpoch by rememberSaveable { mutableStateOf<Long?>(null) }

    val active = people.filter { it.active }
    val shiftsToday = active.map { ShiftEngine.shift(it, today) }
    val dayCount = shiftsToday.count { it == Shift.DAY || it == Shift.WORK }
    val nightCount = shiftsToday.count { it == Shift.NIGHT }
    val restCount = shiftsToday.count { it == Shift.REST }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                JalaliDate.longText(today),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp)) {
                StatBox("حاضر در روز", dayCount, PresentGreen, Modifier.weight(1f))
                StatBox("حاضر در شب", nightCount, NightIndigo, Modifier.weight(1f))
                StatBox("در حال استراحت", restCount, RestGray, Modifier.weight(1f))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                JalaliMonthCalendar(
                    year = viewYear,
                    month = viewMonth,
                    onMonthChange = { y, m -> viewYear = y; viewMonth = m },
                    today = today,
                    selected = selectedEpoch?.let { LocalDate.ofEpochDay(it) },
                    onSelect = { selectedEpoch = it.toEpochDay() },
                    showOccasions = true,
                    badge = if (active.isEmpty()) null else { date ->
                        active.count { ShiftEngine.isWorking(it, date) }.toPersian()
                    }
                )
            }
        }
    }

    selectedEpoch?.let { epoch ->
        DayDetailSheet(
            date = LocalDate.ofEpochDay(epoch),
            people = active,
            onDismiss = { selectedEpoch = null }
        )
    }
}

@Composable
private fun StatBox(label: String, count: Int, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toPersian(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(date: LocalDate, people: List<Person>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableStateOf(0) }
    val statuses = remember(date, people) { people.map { it to ShiftEngine.status(it, date) } }
    val leaderNames = remember(people) { people.filter { it.isLeader }.associate { it.id to it.name } }
    val employments = Employment.entries

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AppBackground) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(
                    JalaliDate.longText(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                val present = statuses.count { it.second.working }
                Text(
                    "کل: ${present.toPersian()} نفر حاضر • ${(statuses.size - present).toPersian()} نفر در استراحت",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.White) {
                employments.forEachIndexed { i, e ->
                    val group = statuses.filter { it.first.employment == e }
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(e.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${group.count { it.second.working }.toPersian()} از ${group.size.toPersian()}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    )
                }
            }
            val group = statuses.filter { it.first.employment == employments[tab] }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (group.isEmpty()) EmptyMessage("نیروی ${employments[tab].title} ثبت نشده است.")
                // Administrative and operational staff are shown in separate boxes.
                Category.entries.forEach { cat ->
                    CategoryBox(
                        category = cat,
                        items = group.filter { it.first.category == cat },
                        leaderNames = leaderNames,
                        stateKey = "${employments[tab].name}-${cat.name}"
                    )
                }
            }
        }
    }
}

/** A collapsible box for one category (اداری / عملیاتی), closed by default. */
@Composable
private fun CategoryBox(
    category: Category,
    items: List<Pair<Person, DayStatus>>,
    leaderNames: Map<String, String>,
    stateKey: String
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val present = items.count { it.second.working }
    // Administrative staff only have two states; operational staff have day, night and two rest weeks.
    val buckets = if (category == Category.ADMIN) {
        listOf(Bucket.DAY, Bucket.REST)
    } else {
        // "هفته اول/دوم استراحت" only exists for people who rest two weeks; everyone else is just "استراحت".
        val hasTwoWeekRest = items.any { it.first.pattern.hasTwoRestWeeks }
        val hasShortRest = items.any { !it.first.pattern.hasTwoRestWeeks }
        buildList {
            add(Bucket.DAY)
            add(Bucket.NIGHT)
            if (hasShortRest) add(Bucket.REST)
            if (hasTwoWeekRest) {
                add(Bucket.REST_WEEK1)
                add(Bucket.REST_WEEK2)
            }
        }
    }
    val byBucket = items.groupBy { bucketOf(it.first, it.second) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEBFA)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        category.title,
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${present.toPersian()} حاضر • ${(items.size - present).toPersian()} استراحت",
                        color = Purple,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(if (expanded) "▴" else "▾", color = Navy, fontSize = 22.sp)
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (items.isEmpty()) {
                        Text("نیروی ${category.title} ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    buckets.forEach { bucket ->
                        BucketSection(
                            bucket = bucket,
                            items = byBucket[bucket].orEmpty().sortedByTeam(leaderNames) { it.first },
                            stateKey = "$stateKey-${bucket.name}"
                        )
                    }
                }
            }
        }
    }
}

/** One collapsible sub-section (e.g. روزکار) listing leader first, then his members. */
@Composable
private fun BucketSection(bucket: Bucket, items: List<Pair<Person, DayStatus>>, stateKey: String) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    val color = bucketColor(bucket)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bucket.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = color)
                Text("${items.size.toPersian()} نفر", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(if (expanded) "  ▴" else "  ▾", color = color, fontSize = 18.sp)
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (items.isEmpty()) {
                        Text("کسی در این گروه نیست.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    items.forEach { (p, s) -> StatusRow(p, s, color, bucket) }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(person: Person, status: DayStatus, color: Color, bucket: Bucket) {
    val isMember = person.leaderId != null && !person.isLeader
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = if (isMember) 14.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F6FD)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (person.isLeader) "${person.name} • سرشیفت" else person.name,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text(statusLine(status), style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
            Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    bucket.title,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

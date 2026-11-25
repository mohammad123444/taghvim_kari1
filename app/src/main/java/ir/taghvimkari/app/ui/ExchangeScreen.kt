package ir.taghvimkari.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.toPersian

/**
 * «گزارش» tab: the one place where the staff list, the reward / punishment records and the evaluations
 * are sent (shared as a file) and received. The other tabs no longer have send / receive buttons.
 */
@Composable
fun ExchangeScreen(viewModel: PeopleViewModel) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }
    var peopleFailed by rememberSaveable { mutableStateOf(false) }
    var reportsFailed by rememberSaveable { mutableStateOf(false) }
    var evaluationsFailed by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap)
    ) {
        ExchangeSection(
            title = "نفرات",
            count = "${viewModel.people.size.toPersian()} نفر",
            note = "فایل دریافتی، فهرست نیروهای فعلی را کاملاً جایگزین می‌کند (ادغام نمی‌شود) و نام‌های تکراری حذف می‌شوند.",
            sendEnabled = viewModel.people.isNotEmpty(),
            sendFailed = peopleFailed,
            onSend = { peopleFailed = !DataExchange.sharePeople(context, viewModel.people) },
            onReceive = { filePicker.launch(arrayOf("*/*")) }
        )
        ExchangeSection(
            title = "سوابق تشویق و تنبیه",
            count = "${viewModel.reports.size.toPersian()} مورد",
            note = "سوابق دریافتی به سوابق فعلی اضافه می‌شود و چیزی حذف نمی‌شود.",
            sendEnabled = viewModel.reports.isNotEmpty(),
            sendFailed = reportsFailed,
            onSend = { reportsFailed = !DataExchange.shareReports(context, viewModel.reports) },
            onReceive = { filePicker.launch(arrayOf("*/*")) }
        )
        ExchangeSection(
            title = "ارزیابی‌ها",
            count = "${viewModel.evaluations.size.toPersian()} مورد",
            note = "ارزیابی‌های دریافتی به ارزیابی‌های فعلی اضافه می‌شود و چیزی حذف نمی‌شود.",
            sendEnabled = viewModel.evaluations.isNotEmpty(),
            sendFailed = evaluationsFailed,
            onSend = { evaluationsFailed = !DataExchange.shareEvaluations(context, viewModel.evaluations) },
            onReceive = { filePicker.launch(arrayOf("*/*")) }
        )
        Text(
            "نوع فایل دریافتی خودکار تشخیص داده می‌شود و قبل از ثبت، از شما تأیید گرفته می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ExchangeSection(
    title: String,
    count: String,
    note: String,
    sendEnabled: Boolean,
    sendFailed: Boolean,
    onSend: () -> Unit,
    onReceive: () -> Unit
) {
    AppCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Purple, style = MaterialTheme.typography.titleMedium)
        Text("تعداد ثبت‌شده: $count")
        ExchangeRow(sendEnabled = sendEnabled, sendFailed = sendFailed, onSend = onSend, onReceive = onReceive)
        Text(note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

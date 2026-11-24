package ir.taghvimkari.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.BuildConfig
import ir.taghvimkari.app.domain.JalaliDate
import ir.taghvimkari.app.domain.TextSize
import ir.taghvimkari.app.domain.daysSinceBackup
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.toPersianDigits
import java.time.LocalDate

/** Everything about the app itself rather than the staff: text size, backup, receiving files, version, updates. */
@Composable
fun SettingsScreen(viewModel: PeopleViewModel) {
    val context = LocalContext.current
    val people = viewModel.people
    val checking = viewModel.updateUi is UpdateUi.Checking
    var backupFailed by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard(
            "اطلاعات ذخیره‌شده"
        ) {
            Text("نیروهای ثبت‌شده: ${people.size.toPersian()} نفر")
            Text("سرشیفت‌ها: ${people.count { it.isLeader }.toPersian()} نفر")
            Text("سوابق تشویق و تنبیه: ${viewModel.reports.size.toPersian()} مورد")
            Text("ارزیابی‌ها: ${viewModel.evaluations.size.toPersian()} مورد")
        }

        SettingsCard("اندازهٔ نوشته") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextSize.entries.forEach { size ->
                    val on = viewModel.textSize == size
                    Surface(
                        color = if (on) Purple else Purple.copy(alpha = .12f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { viewModel.updateTextSize(size) }
                    ) {
                        Text(
                            size.title,
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            color = if (on) Color.White else Purple,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        SettingsCard("پشتیبان‌گیری") {
            val days = daysSinceBackup(LocalDate.now().toEpochDay(), viewModel.lastBackup)
            Text(
                if (viewModel.lastBackup == null) "هنوز پشتیبان نگرفته‌اید."
                else "آخرین پشتیبان: ${JalaliDate.from(LocalDate.ofEpochDay(viewModel.lastBackup!!)).display()}" +
                    (if (days != null) " (${days.toInt().toPersian()} روز پیش)" else "")
            )
            Button(
                onClick = { backupFailed = !viewModel.sendBackup(context) },
                enabled = people.isNotEmpty() || viewModel.reports.isNotEmpty() || viewModel.evaluations.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("ارسال") }
            if (backupFailed) {
                Text("ارسال انجام نشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        SettingsCard("بروزرسانی") {
            Text("نسخهٔ فعلی: ${BuildConfig.VERSION_NAME.toPersianDigits()}")
            if (viewModel.updatesEnabled) {
                Button(
                    onClick = { viewModel.checkForUpdate(manual = true) },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("  در حال بررسی…")
                    } else {
                        Text("بررسی بروزرسانی")
                    }
                }
            } else {
                Text("بروزرسانی برای این نسخه فعال نیست.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = Purple,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}

package ir.taghvimkari.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ir.taghvimkari.app.BuildConfig
import ir.taghvimkari.app.data.DataExchange
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.toPersianDigits

/** Everything about the app itself rather than the staff: sharing data, version, updates, stored data. */
@Composable
fun SettingsScreen(viewModel: PeopleViewModel) {
    val context = LocalContext.current
    val people = viewModel.people
    val checking = viewModel.updateUi is UpdateUi.Checking

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.offerImport(uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تنظیمات نرم‌افزار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)

        SettingsCard("اطلاعات ذخیره‌شده") {
            Text("نیروهای ثبت‌شده: ${people.size.toPersian()} نفر")
            Text("سرشیفت‌ها: ${people.count { it.isLeader }.toPersian()} نفر")
            Text("گزارش‌های تشویق و تنبیه: ${viewModel.reports.size.toPersian()} مورد")
            Text("ارزیابی‌ها: ${viewModel.evaluations.size.toPersian()} مورد")
            Text(
                "اطلاعات روی همین دستگاه ذخیره می‌شود و با حذف برنامه پاک می‌شود؛ برای نگهداری، یک نسخه از آن را برای خودتان یا دیگری ارسال کنید.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        SettingsCard("ارسال و دریافت اطلاعات") {
            Text(
                "ارسال هر بخش جداست و دکمهٔ خودش را در همان بخش دارد: «ارسال اطلاعات نیروها» در تب نیروها، " +
                    "«ارسال ارزیابی» در تب ارزیابی (نتایج) و «ارسال گزارش‌ها» در تب گزارش. هر کدام فقط همان اطلاعات را می‌فرستد.",
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("دریافت اطلاعات از فایل")
            }
            Text(
                "نوع فایل خودکار تشخیص داده می‌شود. ارزیابی‌ها و گزارش‌های دریافتی با اطلاعات فعلی ادغام می‌شوند و چیزی حذف نمی‌شود؛ " +
                    "فقط فهرست نیروهای دریافتی (پس از تأیید) جایگزین فهرست قبلی می‌شود. " +
                    "گیرنده می‌تواند روی فایل بزند و با این برنامه باز کند.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
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
                Text(
                    "برنامه هنگام باز شدن هم خودش نسخهٔ جدید را بررسی می‌کند.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
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
            Text(title, fontWeight = FontWeight.Bold, color = Purple, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

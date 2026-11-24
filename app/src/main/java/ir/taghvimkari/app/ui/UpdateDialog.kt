package ir.taghvimkari.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.taghvimkari.app.BuildConfig
import ir.taghvimkari.app.data.UpdateInfo
import ir.taghvimkari.app.data.UpdateInstaller
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.toPersianDigits
import java.io.File

@Composable
fun UpdateDialog(state: UpdateUi, onDismiss: () -> Unit, onDownload: (UpdateInfo) -> Unit) {
    val context = LocalContext.current
    when (state) {
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("نسخهٔ جدید ${state.info.version.toPersianDigits()}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نسخهٔ فعلی شما: ${BuildConfig.VERSION_NAME.toPersianDigits()}")
                    if (state.info.notes.isNotBlank()) Text(state.info.notes.take(400))
                    Text("نسخهٔ جدید داخل برنامه دانلود و روی همین نسخه نصب می‌شود. اطلاعات ثبت‌شده حفظ می‌شود.")
                }
            },
            confirmButton = { Button(onClick = { onDownload(state.info) }) { Text("دانلود و نصب") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("بعداً") } }
        )

        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال دانلود…", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.percent.toPersian()}٪")
                }
            },
            confirmButton = {}
        )

        is UpdateUi.Downloaded -> InstallDialog(context, File(state.path), onDismiss)

        is UpdateUi.DownloadFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("دانلود انجام نشد", fontWeight = FontWeight.Bold) },
            text = { Text("اتصال اینترنت را بررسی کنید و دوباره تلاش کنید، یا فایل را از مرورگر دانلود کنید.") },
            confirmButton = { Button(onClick = { onDownload(state.info) }) { Text("تلاش دوباره") } },
            dismissButton = {
                TextButton(onClick = {
                    openUrl(context, state.info.apkUrl?.takeIf { it.isNotBlank() } ?: state.info.pageUrl)
                    onDismiss()
                }) { Text("دانلود با مرورگر") }
            }
        )

        is UpdateUi.UpToDate -> MessageDialog(
            "بروزرسانی",
            "نسخهٔ نصب‌شده: ${BuildConfig.VERSION_NAME.toPersianDigits()}\n" +
                (state.latest?.let { "آخرین نسخهٔ منتشرشده در GitHub: ${it.toPersianDigits()}\n" } ?: "در GitHub هنوز Release منتشرشده‌ای پیدا نشد.\n") +
                "برای دیدن نسخهٔ جدید، شمارهٔ Release (Tag) باید بزرگ‌تر از نسخهٔ نصب‌شده باشد؛ مثلاً v۲٫۳٫۰.",
            onDismiss
        )
        is UpdateUi.Failed -> MessageDialog("بروزرسانی", state.reason, onDismiss)
        UpdateUi.Checking, UpdateUi.Idle -> Unit
    }
}

@Composable
private fun InstallDialog(context: Context, file: File, onDismiss: () -> Unit) {
    var needPermission by remember { mutableStateOf(false) }
    var installFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("آمادهٔ نصب", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دانلود کامل شد. برای نصب دکمهٔ «نصب» را بزنید.")
                if (needPermission) {
                    Text("اندروید باید اجازهٔ نصب را به این برنامه بدهد. در صفحهٔ باز‌شده گزینهٔ «اجازه دادن» را روشن کنید، برگردید و دوباره «نصب» را بزنید.")
                }
                if (installFailed) {
                    Text("نصب شروع نشد. اگر قبلاً برنامه را از منبع دیگری نصب کرده‌اید، ممکن است امضای آن متفاوت باشد؛ در این صورت یک‌بار برنامه را حذف و نسخهٔ جدید را نصب کنید.")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!UpdateInstaller.canInstall(context)) {
                    needPermission = true
                    UpdateInstaller.openInstallPermissionSettings(context)
                } else {
                    needPermission = false
                    installFailed = !UpdateInstaller.install(context, file)
                }
            }) { Text("نصب") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بعداً") } }
    )
}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("باشه") } }
    )
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

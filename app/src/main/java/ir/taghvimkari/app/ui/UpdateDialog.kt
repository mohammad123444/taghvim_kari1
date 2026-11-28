package ir.taghvimkari.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import ir.taghvimkari.app.domain.toPersian
import ir.taghvimkari.app.domain.toPersianDigits

@Composable
fun UpdateDialog(state: UpdateUi, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        is UpdateUi.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("نسخهٔ جدید ${state.info.version.toPersianDigits()}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نسخهٔ فعلی شما: ${BuildConfig.VERSION_NAME.toPersianDigits()}")
                    if (state.info.notes.isNotBlank()) Text(state.info.notes.take(400))
                    Text("فایل نسخهٔ جدید با مرورگر دانلود می‌شود؛ بعد از دانلود روی آن بزنید تا روی همین نسخه نصب شود. اطلاعات ثبت‌شده حفظ می‌شود.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    openUrl(context, state.info.apkUrl?.takeIf { it.isNotBlank() } ?: state.info.pageUrl)
                    onDismiss()
                }) { Text("دانلود نسخهٔ جدید") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("بعداً") } }
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

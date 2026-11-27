package ir.taghvimkari.app.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import ir.taghvimkari.app.domain.Person

/**
 * «مقایسه» opened from the نیروها page: a full-screen window with the whole compare screen inside it
 * (dates, choosing people, the chart, together-periods) and a «بستن» button on top.
 */
@Composable
fun CompareDialog(people: List<Person>, onDismiss: () -> Unit) {
    // System-bar sizes are read from the app window (the dialog window may report none), as in FormDialog.
    val bars = WindowInsets.systemBars
    val density = LocalDensity.current
    val topPx = bars.getTop(density)
    val bottomInsets = WindowInsets(0, 0, 0, bars.getBottom(density))
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        Column(Modifier.fillMaxSize().background(AppBackground)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Navy)
                    .padding(top = with(density) { topPx.toDp() })
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "مقایسه نیروها",
                    Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                TextButton(onClick = onDismiss) { Text("بستن", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(bottomInsets.union(WindowInsets.ime))) {
                CompareScreen(people)
            }
        }
    }
}

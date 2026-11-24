package ir.taghvimkari.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ir.taghvimkari.app.domain.TextSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Navy = Color(0xFF24245B)
val Purple = Color(0xFF6558C8)
val AppBackground = Color(0xFFF6F5FC)
val Blue = Color(0xFF1976D2)
val Green = Color(0xFF218838)
val Orange = Color(0xFFEF8B24)

@Composable
fun TaghvimTheme(textSize: TextSize = TextSize.NORMAL, content: @Composable () -> Unit) {
    // The chosen text size multiplies the phone's own font scale, so it applies to the whole app.
    val base = LocalDensity.current
    val density = remember(base, textSize) { Density(base.density, base.fontScale * textSize.factor) }
    // The app is Persian-only: force RTL even when the phone language is English.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides density) {
        MaterialTheme(
            colorScheme = lightColorScheme(primary = Navy, secondary = Purple, background = AppBackground),
            content = content
        )
    }
}

package ir.taghvimkari.app.ui

import androidx.compose.material3.MaterialTheme
import android.content.Context
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.taghvimkari.app.data.AppFonts
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import ir.taghvimkari.app.domain.TextSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val Navy = Color(0xFF24245B)
val Purple = Color(0xFF6558C8)
val AppBackground = Color(0xFFF6F5FC)
val Blue = Color(0xFF1976D2)
val Green = Color(0xFF218838)
val Orange = Color(0xFFEF8B24)

/** Reward (تشویق) is always this green and punishment (تنبیه) this red, on every screen. */
val RewardGreen = Color(0xFF218838)
val PunishRed = Color(0xFFD32F2F)

/** The one gap between cards / boxes on every page. */
val CardGap = 12.dp

@Composable
fun TaghvimTheme(textSize: TextSize = TextSize.NORMAL, content: @Composable () -> Unit) {
    // The chosen text size multiplies the phone's own font scale, so it applies to the whole app.
    val base = LocalDensity.current
    val density = remember(base, textSize) { Density(base.density, base.fontScale * textSize.factor) }
    val context = LocalContext.current
    val family = remember { appFontFamily(context) }
    // The app is Persian-only: force RTL even when the phone language is English.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides density) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Purple,
                onPrimary = Color.White,
                primaryContainer = Purple.copy(alpha = .14f).compositeOver(Color.White),
                onPrimaryContainer = Navy,
                secondary = Navy,
                onSecondary = Color.White,
                secondaryContainer = Purple.copy(alpha = .14f).compositeOver(Color.White),
                onSecondaryContainer = Navy,
                background = AppBackground,
                onBackground = Color(0xFF1B1B2F),
                surface = Color.White,
                onSurface = Color(0xFF1B1B2F),
                surfaceVariant = Color(0xFFF0EEF9),
                onSurfaceVariant = Color(0xFF5E5B7A),
                surfaceTint = Color.Transparent,
                outline = Color(0xFFD5D2E8),
                outlineVariant = Color(0xFFE6E4F4)
            ),
            // One set of corner sizes for cards, menus, dialogs and fields everywhere.
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(26.dp)
            ),
            typography = if (family == null) Typography() else Typography().withFamily(family),
            content = {
                if (family == null) content() else ProvideTextStyle(TextStyle(fontFamily = family)) { content() }
            }
        )
    }
}

/** Regular + bold Vazirmatn when its files are in the app, otherwise null (the phone's font). */
@OptIn(ExperimentalTextApi::class)
private fun appFontFamily(context: Context): FontFamily? {
    if (!AppFonts.available(context)) return null
    val assets = context.assets
    return FontFamily(
        Font(AppFonts.REGULAR_FILE, assets, FontWeight.Normal),
        Font(AppFonts.REGULAR_FILE, assets, FontWeight.Medium),
        Font(AppFonts.BOLD_FILE, assets, FontWeight.SemiBold),
        Font(AppFonts.BOLD_FILE, assets, FontWeight.Bold)
    )
}

private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

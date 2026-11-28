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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF24245B)
val Purple = Color(0xFF6558C8)
val AppBackground = Color(0xFFF6F5FC)
val Blue = Color(0xFF1976D2)
val Green = Color(0xFF218838)
val Orange = Color(0xFFEF8B24)

/** Reward (تشویق) is always this green and punishment (تنبیه) this red, on every screen. */
val RewardGreen = Color(0xFF218838)
val PunishRed = Color(0xFFD32F2F)

/**
 * The ONLY text sizes of the app. Every screen takes its size from here (directly, or through the styles of
 * [appTypography]), so the same kind of text has the same size everywhere; «اندازهٔ نوشته» in the settings scales them all.
 */
object AppText {
    /** Captions, hints, chips, small labels. */
    val small: TextUnit = 12.sp
    /** Normal text, buttons, text boxes, names in lists. */
    val body: TextUnit = 15.sp
    /** Names of leaders, titles of cards and folds. */
    val title: TextUnit = 17.sp
    /** Page header and dialog headers. */
    val heading: TextUnit = 21.sp
    /** Big numbers (counts, scores). */
    val stat: TextUnit = 28.sp
}

/** The one gap between cards / boxes on every page. */
val CardGap = 12.dp

@Composable
fun TaghvimTheme(textSize: TextSize = TextSize.NORMAL, content: @Composable () -> Unit) {
    // The chosen text size multiplies the phone's own font scale, so it applies to the whole app.
    val base = LocalDensity.current
    val density = remember(base, textSize) { Density(base.density, base.fontScale * textSize.factor) }
    val context = LocalContext.current
    // One font for everything: Vazirmatn when its files are in the app, otherwise the phone's default font (never a mix).
    val family = remember { appFontFamily(context) ?: FontFamily.Default }
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
            typography = appTypography(family),
            content = {
                ProvideTextStyle(TextStyle(fontFamily = family, letterSpacing = 0.sp)) { content() }
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

/**
 * Every text style uses the same font and the same few sizes of [AppText], with no letter spacing (spacing between letters
 * breaks the joined Persian letters). Styles that mean the same thing share one look.
 */
private fun appTypography(f: FontFamily): Typography {
    fun style(size: TextUnit, line: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = f, fontSize = size, lineHeight = line, fontWeight = weight, letterSpacing = 0.sp
    )
    val small = style(AppText.small, 18.sp)
    val body = style(AppText.body, 23.sp)
    val bodyMedium = style(AppText.body, 23.sp, FontWeight.Medium)
    val title = style(AppText.title, 25.sp, FontWeight.Medium)
    val heading = style(AppText.heading, 29.sp, FontWeight.SemiBold)
    val stat = style(AppText.stat, 36.sp, FontWeight.SemiBold)
    return Typography(
        displayLarge = stat, displayMedium = stat, displaySmall = stat,
        headlineLarge = heading, headlineMedium = heading, headlineSmall = heading,
        titleLarge = heading, titleMedium = title, titleSmall = bodyMedium,
        bodyLarge = body, bodyMedium = body, bodySmall = small,
        labelLarge = bodyMedium, labelMedium = small, labelSmall = small
    )
}

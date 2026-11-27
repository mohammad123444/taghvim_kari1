package ir.taghvimkari.app.data

import android.content.Context
import android.graphics.Typeface

/**
 * The Persian font of the app (Vazirmatn). The two files are looked up in app/src/main/assets/fonts/:
 * Vazirmatn-Regular.ttf and Vazirmatn-Bold.ttf. When they are not there the phone's own font is used,
 * so the app works either way.
 */
object AppFonts {
    const val REGULAR_FILE = "fonts/Vazirmatn-Regular.ttf"
    const val BOLD_FILE = "fonts/Vazirmatn-Bold.ttf"

    private var regular: Typeface? = null
    private var bold: Typeface? = null
    private var loaded = false

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        regular = runCatching { Typeface.createFromAsset(context.applicationContext.assets, REGULAR_FILE) }.getOrNull()
        bold = runCatching { Typeface.createFromAsset(context.applicationContext.assets, BOLD_FILE) }.getOrNull() ?: regular
    }

    /** The app font, or null when the font files are not part of the app. */
    fun typeface(context: Context, bold: Boolean): Typeface? {
        load(context)
        return if (bold) this.bold else regular
    }

    fun available(context: Context): Boolean = typeface(context, false) != null
}

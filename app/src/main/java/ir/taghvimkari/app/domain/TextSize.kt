package ir.taghvimkari.app.domain

/** The three text sizes of the app (تنظیمات). [factor] multiplies the phone's own font scale. */
enum class TextSize(val id: String, val title: String, val factor: Float) {
    SMALL("small", "کوچک", 0.9f),
    NORMAL("normal", "معمولی", 1.0f),
    LARGE("large", "بزرگ", 1.2f);

    companion object {
        fun fromId(id: String?): TextSize = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

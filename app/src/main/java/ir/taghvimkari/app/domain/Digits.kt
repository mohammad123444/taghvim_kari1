package ir.taghvimkari.app.domain

/** Latin digits -> Persian digits (for display only). */
fun String.toPersianDigits(): String = buildString {
    for (c in this@toPersianDigits) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

/** Persian / Arabic-Indic digits -> Latin digits (for parsing user input). */
fun String.toLatinDigits(): String = buildString {
    for (c in this@toLatinDigits) {
        append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }
}

fun Int.toPersian(): String = toString().toPersianDigits()

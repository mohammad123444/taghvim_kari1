package ir.taghvimkari.app.domain

/**
 * Official holidays of Iran.
 *
 * Solar holidays are the same every year. Religious holidays follow the lunar calendar and the
 * moon sighting, so their Jalali dates are announced each year: they are listed here only for the
 * years whose official calendar is known (1405 and 1406). Other years show the solar holidays only.
 */
object Holidays {

    private val FIXED: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 1) to "عید نوروز",
        (1 to 2) to "عید نوروز",
        (1 to 3) to "عید نوروز",
        (1 to 4) to "عید نوروز",
        (1 to 12) to "روز جمهوری اسلامی ایران",
        (1 to 13) to "روز طبیعت",
        (3 to 14) to "رحلت حضرت امام خمینی (ره)",
        (3 to 15) to "قیام ۱۵ خرداد",
        (11 to 22) to "پیروزی انقلاب اسلامی ایران",
        (12 to 29) to "روز ملی شدن صنعت نفت ایران"
    )

    /** year -> (month, day, occasion). */
    private val RELIGIOUS: Map<Int, List<Triple<Int, Int, String>>> = mapOf(
        1405 to listOf(
            Triple(1, 1, "عید سعید فطر"),
            Triple(1, 2, "تعطیل به مناسبت عید سعید فطر"),
            Triple(3, 6, "عید سعید قربان"),
            Triple(3, 14, "عید سعید غدیر خم"),
            Triple(4, 3, "تاسوعای حسینی"),
            Triple(4, 4, "عاشورای حسینی"),
            Triple(5, 13, "اربعین حسینی"),
            Triple(5, 21, "رحلت پیامبر اکرم (ص) و شهادت امام حسن مجتبی (ع)"),
            Triple(5, 22, "شهادت امام رضا (ع)"),
            Triple(5, 30, "شهادت امام حسن عسکری (ع)"),
            Triple(6, 8, "میلاد پیامبر اکرم (ص) و امام جعفر صادق (ع)"),
            Triple(8, 22, "شهادت حضرت فاطمه زهرا (س)"),
            Triple(10, 2, "ولادت امام علی (ع) و روز پدر"),
            Triple(10, 16, "مبعث حضرت رسول اکرم (ص)"),
            Triple(11, 4, "ولادت حضرت قائم (عج) و جشن نیمه شعبان"),
            Triple(12, 9, "شهادت حضرت علی (ع)"),
            Triple(12, 19, "عید سعید فطر"),
            Triple(12, 20, "تعطیل به مناسبت عید سعید فطر")
        ),
        1406 to listOf(
            Triple(1, 14, "شهادت امام جعفر صادق (ع)"),
            Triple(2, 27, "عید سعید قربان"),
            Triple(3, 4, "عید سعید غدیر خم"),
            Triple(3, 25, "تاسوعای حسینی"),
            Triple(3, 26, "عاشورای حسینی"),
            Triple(5, 3, "اربعین حسینی"),
            Triple(5, 11, "رحلت پیامبر اکرم (ص) و شهادت امام حسن مجتبی (ع)"),
            Triple(5, 12, "شهادت امام رضا (ع)"),
            Triple(5, 20, "شهادت امام حسن عسکری (ع)"),
            Triple(5, 29, "میلاد پیامبر اکرم (ص) و امام جعفر صادق (ع)"),
            Triple(8, 12, "شهادت حضرت فاطمه زهرا (س)"),
            Triple(9, 21, "ولادت امام علی (ع) و روز پدر"),
            Triple(10, 5, "مبعث حضرت رسول اکرم (ص)"),
            Triple(10, 23, "ولادت حضرت قائم (عج) و جشن نیمه شعبان"),
            Triple(11, 28, "شهادت حضرت علی (ع)"),
            Triple(12, 8, "عید سعید فطر"),
            Triple(12, 9, "تعطیل به مناسبت عید سعید فطر")
        )
    )

    /** day of month -> occasion(s) of that day, for the official holidays of one Jalali month. */
    fun forMonth(year: Int, month: Int): Map<Int, String> {
        val names = LinkedHashMap<Int, MutableList<String>>()
        FIXED.forEach { (key, name) ->
            if (key.first == month) names.getOrPut(key.second) { mutableListOf() }.add(name)
        }
        RELIGIOUS[year].orEmpty().forEach { (m, d, name) ->
            if (m == month) names.getOrPut(d) { mutableListOf() }.add(name)
        }
        return names.mapValues { it.value.joinToString(" / ") }
    }

    /** True when the lunar (religious) holidays of this Jalali year are known. */
    fun hasReligiousData(year: Int): Boolean = year in RELIGIOUS
}

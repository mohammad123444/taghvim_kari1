package ir.taghvimkari.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** One printed row of the جمع‌بندی table; every value is already formatted text. */
data class PdfRow(
    val name: String,
    val total: String,
    val extra: String,
    val comment: String,
    val level: String,
    val reward: String,
    val evaluation: String
)

/** One employment group (رسمی / قرارداد مستقیم / پیمانکاری) with its average of the «جمع» column. */
data class PdfGroup(val title: String, val rows: List<PdfRow>, val average: String)

/** Makes the جمع‌بندی table of a period as an A4 PDF (right-to-left, Persian) and opens the share sheet. */
object SummaryPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 30f
    private const val ROW_H = 24f
    private const val COMMENT_H = 14f
    private const val NAVY = 0xFF1F2A5A.toInt()
    private const val PURPLE = 0xFF5B3FA6.toInt()

    // Columns from the right edge: name, جمع, ملاحظات, رئیس, تشویق / تنبیه, ارزشیابی (widths add up to the table width).
    private val HEADERS = listOf("نام", "جمع", "ملاحظات", "رئیس", "تشویق / تنبیه", "ارزشیابی")
    private val WIDTHS = listOf(175f, 55f, 70f, 55f, 95f, 85f)

    private var fonts: Context? = null

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = fonts?.let { AppFonts.typeface(it, bold) } ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Draws [text] right-to-left inside the cell [left]..[right]; long text is made smaller, then cut. */
    private fun cell(canvas: Canvas, text: String, left: Float, right: Float, baseline: Float, p: Paint, center: Boolean = true) {
        if (text.isEmpty()) return
        val room = right - left - 6f
        val startSize = p.textSize
        while (p.measureText(text) > room && p.textSize > 6.5f) p.textSize -= 0.5f
        var shown = text
        while (p.measureText(shown) > room && shown.length > 1) shown = shown.dropLast(1)
        val width = p.measureText(shown)
        val x = if (center) left + (right - left - width) / 2 else right - 3f - width
        canvas.drawTextRun(shown, 0, shown.length, 0, shown.length, x, baseline, true, p)
        p.textSize = startSize
    }

    fun create(context: Context, periodText: String, groups: List<PdfGroup>): File {
        fonts = context.applicationContext
        val doc = PdfDocument()
        val tableW = WIDTHS.sum()
        val tableRight = PAGE_W - MARGIN
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
            if (pageNo == 1) {
                cell(canvas!!, "جمع‌بندی", MARGIN, tableRight, y + 16f, paint(18f, true, NAVY), center = false)
                y += 26f
                cell(canvas!!, periodText, MARGIN, tableRight, y + 10f, paint(11f, false, Color.DKGRAY), center = false)
                y += 24f
            }
        }

        fun drawHeader(title: String, count: Int) {
            val c = canvas!!
            val band = Paint().apply { color = 0x1F5B3FA6 }
            c.drawRect(MARGIN, y, tableRight, y + ROW_H, band)
            cell(c, title, MARGIN, tableRight, y + 16f, paint(12f, true, PURPLE), center = false)
            cell(c, "$count نفر", MARGIN, MARGIN + 90f, y + 16f, paint(10f, false, Color.GRAY))
            y += ROW_H
            val head = Paint().apply { color = 0x0F5B3FA6 }
            c.drawRect(MARGIN, y, tableRight, y + ROW_H, head)
            var right = tableRight
            HEADERS.forEachIndexed { i, h ->
                cell(c, h, right - WIDTHS[i], right, y + 16f, paint(10f, true, PURPLE))
                right -= WIDTHS[i]
            }
            y += ROW_H
        }

        newPage()
        val line = Paint().apply { color = 0xFFE0DEEF.toInt(); strokeWidth = 0.6f }
        groups.forEach { g ->
            if (g.rows.isEmpty()) return@forEach
            if (y + ROW_H * 3 > PAGE_H - MARGIN) newPage()
            drawHeader(g.title, g.rows.size)
            g.rows.forEach { r ->
                val needed = ROW_H + if (r.comment.isNotBlank()) COMMENT_H else 0f
                if (y + needed > PAGE_H - MARGIN - 20f) {
                    newPage()
                    drawHeader(g.title, g.rows.size)
                }
                val c = canvas!!
                val values = listOf(r.name, r.total, r.extra, r.level, r.reward, r.evaluation)
                var right = tableRight
                values.forEachIndexed { i, v ->
                    val p = when (i) {
                        0 -> paint(11f, false, Color.BLACK)
                        1 -> paint(11f, true, NAVY)
                        else -> paint(11f, false, Color.DKGRAY)
                    }
                    cell(c, v, right - WIDTHS[i], right, y + 16f, p, center = i != 0)
                    right -= WIDTHS[i]
                }
                y += ROW_H
                if (r.comment.isNotBlank()) {
                    cell(c, "ملاحظات: ${r.comment}", MARGIN, tableRight - 4f, y + 8f, paint(8.5f, false, Color.GRAY), center = false)
                    y += COMMENT_H
                }
                c.drawLine(MARGIN, y, tableRight, y, line)
            }
            // average of the «جمع» column for this group
            val c = canvas!!
            cell(c, "میانگین جمع: ${g.average}", MARGIN, tableRight, y + 16f, paint(11f, true, NAVY), center = false)
            y += ROW_H + 10f
        }
        page?.let { doc.finishPage(it) }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "taghvim-summary-${LocalDate.now()}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Creates the PDF and opens Android's share sheet. Returns false when anything went wrong. */
    fun share(context: Context, periodText: String, groups: List<PdfGroup>): Boolean = runCatching {
        val file = create(context, periodText, groups)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "ارسال جمع‌بندی (PDF)").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

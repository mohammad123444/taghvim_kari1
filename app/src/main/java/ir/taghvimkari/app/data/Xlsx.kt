package ir.taghvimkari.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny Excel (.xlsx) writer with no library: one right-to-left sheet, a bold header row and
 * text or whole-number cells. A cell is a [String], a [Number] or null (empty).
 */
object Xlsx {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** [rows] includes the header as its first row. */
    fun build(sheetName: String, rows: List<List<Any?>>, columnWidths: List<Int> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", CONTENT_TYPES)
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheetName))
            entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            entry("xl/styles.xml", STYLES)
            entry("xl/worksheets/sheet1.xml", sheet(rows, columnWidths))
        }
        return out.toByteArray()
    }

    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    private const val CONTENT_TYPES = HEAD +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK_RELS = HEAD +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    // style 0 = normal, style 1 = bold on a light fill (the header)
    private const val STYLES = HEAD +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE8E5F6\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs>" +
        "</styleSheet>"

    private fun workbook(sheetName: String): String = HEAD +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"${escape(cleanSheetName(sheetName))}\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"

    private fun cleanSheetName(name: String): String =
        name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifBlank { "Sheet1" }

    private fun sheet(rows: List<List<Any?>>, widths: List<Int>): String {
        val sb = StringBuilder(HEAD)
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        if (widths.isNotEmpty()) {
            sb.append("<cols>")
            widths.forEachIndexed { i, w ->
                sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val ref = columnLetters(c) + (r + 1)
                val style = if (r == 0) " s=\"1\"" else ""
                when (value) {
                    null -> Unit
                    is Number -> sb.append("<c r=\"$ref\"$style><v>${value.toLong()}</v></c>")
                    else -> sb.append("<c r=\"$ref\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value.toString())}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 0 -> A, 25 -> Z, 26 -> AA */
    private fun columnLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                // characters XML 1.0 does not allow are dropped
                ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

/** Writes bytes into the cache folder and opens Android's share sheet for them. */
object ShareBytes {
    fun share(context: Context, fileName: String, mime: String, bytes: ByteArray, chooserTitle: String): Boolean = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

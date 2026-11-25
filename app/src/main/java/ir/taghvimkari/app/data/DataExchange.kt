package ir.taghvimkari.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ir.taghvimkari.app.domain.EvaluationEntry
import ir.taghvimkari.app.domain.Person
import ir.taghvimkari.app.domain.SummaryCell
import ir.taghvimkari.app.domain.ReportEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Sending and receiving data as JSON files. There are separate kinds of file, one per place in the app, and one
 * «full» file with everything in it (used by the backup):
 *  - "people":      the staff list (ثبت نیروها)        {"app":"taghvim-kari","format":1,"type":"people","people":[...]}
 *  - "evaluations": evaluations (ارزیابی)               {"app":"taghvim-kari","format":1,"type":"evaluations","evaluations":[...]}
 *  - "reports":     reward/punishment reports (سوابق)   {"app":"taghvim-kari","format":1,"type":"reports","reports":[...]}
 *  - "summary":     the typed cells of the جمع‌بندی table  {"app":"taghvim-kari","format":1,"type":"summary","summary":[...]}
 *  - "full":        everything (people, reports, evaluations, summary)  — the backup file
 * Files without "type" are the old all-in-one files of earlier versions; they can still be received.
 */
data class ExchangeData(
    val people: List<Person>,
    val reports: List<ReportEntry>?,
    val evaluations: List<EvaluationEntry>? = null,
    val summary: List<SummaryCell>? = null
)

/** What a received file contains. */
sealed interface Incoming {
    data class People(val people: List<Person>) : Incoming
    data class Evaluations(val list: List<EvaluationEntry>) : Incoming
    data class Reports(val list: List<ReportEntry>) : Incoming
    data class Summary(val list: List<SummaryCell>) : Incoming
    /** Old all-in-one file. */
    data class Full(val data: ExchangeData) : Incoming
}

object DataExchange {
    private const val APP = "taghvim-kari"
    private const val FORMAT = 1
    private const val TYPE_PEOPLE = "people"
    private const val TYPE_EVALUATIONS = "evaluations"
    private const val TYPE_REPORTS = "reports"
    private const val TYPE_SUMMARY = "summary"
    private const val TYPE_FULL = "full"
    private const val MAX_BYTES = 5 * 1024 * 1024
    const val MIME = "application/json"

    private fun header(type: String): JSONObject = JSONObject()
        .put("app", APP)
        .put("format", FORMAT)
        .put("type", type)
        .put("exportedAt", LocalDate.now().toString())

    fun encodePeople(people: List<Person>): String =
        header(TYPE_PEOPLE).put("people", LocalStore.encode(people)).toString(2)

    fun encodeEvaluations(evaluations: List<EvaluationEntry>): String =
        header(TYPE_EVALUATIONS).put("evaluations", EvaluationStore.encode(evaluations)).toString(2)

    fun encodeReports(reports: List<ReportEntry>): String =
        header(TYPE_REPORTS).put("reports", ReportStore.encode(reports)).toString(2)

    fun encodeSummary(summary: List<SummaryCell>): String =
        header(TYPE_SUMMARY).put("summary", SummaryStore.encode(summary)).toString(2)

    /** Everything in one file: the backup. */
    fun encodeFull(
        people: List<Person>,
        reports: List<ReportEntry>,
        evaluations: List<EvaluationEntry>,
        summary: List<SummaryCell>
    ): String = header(TYPE_FULL)
        .put("people", LocalStore.encode(people))
        .put("reports", ReportStore.encode(reports))
        .put("evaluations", EvaluationStore.encode(evaluations))
        .put("summary", SummaryStore.encode(summary))
        .toString(2)

    /** Parses a received file. Throws [IllegalArgumentException] with a Persian message if it is not valid. */
    fun parse(text: String): Incoming {
        val trimmed = text.trim().removePrefix("\uFEFF")
        val result: Incoming = try {
            if (trimmed.startsWith("[")) {
                Incoming.Full(ExchangeData(peopleOf(JSONArray(trimmed)), null, null))
            } else {
                val obj = JSONObject(trimmed)
                require(obj.optString("app") == APP) { "این فایل مربوط به این برنامه نیست." }
                when (obj.optString("type")) {
                    TYPE_PEOPLE -> Incoming.People(peopleOf(obj.getJSONArray("people")))
                    TYPE_EVALUATIONS -> {
                        val list = obj.optJSONArray("evaluations")?.let { EvaluationStore.decode(it) }.orEmpty()
                        require(list.isNotEmpty()) { "در این فایل ارزیابی‌ای وجود ندارد." }
                        Incoming.Evaluations(list)
                    }
                    TYPE_REPORTS -> {
                        val list = obj.optJSONArray("reports")?.let { ReportStore.decode(it) }.orEmpty()
                        require(list.isNotEmpty()) { "در این فایل سابقه‌ای وجود ندارد." }
                        Incoming.Reports(list)
                    }
                    TYPE_SUMMARY -> {
                        val list = obj.optJSONArray("summary")?.let { SummaryStore.decode(it) }.orEmpty()
                        require(list.isNotEmpty()) { "در این فایل اطلاعات جمع‌بندی وجود ندارد." }
                        Incoming.Summary(list)
                    }
                    TYPE_FULL -> {
                        val people = obj.optJSONArray("people")?.let { LocalStore.decode(it) }.orEmpty()
                        val reports = obj.optJSONArray("reports")?.let { ReportStore.decode(it) }
                        val evaluations = obj.optJSONArray("evaluations")?.let { EvaluationStore.decode(it) }
                        val summary = obj.optJSONArray("summary")?.let { SummaryStore.decode(it) }
                        require(
                            people.isNotEmpty() || !reports.isNullOrEmpty() ||
                                !evaluations.isNullOrEmpty() || !summary.isNullOrEmpty()
                        ) { "در این فایل اطلاعاتی وجود ندارد." }
                        Incoming.Full(ExchangeData(people, reports, evaluations, summary))
                    }
                    else -> {
                        val reports = obj.optJSONArray("reports")?.let { ReportStore.decode(it) }
                        val evaluations = obj.optJSONArray("evaluations")?.let { EvaluationStore.decode(it) }
                        Incoming.Full(ExchangeData(peopleOf(obj.getJSONArray("people")), reports, evaluations))
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("فایل معتبر نیست.")
        }
        return result
    }

    private fun peopleOf(array: JSONArray): List<Person> {
        val people = LocalStore.decode(array)
        require(people.isNotEmpty()) { "در این فایل اطلاعاتی وجود ندارد." }
        return people
    }

    fun readFile(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("فایل باز نشد.")
        stream.use {
            val bytes = it.readBytes()
            require(bytes.size <= MAX_BYTES) { "فایل بیش از حد بزرگ است." }
            return bytes.toString(Charsets.UTF_8)
        }
    }

    /** Writes [json] to a file and opens Android's share sheet (Telegram, WhatsApp, e-mail, Bluetooth ...). */
    private fun shareJson(context: Context, fileName: String, json: String, chooserTitle: String): Boolean = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeText(json, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun sharePeople(context: Context, people: List<Person>): Boolean =
        shareJson(context, "taghvim-people-${LocalDate.now()}.json", encodePeople(people), "ارسال اطلاعات نیروها")

    fun shareEvaluations(context: Context, evaluations: List<EvaluationEntry>): Boolean =
        shareJson(context, "taghvim-evaluations-${LocalDate.now()}.json", encodeEvaluations(evaluations), "ارسال ارزیابی‌ها")

    fun shareReports(context: Context, reports: List<ReportEntry>): Boolean =
        shareJson(context, "taghvim-reports-${LocalDate.now()}.json", encodeReports(reports), "ارسال سوابق")

    fun shareSummary(context: Context, summary: List<SummaryCell>): Boolean =
        shareJson(context, "taghvim-summary-${LocalDate.now()}.json", encodeSummary(summary), "ارسال جمع‌بندی")

    /**
     * Backup: ONE file with everything (staff, reports, evaluations and the typed جمع‌بندی cells), so a single
     * file is enough to restore the whole app. Returns false if there is nothing to send or sharing failed.
     */
    fun shareBackup(
        context: Context,
        people: List<Person>,
        evaluations: List<EvaluationEntry>,
        reports: List<ReportEntry>,
        summary: List<SummaryCell>
    ): Boolean {
        if (people.isEmpty() && evaluations.isEmpty() && reports.isEmpty() && summary.isEmpty()) return false
        return shareJson(
            context,
            "taghvim-backup-${LocalDate.now()}.json",
            encodeFull(people, reports, evaluations, summary),
            "پشتیبان‌گیری"
        )
    }
}

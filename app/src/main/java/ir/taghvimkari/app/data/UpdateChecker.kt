package ir.taghvimkari.app.data

import ir.taghvimkari.app.domain.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult

    /** [latest] is the newest published version found (null when the repository has no release yet). */
    data class UpToDate(val latest: String?) : UpdateResult

    /** [reason] is a Persian sentence telling the user what went wrong. */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Looks at the published GitHub Releases of [repo] ("owner/name") and picks the highest version.
 * (The old "releases/latest" call hid pre-releases and answered 404 for a private repository,
 * which the app wrongly reported as "up to date".)
 */
object UpdateChecker {
    suspend fun fetchLatest(repo: String, currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        runCatching<UpdateResult> {
            val conn = (URL("https://api.github.com/repos/$repo/releases?per_page=30").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "taghvim-kari-android")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                when (val code = conn.responseCode) {
                    200 -> Unit
                    404 -> return@runCatching UpdateResult.Failed(
                        "مخزن «$repo» پیدا نشد. اگر مخزن خصوصی (private) است، بروزرسانی داخل برنامه کار نمی‌کند؛ مخزن باید عمومی (public) باشد."
                    )
                    403, 429 -> return@runCatching UpdateResult.Failed(
                        "GitHub موقتاً درخواست‌ها را محدود کرده است. چند دقیقه بعد دوباره تلاش کنید."
                    )
                    else -> return@runCatching UpdateResult.Failed("پاسخ نامعتبر از GitHub (کد ${code}).")
                }
                val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                var best: org.json.JSONObject? = null
                for (i in 0 until releases.length()) {
                    val r = releases.getJSONObject(i)
                    if (r.optBoolean("draft", false)) continue
                    val tag = r.optString("tag_name")
                    if (tag.isBlank()) continue
                    if (best == null || Version.isNewer(tag, best.getString("tag_name"))) best = r
                }
                if (best == null) return@runCatching UpdateResult.UpToDate(null)
                val tag = best.getString("tag_name")
                if (!Version.isNewer(tag, currentVersion)) return@runCatching UpdateResult.UpToDate(tag.removePrefix("v"))

                var apk: String? = null
                val assets = best.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) {
                            apk = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                UpdateResult.Available(
                    UpdateInfo(
                        version = tag.removePrefix("v"),
                        pageUrl = best.optString("html_url"),
                        apkUrl = apk,
                        notes = best.optString("body")
                    )
                )
            } finally {
                conn.disconnect()
            }
        }.getOrElse { UpdateResult.Failed("اتصال به GitHub برقرار نشد. اینترنت را بررسی کنید.") }
    }
}

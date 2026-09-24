package com.afudm.afuremote.update

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.SocketException
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val API = "https://api.github.com/repos/pirncedark/AfuRemote/releases"
    private const val MANIFEST = "https://github.com/pirncedark/AfuRemote/releases/latest/download/AfuRemote-update.json"
    private const val PREFS = "afuremote_updates"
    private const val LAST_CHECK = "last_check"
    // Otomatik denetim her açılışta; GitHub sınırına takılmamak için en sık saatte bir.
    private const val AUTO_CHECK_MS = 60L * 60 * 1000
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()

    suspend fun check(currentVersionCode: Int): AppUpdate? = withContext(Dispatchers.IO) {
        var manifest: String? = null
        try {
            http.newCall(Request.Builder().url(MANIFEST).build()).execute().use { r ->
                if (r.isSuccessful) manifest = r.body?.string()?.takeIf { UpdateParser.manifest(it, -1) != null }
                else if (!shouldFallbackToApi(r.code)) throw statusException(r.code)
            }
        } catch (e: IOException) {
            if (e.message == rateLimitMessage()) throw e
            if (e is UnknownHostException || e is SocketTimeoutException || e is SocketException) throw IOException(internetMessage())
            // Missing/invalid manifests and transient manifest errors fall through to the legacy API.
        }
        manifest?.let { UpdateParser.manifest(it, -1) }?.let { return@withContext it.takeIf { update -> update.versionCode > currentVersionCode } }
        try {
            val legacy = http.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
                if (!r.isSuccessful) {
                    throw statusException(r.code)
                }
                r.body?.string().orEmpty()
            }
            UpdateParser.manifestOrLegacy(manifest, legacy, currentVersionCode)
        } catch (e: UnknownHostException) {
            throw IOException("İnternet bağlantısı yok.")
        } catch (e: SocketTimeoutException) {
            throw IOException(internetMessage())
        } catch (e: SocketException) {
            throw IOException(internetMessage())
        } catch (e: IOException) {
            if (e.message == rateLimitMessage() || e.message == "G\u00fcncelleme denetlenemedi.") throw e
            throw IOException(internetMessage())
        }
    }

    internal fun rateLimitMessage() = "GitHub \u015fu an yo\u011fun, birka\u00e7 dakika sonra tekrar dene."
    internal fun shouldFallbackToApi(status: Int) = status != 403 && status != 429
    internal fun statusException(status: Int) = IOException(if (status == 403 || status == 429) rateLimitMessage() else "G\u00fcncelleme denetlenemedi.")
    private fun internetMessage() = "\u0130nternet ba\u011flant\u0131s\u0131 yok."

    fun shouldCheckAutomatically(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(LAST_CHECK, 0L) >= AUTO_CHECK_MS

    fun markChecked(context: Context) = prefs(context).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()

    fun enqueueDownload(context: Context, update: AppUpdate) {
        val data = workDataOf(
            ApkDownloadWorker.KEY_APK to update.apkUrl,
            ApkDownloadWorker.KEY_SHA to update.checksumUrl,
            ApkDownloadWorker.KEY_VERSION_CODE to update.versionCode
        )
        enqueueDownload(context, data)
    }

    fun retryDownload(context: Context, input: Data) = enqueueDownload(context, input)

    private fun enqueueDownload(context: Context, input: Data) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(input)
            .addTag(VERSION_TAG + input.getInt(ApkDownloadWorker.KEY_VERSION_CODE, 0))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("afuremote_update", ExistingWorkPolicy.REPLACE, request)
    }

    /** Version code of the release a download job belongs to, read back from its tag. */
    fun versionCodeOf(tags: Set<String>): Int =
        tags.firstNotNullOfOrNull { tag -> tag.takeIf { it.startsWith(VERSION_TAG) }?.removePrefix(VERSION_TAG)?.toIntOrNull() } ?: 0

    private const val VERSION_TAG = "afuremote-version:"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

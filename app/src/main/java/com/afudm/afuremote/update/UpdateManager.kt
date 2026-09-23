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
import java.util.concurrent.TimeUnit

object UpdateManager {
    private const val API = "https://api.github.com/repos/pirncedark/AfuRemote/releases"
    private const val PREFS = "afuremote_updates"
    private const val LAST_CHECK = "last_check"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun check(currentVersionCode: Int): AppUpdate? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub ${r.code}")
            UpdateParser.latest(r.body?.string().orEmpty(), currentVersionCode)
        }
    }

    fun shouldCheckAutomatically(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(LAST_CHECK, 0L) >= DAY_MS

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

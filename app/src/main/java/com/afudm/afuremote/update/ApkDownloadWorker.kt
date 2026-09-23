package com.afudm.afuremote.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.afudm.afuremote.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = progressInfo(0)

    override suspend fun doWork(): Result = runCatching {
        runCatching { setForeground(progressInfo(0)) }
        val apk = File(applicationContext.cacheDir, "AfuRemote-update.apk")
        download(inputData.getString(KEY_APK)!!, apk)
        val expected = URL(inputData.getString(KEY_SHA)!!).openStream().bufferedReader().use { it.readText() }
        check(Sha256.verify(apk, expected)) { "İndirilen dosyanın güvenlik özeti tutmadı" }
        notifyReady(apk)
        Result.success(workDataOf(KEY_APK_PATH to apk.absolutePath))
    }.getOrElse {
        // Keep the input so the update screen can retry the same download.
        val output = Data.Builder()
            .putAll(inputData)
            .putString(KEY_ERROR, it.localizedMessage ?: "Güncelleme indirilemedi")
            .build()
        Result.failure(output)
    }

    private suspend fun download(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        val total = connection.contentLengthLong
        var done = 0L
        var last = -1
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    val percent = if (total > 0) (done * 100 / total).toInt() else 0
                    if (percent != last) {
                        last = percent
                        setProgress(workDataOf(KEY_PROGRESS to percent))
                        runCatching { setForegroundAsync(progressInfo(percent)) }
                    }
                }
            }
        }
    }

    private fun notifyReady(apk: File) {
        val intent = Installer.installIntent(applicationContext, apk)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(INSTALL_CHANNEL, "AfuRemote güncellemeleri", NotificationManager.IMPORTANCE_HIGH))
        val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote güncellemesi hazır")
            .setContentText("Uygulamaya dönüp Kur düğmesine dokunun.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(INSTALL_ID, notification) }
    }

    private fun progressInfo(percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "AfuRemote güncelleme indirme", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle("AfuRemote güncellemesi indiriliyor")
            .setContentText("%$percent")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
    }

    companion object {
        const val KEY_APK = "apkUrl"
        const val KEY_SHA = "checksumUrl"
        const val KEY_APK_PATH = "apkPath"
        const val KEY_VERSION_CODE = "versionCode"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS = "percent"
        private const val INSTALL_CHANNEL = "afuremote_update_install"
        private const val PROGRESS_CHANNEL = "afuremote_update_progress"
        private const val INSTALL_ID = 7301
        private const val PROGRESS_ID = 7302
    }
}

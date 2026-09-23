package com.afudm.afuremote.update

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateSection(versionCode: Int, versionName: String) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<AppUpdate?>(null) }
    var resumed by remember { mutableStateOf(false) }
    var settingsFallback by remember { mutableStateOf(false) }
    var downloadObserved by remember { mutableStateOf(false) }
    var settingsRequested by remember { mutableStateOf(false) }
    var launchedApkPath by remember { mutableStateOf<String?>(null) }
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("afuremote_update")
        .collectAsState(initial = emptyList())
    // A finished download of the version that is now installed (or older) is stale: never offer it again.
    val work = workInfos.lastOrNull()?.takeIf { it.inputData.getInt(ApkDownloadWorker.KEY_VERSION_CODE, 0) > versionCode }
    val apkPath = work?.outputData?.getString(ApkDownloadWorker.KEY_APK_PATH)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (UpdateManager.shouldCheckAutomatically(context)) {
            UpdateManager.markChecked(context)
            runCatching { UpdateManager.check(versionCode) }.getOrNull()?.let { found = it }
        }
    }

    LaunchedEffect(work?.state) {
        if (work?.state == WorkInfo.State.ENQUEUED || work?.state == WorkInfo.State.RUNNING) {
            downloadObserved = true
        }
    }

    LaunchedEffect(work?.state, apkPath, resumed, settingsRequested, downloadObserved) {
        if (work?.state == WorkInfo.State.SUCCEEDED && apkPath != null && resumed &&
            (downloadObserved || settingsRequested)
        ) {
            if (settingsRequested && canInstall(context)) {
                launchedApkPath = apkPath
                settingsRequested = false
                openInstaller(context, apkPath)
            } else if (!settingsRequested && launchedApkPath != apkPath) {
                if (canInstall(context)) {
                    launchedApkPath = apkPath
                    openInstaller(context, apkPath)
                } else {
                    installFromApp(context, apkPath, { settingsRequested = true }, { settingsFallback = true })
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Yüklü sürüm: $versionName")
        OutlinedButton(onClick = {
            scope.launch {
                status = "Denetleniyor…"
                runCatching { UpdateManager.check(versionCode) }
                    .onSuccess { if (it == null) status = "Uygulama güncel" else { status = ""; found = it } }
                    .onFailure { status = it.localizedMessage ?: "G\u00fcncelleme denetlenemedi" }
            }
        }) { Text("Güncellemeleri denetle") }
        if (status.isNotBlank()) Text(status)
        when (work?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Text("İndirme sırada bekliyor")
            WorkInfo.State.RUNNING -> {
                val percent = work.progress.getInt(ApkDownloadWorker.KEY_PROGRESS, 0)
                Text("İndiriliyor… %$percent")
            }
            WorkInfo.State.FAILED -> {
                Text(work.outputData.getString("error") ?: "Güncelleme indirilemedi")
                OutlinedButton(onClick = { UpdateManager.retryDownload(context, work.inputData) }) {
                    Text("Tekrar dene")
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                Text("Güncelleme hazır")
                Button(onClick = { apkPath?.let { installFromApp(context, it, { settingsRequested = true }, { settingsFallback = true }) } }) {
                    Text("Kur")
                }
            }
            else -> Unit
        }
        if (settingsFallback) {
            Text("TV: Ayarlar → Cihaz tercihleri → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar → AfuRemote'u açın")
        }
    }

    found?.let { update ->
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Yeni AfuRemote sürümü: ${update.versionName}") },
            text = { Text(update.releaseNotes.take(400).ifBlank { "Hata düzeltmeleri ve iyileştirmeler." }) },
            confirmButton = {
                TextButton(onClick = {
                    found = null
                    UpdateManager.enqueueDownload(context, update)
                }) { Text("İndir ve kur") }
            },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Sonra") } }
        )
    }
}

private fun canInstall(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

private fun installFromApp(context: android.content.Context, apkPath: String, onSettings: () -> Unit, onFallback: () -> Unit) {
    if (Installer.action(canInstall(context)) == Installer.Action.OPEN_SETTINGS) {
        try {
            onSettings()
            context.startActivity(Installer.settingsIntent(context))
        } catch (_: ActivityNotFoundException) {
            onFallback()
        }
        return
    }
    openInstaller(context, apkPath)
}

private fun openInstaller(context: android.content.Context, apkPath: String) {
    runCatching { context.startActivity(Installer.installIntent(context, File(apkPath))) }
}

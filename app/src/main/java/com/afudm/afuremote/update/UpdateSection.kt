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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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

/** Güncelleme ekranları arasında paylaşılan durum (otomatik güncelleyici + ayarlardaki bölüm). */
class UpdateState {
    var found by mutableStateOf<AppUpdate?>(null)
    var settingsRequested by mutableStateOf(false)
    var settingsFallback by mutableStateOf(false)
    var downloadObserved by mutableStateOf(false)
    var launchedApkPath by mutableStateOf<String?>(null)
}

@Composable
private fun currentWork(versionCode: Int): State<WorkInfo?> {
    val context = LocalContext.current
    val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("afuremote_update").collectAsState(initial = emptyList())
    // A finished download of the version that is now installed (or older) is stale: never offer it again.
    return remember(infos.value) { mutableStateOf(infos.value.lastOrNull()?.takeIf { UpdateManager.versionCodeOf(it.tags) > versionCode }) }
}

/**
 * Her zaman ekranda duran görünmez güncelleyici: açılışta güncelleme linkini denetler,
 * yeni sürüm varsa kendiliğinden indirir ve indirme bitince kurulum ekranını açar.
 * Debug sürümlerde (CI e2e) otomatik indirme yerine soru penceresi gösterir.
 */
@Composable
fun AutoUpdater(versionCode: Int, state: UpdateState, autoDownload: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    var resumed by remember { mutableStateOf(false) }
    val work by currentWork(versionCode)
    val apkPath = work?.outputData?.getString(ApkDownloadWorker.KEY_APK_PATH)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> resumed = event == Lifecycle.Event.ON_RESUME }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (UpdateManager.shouldCheckAutomatically(context)) {
            UpdateManager.markChecked(context)
            val update = runCatching { UpdateManager.check(versionCode) }.getOrNull() ?: return@LaunchedEffect
            if (autoDownload) UpdateManager.enqueueDownload(context, update) else state.found = update
        }
    }

    LaunchedEffect(work?.state) {
        if (work?.state == WorkInfo.State.ENQUEUED || work?.state == WorkInfo.State.RUNNING) state.downloadObserved = true
    }

    LaunchedEffect(work?.state, apkPath, resumed, state.settingsRequested, state.downloadObserved) {
        if (work?.state == WorkInfo.State.SUCCEEDED && apkPath != null && resumed &&
            (state.downloadObserved || state.settingsRequested)
        ) {
            if (state.settingsRequested && canInstall(context)) {
                state.launchedApkPath = apkPath
                state.settingsRequested = false
                openInstaller(context, apkPath)
            } else if (!state.settingsRequested && state.launchedApkPath != apkPath) {
                if (canInstall(context)) {
                    state.launchedApkPath = apkPath
                    openInstaller(context, apkPath)
                } else {
                    installFromApp(context, apkPath, { state.settingsRequested = true }, { state.settingsFallback = true })
                }
            }
        }
    }

    state.found?.let { update ->
        AlertDialog(
            onDismissRequest = { state.found = null },
            title = { Text("Yeni AfuRemote sürümü: ${update.versionName}") },
            text = { Text(ReleaseNotes.forDisplay(update.releaseNotes).take(400).ifBlank { "Hata düzeltmeleri ve iyileştirmeler." }) },
            confirmButton = {
                TextButton(onClick = {
                    state.found = null
                    UpdateManager.enqueueDownload(context, update)
                }) { Text("İndir ve kur") }
            },
            dismissButton = { TextButton(onClick = { state.found = null }) { Text("Sonra") } }
        )
    }
}

/** Ayarlardaki görünür bölüm: sürüm, elle denetleme, indirme durumu. */
@Composable
fun UpdateSection(versionCode: Int, versionName: String, state: UpdateState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val work by currentWork(versionCode)
    val apkPath = work?.outputData?.getString(ApkDownloadWorker.KEY_APK_PATH)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Yüklü sürüm: $versionName")
        OutlinedButton(onClick = {
            scope.launch {
                status = "Denetleniyor…"
                runCatching { UpdateManager.check(versionCode) }
                    .onSuccess { if (it == null) status = "Uygulama güncel" else { status = ""; state.found = it } }
                    .onFailure { status = it.localizedMessage ?: "Güncelleme denetlenemedi" }
            }
        }) { Text("Güncellemeleri denetle") }
        if (status.isNotBlank()) Text(status)
        when (work?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Text("İndirme sırada bekliyor")
            WorkInfo.State.RUNNING -> Text("İndiriliyor… %${work?.progress?.getInt(ApkDownloadWorker.KEY_PROGRESS, 0) ?: 0}")
            WorkInfo.State.FAILED -> {
                Text(work?.outputData?.getString(ApkDownloadWorker.KEY_ERROR) ?: "Güncelleme indirilemedi")
                OutlinedButton(onClick = { work?.let { UpdateManager.retryDownload(context, it.outputData) } }) { Text("Tekrar dene") }
            }
            WorkInfo.State.SUCCEEDED -> {
                Text("Güncelleme hazır")
                Button(onClick = {
                    apkPath?.let { installFromApp(context, it, { state.settingsRequested = true }, { state.settingsFallback = true }) }
                }) { Text("Kur") }
            }
            else -> Unit
        }
        if (state.settingsFallback) {
            Text("TV: Ayarlar → Cihaz tercihleri → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar → AfuRemote'u açın")
        }
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

package com.afudm.afuremote.update

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateSection(versionCode: Int, versionName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<AppUpdate?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (UpdateManager.shouldCheckAutomatically(context)) {
            UpdateManager.markChecked(context)
            runCatching { UpdateManager.check(versionCode) }.getOrNull()?.let { found = it }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Yüklü sürüm: $versionName")
        OutlinedButton(onClick = {
            scope.launch {
                status = "Denetleniyor…"
                runCatching { UpdateManager.check(versionCode) }
                    .onSuccess { if (it == null) status = "Uygulama güncel" else { status = ""; found = it } }
                    .onFailure { status = "Denetlenemedi — internet bağlantısını kontrol edin" }
            }
        }) { Text("Güncellemeleri denetle") }
        if (status.isNotBlank()) Text(status)
    }

    found?.let { update ->
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Yeni AfuRemote sürümü: ${update.versionName}") },
            text = { Text(update.releaseNotes.take(400).ifBlank { "Hata düzeltmeleri ve iyileştirmeler." }) },
            confirmButton = {
                TextButton(onClick = {
                    found = null
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    UpdateManager.enqueueDownload(context, update)
                    Toast.makeText(context, "Güncelleme indiriliyor — bildirimden takip edebilirsiniz", Toast.LENGTH_LONG).show()
                }) { Text("İndir ve kur") }
            },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Sonra") } }
        )
    }
}

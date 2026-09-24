package com.afudm.afuremote.phone

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PhonePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permission = remember { PhonePermissions.required(Build.VERSION.SDK_INT) }
    var granted by remember(permission) {
        mutableStateOf(permission == null || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permission != null) {
                granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        denied = !isGranted
    }

    if (granted) {
        content()
        return
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (denied) "TV bağlantısı için izin gerekli" else "AfuRemote TV'leri bulsun") },
        text = {
            Text(PhonePermissions.rationale(permission!!) + if (denied) " İzin verilmediği için otomatik bulma ve elle IP ile bağlantı çalışmaz. Uygulama ayarlarından Yerel ağ iznini açın." else " AfuRemote konumunuzu kullanmaz.")
        },
        confirmButton = {
            if (denied) TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("Ayarları aç") }
            else Button(onClick = { launcher.launch(permission!!) }) { Text("İzin ver ve devam et") }
        },
        dismissButton = { if (!denied) TextButton(onClick = { denied = true }) { Text("Şimdi değil") } }
    )
}

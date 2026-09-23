package com.afudm.afuremote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Installer {
    enum class Action {
        OPEN_SETTINGS,
        INSTALL
    }

    fun action(canInstallPackages: Boolean): Action =
        if (canInstallPackages) Action.INSTALL else Action.OPEN_SETTINGS

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun settingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
}

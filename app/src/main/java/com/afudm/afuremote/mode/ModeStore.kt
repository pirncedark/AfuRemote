package com.afudm.afuremote.mode

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object ModeStore {
    private const val PREFS = "afuremote_mode"
    private const val KEY = "override"

    fun current(context: Context): AppMode {
        val ui = context.getSystemService(UiModeManager::class.java)
        val tvUi = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val leanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return AppMode.detect(tvUi, leanback, prefs(context).getString(KEY, null))
    }

    fun setOverride(context: Context, value: String?) {
        prefs(context).edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

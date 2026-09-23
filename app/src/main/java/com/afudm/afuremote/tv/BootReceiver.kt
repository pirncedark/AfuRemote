package com.afudm.afuremote.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afudm.afuremote.mode.AppMode
import com.afudm.afuremote.mode.ModeStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && ModeStore.current(context) == AppMode.TV) AfuTvService.start(context)
    }
}

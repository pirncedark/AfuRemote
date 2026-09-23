package com.afudm.afuremote

import android.app.Application

class AfuRemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppVisibility)
    }
}

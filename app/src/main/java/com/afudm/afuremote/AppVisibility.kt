package com.afudm.afuremote

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Uygulamanın bir ekranı görünür mü? (Erişilebilirlik kapalıyken yalnız o zaman ekran açabiliriz.) */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    val isForeground: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) { started = maxOf(0, started - 1) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

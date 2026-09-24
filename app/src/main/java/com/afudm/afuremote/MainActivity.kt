package com.afudm.afuremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.afudm.afuremote.mode.AppMode
import com.afudm.afuremote.mode.ModeStore
import com.afudm.afuremote.ui.AfuTheme
import com.afudm.afuremote.tv.AfuTvService
import com.afudm.afuremote.tv.TvHomeScreen
import com.afudm.afuremote.phone.PhoneHomeScreen
import com.afudm.afuremote.update.AutoUpdater
import com.afudm.afuremote.update.UpdateSection
import com.afudm.afuremote.update.UpdateState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Koyu zemin: durum/gezinme çubuğu simgeleri açık renk kalsın.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        applyModeExtra(intent)
        setContent {
            AfuTheme {
                var mode by remember { mutableStateOf(ModeStore.current(this)) }
                val change: (String?) -> Unit = { ModeStore.setOverride(this, it); mode = ModeStore.current(this) }
                LaunchedEffect(mode) { if (mode == AppMode.TV) AfuTvService.start(this@MainActivity) }
                val updates = remember { UpdateState() }
                val updateSection: @Composable () -> Unit = { UpdateSection(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, updates) }
                Surface(Modifier.fillMaxSize()) {
                    when (mode) {
                        AppMode.TV -> TvHomeScreen(BuildConfig.VERSION_NAME, change, updateSection)
                        AppMode.PHONE -> PhoneHomeScreen(BuildConfig.VERSION_NAME, change, updateSection)
                    }
                    // Güncelleme linki açılışta denetlenir; yayın sürümünde yeni APK kendiliğinden inip kurulum açılır.
                    AutoUpdater(BuildConfig.VERSION_CODE, updates, autoDownload = !BuildConfig.DEBUG)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyModeExtra(intent)) recreate()
    }

    /** e2e ve elle test için: `am start ... --es mode tv|phone|auto`. Değişiklik olduysa true. */
    private fun applyModeExtra(intent: Intent?): Boolean {
        when (intent?.getStringExtra("mode")) {
            "tv" -> ModeStore.setOverride(this, "tv")
            "phone" -> ModeStore.setOverride(this, "phone")
            "auto" -> ModeStore.setOverride(this, null)
            else -> return false
        }
        return true
    }
}

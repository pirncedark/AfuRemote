package com.afudm.afuremote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afudm.afuremote.mode.AppMode
import com.afudm.afuremote.mode.ModeStore
import com.afudm.afuremote.ui.AfuTheme
import com.afudm.afuremote.ui.ModeSection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyModeExtra(intent)
        setContent {
            AfuTheme {
                var mode by remember { mutableStateOf(ModeStore.current(this)) }
                val change: (String?) -> Unit = { ModeStore.setOverride(this, it); mode = ModeStore.current(this) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp)) {
                        Text(if (mode == AppMode.TV) "AfuRemote TV" else "AfuRemote")
                        ModeSection(change)
                    }
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

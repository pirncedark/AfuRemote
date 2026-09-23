package com.afudm.afuremote.tv

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class WebActivity : ComponentActivity() {
    private var webView: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        setContent {
            BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
            AndroidView(factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(url)
                    webView = this
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
    override fun onDestroy() { webView?.destroy(); webView = null; super.onDestroy() }
    companion object { const val EXTRA_URL = "url" }
}

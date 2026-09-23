package com.afudm.afuremote.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.protocol.PHONE_MEDIA_PORT
import fi.iki.elonen.NanoHTTPD

object PhoneStream {
    private var server: LocalMediaServer? = null

    /** Videoyu yayınlar, TV'nin açacağı adresi döner (Wi-Fi yoksa / okunamazsa null). */
    @Synchronized
    fun publish(context: Context, uri: Uri): String? {
        val app = context.applicationContext
        val ip = LocalIp.wifiIpv4() ?: return null
        val s = server ?: LocalMediaServer(app).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); server = it }
        val path = s.publish(uri) ?: return null
        // Ön plan servisi: paylaşım ekranı kapansa da yayın sürer; URI okuma izni servise devredilir.
        ContextCompat.startForegroundService(
            app,
            Intent(app, PhoneStreamService::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return "http://$ip:$PHONE_MEDIA_PORT$path"
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }
}

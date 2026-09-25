package com.afudm.afuremote.phone

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.afudm.afuremote.pairing.PairingStore
import com.afudm.afuremote.atvremote.AtvCredentialStore
import com.afudm.afuremote.atvremote.AtvTlsClientFactory
import com.afudm.afuremote.atvremote.AtvPairingManager
import com.afudm.afuremote.atvremote.AtvRemoteClient

/** Telefon tarafı nesneleri tek yerde (MainActivity ve ShareActivity paylaşır). */
object PhoneGraph {
    class Graph(val store: PairingStore, val client: TvClient, val known: KnownTvStore, val discovery: TvDiscovery, val controller: PhoneController,
                val atvPairing: AtvPairingManager, val atvRemote: AtvRemoteClient)

    @Volatile private var instance: Graph? = null

    fun get(context: Context): Graph = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): Graph {
        val store = PairingStore(app)
        val client = TvClient(deviceIdProvider = store::deviceId)
        val name = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val known = KnownTvStore(app)
        val credentials = AtvCredentialStore(app)
        val tls = AtvTlsClientFactory(credentials)
        return Graph(store, client, known, TvDiscovery(app, client, known), PhoneController(client, store, name),
            AtvPairingManager(credentials, tls), AtvRemoteClient(credentials, tls, name))
    }
}

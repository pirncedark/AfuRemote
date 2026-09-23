package com.afudm.afuremote.phone

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.afudm.afuremote.pairing.PairingStore

/** Telefon tarafı nesneleri tek yerde (MainActivity ve ShareActivity paylaşır). */
object PhoneGraph {
    class Graph(val store: PairingStore, val client: TvClient, val discovery: TvDiscovery, val controller: PhoneController)

    @Volatile private var instance: Graph? = null

    fun get(context: Context): Graph = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): Graph {
        val store = PairingStore(app)
        val client = TvClient(deviceIdProvider = store::deviceId)
        val name = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
        return Graph(store, client, TvDiscovery(app, client), PhoneController(client, store, name))
    }
}

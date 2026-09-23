package com.afudm.afuremote.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.afudm.afuremote.protocol.SERVICE_TYPE
import com.afudm.afuremote.protocol.TV_PORT

class NsdAdvertiser(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    @Volatile private var registered = false
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { registered = true; Log.i(TAG, "NSD registered: ${info.serviceName}") }
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.e(TAG, "NSD registration failed: $errorCode") }
        override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.e(TAG, "NSD unregister failed: $errorCode") }
    }

    fun register(name: String) {
        if (registered) return
        val service = NsdServiceInfo().apply { serviceName = name; serviceType = SERVICE_TYPE; port = TV_PORT }
        runCatching { manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.e(TAG, "NSD register exception", it) }
    }

    fun unregister() { if (registered) runCatching { manager.unregisterService(listener) }; registered = false }
    private companion object { const val TAG = "AfuRemoteNsd" }
}

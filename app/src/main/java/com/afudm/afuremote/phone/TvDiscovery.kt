package com.afudm.afuremote.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.afudm.afuremote.protocol.SERVICE_TYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

class TvDiscovery(context: Context, private val client: TvClient) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolveLock = Mutex() // Android 13 ve öncesi aynı anda tek çözümleme
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var users = 0

    @Synchronized fun acquire() { users++; if (users == 1) start() }
    @Synchronized fun release() { users = maxOf(0, users - 1); if (users == 0) stop() }

    private fun start() {
        _devices.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) { scope.launch { resolveAndVerify(service) } }
            override fun onServiceLost(service: NsdServiceInfo) {
                _devices.update { list -> list.filterNot { it.serviceName == service.serviceName } }
            }
            override fun onDiscoveryStarted(serviceType: String) { Log.i(TAG, "TV aramasi basladi") }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.e(TAG, "arama baslamadi: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    private fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }

    private suspend fun resolveAndVerify(service: NsdServiceInfo) {
        val resolved = resolveLock.withLock { resolve(service) } ?: return
        val host = pickHost(resolved) ?: return
        val info = client.info(host, resolved.port) ?: return
        val device = TvDevice(info.id, info.name, info.model, host, resolved.port, service.serviceName)
        Log.i(TAG, "TV bulundu: ${info.name} ($host)")
        _devices.update { list -> list.filterNot { it.id == device.id } + device }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) { if (cont.isActive) cont.resume(info) }
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
        })
    }

    @Suppress("DEPRECATION")
    private fun pickHost(info: NsdServiceInfo): String? {
        val all: List<InetAddress> = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        return (all.firstOrNull { it is Inet4Address } ?: all.firstOrNull())?.hostAddress
    }

    private companion object { const val TAG = "AfuRemotePhone" }
}

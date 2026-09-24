package com.afudm.afuremote.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.net.Subnet
import com.afudm.afuremote.protocol.SERVICE_TYPE
import com.afudm.afuremote.protocol.TV_PORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * TV bulma, dört katman:
 * 1) daha önce bulunan TV'ler açılışta hemen denenir,
 * 2) NSD (mDNS) multicast kilidiyle çalışır,
 * 3) NSD birkaç saniyede sonuç vermezse telefonun alt ağı 9870 portunda taranır,
 * 4) listedeki TV'ler düzenli yoklanır; iki kez yanıt vermeyen düşürülür.
 */
class TvDiscovery(context: Context, private val client: TvClient, private val known: KnownTvStore) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolveLock = Mutex() // Android 13 ve öncesi aynı anda tek çözümleme
    private val probe = TvClient(OkHttpClient.Builder().connectTimeout(900, TimeUnit.MILLISECONDS).readTimeout(1500, TimeUnit.MILLISECONDS).build())
    private val misses = ConcurrentHashMap<String, Int>()
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var job: Job? = null
    private var users = 0

    @Synchronized fun acquire() { users++; if (users == 1) start() }
    @Synchronized fun release() { users = maxOf(0, users - 1); if (users == 0) stop() }

    /** Yenile düğmesi: NSD'yi yeniden başlatır ve alt ağı her durumda tarar. */
    @Synchronized fun refresh() {
        if (users == 0) return
        stop()
        start(forceScan = true)
    }

    private fun start(forceScan: Boolean = false) {
        multicast = runCatching {
            app.getSystemService(WifiManager::class.java)?.createMulticastLock("afuremote-bulma")?.apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        startNsd()
        job = scope.launch {
            _scanning.value = true
            known.all().forEach { tv -> launch { verify(tv.host, tv.port, "") } }
            _devices.value.forEach { tv -> launch { verify(tv.host, tv.port, tv.serviceName) } }
            delay(NSD_GRACE_MS)
            if (forceScan || _devices.value.isEmpty()) scanSubnet()
            _scanning.value = false
            while (isActive) {
                delay(HEARTBEAT_MS)
                heartbeat()
            }
        }
    }

    private fun stop() {
        job?.cancel(); job = null
        _scanning.value = false
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        multicast?.let { runCatching { if (it.isHeld) it.release() } }
        multicast = null
    }

    private fun startNsd() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) { scope.launch { resolveAndVerify(service) } }
            override fun onServiceLost(service: NsdServiceInfo) {
                _devices.update { list -> list.filterNot { it.serviceName.isNotEmpty() && it.serviceName == service.serviceName } }
            }
            override fun onDiscoveryStarted(serviceType: String) { Log.i(TAG, "TV aramasi basladi") }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.e(TAG, "arama baslamadi: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.e(TAG, "NSD baslatilamadi", it); listener = null }
    }

    private suspend fun scanSubnet() {
        val lan = LocalIp.lan() ?: return
        val hosts = Subnet.hosts(lan.ip, lan.prefix)
        Log.i(TAG, "alt ag taraniyor: ${lan.ip}/${lan.prefix} (${hosts.size} adres)")
        val gate = Semaphore(SCAN_PARALLEL)
        coroutineScope {
            hosts.forEach { host ->
                launch { gate.withPermit { if (portOpen(host)) verify(host, TV_PORT, "") } }
            }
        }
    }

    private fun portOpen(host: String): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, TV_PORT), SCAN_CONNECT_MS) }
        true
    }.getOrDefault(false)

    private suspend fun heartbeat() {
        coroutineScope {
            _devices.value.forEach { tv ->
                launch {
                    val alive = probe.info(tv.host, tv.port)?.id == tv.id
                    if (alive) misses.remove(tv.id)
                    else if ((misses.merge(tv.id, 1) { a, b -> a + b } ?: 0) >= 2) {
                        misses.remove(tv.id)
                        Log.i(TAG, "TV yanit vermiyor, listeden dustu: ${tv.name}")
                        _devices.update { list -> list.filterNot { it.id == tv.id } }
                    }
                }
            }
        }
    }

    private fun verify(host: String, port: Int, serviceName: String) {
        val info = probe.info(host, port) ?: client.info(host, port) ?: return
        val device = TvDevice(info.id, info.name, info.model, host, port, serviceName)
        misses.remove(device.id)
        known.remember(device)
        _devices.update { list ->
            val old = list.firstOrNull { it.id == device.id }
            // NSD adını koru: kaybolma bildirimi doğru cihazı düşürsün.
            val merged = if (serviceName.isEmpty() && old != null) device.copy(serviceName = old.serviceName) else device
            if (old == merged) list else list.filterNot { it.id == device.id } + merged
        }
        Log.i(TAG, "TV bulundu: ${info.name} ($host)")
    }

    private suspend fun resolveAndVerify(service: NsdServiceInfo) {
        val resolved = resolveLock.withLock { resolve(service) } ?: return
        val host = pickHost(resolved) ?: return
        verify(host, resolved.port, service.serviceName)
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

    private companion object {
        const val TAG = "AfuRemotePhone"
        const val NSD_GRACE_MS = 2_500L
        const val HEARTBEAT_MS = 8_000L
        const val SCAN_PARALLEL = 48
        const val SCAN_CONNECT_MS = 350
    }
}

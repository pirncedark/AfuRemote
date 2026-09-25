package com.afudm.afuremote.phone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.afudm.afuremote.net.LocalIp
import com.afudm.afuremote.atvremote.AtvMdnsResult
import com.afudm.afuremote.net.Subnet
import com.afudm.afuremote.protocol.SERVICE_TYPE
import com.afudm.afuremote.protocol.TV_PORT
import com.afudm.afuremote.protocol.DISCOVERY_QUERY
import com.afudm.afuremote.protocol.DISCOVERY_UDP_PORT
import com.afudm.afuremote.protocol.UdpDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
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
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
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
        // Acquire before discoverServices: multicast packets can arrive immediately.
        startNsd()
        job = scope.launch {
            _scanning.value = true
            known.all().forEach { tv -> launch { verify(tv.host, tv.port, "") } }
            _devices.value.forEach { tv -> launch { verify(tv.host, tv.port, tv.serviceName) } }
            delay(NSD_GRACE_MS)
            if (forceScan || _devices.value.isEmpty()) { scanUdp(); if (_devices.value.isEmpty()) scanSubnet() }
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
        listeners.toList().forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        multicast?.let { runCatching { if (it.isHeld) it.release() } }
        multicast = null
    }

    private fun startNsd() {
        listOf(SERVICE_TYPE, "_androidtvremote2._tcp.", "_googlecast._tcp.").forEach { serviceType ->
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
        listeners += l
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.e(TAG, "NSD baslatilamadi ($serviceType)", it); listeners.remove(l) }
        }
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

    suspend fun addManually(host: String, port: Int = TV_PORT): TvDevice? = withContext(Dispatchers.IO) {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isBlank() || port !in 1..65535) return@withContext null
        val info = probe.info(clean, port) ?: client.info(clean, port) ?: return@withContext null
        val device = TvDevice(info.id, info.name, info.model, clean, port, "")
        known.remember(device)
        _devices.update { list -> list.filterNot { it.id == device.id } + device }
        device
    }

    private suspend fun scanUdp() {
        val lan = LocalIp.lan() ?: return
        val parts = lan.ip.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4) return
        val bits = lan.prefix.coerceIn(1, 30)
        val ip = parts.fold(0L) { a, b -> (a shl 8) or b.toLong() }
        val mask = (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        val broadcast = (ip and mask) or (mask.inv() and 0xFFFFFFFFL)
        val targets = listOf("255.255.255.255", listOf(24,16,8,0).joinToString(".") { ((broadcast shr it) and 255).toString() }).distinct()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val query = DISCOVERY_QUERY.toByteArray(Charsets.UTF_8)
                targets.forEach { host -> socket.send(DatagramPacket(query, query.size, InetAddress.getByName(host), DISCOVERY_UDP_PORT)) }
                val started = System.nanoTime()
                val seen = linkedSetOf<String>()
                while (!UdpDiscovery.timedOut(started, System.nanoTime(), UDP_WAIT_MS * 1_000_000)) {
                    val packet = DatagramPacket(ByteArray(512), 512)
                    try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { continue }
                    val reply = UdpDiscovery.parse(String(packet.data, packet.offset, packet.length, Charsets.UTF_8)) ?: continue
                    if (seen.add(reply.id)) verify(packet.address.hostAddress ?: continue, reply.port, "")
                }
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
        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolveLock.withLock { resolve(service) } } ?: return
        val host = pickHost(resolved) ?: return
        if (service.serviceType.contains("_androidtvremote2._tcp") || service.serviceType.contains("_googlecast._tcp")) {
            val parsed = AtvMdnsResult.parse(service.serviceName, resolved.port, service.attributes.map { (k, v) -> "$k=${String(v, Charsets.UTF_8)}" }, host)
                ?: return
            val device = TvDevice("atv:${parsed.identity}", parsed.name, parsed.name, host, 6466, service.serviceName,
                TvDevice.Backend.ATV_REMOTE_V2, parsed.bt.ifBlank { if (parsed.backend == AtvMdnsResult.Backend.GOOGLE_CAST) "Google Cast" else "Android TV" })
            _devices.update { old ->
                val found = old.firstOrNull { it.host.equals(host, true) }
                if (found == null) old + device
                else old.map { if (it.host.equals(host, true)) it.copy(name = parsed.name.ifBlank { it.name }, serviceName = listOf(it.serviceName, service.serviceName).filter { name -> name.isNotBlank() }.distinct().joinToString("+")) else it }
            }
            return
        }
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
        const val UDP_WAIT_MS = 1_200L
        const val RESOLVE_TIMEOUT_MS = 4_000L
    }
}

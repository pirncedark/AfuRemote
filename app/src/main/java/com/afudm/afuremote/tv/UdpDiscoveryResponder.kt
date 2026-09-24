package com.afudm.afuremote.tv

import android.util.Log
import com.afudm.afuremote.protocol.DISCOVERY_QUERY
import com.afudm.afuremote.protocol.DISCOVERY_UDP_PORT
import com.afudm.afuremote.protocol.DiscoveryReply
import com.afudm.afuremote.protocol.UdpDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscoveryResponder(private val name: String, private val id: String) {
    private var job: Job? = null
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            runCatching {
                DatagramSocket(DISCOVERY_UDP_PORT, InetAddress.getByName("0.0.0.0")).use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 1_000
                    val buffer = ByteArray(256)
                    while (isActive) {
                        val request = DatagramPacket(ByteArray(256), 256)
                        try { socket.receive(request) } catch (_: java.net.SocketTimeoutException) { continue }
                        if (String(request.data, request.offset, request.length, Charsets.UTF_8) != DISCOVERY_QUERY) continue
                        val bytes = UdpDiscovery.encode(DiscoveryReply(name, 9870, id)).toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(bytes, bytes.size, request.address, request.port))
                    }
                }
            }.onFailure { if (isActive) Log.e("AfuRemoteNsd", "UDP discovery failed", it) }
        }
    }
    fun stop() { job?.cancel(); job = null }
}

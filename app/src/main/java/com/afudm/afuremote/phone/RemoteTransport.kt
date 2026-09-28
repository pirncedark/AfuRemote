package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.RemoteKey

sealed interface RemoteCommand {
    data class Key(val key: RemoteKey) : RemoteCommand
    data class Text(val value: String) : RemoteCommand
    data class Launch(val app: String) : RemoteCommand
    data class Open(val request: OpenRequest) : RemoteCommand
}

/** UI-facing transport contract. Backend selection and connection details stay behind this boundary. */
interface RemoteTransport {
    val state: State
    enum class State { STOPPED, CONNECTING, READY, RECONNECTING }
    fun connect(tv: TvDevice)
    suspend fun isAlive(tv: TvDevice): Boolean
    suspend fun send(tv: TvDevice, command: RemoteCommand, pairAtv: suspend () -> Boolean, onPairing: (String) -> Unit): SendResult
    fun close()
}

class RoutingRemoteTransport(private val http: PhoneController, private val atv: com.afudm.afuremote.atvremote.AtvRemoteClient, private val probe: TvClient) : RemoteTransport {
    private var selected: String? = null
    override val state: RemoteTransport.State
        get() = when (atvState()) {
            "CONNECTING" -> RemoteTransport.State.CONNECTING
            "READY" -> RemoteTransport.State.READY
            "RECONNECTING" -> RemoteTransport.State.RECONNECTING
            else -> RemoteTransport.State.STOPPED
        }

    override fun connect(tv: TvDevice) {
        val key = "${tv.id}:${tv.host}:${tv.port}"
        if (selected != key) atv.closeSelected()
        selected = key
        if (tv.backend == TvDevice.Backend.ATV_REMOTE_V2) atv.connect(tv)
    }

    override suspend fun isAlive(tv: TvDevice): Boolean = if (tv.backend == TvDevice.Backend.ATV_REMOTE_V2) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(tv.host, tv.port), 350) }; true }.getOrDefault(false)
        }
    } else probe.info(tv.host, tv.port)?.id == tv.id

    override suspend fun send(tv: TvDevice, command: RemoteCommand, pairAtv: suspend () -> Boolean, onPairing: (String) -> Unit): SendResult {
        connect(tv)
        return if (tv.backend == TvDevice.Backend.ATV_REMOTE_V2) when (command) {
            is RemoteCommand.Key -> atv.key(tv, command.key, pairAtv)
            is RemoteCommand.Text -> atv.text(tv, command.value, pairAtv)
            is RemoteCommand.Launch -> atv.launch(tv, "market://launch?id=${command.app}", pairAtv)
            is RemoteCommand.Open -> atv.launch(tv, command.request.url, pairAtv)
        } else when (command) {
            is RemoteCommand.Key -> http.key(tv, command.key, onPairing)
            is RemoteCommand.Text -> http.text(tv, command.value, onPairing)
            is RemoteCommand.Launch -> http.launch(tv, command.app, onPairing)
            is RemoteCommand.Open -> http.open(tv, command.request, onPairing)
        }
    }

    override fun close() { selected = null; atv.closeSelected() }

    private fun atvState(): String = runCatching { atv.currentState().name }.getOrDefault("STOPPED")
}

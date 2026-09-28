package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.protocol.AtvWireImeCounters
import com.afudm.afuremote.atvremote.protocol.AtvWireProtocol
import com.afudm.afuremote.phone.SendResult
import com.afudm.afuremote.phone.TvDevice
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manages one selected TV's persistent connection; no socket work runs on the UI thread. */
class AtvRemoteClient(private val credentials: AtvCredentialStore, private val tls: AtvTlsClientFactory, private val clientName: String) : AutoCloseable {
    @Volatile private var session: AtvSession? = null
    @Volatile private var endpoint: String? = null

    /** Opens a background session immediately for an already paired device. */
    @Synchronized fun connect(tv: TvDevice) {
        val key = "${tv.host.lowercase()}:${tv.port}"
        if (endpoint == key && session != null) return
        closeSession()
        if (credentials.fingerprint(tv.host) == null) return
        endpoint = key
        session = AtvSession(tv.host, tv.port, tls).also { it.start() }
    }

    suspend fun key(tv: TvDevice, key: RemoteKey, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { counters ->
        AtvWireProtocol.keyMessage(AtvCommandMapper.keyCode(key))
    }

    suspend fun text(tv: TvDevice, value: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { counters ->
        AtvWireProtocol.imeMessage(value, counters)
    }

    suspend fun launch(tv: TvDevice, appLink: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) {
        AtvWireProtocol.launchMessage(appLink)
    }

    private suspend fun perform(tv: TvDevice, pair: suspend () -> Boolean, command: (AtvWireImeCounters) -> com.afudm.afuremote.atvremote.proto.RemoteProto.RemoteMessage): SendResult = withContext(Dispatchers.IO) {
        try {
            if (credentials.fingerprint(tv.host) == null && !pair()) return@withContext SendResult.Failed("TV ekranındaki 6 haneli kodla eşleştirme tamamlanmadı")
            synchronized(this@AtvRemoteClient) { connect(tv) }
            val active = session ?: return@withContext SendResult.Failed("Android TV bağlantısı kurulamadı")
            if (!active.send(command)) SendResult.Failed("Android TV bağlantısı kapandı; yeniden deneyin") else SendResult.Ok
        } catch (e: Exception) {
            SendResult.Failed(e.message ?: "Android TV Remote v2 bağlantısı kurulamadı")
        }
    }

    @Synchronized fun closeSelected() = closeSession()
    fun currentState(): AtvSession.State = session?.state ?: AtvSession.State.STOPPED
    @Synchronized override fun close() = closeSession()

    private fun closeSession() {
        session?.close()
        session = null
        endpoint = null
    }
}

object AtvCommandMapper {
    fun keyCode(key: RemoteKey): Int = when (key) {
        RemoteKey.DPAD_UP -> 19; RemoteKey.DPAD_DOWN -> 20; RemoteKey.DPAD_LEFT -> 21; RemoteKey.DPAD_RIGHT -> 22
        RemoteKey.DPAD_CENTER -> 23; RemoteKey.BACK -> 4; RemoteKey.HOME -> 3; RemoteKey.VOL_UP -> 24; RemoteKey.VOL_DOWN -> 25
        RemoteKey.MUTE -> 164; RemoteKey.PLAY_PAUSE -> 85; RemoteKey.SEEK_FWD -> 90; RemoteKey.SEEK_BACK -> 89; RemoteKey.POWER -> 26
    }
}

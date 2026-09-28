package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.proto.RemoteProto
import com.afudm.afuremote.phone.SendResult
import com.afudm.afuremote.phone.TvDevice
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** IME sayaçları: TV her oturumun başında kendi sayacını gönderir, istemci onu yankılar. */
data class AtvImeCounters(val ime: Int = 0, val field: Int = 0)

class AtvRemoteClient(private val credentials: AtvCredentialStore, private val tls: AtvTlsClientFactory, private val clientName: String) {
    suspend fun key(tv: TvDevice, key: RemoteKey, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { _, output ->
        val code = AtvCommandMapper.keyCode(key)
        sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteKeyInject(
            RemoteProto.RemoteKeyInject.newBuilder().setKeyCodeValue(code).setDirection(RemoteProto.RemoteDirection.SHORT)).build())
    }

    suspend fun text(tv: TvDevice, value: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { counters, output ->
        // Protokol imleci metin uzunluğu eksi bir olarak ister (bkz. androidtvremote2 send_text).
        val cursor = (value.length - 1).coerceAtLeast(0)
        val edit = RemoteProto.RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
            RemoteProto.RemoteImeObject.newBuilder().setValue(value).setStart(cursor).setEnd(cursor)).build()
        sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteImeBatchEdit(
            RemoteProto.RemoteImeBatchEdit.newBuilder().setImeCounter(counters.ime).setFieldCounter(counters.field).addEditInfo(edit)).build())
    }

    suspend fun launch(tv: TvDevice, appLink: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { _, output ->
        sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteAppLinkLaunchRequest(
            RemoteProto.RemoteAppLinkLaunchRequest.newBuilder().setAppLink(appLink)).build())
    }

    /**
     * TLS el sıkışması ve protobuf okuma/yazma bloklayıcıdır; UI dispatcher'sında
     * çalıştırılırsa NetworkOnMainThreadException ile komutlar hiç gitmez.
     */
    private suspend fun perform(
        tv: TvDevice,
        pair: suspend () -> Boolean,
        command: (AtvImeCounters, OutputStream) -> Unit
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            if (credentials.fingerprint(tv.host) == null && !pair()) {
                SendResult.Failed("TV ekranındaki 6 haneli kodla eşleştirme tamamlanmadı")
            } else {
                val (socket, _) = tls.connect(tv.host, tv.port, true)
                socket.use {
                    val input = it.inputStream
                    val output = it.outputStream
                    val counters = handshake(input, output)
                    command(counters, output)
                }
                SendResult.Ok
            }
        } catch (e: Exception) {
            SendResult.Failed(e.message ?: "Android TV Remote v2 bağlantısı kurulamadı")
        }
    }

    /** TV hazır olana kadar yapılandırma/aktivasyon/ping yanıtlarını verir. */
    private fun handshake(input: InputStream, output: OutputStream): AtvImeCounters {
        var counters = AtvImeCounters()
        var active = CAPABILITIES
        var ready = false
        for (attempt in 0 until HANDSHAKE_FRAMES) {
            val message = RemoteProto.RemoteMessage.parseFrom(readFrame(input))
            when {
                message.hasRemotePingRequest() -> sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemotePingResponse(
                    RemoteProto.RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)).build())
                message.hasRemoteConfigure() -> {
                    // TV'nin desteklediği özelliklerle kesiş, kendi maskeni olarak yankıla.
                    active = active and message.remoteConfigure.code1
                    sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteConfigure(
                        RemoteProto.RemoteConfigure.newBuilder().setCode1(active)
                            .setDeviceInfo(RemoteProto.RemoteDeviceInfo.newBuilder().setModel("AfuRemote").setVendor("AfuRemote")
                                .setUnknown1(1).setUnknown2("1")
                                .setPackageName("atvremote").setAppVersion("1.0.0"))).build())
                }
                message.hasRemoteSetActive() -> sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteSetActive(
                    RemoteProto.RemoteSetActive.newBuilder().setActive(active)).build())
                message.hasRemoteImeBatchEdit() -> counters = AtvImeCounters(
                    message.remoteImeBatchEdit.imeCounter, message.remoteImeBatchEdit.fieldCounter)
                message.hasRemoteStart() -> { ready = message.remoteStart.started; if (ready) return counters }
                message.hasRemoteError() -> error("TV komutu reddetti")
            }
        }
        check(ready) { "TV kumanda oturumu başlamadı" }
        return counters
    }

    private fun sendCommand(output: OutputStream, message: RemoteProto.RemoteMessage) { output.write(AtvFraming.frame(message.toByteArray())); output.flush() }
    private fun readFrame(input: InputStream): ByteArray {
        var length = 0; var shift = 0
        while (shift < 35) { val b = input.read(); check(b >= 0) { "TV bağlantısı kapandı" }; length = length or ((b and 0x7f) shl shift); if (b and 0x80 == 0) break; shift += 7 }
        require(length in 1..1_048_576)
        return ByteArray(length).also { bytes -> var pos = 0; while (pos < length) { val n = input.read(bytes, pos, length - pos); check(n > 0); pos += n } }
    }

    private companion object {
        const val CAPABILITIES = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 5) or (1 shl 6) or (1 shl 9)
        const val HANDSHAKE_FRAMES = 16
    }
}

object AtvCommandMapper {
    fun keyCode(key: RemoteKey): Int = when (key) {
        RemoteKey.DPAD_UP -> 19; RemoteKey.DPAD_DOWN -> 20; RemoteKey.DPAD_LEFT -> 21; RemoteKey.DPAD_RIGHT -> 22
        RemoteKey.DPAD_CENTER -> 23; RemoteKey.BACK -> 4; RemoteKey.HOME -> 3; RemoteKey.VOL_UP -> 24; RemoteKey.VOL_DOWN -> 25
        RemoteKey.MUTE -> 164; RemoteKey.PLAY_PAUSE -> 85; RemoteKey.SEEK_FWD -> 90; RemoteKey.SEEK_BACK -> 89; RemoteKey.POWER -> 26
    }
}

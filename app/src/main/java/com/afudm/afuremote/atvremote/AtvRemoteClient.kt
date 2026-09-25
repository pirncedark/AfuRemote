package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.proto.RemoteProto
import com.afudm.afuremote.phone.SendResult
import com.afudm.afuremote.phone.TvDevice
import com.afudm.afuremote.protocol.RemoteKey
import java.io.InputStream
import java.io.OutputStream

class AtvRemoteClient(private val credentials: AtvCredentialStore, private val tls: AtvTlsClientFactory, private val clientName: String) {
    suspend fun key(tv: TvDevice, key: RemoteKey, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { socket ->
        val code = when (key) {
            RemoteKey.DPAD_UP -> 19; RemoteKey.DPAD_DOWN -> 20; RemoteKey.DPAD_LEFT -> 21; RemoteKey.DPAD_RIGHT -> 22
            RemoteKey.DPAD_CENTER -> 23; RemoteKey.BACK -> 4; RemoteKey.HOME -> 3; RemoteKey.VOL_UP -> 24; RemoteKey.VOL_DOWN -> 25
            RemoteKey.MUTE -> 164; RemoteKey.PLAY_PAUSE -> 85; RemoteKey.SEEK_FWD -> 90; RemoteKey.SEEK_BACK -> 89; RemoteKey.POWER -> 26
        }
        sendCommand(socket.outputStream, RemoteProto.RemoteMessage.newBuilder().setRemoteKeyInject(
            RemoteProto.RemoteKeyInject.newBuilder().setKeyCodeValue(code).setDirection(RemoteProto.RemoteDirection.SHORT)).build())
    }

    suspend fun text(tv: TvDevice, value: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { socket ->
        val edit = RemoteProto.RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
            RemoteProto.RemoteImeObject.newBuilder().setValue(value).setStart(value.length).setEnd(value.length)).build()
        sendCommand(socket.outputStream, RemoteProto.RemoteMessage.newBuilder().setRemoteImeBatchEdit(
            RemoteProto.RemoteImeBatchEdit.newBuilder().setImeCounter(0).setFieldCounter(0).addEditInfo(edit)).build())
    }

    suspend fun launch(tv: TvDevice, appLink: String, pair: suspend () -> Boolean): SendResult = perform(tv, pair) { socket ->
        sendCommand(socket.outputStream, RemoteProto.RemoteMessage.newBuilder().setRemoteAppLinkLaunchRequest(
            RemoteProto.RemoteAppLinkLaunchRequest.newBuilder().setAppLink(appLink)).build())
    }

    private suspend fun perform(tv: TvDevice, pair: suspend () -> Boolean, command: (javax.net.ssl.SSLSocket) -> Unit): SendResult = try {
        if (credentials.fingerprint(tv.host) == null && !pair()) return SendResult.Failed("TV ekranındaki 6 haneli kodla eşleştirme tamamlanmadı")
        val (socket, _) = tls.connect(tv.host, tv.port, true)
        socket.use {
            val input = it.inputStream; val output = it.outputStream
            var ready = false
            for (attempt in 0 until 16) {
                val message = RemoteProto.RemoteMessage.parseFrom(readFrame(input))
                when {
                    message.hasRemotePingRequest() -> sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemotePingResponse(
                        RemoteProto.RemotePingResponse.newBuilder().setVal1(message.remotePingRequest.val1)).build())
                    message.hasRemoteConfigure() -> sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteConfigure(
                        RemoteProto.RemoteConfigure.newBuilder().setCode1(message.remoteConfigure.code1 and CAPABILITIES)
                            .setDeviceInfo(RemoteProto.RemoteDeviceInfo.newBuilder().setModel("AfuRemote").setVendor("AfuRemote")
                                .setPackageName("com.afudm.afuremote").setAppVersion("1"))).build())
                    message.hasRemoteSetActive() -> sendCommand(output, RemoteProto.RemoteMessage.newBuilder().setRemoteSetActive(
                        RemoteProto.RemoteSetActive.newBuilder().setActive(1)).build())
                    message.hasRemoteStart() -> { ready = message.remoteStart.started; if (ready) break }
                }
            }
            check(ready) { "TV kumanda oturumu başlamadı" }
            command(it)
        }
        SendResult.Ok
    } catch (e: Exception) { SendResult.Failed(e.message ?: "Android TV Remote v2 bağlantısı kurulamadı") }

    private fun sendCommand(output: OutputStream, message: RemoteProto.RemoteMessage) { output.write(AtvFraming.frame(message.toByteArray())); output.flush() }
    private fun readFrame(input: InputStream): ByteArray {
        var length = 0; var shift = 0
        while (shift < 35) { val b = input.read(); check(b >= 0) { "TV bağlantısı kapandı" }; length = length or ((b and 0x7f) shl shift); if (b and 0x80 == 0) break; shift += 7 }
        require(length in 1..1_048_576)
        return ByteArray(length).also { bytes -> var pos = 0; while (pos < length) { val n = input.read(bytes, pos, length - pos); check(n > 0); pos += n } }
    }

    private companion object { const val CAPABILITIES = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 5) or (1 shl 6) or (1 shl 9) }
}

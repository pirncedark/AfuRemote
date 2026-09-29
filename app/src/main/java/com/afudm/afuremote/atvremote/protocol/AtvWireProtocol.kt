package com.afudm.afuremote.atvremote.protocol

import com.afudm.afuremote.atvremote.proto.RemoteProto
import com.google.polo.wire.protobuf.PoloProto
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Pure JVM implementation of the length-delimited protobuf wire format and ATV messages. */
object AtvWireProtocol {
    fun encodeFrame(message: ByteArray): ByteArray {
        require(message.size <= MAX_FRAME_SIZE) { "Protobuf message is too large" }
        val out = ByteArrayOutputStream()
        writeVarint(out, message.size)
        out.write(message)
        return out.toByteArray()
    }

    fun decodeFrame(frame: ByteArray): ByteArray {
        val (size, offset) = readVarint(frame)
        require(size <= MAX_FRAME_SIZE) { "Protobuf message is too large" }
        require(size == frame.size - offset) { "Invalid protobuf frame length: expected $size bytes" }
        return frame.copyOfRange(offset, frame.size)
    }

    /** Reads one complete frame from a blocking stream without discarding partial reads. */
    fun readFrame(input: InputStream): ByteArray {
        var length = 0
        var shift = 0
        var count = 0
        while (count < 5) {
            val value = input.read()
            check(value >= 0) { "TV connection closed" }
            if (shift == 28) require((value and 0xf0) == 0) { "Invalid frame length" }
            length = length or ((value and 0x7f) shl shift)
            count++
            if (value and 0x80 == 0) break
            shift += 7
        }
        require(count <= 5 && length in 1..MAX_FRAME_SIZE) { "Invalid frame length" }
        val payload = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(payload, offset, length - offset)
            check(read > 0) { "Incomplete frame" }
            offset += read
        }
        return payload
    }

    fun encodeVarint(value: Int): ByteArray {
        require(value >= 0) { "Varint value must be non-negative" }
        val out = ByteArrayOutputStream()
        writeVarint(out, value)
        return out.toByteArray()
    }

    fun readVarint(bytes: ByteArray, offset: Int = 0): Pair<Int, Int> {
        require(offset in 0..bytes.size) { "Invalid varint offset" }
        var result = 0
        var shift = 0
        var index = offset
        while (index < bytes.size && shift <= 28) {
            val b = bytes[index++].toInt() and 0xff
            if (shift == 28) require((b and 0xf0) == 0) { "Varint overflows 32 bits" }
            result = result or ((b and 0x7f) shl shift)
            if ((b and 0x80) == 0) return result to index
            shift += 7
        }
        throw IllegalArgumentException(if (index == bytes.size) "Incomplete varint" else "Invalid varint")
    }

    fun pairingSecret(clientModulus: ByteArray, clientExponent: ByteArray, serverModulus: ByteArray, serverExponent: ByteArray, nonce: ByteArray): ByteArray {
        // Six hexadecimal code characters: two for the digest prefix and four (2 bytes) for nonce.
        require(nonce.size == 2) { "Android TV pairing nonce must be 2 bytes" }
        return MessageDigest.getInstance("SHA-256").digest(clientModulus + clientExponent + serverModulus + serverExponent + nonce)
    }

    fun pairingCodeMatches(code: String, secret: ByteArray): Boolean {
        if (!code.matches(Regex("[0-9a-fA-F]{6}")) || secret.isEmpty()) return false
        return (secret[0].toInt() and 0xff) == code.substring(0, 2).toInt(16)
    }

    fun keyMessage(keyCode: Int): RemoteProto.RemoteMessage = RemoteProto.RemoteMessage.newBuilder()
        .setRemoteKeyInject(RemoteProto.RemoteKeyInject.newBuilder().setKeyCodeValue(keyCode).setDirection(RemoteProto.RemoteDirection.SHORT)).build()

    fun imeMessage(value: String, counters: AtvWireImeCounters): RemoteProto.RemoteMessage {
        val cursor = (value.length - 1).coerceAtLeast(0)
        val edit = RemoteProto.RemoteEditInfo.newBuilder().setInsert(1).setTextFieldStatus(
            RemoteProto.RemoteImeObject.newBuilder().setValue(value).setStart(cursor).setEnd(cursor)).build()
        return RemoteProto.RemoteMessage.newBuilder().setRemoteImeBatchEdit(
            RemoteProto.RemoteImeBatchEdit.newBuilder().setImeCounter(counters.ime).setFieldCounter(counters.field).addEditInfo(edit)).build()
    }

    fun launchMessage(appLink: String): RemoteProto.RemoteMessage = RemoteProto.RemoteMessage.newBuilder()
        .setRemoteAppLinkLaunchRequest(RemoteProto.RemoteAppLinkLaunchRequest.newBuilder().setAppLink(appLink)).build()

    fun pairingOuter(): PoloProto.OuterMessage.Builder = PoloProto.OuterMessage.newBuilder()
        .setProtocolVersion(2).setStatus(PoloProto.OuterMessage.Status.STATUS_OK)

    private fun writeVarint(out: ByteArrayOutputStream, input: Int) {
        var value = input
        while (value >= 0x80) { out.write((value and 0x7f) or 0x80); value = value ushr 7 }
        out.write(value)
    }

    const val MAX_FRAME_SIZE = 1_048_576
}

data class AtvWireImeCounters(val ime: Int = 0, val field: Int = 0)

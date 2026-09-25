package com.afudm.afuremote.atvremote

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Length-delimited protobuf frames used by the Android TV Remote v2 TLS streams. */
object AtvFraming {
    fun frame(message: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var size = message.size
        while (size >= 0x80) { out.write((size and 0x7f) or 0x80); size = size ushr 7 }
        out.write(size)
        out.write(message)
        return out.toByteArray()
    }

    fun unframe(frame: ByteArray): ByteArray {
        var size = 0
        var shift = 0
        var index = 0
        while (index < frame.size && shift < 35) {
            val b = frame[index++].toInt() and 0xff
            size = size or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0) {
                require(size == frame.size - index) { "Invalid protobuf frame length" }
                return frame.copyOfRange(index, frame.size)
            }
            shift += 7
        }
        throw IllegalArgumentException("Invalid protobuf frame")
    }
}

object AtvPairingSecret {
    fun calculate(clientModulus: ByteArray, clientExponent: ByteArray, serverModulus: ByteArray, serverExponent: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == 4)
        return MessageDigest.getInstance("SHA-256").digest(clientModulus + clientExponent + serverModulus + serverExponent + nonce)
    }

    fun matches(code: String, secret: ByteArray): Boolean {
        if (!code.matches(Regex("[0-9a-fA-F]{6}"))) return false
        val prefix = code.substring(0, 2).toInt(16).toByte()
        return secret.isNotEmpty() && secret[0] == prefix
    }
}

data class AtvMdnsResult(val serviceName: String, val name: String, val host: String, val port: Int, val backend: Backend) {
    enum class Backend { AFUREMOTE, ATV_REMOTE_V2, GOOGLE_CAST }
    val identity: String get() = host.lowercase()

    companion object {
        fun parse(serviceName: String, port: Int, txt: List<String>, host: String, type: String = serviceName.substringAfterLast('.', "")): AtvMdnsResult? {
            if (host.isBlank() || port !in 1..65535) return null
            val normalized = serviceName.trim().trimEnd('.')
            val backend = when {
                normalized.contains("_androidtvremote2._tcp") -> Backend.ATV_REMOTE_V2
                normalized.contains("_googlecast._tcp") -> Backend.GOOGLE_CAST
                normalized.contains("_afuremote._tcp") -> Backend.AFUREMOTE
                else -> return null
            }
            val fn = txt.firstNotNullOfOrNull { it.takeIf { value -> value.startsWith("fn=") }?.substringAfter('=') }
            val instance = normalized.substringBefore("._")
            val name = fn?.takeIf(String::isNotBlank) ?: instance.ifBlank { host }
            return AtvMdnsResult(normalized, name, host, port, backend)
        }

        fun merge(results: List<AtvMdnsResult>): List<AtvMdnsResult> = results
            .groupBy { it.identity }
            .values.map { same ->
                val preferred = same.firstOrNull { it.backend == Backend.ATV_REMOTE_V2 } ?: same.first()
                val named = same.firstOrNull { it.name.isNotBlank() && it.name != it.host } ?: preferred
                preferred.copy(name = named.name, serviceName = same.joinToString("+") { it.serviceName })
            }
    }
}

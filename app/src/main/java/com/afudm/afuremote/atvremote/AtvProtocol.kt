package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.protocol.AtvWireProtocol

/** Length-delimited protobuf frames used by the Android TV Remote v2 TLS streams. */
object AtvFraming {
    fun frame(message: ByteArray): ByteArray = AtvWireProtocol.encodeFrame(message)
    fun unframe(frame: ByteArray): ByteArray = AtvWireProtocol.decodeFrame(frame)
}

object AtvPairingSecret {
    fun calculate(clientModulus: ByteArray, clientExponent: ByteArray, serverModulus: ByteArray, serverExponent: ByteArray, nonce: ByteArray): ByteArray {
        return AtvWireProtocol.pairingSecret(clientModulus, clientExponent, serverModulus, serverExponent, nonce)
    }

    fun matches(code: String, secret: ByteArray): Boolean {
        return AtvWireProtocol.pairingCodeMatches(code, secret)
    }
}

data class AtvMdnsResult(val serviceName: String, val name: String, val host: String, val port: Int, val backend: Backend, val bt: String = "") {
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
            val bt = txt.firstNotNullOfOrNull { it.takeIf { value -> value.startsWith("bt=") }?.substringAfter('=') }.orEmpty()
            val instance = normalized.substringBefore("._")
            val name = (if (backend == Backend.ATV_REMOTE_V2) instance else fn?.takeIf(String::isNotBlank) ?: instance).ifBlank { host }
            return AtvMdnsResult(normalized, name, host, port, backend, bt)
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

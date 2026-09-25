package com.afudm.afuremote.atvremote

import com.google.polo.wire.protobuf.PoloProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey

class AtvPairingManager(private val credentials: AtvCredentialStore, private val tls: AtvTlsClientFactory) {
    suspend fun pair(host: String, port: Int, clientName: String, askCode: suspend () -> String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (socket, fingerprint) = tls.connect(host, 6467, false)
            socket.use {
                val input = it.inputStream; val output = it.outputStream
                send(output, outer().setPairingRequest(PoloProto.PairingRequest.newBuilder().setServiceName("atvremote").setClientName(clientName).build()).build())
                check(read(input).hasPairingRequestAck()) { "TV eşleştirmeyi başlatmadı" }
                val encoding = PoloProto.Options.Encoding.newBuilder().setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL).setSymbolLength(6).build()
                send(output, outer().setOptions(PoloProto.Options.newBuilder().addInputEncodings(encoding).setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT).build()).build())
                read(input)
                send(output, outer().setConfiguration(PoloProto.Configuration.newBuilder().setEncoding(encoding).setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT).build()).build())
                check(read(input).hasConfigurationAck()) { "TV yapılandırma yanıtı vermedi" }

                val code = askCode().trim().replace(" ", "").uppercase()
                require(code.matches(Regex("[0-9A-F]{6}"))) { "Kod 6 haneli hexadecimal olmalı" }
                val clientKey = credentials.ensureCertificate().publicKey as RSAPublicKey
                val serverKey = it.session.peerCertificates.first().publicKey as RSAPublicKey
                val nonce = code.substring(2).chunked(2).map { part -> part.toInt(16).toByte() }.toByteArray()
                val secret = AtvPairingSecret.calculate(unsigned(clientKey.modulus), unsigned(clientKey.publicExponent), unsigned(serverKey.modulus), unsigned(serverKey.publicExponent), nonce)
                check(AtvPairingSecret.matches(code, secret)) { "Kod TV ekranındaki kodla eşleşmedi" }
                send(output, outer().setSecret(PoloProto.Secret.newBuilder().setSecret(com.google.protobuf.ByteString.copyFrom(secret)).build()).build())
                val ack = read(input)
                check(ack.hasSecretAck() && ack.secretAck.secret == com.google.protobuf.ByteString.copyFrom(secret)) { "TV eşleştirmeyi kabul etmedi" }
                credentials.saveFingerprint(host, fingerprint)
            }
        }.isSuccess
    }

    private fun unsigned(value: BigInteger): ByteArray = value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
    private fun outer() = PoloProto.OuterMessage.newBuilder().setProtocolVersion(2).setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
    private fun send(output: OutputStream, message: PoloProto.OuterMessage) { output.write(AtvFraming.frame(message.toByteArray())); output.flush() }
    private fun read(input: InputStream): PoloProto.OuterMessage = PoloProto.OuterMessage.parseFrom(readFrame(input))
    private fun readFrame(input: InputStream): ByteArray {
        var length = 0; var shift = 0
        while (shift < 35) {
            val byte = input.read(); check(byte >= 0) { "TV bağlantısı kapandı" }
            length = length or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        require(length in 1..1_048_576) { "Geçersiz protobuf çerçevesi" }
        return ByteArray(length).also { bytes -> var offset = 0; while (offset < length) { val n = input.read(bytes, offset, length - offset); check(n > 0) { "TV bağlantısı eksik veri gönderdi" }; offset += n } }
    }
}

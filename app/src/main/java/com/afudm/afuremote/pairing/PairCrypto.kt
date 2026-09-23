package com.afudm.afuremote.pairing

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pure JVM cryptographic primitives shared by phone and TV. */
object PairCrypto {
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    fun encodePublic(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun decodePublic(value: String): PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(java.util.Base64.getDecoder().decode(value)))

    fun sharedKey(pair: KeyPair, peer: PublicKey, phonePub: ByteArray, tvPub: ByteArray, deviceId: String): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(pair.private)
        agreement.doPhase(peer, true)
        val secret = agreement.generateSecret()
        val salt = sha256(phonePub + tvPub)
        return hkdf(secret, salt, "afuremote-auth-v1".toByteArray(UTF_8) + deviceId.toByteArray(UTF_8), 32)
    }

    fun pairingCode(phonePub: ByteArray, tvPub: ByteArray, deviceId: String): String {
        val data = "afuremote-pair-v1".toByteArray(UTF_8) + phonePub + tvPub + deviceId.toByteArray(UTF_8)
        val n = ByteBuffer.wrap(sha256(data), 0, 4).int.toLong() and 0xffffffffL
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    fun sign(key: ByteArray, method: String, path: String, time: String, nonce: String, body: ByteArray): String {
        val payload = "$method\n$path\n$time\n$nonce\n${hex(sha256(body))}".toByteArray(UTF_8)
        return hex(hmac(key, payload))
    }

    fun secureEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun randomNonce(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(::hex)

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArray(size)
        var previous = byteArrayOf()
        var offset = 0
        var counter = 1
        while (offset < size) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, size - offset)
            previous.copyInto(out, offset, 0, n)
            offset += n
            counter++
        }
        return out
    }
}

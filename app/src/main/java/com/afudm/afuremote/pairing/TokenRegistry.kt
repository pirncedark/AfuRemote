package com.afudm.afuremote.pairing

/** TV-side allowlist: one derived auth key per approved device, plus a bounded replay cache. */
class TokenRegistry(private val keys: MutableMap<String, String> = mutableMapOf()) {
    private val nonces = mutableMapOf<String, LinkedHashMap<String, Long>>()

    @Synchronized fun issue(deviceId: String, key: ByteArray) {
        keys[deviceId] = PairCrypto.hex(key)
    }

    @Synchronized fun keyFor(deviceId: String): ByteArray? = keys[deviceId]?.let { hex ->
        runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
    }

    /** False when [nonce] was already used by [deviceId] in the last two minutes. */
    @Synchronized fun acceptNonce(deviceId: String, nonce: String, now: Long): Boolean {
        val cache = nonces.getOrPut(deviceId) { LinkedHashMap() }
        cache.entries.removeAll { now - it.value > NONCE_TTL_MS }
        if (nonce in cache) return false
        cache[nonce] = now
        while (cache.size > MAX_NONCES) cache.remove(cache.keys.first())
        return true
    }

    @Synchronized fun snapshot(): Map<String, String> = keys.toMap()

    @Synchronized fun clear() {
        keys.clear()
        nonces.clear()
    }

    companion object {
        private const val NONCE_TTL_MS = 120_000L
        private const val MAX_NONCES = 256

        fun randomToken(): String = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }.let(PairCrypto::hex)
    }
}

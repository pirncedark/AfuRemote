package com.afudm.afuremote.pairing

/** TV-side device key allowlist and bounded replay cache. */
class TokenRegistry(private val keys: MutableMap<String, String> = mutableMapOf()) {
    private val nonces = mutableMapOf<String, LinkedHashMap<String, Long>>()
    @Synchronized fun issue(deviceId: String, key: ByteArray) { keys[deviceId] = PairCrypto.hex(key) }
    @Synchronized fun keyFor(deviceId: String): ByteArray? = keys[deviceId]?.let { runCatching { it.chunked(2).map { b -> b.toInt(16).toByte() }.toByteArray() }.getOrNull() }
    @Synchronized fun acceptNonce(deviceId: String, nonce: String, now: Long): Boolean {
        val cache = nonces.getOrPut(deviceId) { LinkedHashMap() }
        cache.entries.removeAll { now - it.value > 120_000 }
        if (nonce in cache) return false
        cache[nonce] = now
        while (cache.size > 256) cache.remove(cache.keys.first())
        return true
    }
    @Synchronized fun snapshot(): Map<String, String> = keys.toMap()
    @Synchronized fun clear() { keys.clear(); nonces.clear() }
    companion object {
        fun randomToken(): String = java.security.SecureRandom().let { r -> ByteArray(24).also(r::nextBytes).let(PairCrypto::hex) }
    }
}

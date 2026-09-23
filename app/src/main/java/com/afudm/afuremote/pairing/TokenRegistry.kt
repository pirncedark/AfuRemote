package com.afudm.afuremote.pairing

import java.security.SecureRandom

/** TV-side allowlist: one token per approved device. */
class TokenRegistry(
    private val tokens: MutableMap<String, String> = mutableMapOf(),
    private val newToken: () -> String = { randomToken() }
) {
    @Synchronized fun issue(deviceId: String): String = newToken().also { tokens[deviceId] = it }

    @Synchronized fun isValid(token: String?): Boolean = !token.isNullOrBlank() && token in tokens.values

    @Synchronized fun snapshot(): Map<String, String> = tokens.toMap()

    @Synchronized fun clear() = tokens.clear()

    companion object {
        private val random = SecureRandom()

        fun randomToken(): String {
            val bytes = ByteArray(24)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

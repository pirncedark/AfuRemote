package com.afudm.afuremote.pairing

import android.content.Context
import com.afudm.afuremote.phone.TokenStore
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class PairingStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("afuremote_pairing", Context.MODE_PRIVATE)

    fun approvedDevices(): Map<String, String> = read(KEY_APPROVED)
    fun tvRegistry(): TokenRegistry = TokenRegistry(approvedDevices().toMutableMap())
    fun saveApproved(map: Map<String, String>) = write(KEY_APPROVED, map)

    override fun tokenForTv(tvId: String): String? = read(KEY_TV_TOKENS)[tvId]
    override fun saveTokenForTv(tvId: String, token: String) = write(KEY_TV_TOKENS, read(KEY_TV_TOKENS) + (tvId to token))
    override fun forgetTv(tvId: String) = write(KEY_TV_TOKENS, read(KEY_TV_TOKENS) - tvId)

    @Synchronized
    override fun deviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: TokenRegistry.randomToken().take(16).also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    private fun read(key: String): Map<String, String> =
        prefs.getString(key, null)?.let { runCatching { ProtocolJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()

    private fun write(key: String, map: Map<String, String>) {
        prefs.edit().putString(key, ProtocolJson.encodeToString(map)).apply()
    }

    private companion object {
        const val KEY_APPROVED = "approved"
        const val KEY_TV_TOKENS = "tv_tokens"
        const val KEY_DEVICE_ID = "device_id"
    }
}

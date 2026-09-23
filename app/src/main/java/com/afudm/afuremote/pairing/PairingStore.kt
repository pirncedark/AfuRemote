package com.afudm.afuremote.pairing

import android.content.Context
import com.afudm.afuremote.phone.TokenStore
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class PairingStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("afuremote_pairing", Context.MODE_PRIVATE)

    fun approvedDevices(): Map<String, String> = read(KEY_DEVICE_KEYS)
    fun tvRegistry(): TokenRegistry = TokenRegistry(approvedDevices().toMutableMap())
    fun saveApproved(map: Map<String, String>) = write(KEY_DEVICE_KEYS, map)

    override fun keyForTv(tvId: String): String? = read(KEY_TV_KEYS)[tvId]
    override fun saveKeyForTv(tvId: String, key: String) = write(KEY_TV_KEYS, read(KEY_TV_KEYS) + (tvId to key))
    override fun forgetTv(tvId: String) = write(KEY_TV_KEYS, read(KEY_TV_KEYS) - tvId)

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
        const val KEY_DEVICE_KEYS = "device_auth_keys_v1"
        const val KEY_TV_KEYS = "tv_auth_keys_v1"
        const val KEY_DEVICE_ID = "device_id"
    }
}

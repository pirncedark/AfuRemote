package com.afudm.afuremote.phone

/** Phone-side shared authentication key per TV and this device's stable identity. */
interface TokenStore {
    fun keyForTv(tvId: String): String?
    fun saveKeyForTv(tvId: String, key: String)
    fun forgetTv(tvId: String)
    fun deviceId(): String
}

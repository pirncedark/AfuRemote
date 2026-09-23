package com.afudm.afuremote.phone

/** Phone-side token per TV and this device's stable identity. */
interface TokenStore {
    fun tokenForTv(tvId: String): String?
    fun saveTokenForTv(tvId: String, token: String)
    fun forgetTv(tvId: String)
    fun deviceId(): String
}

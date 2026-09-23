package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Token yoksa ya da TV token'ı tanımıyorsa (TV sıfırlandı) kendiliğinden yeniden eşleşir. */
class PhoneController(private val api: TvApi, private val store: TokenStore, private val deviceName: String) {
    suspend fun open(tv: TvDevice, req: OpenRequest, onPairing: () -> Unit): SendResult =
        withAuth(tv, onPairing) { api.open(tv, it, req) }

    suspend fun key(tv: TvDevice, key: RemoteKey, onPairing: () -> Unit): SendResult =
        withAuth(tv, onPairing) { api.key(tv, it, key) }

    private suspend fun withAuth(tv: TvDevice, onPairing: () -> Unit, call: (String) -> SendResult): SendResult =
        withContext(Dispatchers.IO) {
            val token = store.tokenForTv(tv.id) ?: pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            val first = call(token)
            if (first != SendResult.Unauthorized) return@withContext first
            store.forgetTv(tv.id)
            val fresh = pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            call(fresh)
        }

    private fun pairNow(tv: TvDevice, onPairing: () -> Unit): String? {
        onPairing()
        val token = api.pair(tv, PairRequest(deviceName, store.deviceId())) ?: return null
        store.saveTokenForTv(tv.id, token)
        return token
    }
}

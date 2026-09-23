package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.LaunchRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.TextRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** TV anahtarı yoksa veya TV tanımıyorsa yeniden eşleştirir. */
class PhoneController(private val api: TvApi, private val store: TokenStore, private val deviceName: String) {
    suspend fun open(tv: TvDevice, req: OpenRequest, onPairing: (String) -> Unit): SendResult =
        withAuth(tv, onPairing) { api.open(tv, it, req) }

    suspend fun key(tv: TvDevice, key: RemoteKey, onPairing: (String) -> Unit): SendResult =
        withAuth(tv, onPairing) { api.key(tv, it, key) }

    suspend fun launch(tv: TvDevice, pkg: String, onPairing: (String) -> Unit): SendResult =
        withAuth(tv, onPairing) { api.command(tv, it, "/v1/launch", ProtocolJson.encodeToString(LaunchRequest(pkg))) }

    suspend fun text(tv: TvDevice, text: String, onPairing: (String) -> Unit): SendResult =
        withAuth(tv, onPairing) { api.command(tv, it, "/v1/text", ProtocolJson.encodeToString(TextRequest(text))) }

    private suspend fun withAuth(tv: TvDevice, onPairing: (String) -> Unit, call: (String) -> SendResult): SendResult =
        withContext(Dispatchers.IO) {
            val key = store.keyForTv(tv.id) ?: pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            val first = call(key)
            if (first != SendResult.Unauthorized) return@withContext first
            store.forgetTv(tv.id)
            val fresh = pairNow(tv, onPairing) ?: return@withContext SendResult.Failed(Messages.DENIED)
            call(fresh)
        }

    private fun pairNow(tv: TvDevice, onPairing: (String) -> Unit): String? {
        onPairing(Messages.WAITING_TV)
        val key = api.pair(tv, PairStartRequest(store.deviceId(), deviceName, "")) { code -> onPairing(Messages.pairingCode(code)) } ?: return null
        store.saveKeyForTv(tv.id, key)
        return key
    }
}

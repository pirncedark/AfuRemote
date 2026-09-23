package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneControllerTest {
    private val tv = TvDevice("tv-1", "Salon", "X", "192.168.1.20", 9870, "AfuRemote Salon")

    private class MemoryStore : TokenStore {
        val keys = mutableMapOf<String, String>()
        override fun keyForTv(tvId: String) = keys[tvId]
        override fun saveKeyForTv(tvId: String, key: String) { keys[tvId] = key }
        override fun forgetTv(tvId: String) { keys.remove(tvId) }
        override fun deviceId() = "tel-1"
    }

    private class FakeApi(var valid: String = "key", var answer: String? = "key") : TvApi {
        var pairs = 0
        val used = mutableListOf<String>()
        var nextResult: SendResult? = null
        override fun pair(tv: TvDevice, req: PairStartRequest, onCode: (String) -> Unit): String? {
            pairs++
            onCode("123 456")
            return answer
        }
        override fun open(tv: TvDevice, authKey: String, req: OpenRequest) = check(authKey)
        override fun key(tv: TvDevice, authKey: String, key: RemoteKey) = check(authKey)
        private fun check(token: String): SendResult {
            used += token
            return nextResult ?: if (token == valid) SendResult.Ok else SendResult.Unauthorized
        }
    }

    @Test
    fun `first command pairs once and reuses key`() = runBlocking {
        val store = MemoryStore()
        val api = FakeApi()
        var code = ""
        val controller = PhoneController(api, store, "Phone")
        assertEquals(SendResult.Ok, controller.key(tv, RemoteKey.VOL_UP) { code = it })
        assertEquals(SendResult.Ok, controller.key(tv, RemoteKey.VOL_UP) {})
        assertEquals(1, api.pairs)
        assertEquals(Messages.pairingCode("123 456"), code)
        assertEquals("key", store.keys["tv-1"])
    }

    @Test
    fun `permission failure on stored key triggers re-pair`() = runBlocking {
        val store = MemoryStore().apply { keys["tv-1"] = "old" }
        val api = FakeApi()
        val controller = PhoneController(api, store, "Phone")
        assertEquals(SendResult.Ok, controller.open(tv, OpenRequest("https://x/a.mp4")) {})
        assertEquals(listOf("old", "key"), api.used)
        assertEquals("key", store.keys["tv-1"])
    }

    @Test
    fun `clock failure does not forget stored key or start pairing`() = runBlocking {
        val store = MemoryStore().apply { keys["tv-1"] = "retained" }
        val api = FakeApi().apply { nextResult = SendResult.Failed(Messages.CLOCK) }
        val controller = PhoneController(api, store, "Phone")
        assertEquals(SendResult.Failed(Messages.CLOCK), controller.key(tv, RemoteKey.MUTE) {})
        assertEquals("retained", store.keys["tv-1"])
        assertEquals(0, api.pairs)
    }

    @Test
    fun `denied pairing stores nothing`() = runBlocking {
        val store = MemoryStore()
        val controller = PhoneController(FakeApi(answer = null), store, "Phone")
        assertEquals(SendResult.Failed(Messages.DENIED), controller.key(tv, RemoteKey.MUTE) {})
        assertEquals(emptyMap<String, String>(), store.keys)
    }
}

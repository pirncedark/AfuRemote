package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneControllerTest {
    private val tv = TvDevice("tv-1", "Salon", "X", "192.168.1.20", 9870, "AfuRemote Salon")
    private class MemoryStore : TokenStore {
        val tokens = mutableMapOf<String, String>()
        override fun tokenForTv(tvId: String) = tokens[tvId]
        override fun saveTokenForTv(tvId: String, token: String) { tokens[tvId] = token }
        override fun forgetTv(tvId: String) { tokens.remove(tvId) }
        override fun deviceId() = "tel-1"
    }
    private class FakeApi(var validToken: String = "yeni", var pairAnswer: String? = "yeni") : TvApi {
        var pairCalls = 0
        val sentWith = mutableListOf<String>()
        override fun pair(tv: TvDevice, req: PairRequest): String? { pairCalls++; return pairAnswer }
        override fun open(tv: TvDevice, token: String, req: OpenRequest) = check(token)
        override fun key(tv: TvDevice, token: String, key: RemoteKey) = check(token)
        private fun check(token: String): SendResult { sentWith += token; return if (token == validToken) SendResult.Ok else SendResult.Unauthorized }
    }
    @Test fun `first command pairs once and reuses token`() = runBlocking {
        val store = MemoryStore(); val api = FakeApi(); var prompts = 0
        val controller = PhoneController(api, store, "Telefonum")
        assertEquals(SendResult.Ok, controller.key(tv, RemoteKey.VOL_UP) { prompts++ })
        assertEquals(SendResult.Ok, controller.key(tv, RemoteKey.VOL_UP) { prompts++ })
        assertEquals(1, api.pairCalls); assertEquals(1, prompts); assertEquals("yeni", store.tokens["tv-1"])
    }
    @Test fun `stale token after TV reset pairs again`() = runBlocking {
        val store = MemoryStore().apply { tokens["tv-1"] = "eski" }; val api = FakeApi()
        val controller = PhoneController(api, store, "Telefonum")
        assertEquals(SendResult.Ok, controller.open(tv, OpenRequest("https://x/a.mp4")) {})
        assertEquals(listOf("eski", "yeni"), api.sentWith); assertEquals("yeni", store.tokens["tv-1"])
    }
    @Test fun `denied pairing gives clear message and stores nothing`() = runBlocking {
        val store = MemoryStore(); val controller = PhoneController(FakeApi(pairAnswer = null), store, "Telefonum")
        assertEquals(SendResult.Failed(Messages.DENIED), controller.key(tv, RemoteKey.MUTE) {})
        assertEquals(emptyMap<String, String>(), store.tokens)
    }
}

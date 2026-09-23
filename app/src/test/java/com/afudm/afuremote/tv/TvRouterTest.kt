package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRouterTest {
    private class FakeActions(var decision: PairDecision = PairDecision.APPROVED, var keyResult: ApiResult = ApiResult(true)) : TvActions {
        val opened = mutableListOf<ClassifiedLink>()
        val keys = mutableListOf<RemoteKey>()
        override fun info() = InfoResponse("tv-1", "Salon", "X", "0.1.0")
        override suspend fun askPairApproval(req: PairRequest) = decision
        override fun open(link: ClassifiedLink, title: String): ApiResult { opened += link; return ApiResult(true) }
        override fun key(key: RemoteKey): ApiResult { keys += key; return keyResult }
    }

    private val actions = FakeActions()
    private var saved: Map<String, String> = emptyMap()
    private val registry = TokenRegistry()
    private val router = TvRouter(actions, registry) { saved = it }

    private fun call(method: String, path: String, token: String? = null, body: String = "") =
        runBlocking { router.handle(method, path, token, body) }

    private fun pair(): String {
        val r = call("POST", "/v1/pair", body = """{"deviceName":"Tel","deviceId":"d1"}""")
        assertEquals(200, r.status)
        return ProtocolJson.decodeFromString<PairResponse>(r.body).token
    }

    @Test
    fun `info needs no token`() {
        val r = call("GET", "/v1/info")
        assertEquals(200, r.status)
        assertTrue(r.body.contains("\"protocol\":\"v1\""))
    }

    @Test
    fun `commands without a valid token are rejected`() {
        assertEquals(401, call("POST", "/v1/key", null, """{"key":"vol_up"}""").status)
        assertEquals(401, call("POST", "/v1/key", "yanlis", """{"key":"vol_up"}""").status)
        assertTrue(actions.keys.isEmpty())
    }

    @Test
    fun `approved pairing issues a working token and persists it`() {
        val token = pair()
        assertEquals(mapOf("d1" to token), saved)
        assertEquals(200, call("POST", "/v1/key", token, """{"key":"vol_up"}""").status)
        assertEquals(listOf(RemoteKey.VOL_UP), actions.keys)
    }

    @Test
    fun `denied pairing is 403 and prompt impossible is 409`() {
        actions.decision = PairDecision.DENIED
        assertEquals(403, call("POST", "/v1/pair", body = """{"deviceName":"T","deviceId":"d"}""").status)
        actions.decision = PairDecision.CANNOT_PROMPT
        val r = call("POST", "/v1/pair", body = """{"deviceName":"T","deviceId":"d"}""")
        assertEquals(409, r.status)
        assertTrue(r.body.contains(HATA_ERISILEBILIRLIK))
    }

    @Test
    fun `pair requests with invalid identity are rejected before prompting`() {
        assertEquals(400, call("POST", "/v1/pair", body = """{"deviceName":"  ","deviceId":"d1"}""").status)
        assertEquals(400, call("POST", "/v1/pair", body = """{"deviceName":"Tel","deviceId":"bad/id"}""").status)
    }

    @Test
    fun `open classifies the link and forceMedia overrides it`() {
        val token = pair()
        assertEquals(200, call("POST", "/v1/open", token, """{"url":"https://x.com/a.mp4"}""").status)
        assertEquals(200, call("POST", "/v1/open", token, """{"url":"http://10.0.0.5:9871/m/abc","forceMedia":true}""").status)
        assertEquals(listOf(LinkKind.MEDIA, LinkKind.MEDIA), actions.opened.map { it.kind })
    }

    @Test
    fun `bad input never crashes the server`() {
        val token = pair()
        assertEquals(400, call("POST", "/v1/open", token, """{"url":"ftp://x"}""").status)
        assertEquals(400, call("POST", "/v1/open", token, "").status)
        assertEquals(400, call("POST", "/v1/open", token, "{bozuk").status)
        assertEquals(400, call("POST", "/v1/key", token, """{"key":"uc"}""").status)
        assertEquals(400, call("POST", "/v1/pair", body = """{"deviceName":"x"}""").status)
        assertEquals(404, call("GET", "/v1/open", token).status)
        assertEquals(404, call("POST", "/baska", token, "{}").status)
    }

    @Test
    fun `accessibility off maps to 409`() {
        val token = pair()
        actions.keyResult = ApiResult(false, HATA_ERISILEBILIRLIK)
        assertEquals(409, call("POST", "/v1/key", token, """{"key":"back"}""").status)
    }
}

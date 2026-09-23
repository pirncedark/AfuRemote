package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.PairStartResponse
import com.afudm.afuremote.pairing.PairCrypto
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRouterTest {
    private class FakeActions(var decision: PairDecision = PairDecision.APPROVED, var keyResult: ApiResult = ApiResult(true), var promptAvailable: Boolean = true) : TvActions {
        val opened = mutableListOf<ClassifiedLink>()
        val keys = mutableListOf<RemoteKey>()
        override fun info() = InfoResponse("tv-1", "Salon", "X", "0.1.0")
        override fun startPairPrompt(pairId: String, req: PairStartRequest, code: String) = promptAvailable
        override suspend fun awaitPairApproval(pairId: String) = decision
        override fun open(link: ClassifiedLink, title: String): ApiResult { opened += link; return ApiResult(true) }
        override fun key(key: RemoteKey): ApiResult { keys += key; return keyResult }
    }

    private val actions = FakeActions()
    private var saved: Map<String, String> = emptyMap()
    private val registry = TokenRegistry()
    private val router = TvRouter(actions, registry, onPairingsChanged = { saved = it })

    private fun call(method: String, path: String, body: String = "", headers: Map<String,String> = emptyMap()) =
        runBlocking { router.handle(method, path, headers, body) }

    private val kp = PairCrypto.newKeyPair()
    private var sharedKey: ByteArray? = null
    private fun pair(): String {
        val phonePub=java.util.Base64.getEncoder().encodeToString(kp.public.encoded)
        val r=call("POST","/v1/pair/start", """{"deviceId":"d1","deviceName":"Tel","phonePub":"$phonePub"}""")
        assertEquals(200,r.status)
        val started=ProtocolJson.decodeFromString<PairStartResponse>(r.body)
        val tvPub=java.util.Base64.getDecoder().decode(started.tvPub)
        sharedKey=PairCrypto.sharedKey(kp,PairCrypto.decodePublic(started.tvPub),kp.public.encoded,tvPub,"d1")
        val c=call("POST","/v1/pair/confirm","""{"pairId":"${started.pairId}"}""")
        assertEquals(200,c.status)
        return ""
    }
    private fun signed(path:String,body:String,nonce:String=PairCrypto.randomNonce(),time:Long=System.currentTimeMillis(),sigOverride:String?=null):Map<String,String>{val ts=time.toString();return mapOf("x-afu-device" to "d1","x-afu-time" to ts,"x-afu-nonce" to nonce,"x-afu-sig" to (sigOverride?:PairCrypto.sign(sharedKey!!,"POST",path,ts,nonce,body.toByteArray())))}

    @Test
    fun `info needs no token`() {
        val r = call("GET", "/v1/info")
        assertEquals(200, r.status)
        assertTrue(r.body.contains("\"protocol\":\"v1\""))
    }

    @Test
    fun `commands without a valid token are rejected`() {
        assertEquals(401, call("POST", "/v1/key", """{"key":"vol_up"}""").status)
        assertEquals(401, call("POST", "/v1/key", """{"key":"vol_up"}""", mapOf("x-afu-device" to "missing")).status)
        assertTrue(actions.keys.isEmpty())
    }

    @Test
    fun `approved pairing issues a working token and persists it`() {
        pair()
        assertEquals(true, saved.containsKey("d1"))
        val body="""{"key":"vol_up"}"""
        assertEquals(200, call("POST", "/v1/key", body, signed("/v1/key",body)).status)
        assertEquals(listOf(RemoteKey.VOL_UP), actions.keys)
    }

    @Test fun `bad signature stale time replay and altered body all return unauthorized`() {
        pair()
        val body="""{"key":"vol_up"}"""
        val n=PairCrypto.randomNonce(); val headers=signed("/v1/key",body,nonce=n)
        assertEquals(401,call("POST","/v1/key",body,headers + ("x-afu-sig" to "00")).status)
        assertEquals(401,call("POST","/v1/key","""{"key":"mute"}""",headers).status)
        val stale = call("POST", "/v1/key", body, signed("/v1/key", body, time = System.currentTimeMillis() - 61_000))
        assertEquals(401, stale.status)
        val clockError = ProtocolJson.decodeFromString<ApiResult>(stale.body)
        assertEquals("saat_farki", clockError.hata)
        assertTrue(clockError.saat > 0)
        assertEquals(200,call("POST","/v1/key",body,headers).status)
        assertEquals(401,call("POST","/v1/key",body,headers).status)
    }

    @Test
    fun `denied pairing is 403 and prompt impossible is 409`() {
        actions.decision = PairDecision.DENIED
        val req="""{"deviceId":"d","deviceName":"T","phonePub":"${java.util.Base64.getEncoder().encodeToString(kp.public.encoded)}"}"""
        val started=call("POST","/v1/pair/start",req)
        actions.decision=PairDecision.DENIED
        assertEquals(403,call("POST","/v1/pair/confirm","""{"pairId":"${ProtocolJson.decodeFromString<PairStartResponse>(started.body).pairId}"}""").status)
        actions.promptAvailable = false
        val r = call("POST", "/v1/pair/start", """{"deviceId":"d","deviceName":"T","phonePub":"${java.util.Base64.getEncoder().encodeToString(kp.public.encoded)}"}""")
        assertEquals(409, r.status)
        assertTrue(r.body.contains(HATA_ERISILEBILIRLIK))
    }

    @Test
    fun `pair requests with invalid identity are rejected before prompting`() {
        assertEquals(400, call("POST", "/v1/pair/start", """{"deviceId":"d1","deviceName":"  ","phonePub":"x"}""").status)
        assertEquals(400, call("POST", "/v1/pair/start", """{"deviceId":"bad/id","deviceName":"Tel","phonePub":"x"}""").status)
    }

    @Test
    fun `open classifies the link and forceMedia overrides it`() {
        pair()
        val a="""{"url":"https://x.com/a.mp4"}"""; val b="""{"url":"http://10.0.0.5:9871/m/abc","forceMedia":true}"""
        assertEquals(200, call("POST", "/v1/open", a, signed("/v1/open",a)).status)
        assertEquals(200, call("POST", "/v1/open", b, signed("/v1/open",b)).status)
        assertEquals(listOf(LinkKind.MEDIA, LinkKind.MEDIA), actions.opened.map { it.kind })
    }

    @Test
    fun `bad input never crashes the server`() {
        pair()
        assertEquals(404, call("POST", "/v1/pair", "{}" ).status)
        assertEquals(404, call("GET", "/v1/open").status)
        assertEquals(404, call("POST", "/baska", "{}").status)
    }

    @Test
    fun `accessibility off maps to 409`() {
        pair()
        actions.keyResult = ApiResult(false, HATA_ERISILEBILIRLIK)
        val body="""{"key":"back"}"""
        assertEquals(409, call("POST", "/v1/key", body, signed("/v1/key",body)).status)
    }
}

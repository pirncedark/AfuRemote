package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.PairCrypto
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.DEVICE_HEADER
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.LaunchRequest
import com.afudm.afuremote.protocol.TextRequest
import com.afudm.afuremote.protocol.NONCE_HEADER
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairConfirmRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.PairStartResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.protocol.SIGNATURE_HEADER
import com.afudm.afuremote.protocol.TIME_HEADER
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.KeyPair
import java.util.Base64
import java.util.UUID

enum class PairDecision { APPROVED, DENIED, CANNOT_PROMPT }

interface TvActions {
    fun info(): InfoResponse
    fun startPairPrompt(pairId: String, req: PairStartRequest, code: String): Boolean
    suspend fun awaitPairApproval(pairId: String): PairDecision
    fun open(link: ClassifiedLink, title: String): ApiResult
    fun key(key: RemoteKey): ApiResult
    fun launch(pkg: String): ApiResult = ApiResult(false, "desteklenmiyor")
    fun text(text: String): ApiResult = ApiResult(false, "desteklenmiyor")
}

/** İmzalı istek isteyen uçlar. */
private val SIGNED_PATHS = setOf("/v1/open", "/v1/key", "/v1/launch", "/v1/text")
private val PACKAGE_RE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

data class RouterResponse(val status: Int, val body: String)

class TvRouter(
    private val actions: TvActions,
    private val registry: TokenRegistry,
    private val onPairingsChanged: (Map<String, String>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Pending(val req: PairStartRequest, val pair: KeyPair, val tvPub: ByteArray, val expires: Long)
    private val pending = mutableMapOf<String, Pending>()

    suspend fun handle(method: String, path: String, headers: Map<String, String>, body: String): RouterResponse = try {
        route(method, path, headers, body)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IllegalArgumentException) {
        error(400, "bozuk_istek")
    } catch (_: SerializationException) {
        error(400, "bozuk_istek")
    }

    private suspend fun route(method: String, path: String, headers: Map<String, String>, body: String): RouterResponse {
        fun header(name: String): String? = headers[name.lowercase()]
        if (method == "GET" && path == "/v1/info") return ok(ProtocolJson.encodeToString(actions.info()))
        if (method == "POST" && path == "/v1/pair/start") return start(ProtocolJson.decodeFromString(body))
        if (method == "POST" && path == "/v1/pair/confirm") return confirm(ProtocolJson.decodeFromString(body))
        if (method != "POST" || path !in SIGNED_PATHS) return error(404, "yok")

        val device = header(DEVICE_HEADER).orEmpty()
        val time = header(TIME_HEADER)?.toLongOrNull() ?: return error(401, "izin_yok")
        val nonce = header(NONCE_HEADER).orEmpty()
        val sig = header(SIGNATURE_HEADER).orEmpty()
        val key = registry.keyFor(device) ?: return error(401, "izin_yok")
        val current = now()
        if (time < current - 60_000 || time > current + 60_000) return error(401, "saat_farki", current)
        if (!nonce.matches(Regex("[0-9a-f]{32}"))) return error(401, "izin_yok")
        val expected = PairCrypto.sign(key, method, path, time.toString(), nonce, body.toByteArray(Charsets.UTF_8))
        if (!PairCrypto.secureEquals(expected.toByteArray(), sig.toByteArray()) || !registry.acceptNonce(device, nonce, current)) {
            return error(401, "izin_yok")
        }
        return when (path) {
            "/v1/open" -> open(ProtocolJson.decodeFromString(body))
            "/v1/launch" -> launch(ProtocolJson.decodeFromString(body))
            "/v1/text" -> text(ProtocolJson.decodeFromString(body))
            else -> key(ProtocolJson.decodeFromString(body))
        }
    }

    @Synchronized
    private fun start(req: PairStartRequest): RouterResponse {
        pending.entries.removeAll { it.value.expires <= now() }
        if (!req.deviceId.matches(Regex("[A-Za-z0-9._:-]{1,64}")) || req.deviceName.trim().length !in 1..48) return error(400, "bozuk_istek")
        if (pending.values.any { it.expires > now() }) return error(409, "eslesme_bekliyor")
        val phoneBytes = runCatching { Base64.getDecoder().decode(req.phonePub) }.getOrNull() ?: return error(400, "bozuk_istek")
        val phoneKey = runCatching { PairCrypto.decodePublic(req.phonePub) }.getOrNull() ?: return error(400, "bozuk_istek")
        if (!phoneKey.encoded.contentEquals(phoneBytes)) return error(400, "bozuk_istek")
        val pair = PairCrypto.newKeyPair()
        val tvBytes = pair.public.encoded
        val pairId = UUID.randomUUID().toString()
        val code = PairCrypto.pairingCode(phoneBytes, tvBytes, req.deviceId)
        if (!actions.startPairPrompt(pairId, req, code)) return error(409, HATA_ERISILEBILIRLIK)
        pending[pairId] = Pending(req, pair, tvBytes, now() + 60_000)
        return ok(ProtocolJson.encodeToString(PairStartResponse(pairId, PairCrypto.encodePublic(pair.public), now())))
    }

    private suspend fun confirm(req: PairConfirmRequest): RouterResponse {
        val p = synchronized(this) { pending[req.pairId]?.takeIf { it.expires > now() } ?: return error(404, "bilinmeyen_eslesme") }
        val decision = withTimeoutOrNull((p.expires - now()).coerceAtLeast(1)) { actions.awaitPairApproval(req.pairId) } ?: PairDecision.DENIED
        synchronized(this) { pending.remove(req.pairId) }
        return when (decision) {
            PairDecision.APPROVED -> {
                val phoneBytes = Base64.getDecoder().decode(p.req.phonePub)
                val key = PairCrypto.sharedKey(p.pair, PairCrypto.decodePublic(p.req.phonePub), phoneBytes, p.tvPub, p.req.deviceId)
                registry.issue(p.req.deviceId, key)
                onPairingsChanged(registry.snapshot())
                ok("{\"ok\":true}")
            }
            PairDecision.DENIED -> error(403, "reddedildi")
            PairDecision.CANNOT_PROMPT -> error(409, HATA_ERISILEBILIRLIK)
        }
    }

    private fun open(req: OpenRequest): RouterResponse {
        val link = LinkClassifier.classify(req.url) ?: return error(400, "gecersiz_link")
        return result(actions.open(if (req.forceMedia) link.copy(kind = LinkKind.MEDIA) else link, req.title))
    }

    private fun key(req: KeyRequest): RouterResponse {
        val key = RemoteKey.fromWire(req.key) ?: return error(400, "bilinmeyen_tus")
        return result(actions.key(key))
    }

    private fun launch(req: LaunchRequest): RouterResponse {
        if (req.pkg.length > 128 || !req.pkg.matches(PACKAGE_RE)) return error(400, "bozuk_istek")
        return result(actions.launch(req.pkg))
    }

    private fun text(req: TextRequest): RouterResponse {
        if (req.text.length > 500) return error(400, "bozuk_istek")
        return result(actions.text(req.text))
    }

    private fun result(r: ApiResult) = RouterResponse(if (r.ok) 200 else if (r.hata == HATA_ERISILEBILIRLIK) 409 else 500, ProtocolJson.encodeToString(r))
    private fun ok(body: String) = RouterResponse(200, body)
    private fun error(status: Int, hata: String, saat: Long = 0) = RouterResponse(status, ProtocolJson.encodeToString(ApiResult(false, hata, saat)))
}

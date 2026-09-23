package com.afudm.afuremote.tv

import com.afudm.afuremote.classify.ClassifiedLink
import com.afudm.afuremote.classify.LinkClassifier
import com.afudm.afuremote.classify.LinkKind
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.ApiResult
import com.afudm.afuremote.protocol.HATA_ERISILEBILIRLIK
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

enum class PairDecision { APPROVED, DENIED, CANNOT_PROMPT }

interface TvActions {
    fun info(): InfoResponse
    suspend fun askPairApproval(req: PairRequest): PairDecision
    fun open(link: ClassifiedLink, title: String): ApiResult
    fun key(key: RemoteKey): ApiResult
}

data class RouterResponse(val status: Int, val body: String)

class TvRouter(
    private val actions: TvActions,
    private val registry: TokenRegistry,
    private val onPairingsChanged: (Map<String, String>) -> Unit
) {
    suspend fun handle(method: String, path: String, token: String?, body: String): RouterResponse =
        try {
            route(method, path, token, body)
        } catch (e: IllegalArgumentException) {
            error(400, "bozuk_istek")
        }

    private suspend fun route(method: String, path: String, token: String?, body: String): RouterResponse {
        if (method == "GET" && path == "/v1/info") return ok(ProtocolJson.encodeToString(actions.info()))
        if (method == "POST" && path == "/v1/pair") return pair(ProtocolJson.decodeFromString<PairRequest>(body))
        if (method != "POST" || (path != "/v1/open" && path != "/v1/key")) return error(404, "yok")
        if (!registry.isValid(token)) return error(401, "izin_yok")
        return if (path == "/v1/open") open(ProtocolJson.decodeFromString<OpenRequest>(body))
        else key(ProtocolJson.decodeFromString<KeyRequest>(body))
    }

    private suspend fun pair(req: PairRequest): RouterResponse = when (actions.askPairApproval(req)) {
        PairDecision.APPROVED -> {
            val issuedToken = registry.issue(req.deviceId)
            onPairingsChanged(registry.snapshot())
            ok(ProtocolJson.encodeToString(PairResponse(issuedToken)))
        }
        PairDecision.DENIED -> error(403, "reddedildi")
        PairDecision.CANNOT_PROMPT -> error(409, HATA_ERISILEBILIRLIK)
    }

    private fun open(req: OpenRequest): RouterResponse {
        val link = LinkClassifier.classify(req.url) ?: return error(400, "gecersiz_link")
        val target = if (req.forceMedia) link.copy(kind = LinkKind.MEDIA) else link
        return result(actions.open(target, req.title))
    }

    private fun key(req: KeyRequest): RouterResponse {
        val remoteKey = RemoteKey.fromWire(req.key) ?: return error(400, "bilinmeyen_tus")
        return result(actions.key(remoteKey))
    }

    private fun result(result: ApiResult) = RouterResponse(
        when {
            result.ok -> 200
            result.hata == HATA_ERISILEBILIRLIK -> 409
            else -> 500
        },
        ProtocolJson.encodeToString(result)
    )

    private fun ok(json: String) = RouterResponse(200, json)
    private fun error(status: Int, hata: String) = RouterResponse(status, ProtocolJson.encodeToString(ApiResult(false, hata)))
}

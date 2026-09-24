package com.afudm.afuremote.phone

import com.afudm.afuremote.pairing.PairCrypto
import com.afudm.afuremote.protocol.DEVICE_HEADER
import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.NONCE_HEADER
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairConfirmRequest
import com.afudm.afuremote.protocol.PairStartRequest
import com.afudm.afuremote.protocol.PairStartResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.protocol.SIGNATURE_HEADER
import com.afudm.afuremote.protocol.TIME_HEADER
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.ApiResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TvClient(
    private val http: OkHttpClient = defaultHttp(),
    private val deviceIdProvider: () -> String = { "" }
) : TvApi {
    private val pairHttp = http.newBuilder().readTimeout(70, TimeUnit.SECONDS).build()
    private val timeOffsets = ConcurrentHashMap<String, Long>()

    fun info(host: String, port: Int): InfoResponse? = try {
        http.newCall(Request.Builder().url("http://${TvDevice.hostForUrl(host)}:$port/v1/info").build()).execute().use {
            if (!it.isSuccessful) null else ProtocolJson.decodeFromString(it.body?.string().orEmpty())
        }
    } catch (_: Exception) {
        null
    }

    override fun pair(tv: TvDevice, req: PairStartRequest, onCode: (String) -> Unit): String? = try {
        val kp = PairCrypto.newKeyPair()
        val phonePub = kp.public.encoded
        val startReq = req.copy(phonePub = Base64.getEncoder().encodeToString(phonePub))
        val startBody = ProtocolJson.encodeToString(startReq)
        val start = pairHttp.newCall(post(tv, "/v1/pair/start", startBody)).execute().use {
            if (it.code != 200) return null
            ProtocolJson.decodeFromString<PairStartResponse>(it.body?.string().orEmpty())
        }
        timeOffsets[tv.id] = start.tvTime - System.currentTimeMillis()
        val tvPub = Base64.getDecoder().decode(start.tvPub)
        val code = PairCrypto.pairingCode(phonePub, tvPub, req.deviceId)
        onCode(code.chunked(3).joinToString(" "))
        val key = PairCrypto.sharedKey(kp, PairCrypto.decodePublic(start.tvPub), phonePub, tvPub, req.deviceId)
        val confirm = ProtocolJson.encodeToString(PairConfirmRequest(start.pairId))
        val approved = pairHttp.newCall(post(tv, "/v1/pair/confirm", confirm)).execute().use { it.code == 200 }
        if (approved) PairCrypto.hex(key) else null
    } catch (_: Exception) {
        null
    }

    override fun open(tv: TvDevice, authKey: String, req: OpenRequest) = send(tv, authKey, "/v1/open", ProtocolJson.encodeToString(req))

    override fun key(tv: TvDevice, authKey: String, key: RemoteKey) = send(tv, authKey, "/v1/key", ProtocolJson.encodeToString(KeyRequest(key.wire)))

    override fun command(tv: TvDevice, authKey: String, path: String, json: String) = send(tv, authKey, path, json)

    private fun send(tv: TvDevice, keyHex: String, path: String, json: String): SendResult = try {
        val key = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val first = execute(tv, key, path, json)
        val result = if (first.error?.hata == "saat_farki") {
            timeOffsets[tv.id] = first.error.saat - System.currentTimeMillis()
            execute(tv, key, path, json)
        } else {
            first
        }
        when {
            result.code == 200 -> SendResult.Ok
            result.code == 401 && result.error?.hata == "saat_farki" -> SendResult.Failed(Messages.CLOCK)
            result.code == 401 -> SendResult.Unauthorized
            else -> SendResult.Failed(Messages.forError(result.code, result.body))
        }
    } catch (_: IOException) {
        SendResult.Failed(Messages.UNREACHABLE)
    }

    private class Reply(val code: Int, val body: String) {
        val error: ApiResult? = runCatching { ProtocolJson.decodeFromString<ApiResult>(body) }.getOrNull()
    }

    private fun execute(tv: TvDevice, key: ByteArray, path: String, json: String): Reply =
        http.newCall(signed(tv, key, path, json)).execute().use { Reply(it.code, it.body?.string().orEmpty()) }

    private fun signed(tv: TvDevice, key: ByteArray, path: String, json: String): Request {
        val time = (System.currentTimeMillis() + (timeOffsets[tv.id] ?: 0L)).toString()
        val nonce = PairCrypto.randomNonce()
        val bytes = json.toByteArray(Charsets.UTF_8)
        val signature = PairCrypto.sign(key, "POST", path, time, nonce, bytes)
        return Request.Builder()
            .url(tv.url(path))
            .post(bytes.toRequestBody(JSON))
            .header(DEVICE_HEADER, deviceIdFor(tv))
            .header(TIME_HEADER, time)
            .header(NONCE_HEADER, nonce)
            .header(SIGNATURE_HEADER, signature)
            .build()
    }

    private fun deviceIdFor(tv: TvDevice): String = deviceIdProvider()

    private fun post(tv: TvDevice, path: String, json: String) = Request.Builder()
        .url(tv.url(path))
        .post(json.toRequestBody(JSON))
        .build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultHttp() = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    }
}

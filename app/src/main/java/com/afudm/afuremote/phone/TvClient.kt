package com.afudm.afuremote.phone

import com.afudm.afuremote.protocol.InfoResponse
import com.afudm.afuremote.protocol.KeyRequest
import com.afudm.afuremote.protocol.OpenRequest
import com.afudm.afuremote.protocol.PairRequest
import com.afudm.afuremote.protocol.PairResponse
import com.afudm.afuremote.protocol.ProtocolJson
import com.afudm.afuremote.protocol.RemoteKey
import com.afudm.afuremote.protocol.TOKEN_HEADER
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TvClient(private val http: OkHttpClient = defaultHttp()) : TvApi {
    private val pairHttp = http.newBuilder().readTimeout(70, TimeUnit.SECONDS).build()

    fun info(host: String, port: Int): InfoResponse? = try {
        http.newCall(Request.Builder().url("http://${TvDevice.hostForUrl(host)}:$port/v1/info").build()).execute().use { r ->
            if (!r.isSuccessful) null else ProtocolJson.decodeFromString<InfoResponse>(r.body?.string().orEmpty())
        }
    } catch (e: IOException) { null } catch (e: IllegalArgumentException) { null }

    override fun pair(tv: TvDevice, req: PairRequest): String? = try {
        pairHttp.newCall(post(tv, "/v1/pair", ProtocolJson.encodeToString(req), null)).execute().use { r ->
            if (r.code != 200) null else ProtocolJson.decodeFromString<PairResponse>(r.body?.string().orEmpty()).token
        }
    } catch (e: IOException) { null } catch (e: IllegalArgumentException) { null }

    override fun open(tv: TvDevice, token: String, req: OpenRequest): SendResult =
        send(tv, token, "/v1/open", ProtocolJson.encodeToString(req))

    override fun key(tv: TvDevice, token: String, key: RemoteKey): SendResult =
        send(tv, token, "/v1/key", ProtocolJson.encodeToString(KeyRequest(key.wire)))

    private fun send(tv: TvDevice, token: String, path: String, json: String): SendResult = try {
        http.newCall(post(tv, path, json, token)).execute().use { r ->
            when (r.code) {
                200 -> SendResult.Ok
                401 -> SendResult.Unauthorized
                else -> SendResult.Failed(Messages.forError(r.code, r.body?.string()))
            }
        }
    } catch (e: IOException) {
        SendResult.Failed(Messages.UNREACHABLE)
    }

    private fun post(tv: TvDevice, path: String, json: String, token: String?): Request =
        Request.Builder().url(tv.url(path)).post(json.toRequestBody(JSON))
            .apply { if (token != null) header(TOKEN_HEADER, token) }
            .build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}

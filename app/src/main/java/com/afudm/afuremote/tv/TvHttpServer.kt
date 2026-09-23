package com.afudm.afuremote.tv

import com.afudm.afuremote.protocol.TOKEN_HEADER
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

class TvHttpServer(private val router: TvRouter) : NanoHTTPD(PORT) {
    override fun serve(session: IHTTPSession): Response {
        val body = if (session.method == Method.POST) runCatching {
            val files = HashMap<String, String>()
            session.parseBody(files)
            files["postData"].orEmpty().take(MAX_BODY + 1).also {
                if (it.toByteArray().size > MAX_BODY) throw IllegalArgumentException("body too large")
            }
            }.getOrElse { return newFixedLengthResponse(Response.Status.BAD_REQUEST, JSON, "{\"ok\":false,\"hata\":\"bozuk_istek\"}") } else ""
        val token = session.headers[TOKEN_HEADER.lowercase()]
        val routed = runCatching { runBlocking { router.handle(session.method.name, session.uri, token, body) } }
            .getOrElse { return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, JSON, "{\"ok\":false,\"hata\":\"sunucu_hatasi\"}") }
        val status = when (routed.status) {
            200 -> Response.Status.OK
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN
            404 -> Response.Status.NOT_FOUND
            409 -> Response.Status.CONFLICT
            else -> Response.Status.INTERNAL_ERROR
        }
        return newFixedLengthResponse(status, JSON, routed.body)
    }

    companion object { private const val PORT = 9870; private const val MAX_BODY = 64 * 1024; private const val JSON = "application/json" }
}

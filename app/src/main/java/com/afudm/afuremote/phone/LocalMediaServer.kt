package com.afudm.afuremote.phone

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.afudm.afuremote.pairing.TokenRegistry
import com.afudm.afuremote.protocol.PHONE_MEDIA_PORT
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

/** Paylaşılan TEK videoyu tahmin edilemez bir yolda, Range destekli sunar. */
class LocalMediaServer(private val context: Context) : NanoHTTPD(PHONE_MEDIA_PORT) {
    private data class Shared(val uri: Uri, val secret: String, val size: Long, val mime: String)

    @Volatile private var shared: Shared? = null

    fun publish(uri: Uri): String? {
        val size = sizeOf(uri)
        if (size <= 0) return null
        val secret = TokenRegistry.randomToken().take(32)
        shared = Shared(uri, secret, size, context.contentResolver.getType(uri) ?: "video/mp4")
        return "/m/$secret"
    }

    override fun serve(session: IHTTPSession): Response {
        val s = shared ?: return notFound()
        if (session.uri != "/m/${s.secret}") return notFound()
        val header = session.headers["range"]
        val range = RangeParser.parse(header, s.size)
        if (header != null && range == null) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .apply { addHeader("Content-Range", "bytes */${s.size}") }
        }
        val input = runCatching { context.contentResolver.openInputStream(s.uri) }.getOrNull() ?: return notFound()
        return if (range == null) {
            newFixedLengthResponse(Response.Status.OK, s.mime, input, s.size).apply { addHeader("Accept-Ranges", "bytes") }
        } else {
            skipFully(input, range.start)
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, s.mime, input, range.length).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/${s.size}")
            }
        }
    }

    private fun sizeOf(uri: Uri): Long {
        val fromFd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
        if (fromFd != null && fromFd > 0) return fromFd
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) left -= skipped else if (input.read() < 0) return else left -= 1
        }
    }

    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
}

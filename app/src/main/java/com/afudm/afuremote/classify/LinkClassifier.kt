package com.afudm.afuremote.classify

import java.net.URI

enum class LinkKind { MEDIA, YOUTUBE, WEB }

data class ClassifiedLink(val kind: LinkKind, val url: String, val youtubeId: String? = null)

object LinkClassifier {
    private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "m3u8", "mpd", "mov", "m4v", "ts")
    private val URL_IN_TEXT = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{6,}$")

    fun extractUrl(sharedText: String): String? =
        URL_IN_TEXT.find(sharedText)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun classify(url: String): ClassifiedLink? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        youtubeId(host, uri)?.let { return ClassifiedLink(LinkKind.YOUTUBE, trimmed, it) }
        val extension = uri.path.orEmpty().substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return ClassifiedLink(if (extension in MEDIA_EXTENSIONS) LinkKind.MEDIA else LinkKind.WEB, trimmed)
    }

    private fun youtubeId(host: String, uri: URI): String? {
        val path = uri.path.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.trim('/').substringBefore('/')
            "youtube.com", "music.youtube.com" -> when {
                path == "/watch" -> queryParam(uri, "v")
                path.startsWith("/shorts/") || path.startsWith("/live/") || path.startsWith("/embed/") -> path.split('/').getOrNull(2)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { YOUTUBE_ID.matches(it) }
    }

    private fun queryParam(uri: URI, key: String): String? =
        uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == key }?.getOrNull(1)
}

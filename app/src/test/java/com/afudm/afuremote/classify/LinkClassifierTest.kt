package com.afudm.afuremote.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkClassifierTest {
    @Test
    fun `media extensions go to the player`() {
        for (u in listOf("https://x.com/a.mp4", "http://x.com/v/film.MKV?token=1", "https://x.com/live/index.m3u8", "https://x.com/d/manifest.mpd", "https://x.com/a.webm")) {
            assertEquals(u, LinkKind.MEDIA, LinkClassifier.classify(u)?.kind)
        }
    }

    @Test
    fun `youtube variants yield the video id`() {
        val cases = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc" to "dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtube.com/shorts/abcDEF12345" to "abcDEF12345",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ" to "dQw4w9WgXcQ"
        )
        for ((url, id) in cases) {
            val c = LinkClassifier.classify(url)
            assertEquals(url, LinkKind.YOUTUBE, c?.kind)
            assertEquals(url, id, c?.youtubeId)
        }
    }

    @Test
    fun `youtube pages without a video are plain web`() {
        assertEquals(LinkKind.WEB, LinkClassifier.classify("https://www.youtube.com/@kanal")?.kind)
    }

    @Test
    fun `other sites are web and non http is rejected`() {
        assertEquals(LinkKind.WEB, LinkClassifier.classify("https://www.trt.net.tr/canli")?.kind)
        assertNull(LinkClassifier.classify("ftp://x/a.mp4"))
        assertNull(LinkClassifier.classify("   "))
        assertNull(LinkClassifier.classify("javascript:alert(1)"))
    }

    @Test
    fun `url is extracted from shared text with punctuation around it`() {
        assertEquals("https://x.com/a.mp4", LinkClassifier.extractUrl("Bunu izle: https://x.com/a.mp4."))
        assertEquals("https://youtu.be/abc123XYZ", LinkClassifier.extractUrl("(https://youtu.be/abc123XYZ)"))
        assertEquals("https://x.com/a?b=1", LinkClassifier.extractUrl("\"https://x.com/a?b=1\", dedi"))
        assertNull(LinkClassifier.extractUrl("link yok burada"))
    }
}

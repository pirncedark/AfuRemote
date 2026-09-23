package com.afudm.afuremote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateParserTest {
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false, assets: Boolean = true) = """
        {"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,"body":"notlar $tag","assets":[
          ${if (assets) """{"name":"AfuRemote-universal.apk","browser_download_url":"https://x/$tag.apk"},
          {"name":"AfuRemote-universal.apk.sha256","browser_download_url":"https://x/$tag.sha"}""" else ""}
        ]}
    """.trimIndent()

    @Test
    fun `newest stable release wins`() {
        val json = "[${release("afuremote-v0.2.0")},${release("afuremote-v0.10.0")},${release("afuremote-v0.3.0")}]"
        val u = UpdateParser.latest(json, AppVersion.code("0.1.0"))
        assertEquals("0.10.0", u?.versionName)
        assertEquals("https://x/afuremote-v0.10.0.apk", u?.apkUrl)
        assertEquals("https://x/afuremote-v0.10.0.sha", u?.checksumUrl)
    }

    @Test
    fun `prerelease draft foreign tags and assetless releases are skipped`() {
        val json = "[${release("afuremote-v9.0.0", prerelease = true)},${release("afuremote-v8.0.0", draft = true)}," +
            "${release("v7.0.0")},${release("afuremote-v6.0.0", assets = false)},${release("afuremote-v0.2.0")}]"
        assertEquals("0.2.0", UpdateParser.latest(json, AppVersion.code("0.1.0"))?.versionName)
    }

    @Test
    fun `same or older version is not an update`() {
        val json = "[${release("afuremote-v0.2.0")}]"
        assertNull(UpdateParser.latest(json, AppVersion.code("0.2.0")))
        assertNull(UpdateParser.latest(json, AppVersion.code("1.0.0")))
    }

    @Test
    fun `version codes order numerically`() {
        assertEquals(1_002_003, AppVersion.code("1.2.3"))
        assertEquals(1_002_003, AppVersion.code("1.2.3-test"))
        assertTrue(AppVersion.code("0.10.0") > AppVersion.code("0.9.9"))
    }

    @Test
    fun `sha256 verify accepts sha256sum output and rejects mismatch`() {
        val f = File.createTempFile("afuremote", ".apk").apply { writeText("AfuRemote") }
        val hex = MessageDigest.getInstance("SHA-256").digest("AfuRemote".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(Sha256.verify(f, "$hex  AfuRemote-universal.apk\n"))
        assertFalse(Sha256.verify(f, "0".repeat(64)))
        f.delete()
    }
}

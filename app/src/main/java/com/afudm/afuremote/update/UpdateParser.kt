package com.afudm.afuremote.update

import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest

data class AppUpdate(val versionName: String, val versionCode: Int, val releaseNotes: String, val apkUrl: String, val checksumUrl: String)

object AppVersion {
    fun code(versionName: String): Int {
        val parts = versionName.substringBefore('-').split('.')
        fun part(i: Int) = parts.getOrNull(i)?.toIntOrNull() ?: 0
        return part(0) * 1_000_000 + part(1) * 1_000 + part(2)
    }
}

object UpdateParser {
    const val APK = "AfuRemote-universal.apk"
    private val TAG = Regex("""^afuremote-v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$""")

    fun latest(json: String, currentVersionCode: Int): AppUpdate? =
        ProtocolJson.parseToJsonElement(json).jsonArray
            .mapNotNull { runCatching { toUpdate(it.jsonObject) }.getOrNull() }
            .maxByOrNull { it.versionCode }
            ?.takeIf { it.versionCode > currentVersionCode }

    private fun toUpdate(o: JsonObject): AppUpdate? {
        if (o["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
        if (o["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val version = TAG.matchEntire(o["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty())?.groupValues?.get(1) ?: return null
        val assets = o["assets"]?.jsonArray.orEmpty().associate { a ->
            val ao = a.jsonObject
            ao["name"]?.jsonPrimitive?.contentOrNull.orEmpty() to ao["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        val apk = assets[APK].orEmpty()
        val sha = assets["$APK.sha256"].orEmpty()
        if (apk.isBlank() || sha.isBlank()) return null
        return AppUpdate(version, AppVersion.code(version), o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(), apk, sha)
    }
}

object ReleaseNotes {
    /** Turkish part of a bilingual release body, as plain text for the update dialog. */
    fun forDisplay(body: String): String {
        val turkish = body.substringAfter("## Türkçe", missingDelimiterValue = body)
        return turkish.lines()
            .map { it.trim() }
            .filter { it != "---" }
            .map { it.trimStart('#', ' ').replace("**", "").replace("`", "") }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

object Sha256 {
    fun verify(file: File, expectedText: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedText.trim().split(Regex("\\s+")).firstOrNull().orEmpty(), ignoreCase = true)
    }
}

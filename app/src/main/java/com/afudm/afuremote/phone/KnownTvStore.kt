package com.afudm.afuremote.phone

import android.content.Context
import com.afudm.afuremote.protocol.ProtocolJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class KnownTv(val id: String, val name: String, val model: String, val host: String, val port: Int)

/** Daha önce bulunan TV'ler: açılışta NSD beklenmeden doğrudan denenir. */
class KnownTvStore(context: Context) {
    private val prefs = context.getSharedPreferences("afuremote_known_tvs", Context.MODE_PRIVATE)

    @Synchronized fun all(): List<KnownTv> =
        prefs.getString(KEY_LIST, null)?.let { runCatching { ProtocolJson.decodeFromString<List<KnownTv>>(it) }.getOrNull() }.orEmpty()

    @Synchronized fun remember(tv: TvDevice) {
        val list = listOf(KnownTv(tv.id, tv.name, tv.model, tv.host, tv.port)) + all().filterNot { it.id == tv.id }
        prefs.edit().putString(KEY_LIST, ProtocolJson.encodeToString(list.take(MAX))).apply()
    }

    var lastSelected: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) { prefs.edit().putString(KEY_SELECTED, value).apply() }

    private companion object {
        const val KEY_LIST = "known_v1"
        const val KEY_SELECTED = "selected_v1"
        const val MAX = 8
    }
}

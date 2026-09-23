package com.afudm.afuremote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = "v1"
const val TV_PORT = 9870
const val PHONE_MEDIA_PORT = 9871
const val SERVICE_TYPE = "_afuremote._tcp"
const val TOKEN_HEADER = "X-Afu-Token"
const val HATA_ERISILEBILIRLIK = "erisilebilirlik_kapali"

@Serializable
data class InfoResponse(val id: String, val name: String, val model: String, val version: String, val protocol: String = PROTOCOL_VERSION)

@Serializable
data class PairRequest(val deviceName: String, val deviceId: String)

@Serializable
data class PairResponse(val token: String)

@Serializable
data class OpenRequest(val url: String, val title: String = "", val forceMedia: Boolean = false)

@Serializable
data class KeyRequest(val key: String)

@Serializable
data class ApiResult(val ok: Boolean, val hata: String = "")

enum class RemoteKey(val wire: String) {
    BACK("back"), HOME("home"), VOL_UP("vol_up"), VOL_DOWN("vol_down"), MUTE("mute"),
    PLAY_PAUSE("play_pause"), SEEK_FWD("seek_fwd"), SEEK_BACK("seek_back");

    companion object {
        fun fromWire(value: String): RemoteKey? = entries.firstOrNull { it.wire == value }
    }
}

val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

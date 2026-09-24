package com.afudm.afuremote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = "v1"
const val TV_PORT = 9870
const val PHONE_MEDIA_PORT = 9871
const val SERVICE_TYPE = "_afuremote._tcp"
const val DEVICE_HEADER = "X-Afu-Device"
const val TIME_HEADER = "X-Afu-Time"
const val NONCE_HEADER = "X-Afu-Nonce"
const val SIGNATURE_HEADER = "X-Afu-Sig"
const val HATA_ERISILEBILIRLIK = "erisilebilirlik_kapali"

@Serializable
data class InfoResponse(
    val id: String,
    val name: String,
    val model: String,
    val version: String,
    val protocol: String = PROTOCOL_VERSION
)

@Serializable
data class PairStartRequest(val deviceId: String, val deviceName: String, val phonePub: String)

@Serializable
data class PairStartResponse(val pairId: String, val tvPub: String, val tvTime: Long)

@Serializable
data class PairConfirmRequest(val pairId: String)

@Serializable
data class OpenRequest(val url: String, val title: String = "", val forceMedia: Boolean = false)

@Serializable
data class KeyRequest(val key: String)

@Serializable
data class LaunchRequest(val pkg: String)

@Serializable
data class TextRequest(val text: String)

@Serializable
data class ApiResult(val ok: Boolean, val hata: String = "", val saat: Long = 0)

enum class RemoteKey(val wire: String) {
    BACK("back"),
    HOME("home"),
    VOL_UP("vol_up"),
    VOL_DOWN("vol_down"),
    MUTE("mute"),
    PLAY_PAUSE("play_pause"),
    SEEK_FWD("seek_fwd"),
    SEEK_BACK("seek_back"),
    DPAD_UP("up"),
    DPAD_DOWN("down"),
    DPAD_LEFT("left"),
    DPAD_RIGHT("right"),
    DPAD_CENTER("ok"),
    POWER("power");

    companion object {
        fun fromWire(value: String): RemoteKey? = entries.firstOrNull { it.wire == value }
    }
}

val ProtocolJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

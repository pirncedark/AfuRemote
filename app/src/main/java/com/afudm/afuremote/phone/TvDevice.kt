package com.afudm.afuremote.phone

data class TvDevice(val id: String, val name: String, val model: String, val host: String, val port: Int, val serviceName: String, val backend: Backend = Backend.AFUREMOTE) {
    fun url(path: String): String = "http://${hostForUrl(host)}:$port$path"

    enum class Backend { AFUREMOTE, ATV_REMOTE_V2 }

    companion object {
        fun hostForUrl(host: String): String = if (host.contains(':')) "[" + host.replace("%", "%25") + "]" else host
    }
}

sealed interface SendResult {
    data object Ok : SendResult
    data object Unauthorized : SendResult
    data class Failed(val message: String) : SendResult
}

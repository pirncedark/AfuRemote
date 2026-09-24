package com.afudm.afuremote.phone

/** Runtime permission policy for LAN discovery and direct TV connections. */
object PhonePermissions {
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    fun required(apiLevel: Int, targetSdk: Int = 37): String? =
        ACCESS_LOCAL_NETWORK.takeIf { apiLevel >= 37 && targetSdk >= 37 }

    fun rationale(permission: String): String = when (permission) {
        ACCESS_LOCAL_NETWORK -> "TV bulma, elle IP ile bağlanma, kumanda komutları ve telefondaki medyayı TV'ye aktarma yerel ağ erişimi gerektirir."
        else -> "AfuRemote'un TV'nize bağlanması için gereklidir."
    }
}

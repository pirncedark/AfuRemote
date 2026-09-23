package com.afudm.afuremote.net

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalIp {
    data class Lan(val ip: String, val prefix: Int)

    fun wifiIpv4(): String? = lan()?.ip

    /** Yerel ağdaki IPv4 adresi ve ağ öneki (Wi-Fi/Ethernet öncelikli). */
    fun lan(): Lan? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.interfaceAddresses.orEmpty() }
            .firstOrNull { val a = it.address; a is Inet4Address && a.isSiteLocalAddress }
            ?.let { Lan(it.address.hostAddress.orEmpty(), it.networkPrefixLength.toInt()) }
    }.getOrNull()
}

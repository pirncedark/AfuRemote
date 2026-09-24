package com.afudm.afuremote.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class DiscoveryReply(val name: String, val port: Int, val id: String)

object UdpDiscovery {
    fun timedOut(startNanos: Long, nowNanos: Long, timeoutNanos: Long): Boolean = nowNanos - startNanos >= timeoutNanos
    fun encode(reply: DiscoveryReply): String = ProtocolJson.encodeToString(reply)
    fun parse(packet: String): DiscoveryReply? = runCatching {
        ProtocolJson.decodeFromString<DiscoveryReply>(packet).takeIf {
            it.name.isNotBlank() && it.id.isNotBlank() && it.port in 1..65535
        }
    }.getOrNull()

    fun distinct(replies: Iterable<DiscoveryReply>): List<DiscoveryReply> = replies.distinctBy { it.id }
}

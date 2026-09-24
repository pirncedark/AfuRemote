package com.afudm.afuremote.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UdpDiscoveryTest {
    @Test fun `valid discovery reply parses and invalid replies are rejected`() {
        assertEquals(DiscoveryReply("TV", 9870, "id-1"), UdpDiscovery.parse(UdpDiscovery.encode(DiscoveryReply("TV", 9870, "id-1"))))
        assertNull(UdpDiscovery.parse("not json"))
        assertNull(UdpDiscovery.parse("{\"name\":\"TV\",\"port\":70000,\"id\":\"id\"}"))
    }

    @Test fun `duplicate devices collapse by id`() {
        val a = DiscoveryReply("TV", 9870, "id-1")
        val result = UdpDiscovery.distinct(listOf(a, a, DiscoveryReply("another", 9870, "id-2")))
        assertEquals(2, result.size)
        assertEquals(listOf("id-1", "id-2"), result.map { it.id })
    }

    @Test fun `discovery window times out at deadline`() {
        assertFalse(UdpDiscovery.timedOut(100, 199, 100))
        assertTrue(UdpDiscovery.timedOut(100, 200, 100))
    }
}

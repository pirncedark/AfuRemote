package com.afudm.afuremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetTest {
    @Test fun `slash 24 yields 254 hosts nearest first`() {
        val hosts = Subnet.hosts("192.168.1.40", 24)
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.40", hosts.first())
        assertTrue(hosts.containsAll(listOf("192.168.1.1", "192.168.1.254")))
        assertFalse(hosts.contains("192.168.1.0") || hosts.contains("192.168.1.255"))
    }

    @Test fun `large networks are capped to the phone's slash 24`() {
        val hosts = Subnet.hosts("10.0.2.15", 16)
        assertEquals(254, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.0.2.") })
    }

    @Test fun `small networks and bad input`() {
        assertEquals(listOf("192.168.1.9", "192.168.1.10", "192.168.1.11", "192.168.1.14", "192.168.1.13", "192.168.1.12").sorted(), Subnet.hosts("192.168.1.10", 29).sorted())
        assertTrue(Subnet.hosts("fe80::1", 64).isEmpty())
        assertTrue(Subnet.hosts("300.1.1.1", 24).isEmpty())
    }
}

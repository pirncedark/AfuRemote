package com.afudm.afuremote.atvremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AtvProtocolTest {
    @Test fun `frames protobuf with varint length`() {
        val payload = byteArrayOf(0x08, 0x01)
        assertArrayEquals(byteArrayOf(2, 8, 1), AtvFraming.frame(payload))
        assertArrayEquals(payload, AtvFraming.unframe(byteArrayOf(2, 8, 1)))
    }

    @Test fun `pairing secret matches published algorithm vector`() {
        val hash = AtvPairingSecret.calculate(
            byteArrayOf(1), byteArrayOf(1, 0, 1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(0, 0, 0, 0)
        )
        assertEquals("1439e40996f6ddfda4dc906344e6bdaa1c87cbcd6f4c977b4b80944b34f2cef8", hash.joinToString("") { "%02x".format(it) })
    }

    @Test fun `cast TXT friendly name is used and identical endpoints merge`() {
        val cast = AtvMdnsResult.parse("Oturma odasi._googlecast._tcp", 8009, listOf("fn=Oturma odasi"), "192.168.1.3")!!
        val atv = AtvMdnsResult.parse("Oturma odasi._androidtvremote2._tcp", 6466, emptyList(), "192.168.1.3")!!
        assertEquals("Oturma odasi", cast.name)
        assertEquals(1, AtvMdnsResult.merge(listOf(cast, atv)).size)
    }
}

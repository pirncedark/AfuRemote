package com.afudm.afuremote.atvremote

import com.afudm.afuremote.atvremote.protocol.AtvWireImeCounters
import com.afudm.afuremote.atvremote.protocol.AtvWireProtocol
import org.junit.Assert.*
import org.junit.Test

class AtvWireProtocolTest {
    @Test fun `varint boundaries encode and decode`() {
        listOf(0, 127, 128, 16383, 16384).forEach { value ->
            val encoded = AtvWireProtocol.encodeVarint(value)
            assertEquals(value to encoded.size, AtvWireProtocol.readVarint(encoded))
        }
        assertArrayEquals(byteArrayOf(0), AtvWireProtocol.encodeVarint(0))
        assertArrayEquals(byteArrayOf(0x7f), AtvWireProtocol.encodeVarint(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 1), AtvWireProtocol.encodeVarint(128))
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0x7f), AtvWireProtocol.encodeVarint(16383))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x80.toByte(), 1), AtvWireProtocol.encodeVarint(16384))
    }

    @Test fun `incomplete and malformed frames have useful errors`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { AtvWireProtocol.readVarint(byteArrayOf(0x80.toByte())) }.message!!.contains("Incomplete varint"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { AtvWireProtocol.decodeFrame(byteArrayOf(3, 1, 2)) }.message!!.contains("expected 3 bytes"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { AtvWireProtocol.readVarint(ByteArray(6) { 0xff.toByte() }) }.message!!.contains("32 bits"))
    }

    @Test fun `pairing nonce is exactly two bytes from six digit code`() {
        val (cm, ce, sm, se) = listOf(byteArrayOf(1), byteArrayOf(1, 0, 1), byteArrayOf(2), byteArrayOf(3))
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 1, 0, 1, 2, 3, 0xab.toByte(), 0xcd.toByte()))
        assertArrayEquals(expected, AtvWireProtocol.pairingSecret(cm, ce, sm, se, byteArrayOf(0xab.toByte(), 0xcd.toByte())))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { AtvWireProtocol.pairingSecret(cm, ce, sm, se, byteArrayOf(1, 2, 3, 4)) }.message!!.contains("2 bytes"))
    }

    @Test fun `secret byte with high bit matches unsigned pairing code prefix`() {
        assertTrue(AtvWireProtocol.pairingCodeMatches("FF1234", byteArrayOf(0xff.toByte())))
        assertFalse(AtvWireProtocol.pairingCodeMatches("FE1234", byteArrayOf(0xff.toByte())))
    }

    @Test fun `key IME and power messages have stable wire bytes`() {
        assertArrayEquals(byteArrayOf(6, 0x52, 4, 8, 23, 16, 3), AtvFraming.frame(AtvWireProtocol.keyMessage(23).toByteArray()))
        assertArrayEquals(byteArrayOf(6, 0x52, 4, 8, 26, 16, 3), AtvFraming.frame(AtvWireProtocol.keyMessage(26).toByteArray()))
        val ime = AtvWireProtocol.imeMessage("x", AtvWireImeCounters(1, 2))
        assertArrayEquals(byteArrayOf(16, 0xaa.toByte(), 1, 13, 8, 1, 16, 2, 26, 7, 8, 1, 18, 3, 26, 1, 120), AtvFraming.frame(ime.toByteArray()))
        val launch = AtvWireProtocol.launchMessage("x")
        assertArrayEquals(byteArrayOf(6, 0xd2.toByte(), 5, 3, 10, 1, 120), AtvFraming.frame(launch.toByteArray()))
    }
}

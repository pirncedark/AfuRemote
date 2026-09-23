package com.afudm.afuremote.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolTest {
    @Test
    fun `open request round trips with defaults`() {
        val json = ProtocolJson.encodeToString(OpenRequest("https://a/b.mp4"))
        assertEquals(OpenRequest("https://a/b.mp4", "", false), ProtocolJson.decodeFromString<OpenRequest>(json))
    }

    @Test
    fun `unknown fields from a newer phone are ignored`() {
        val req = ProtocolJson.decodeFromString<KeyRequest>("""{"key":"vol_up","future":1}""")
        assertEquals("vol_up", req.key)
    }

    @Test(expected = SerializationException::class)
    fun `missing required field fails loudly`() {
        ProtocolJson.decodeFromString<PairStartRequest>("""{"deviceId":"d","deviceName":"x"}""")
    }

    @Test
    fun `remote keys map from wire names`() {
        assertEquals(RemoteKey.PLAY_PAUSE, RemoteKey.fromWire("play_pause"))
        assertEquals(RemoteKey.SEEK_BACK, RemoteKey.fromWire("seek_back"))
        assertNull(RemoteKey.fromWire("launch_missiles"))
    }
}

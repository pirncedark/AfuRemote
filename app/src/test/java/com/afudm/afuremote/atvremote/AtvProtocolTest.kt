package com.afudm.afuremote.atvremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import com.google.polo.wire.protobuf.PoloProto
import com.afudm.afuremote.atvremote.proto.RemoteProto
import com.afudm.afuremote.protocol.RemoteKey

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

    @Test fun `android tv TXT bluetooth identity is retained and instance name wins`() {
        val tv = AtvMdnsResult.parse("projeksiyon._androidtvremote2._tcp.", 6466,
            listOf("bt=44:F5:3E:01:24:5B"), "192.168.1.3")!!
        assertEquals("projeksiyon", tv.name)
        assertEquals("44:F5:3E:01:24:5B", tv.bt)
    }

    @Test fun `pairing and remote protobuf messages survive encode decode`() {
        val pairing = PoloProto.OuterMessage.newBuilder().setProtocolVersion(2)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
            .setPairingRequest(PoloProto.PairingRequest.newBuilder().setServiceName("atvremote").setClientName("AfuRemote")).build()
        assertEquals("AfuRemote", PoloProto.OuterMessage.parseFrom(pairing.toByteArray()).pairingRequest.clientName)
        val remote = RemoteProto.RemoteMessage.newBuilder().setRemoteKeyInject(
            RemoteProto.RemoteKeyInject.newBuilder().setKeyCodeValue(AtvCommandMapper.keyCode(RemoteKey.DPAD_CENTER))
                .setDirection(RemoteProto.RemoteDirection.SHORT)).build()
        assertEquals(23, RemoteProto.RemoteMessage.parseFrom(remote.toByteArray()).remoteKeyInject.keyCodeValue)
    }

    @Test fun `remote keys map to Android keycodes`() {
        assertEquals(19, AtvCommandMapper.keyCode(RemoteKey.DPAD_UP))
        assertEquals(4, AtvCommandMapper.keyCode(RemoteKey.BACK))
        assertEquals(3, AtvCommandMapper.keyCode(RemoteKey.HOME))
        assertEquals(24, AtvCommandMapper.keyCode(RemoteKey.VOL_UP))
        assertEquals(164, AtvCommandMapper.keyCode(RemoteKey.MUTE))
        assertEquals(85, AtvCommandMapper.keyCode(RemoteKey.PLAY_PAUSE))
    }
}

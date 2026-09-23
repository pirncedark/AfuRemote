package com.afudm.afuremote.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRegistryTest {
    @Test
    fun `issued key is retrievable and snapshot reflects state`() {
        val registry = TokenRegistry()
        val key = ByteArray(32) { it.toByte() }
        registry.issue("telefon-1", key)
        assertArrayEquals(key, registry.keyFor("telefon-1"))
        assertEquals(setOf("telefon-1"), registry.snapshot().keys)
        registry.clear()
        assertNull(registry.keyFor("telefon-1"))
        assertEquals(emptyMap<String, String>(), registry.snapshot())
    }

    @Test
    fun `re-pairing replaces device key`() {
        val registry = TokenRegistry()
        val oldKey = ByteArray(32) { 1 }
        val newKey = ByteArray(32) { 2 }
        registry.issue("telefon-1", oldKey)
        registry.issue("telefon-1", newKey)
        assertArrayEquals(newKey, registry.keyFor("telefon-1"))
    }

    @Test
    fun `nonce cache rejects replay and expires entries after two minutes`() {
        val registry = TokenRegistry()
        assertTrue(registry.acceptNonce("d", "first", 1_000))
        assertFalse(registry.acceptNonce("d", "first", 1_001))
        assertTrue(registry.acceptNonce("d", "first", 121_001))
    }

    @Test
    fun `nonce cache is bounded to 256 entries`() {
        val registry = TokenRegistry()
        repeat(257) { assertTrue(registry.acceptNonce("d", "n$it", 1_000)) }
        // The oldest nonce was evicted to keep 256 entries; the newest ones are still remembered.
        assertTrue(registry.acceptNonce("d", "n0", 1_000))
        assertFalse(registry.acceptNonce("d", "n256", 1_000))
        assertFalse(registry.acceptNonce("d", "n0", 1_000))
    }

    @Test
    fun `ECDH sides derive matching key and pairing code`() {
        val phone = PairCrypto.newKeyPair()
        val tv = PairCrypto.newKeyPair()
        val phonePub = phone.public.encoded
        val tvPub = tv.public.encoded
        assertArrayEquals(
            PairCrypto.sharedKey(phone, tv.public, phonePub, tvPub, "d"),
            PairCrypto.sharedKey(tv, phone.public, phonePub, tvPub, "d")
        )
        assertEquals(PairCrypto.pairingCode(phonePub, tvPub, "d"), PairCrypto.pairingCode(phonePub, tvPub, "d"))
    }
}

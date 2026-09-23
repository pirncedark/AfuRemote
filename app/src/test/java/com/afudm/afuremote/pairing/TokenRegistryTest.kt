package com.afudm.afuremote.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenRegistryTest {
    @Test
    fun `issued token is valid and blank or unknown tokens are not`() {
        val reg = TokenRegistry()
        val t = reg.issue("telefon-1")
        assertTrue(reg.isValid(t))
        assertFalse(reg.isValid(null))
        assertFalse(reg.isValid(""))
        assertFalse(reg.isValid("uydurma"))
    }

    @Test
    fun `re-pairing the same device replaces its old token`() {
        var n = 0
        val reg = TokenRegistry(newToken = { "t${++n}" })
        val first = reg.issue("telefon-1")
        val second = reg.issue("telefon-1")
        assertNotEquals(first, second)
        assertFalse(reg.isValid(first))
        assertTrue(reg.isValid(second))
    }

    @Test
    fun `clear revokes everything and snapshot reflects state`() {
        val reg = TokenRegistry(mutableMapOf("a" to "x"))
        assertEquals(mapOf("a" to "x"), reg.snapshot())
        reg.clear()
        assertFalse(reg.isValid("x"))
        assertEquals(emptyMap<String, String>(), reg.snapshot())
    }

    @Test
    fun `random tokens are long and distinct`() {
        val a = TokenRegistry.randomToken()
        assertEquals(48, a.length)
        assertNotEquals(a, TokenRegistry.randomToken())
    }
}

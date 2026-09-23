package com.afudm.afuremote.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeParserTest {
    @Test fun `open ended and bounded ranges`() {
        assertEquals(ByteRange(500, 999), RangeParser.parse("bytes=500-", 1000))
        assertEquals(ByteRange(0, 99), RangeParser.parse("bytes=0-99", 1000))
        assertEquals(ByteRange(900, 999), RangeParser.parse("bytes=900-5000", 1000))
    }
    @Test fun `suffix ranges take last bytes`() {
        assertEquals(ByteRange(500, 999), RangeParser.parse("bytes=-500", 1000))
        assertEquals(ByteRange(0, 999), RangeParser.parse("bytes=-5000", 1000))
    }
    @Test fun `invalid ranges return null`() {
        listOf("bytes=1000-", "bytes=50-10", "bytes=-", "items=0-1", "bytes=0-1,5-9", null).forEach {
            assertNull(RangeParser.parse(it, 1000))
        }
    }
    @Test fun `length is inclusive`() { assertEquals(100L, ByteRange(0, 99).length) }
}

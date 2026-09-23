package com.afudm.afuremote.mode

import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeTest {
    @Test
    fun `television ui or leanback feature selects TV`() {
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = true, hasLeanback = false, override = null))
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = false, hasLeanback = true, override = null))
    }

    @Test
    fun `plain phone selects PHONE`() {
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = null))
    }

    @Test
    fun `manual override wins over detection and unknown override is ignored`() {
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = true, hasLeanback = true, override = "phone"))
        assertEquals(AppMode.TV, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = "tv"))
        assertEquals(AppMode.PHONE, AppMode.detect(isTelevisionUi = false, hasLeanback = false, override = "garbage"))
    }
}

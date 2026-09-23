package com.afudm.afuremote.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MessagesTest {
    @Test fun `tv error codes become turkish sentences`() {
        assertEquals(Messages.ACCESSIBILITY, Messages.forError(409, """{"ok":false,"hata":"erisilebilirlik_kapali"}"""))
        assertEquals("Bu link anlaşılamadı", Messages.forError(400, """{"ok":false,"hata":"gecersiz_link"}"""))
        assertEquals("TV hata verdi (500)", Messages.forError(500, "html sayfasi"))
    }
    @Test fun `phone messages never say the TV approval label`() {
        listOf(Messages.UNREACHABLE, Messages.NO_TV, Messages.DENIED, Messages.WAITING_TV, Messages.ACCESSIBILITY)
            .forEach { assertFalse(it, it.contains("İzin ver")) }
    }
}

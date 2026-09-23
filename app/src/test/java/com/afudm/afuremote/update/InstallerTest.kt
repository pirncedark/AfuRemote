package com.afudm.afuremote.update

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallerTest {
    @Test
    fun `missing package install permission opens settings`() {
        assertEquals(Installer.Action.OPEN_SETTINGS, Installer.action(false))
    }

    @Test
    fun `package install permission starts installer`() {
        assertEquals(Installer.Action.INSTALL, Installer.action(true))
    }

    @Test
    fun `download worker output key is apkPath`() {
        assertEquals("apkPath", ApkDownloadWorker.KEY_APK_PATH)
    }
}

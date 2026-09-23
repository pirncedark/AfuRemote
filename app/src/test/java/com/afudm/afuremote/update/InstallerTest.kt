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

    @Test
    fun `download job version is read from its tag`() {
        assertEquals(1_000, UpdateManager.versionCodeOf(setOf("com.afudm.afuremote.update.ApkDownloadWorker", "afuremote-version:1000")))
        assertEquals(0, UpdateManager.versionCodeOf(setOf("com.afudm.afuremote.update.ApkDownloadWorker")))
    }
}

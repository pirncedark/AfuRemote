package com.afudm.afuremote.phone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhonePermissionsTest {
    @Test fun android17RequiresLocalNetworkRuntimePermission() {
        assertEquals(PhonePermissions.ACCESS_LOCAL_NETWORK, PhonePermissions.required(37))
        assertTrue(PhonePermissions.rationale(PhonePermissions.ACCESS_LOCAL_NETWORK).contains("IP ile bağlanma"))
    }

    @Test fun androidVersionsBelow17DoNotRequestRuntimePermission() {
        assertEquals(null, PhonePermissions.required(26))
        assertEquals(null, PhonePermissions.required(36))
    }

    @Test fun runtimePermissionRequiresTargetingAndroid17() {
        assertEquals(null, PhonePermissions.required(37, targetSdk = 36))
    }
}

package com.andrerinas.openheadunit.connection.wifi.direct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiDirectLocationGateTest {
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val background = "android.permission.ACCESS_BACKGROUND_LOCATION"

    @Test
    fun `nothing is asked before Android 10 or from Android 13`() {
        for (sdk in listOf(28, 33, 36)) {
            assertNull("sdk=$sdk", WifiDirectLocationGate.missing(sdk, false, false, false))
        }
    }

    @Test
    fun `Android 10 to 12 need fine location`() {
        for (sdk in 29..32) {
            assertEquals("sdk=$sdk", fine, WifiDirectLocationGate.missing(sdk, false, false, true))
            assertEquals("sdk=$sdk", fine, WifiDirectLocationGate.missing(sdk, false, true, false))
        }
    }

    @Test
    fun `in the background they also need location all the time`() {
        assertEquals(background, WifiDirectLocationGate.missing(29, true, false, false))
        assertNull(WifiDirectLocationGate.missing(29, true, true, false))
    }

    @Test
    fun `with a screen of ours started, fine location is enough`() {
        assertNull(WifiDirectLocationGate.missing(29, true, false, true))
    }
}

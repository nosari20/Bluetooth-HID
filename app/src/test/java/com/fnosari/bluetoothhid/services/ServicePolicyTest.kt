package com.fnosari.bluetoothhid.services

import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice.HIDDeviceExitCode
import com.fnosari.bluetoothhid.model.Host
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePolicyTest {

    private val host = Host("AA:BB:CC:DD:EE:FF", "PC")
    private val unavailable = HIDState.Unavailable(HIDDeviceExitCode.ERROR_BLUETOOTH_NOT_ENABLED)

    @Test
    fun busyWhileConnectingConnectedOrTyping() {
        assertTrue(ServicePolicy.isBusy(HIDState.Connected(host), typing = false))
        assertTrue(ServicePolicy.isBusy(HIDState.Connecting(host), typing = false))
        assertTrue(ServicePolicy.isBusy(HIDState.Idle, typing = true))
        assertFalse(ServicePolicy.isBusy(HIDState.Idle, typing = false))
        assertFalse(ServicePolicy.isBusy(HIDState.Starting, typing = false))
        assertFalse(ServicePolicy.isBusy(unavailable, typing = false))
    }

    @Test
    fun stopsWhenIdleAndAppNotVisible() {
        assertTrue(ServicePolicy.shouldStop(HIDState.Idle, typing = false, appVisible = false))
        assertTrue(ServicePolicy.shouldStop(unavailable, typing = false, appVisible = false))
    }

    @Test
    fun keepsRunningWhileAppVisibleOrBusy() {
        assertFalse(ServicePolicy.shouldStop(HIDState.Idle, typing = false, appVisible = true))
        assertFalse(ServicePolicy.shouldStop(HIDState.Connected(host), typing = false, appVisible = false))
        assertFalse(ServicePolicy.shouldStop(HIDState.Idle, typing = true, appVisible = false))
    }
}

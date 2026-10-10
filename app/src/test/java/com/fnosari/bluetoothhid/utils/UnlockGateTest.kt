package com.fnosari.bluetoothhid.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockGateTest {

    @Test
    fun unlockWithoutArmingFails() {
        assertFalse(UnlockGate(3000).tapUnlock(now = 1000))
    }

    @Test
    fun armThenUnlockWithinWindowSucceeds() {
        val gate = UnlockGate(3000)
        gate.tapBackground(now = 1000)
        assertTrue(gate.isArmed(now = 2000))
        assertTrue(gate.tapUnlock(now = 3999))
    }

    @Test
    fun armExpiresAfterWindow() {
        val gate = UnlockGate(3000)
        gate.tapBackground(now = 1000)
        assertFalse(gate.isArmed(now = 4000))
        assertFalse(gate.tapUnlock(now = 4000))
    }

    @Test
    fun unlockConsumesArming() {
        val gate = UnlockGate(3000)
        gate.tapBackground(now = 1000)
        assertTrue(gate.tapUnlock(now = 1500))
        assertFalse(gate.tapUnlock(now = 1600))
    }
}

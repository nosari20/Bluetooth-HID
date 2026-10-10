package com.fnosari.bluetoothhid.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TypingSpeedTest {
    @Test
    fun delays() {
        assertEquals(6L, TypingSpeed.FAST.delayMs)
        assertEquals(10L, TypingSpeed.NORMAL.delayMs)
        assertEquals(25L, TypingSpeed.SAFE.delayMs)
    }

    @Test
    fun fromName() {
        assertEquals(TypingSpeed.SAFE, TypingSpeed.fromName("SAFE"))
        assertEquals(TypingSpeed.NORMAL, TypingSpeed.fromName("bogus"))
    }
}

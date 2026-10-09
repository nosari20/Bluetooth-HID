package com.fnosari.bluetoothhid.connectivity.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HIDReportTest {

    @Test
    fun keyboardReportLayout() {
        val r = HIDReport.Keyboard(HIDModifiers.L_SHIFT, listOf(HIDKeys.letter('a')))
        assertEquals(1, r.id)
        assertArrayEquals(byteArrayOf(0x02, 0x00, 0x04, 0, 0, 0, 0, 0), r.toBytes())
    }

    @Test
    fun keyboardReleaseIsAllZero() {
        assertArrayEquals(ByteArray(8), HIDReport.Keyboard.RELEASE.toBytes())
    }

    @Test
    fun keyboardKeepsAtMostSixKeys() {
        val r = HIDReport.Keyboard(0, (4..11).toList())
        assertArrayEquals(byteArrayOf(0, 0, 4, 5, 6, 7, 8, 9), r.toBytes())
    }

    @Test
    fun mouseReportClampsAndSigns() {
        val r = HIDReport.Mouse(HIDMouseButtons.LEFT, dx = -5, dy = 300, wheel = -200, pan = 1)
        assertEquals(2, r.id)
        assertArrayEquals(byteArrayOf(1, -5, 127, -127, 1), r.toBytes())
    }

    @Test
    fun consumerReportLittleEndian() {
        val r = HIDReport.Consumer(HIDConsumer.VOLUME_UP)
        assertEquals(3, r.id)
        assertArrayEquals(byteArrayOf(0xE9.toByte(), 0x00), r.toBytes())
    }

    @Test
    fun keyHelpers() {
        assertEquals(0x04, HIDKeys.letter('a'))
        assertEquals(0x1D, HIDKeys.letter('Z'))
        assertEquals(0x1E, HIDKeys.digit(1))
        assertEquals(0x27, HIDKeys.digit(0))
        assertEquals(0x3A, HIDKeys.f(1))
        assertEquals(0x45, HIDKeys.f(12))
    }
}

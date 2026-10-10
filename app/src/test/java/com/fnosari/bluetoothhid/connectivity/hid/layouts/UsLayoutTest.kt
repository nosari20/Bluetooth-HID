package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsLayoutTest {

    private fun one(c: Char) = UsLayout.strokesFor(c)!!.single()

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (code in 32..126) assertNotNull("char ${code.toChar()}", UsLayout.strokesFor(code.toChar()))
    }

    @Test
    fun lettersAndCase() {
        assertEquals(KeyStroke(HIDKeys.letter('a')), one('a'))
        assertEquals(KeyStroke(HIDKeys.letter('a'), HIDModifiers.L_SHIFT), one('A'))
    }

    @Test
    fun digitsAndSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(1)), one('1'))
        assertEquals(KeyStroke(HIDKeys.digit(0)), one('0'))
        assertEquals(KeyStroke(HIDKeys.digit(2), HIDModifiers.L_SHIFT), one('@'))
        assertEquals(KeyStroke(HIDKeys.SLASH, HIDModifiers.L_SHIFT), one('?'))
        assertEquals(KeyStroke(HIDKeys.GRAVE, HIDModifiers.L_SHIFT), one('~'))
    }

    @Test
    fun whitespace() {
        assertEquals(KeyStroke(HIDKeys.SPACE), one(' '))
        assertEquals(KeyStroke(HIDKeys.ENTER), one('\n'))
        assertEquals(KeyStroke(HIDKeys.TAB), one('\t'))
    }

    @Test
    fun nonAsciiIsUnmappable() {
        assertNull(UsLayout.strokesFor('é'))
        assertNull(UsLayout.strokesFor('☃'))
    }
}

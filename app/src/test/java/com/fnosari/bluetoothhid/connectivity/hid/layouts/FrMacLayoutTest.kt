package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_ALT
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrMacLayoutTest {

    private fun one(c: Char) = FrMacLayout.strokesFor(c)!!.single()

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (code in 32..126) assertNotNull("${code.toChar()}", FrMacLayout.strokesFor(code.toChar()))
    }

    @Test
    fun macSpecificPositions() {
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH), one('@'))
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH, L_SHIFT), one('#'))
        assertEquals(KeyStroke(HIDKeys.GRAVE), one('<'))
        assertEquals(KeyStroke(HIDKeys.digit(6)), one('§'))
        assertEquals(KeyStroke(HIDKeys.digit(8)), one('!'))
        assertEquals(KeyStroke(HIDKeys.EQUAL), one('-'))
        assertEquals(KeyStroke(HIDKeys.EQUAL, L_SHIFT), one('_'))
        assertEquals(KeyStroke(HIDKeys.SLASH), one('='))
        assertEquals(KeyStroke(HIDKeys.RIGHT_BRACKET, L_SHIFT), one('*'))
    }

    @Test
    fun optionSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(5), L_ALT), one('{'))
        assertEquals(KeyStroke(HIDKeys.digit(5), L_ALT or L_SHIFT), one('['))
        assertEquals(KeyStroke(HIDKeys.MINUS, L_ALT), one('}'))
        assertEquals(KeyStroke(HIDKeys.letter('l'), L_ALT or L_SHIFT), one('|'))
        assertEquals(KeyStroke(HIDKeys.PERIOD, L_ALT or L_SHIFT), one('\\'))
    }

    @Test
    fun deadKeys() {
        assertEquals(listOf(KeyStroke(HIDKeys.letter('n'), L_ALT), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('~'))
        assertEquals(listOf(KeyStroke(HIDKeys.BACKSLASH), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('`'))
        assertEquals(listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.SPACE)), FrMacLayout.strokesFor('^'))
    }

    @Test
    fun registry() {
        assertEquals(listOf("us", "fr-win", "fr-linux", "fr-mac"), Layouts.ALL.map { it.id })
        assertEquals("fr-mac", Layouts.byId("fr-mac").id)
        assertEquals("us", Layouts.byId("nope").id)
        assertEquals("fr-win", Layouts.defaultIdFor("fr"))
        assertEquals("us", Layouts.defaultIdFor("de"))
    }
}

package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.R_ALT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrAzertyLayoutTest {

    private val win = FrAzertyLayout(linux = false)
    private val linux = FrAzertyLayout(linux = true)

    private fun one(l: KeyboardLayout, c: Char) = l.strokesFor(c)!!.single()

    @Test
    fun ids() {
        assertEquals("fr-win", win.id)
        assertEquals("fr-linux", linux.id)
    }

    @Test
    fun everyPrintableAsciiIsMappable() {
        for (l in listOf(win, linux)) for (code in 32..126)
            assertNotNull("${l.id} ${code.toChar()}", l.strokesFor(code.toChar()))
    }

    @Test
    fun azertyLetters() {
        assertEquals(KeyStroke(HIDKeys.letter('q')), one(win, 'a'))
        assertEquals(KeyStroke(HIDKeys.letter('a')), one(win, 'q'))
        assertEquals(KeyStroke(HIDKeys.letter('w')), one(win, 'z'))
        assertEquals(KeyStroke(HIDKeys.letter('z')), one(win, 'w'))
        assertEquals(KeyStroke(HIDKeys.SEMICOLON), one(win, 'm'))
        assertEquals(KeyStroke(HIDKeys.SEMICOLON, L_SHIFT), one(win, 'M'))
        assertEquals(KeyStroke(HIDKeys.letter('e')), one(win, 'e'))
    }

    @Test
    fun digitRow() {
        assertEquals(KeyStroke(HIDKeys.digit(1), L_SHIFT), one(win, '1'))
        assertEquals(KeyStroke(HIDKeys.digit(1)), one(win, '&'))
        assertEquals(KeyStroke(HIDKeys.digit(2)), one(win, 'é'))
        assertEquals(KeyStroke(HIDKeys.digit(0)), one(win, 'à'))
        assertEquals(KeyStroke(HIDKeys.digit(9)), one(win, 'ç'))
    }

    @Test
    fun altGrSymbols() {
        assertEquals(KeyStroke(HIDKeys.digit(0), R_ALT), one(win, '@'))
        assertEquals(KeyStroke(HIDKeys.digit(3), R_ALT), one(win, '#'))
        assertEquals(KeyStroke(HIDKeys.digit(4), R_ALT), one(win, '{'))
        assertEquals(KeyStroke(HIDKeys.EQUAL, R_ALT), one(win, '}'))
        assertEquals(KeyStroke(HIDKeys.digit(8), R_ALT), one(win, '\\'))
        assertEquals(KeyStroke(HIDKeys.digit(9), R_ALT), one(win, '^'))
        assertEquals(KeyStroke(HIDKeys.letter('e'), R_ALT), one(win, '€'))
    }

    @Test
    fun punctuation() {
        assertEquals(KeyStroke(HIDKeys.letter('m')), one(win, ','))
        assertEquals(KeyStroke(HIDKeys.COMMA, L_SHIFT), one(win, '.'))
        assertEquals(KeyStroke(HIDKeys.PERIOD, L_SHIFT), one(win, '/'))
        assertEquals(KeyStroke(HIDKeys.SLASH), one(win, '!'))
        assertEquals(KeyStroke(HIDKeys.NON_US_BACKSLASH), one(win, '<'))
        assertEquals(KeyStroke(HIDKeys.QUOTE), one(win, 'ù'))
    }

    @Test
    fun tildeAndBacktickDeadOnWindowsOnly() {
        assertEquals(
            listOf(KeyStroke(HIDKeys.digit(2), R_ALT), KeyStroke(HIDKeys.SPACE)),
            win.strokesFor('~')
        )
        assertEquals(listOf(KeyStroke(HIDKeys.digit(2), R_ALT)), linux.strokesFor('~'))
        assertEquals(
            listOf(KeyStroke(HIDKeys.digit(7), R_ALT), KeyStroke(HIDKeys.SPACE)),
            win.strokesFor('`')
        )
        assertEquals(listOf(KeyStroke(HIDKeys.digit(7), R_ALT)), linux.strokesFor('`'))
    }

    @Test
    fun deadKeyAccents() {
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.letter('e'))),
            win.strokesFor('ê')
        )
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET), KeyStroke(HIDKeys.letter('q'), L_SHIFT)),
            win.strokesFor('Â')
        )
        assertEquals(
            listOf(KeyStroke(HIDKeys.LEFT_BRACKET, L_SHIFT), KeyStroke(HIDKeys.letter('i'))),
            win.strokesFor('ï')
        )
    }
}

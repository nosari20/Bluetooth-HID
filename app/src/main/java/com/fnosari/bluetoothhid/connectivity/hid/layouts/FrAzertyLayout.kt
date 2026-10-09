package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/**
 * PC French AZERTY. [linux] = xkb `fr`, where AltGr+2 (~) and AltGr+7 (`)
 * are plain keys; on Windows they are dead keys, followed by Space.
 */
class FrAzertyLayout(linux: Boolean) : TableKeyboardLayout(if (linux) "fr-linux" else "fr-win") {

    companion object {
        private const val ALTGR = HIDModifiers.R_ALT
        private const val SHIFT = HIDModifiers.L_SHIFT

        /** US-position usage of AZERTY letter [c]. */
        fun letterUsage(c: Char): Int = when (c.lowercaseChar()) {
            'a' -> HIDKeys.letter('q')
            'q' -> HIDKeys.letter('a')
            'z' -> HIDKeys.letter('w')
            'w' -> HIDKeys.letter('z')
            'm' -> HIDKeys.SEMICOLON
            else -> HIDKeys.letter(c)
        }
    }

    init {
        for (c in 'a'..'z') pair(letterUsage(c), c, c.uppercaseChar())

        val digitRow = "&é\"'(-è_çà" // keys 1..9,0 unshifted
        for (i in 0..9) {
            val d = (i + 1) % 10
            pair(HIDKeys.digit(d), digitRow[i], '0' + d)
        }

        pair(HIDKeys.MINUS, ')', '°')
        pair(HIDKeys.EQUAL, '=', '+')
        key('²', HIDKeys.GRAVE)
        pair(HIDKeys.RIGHT_BRACKET, '$', '£')
        pair(HIDKeys.QUOTE, 'ù', '%')
        pair(HIDKeys.BACKSLASH, '*', 'µ')
        pair(HIDKeys.NON_US_BACKSLASH, '<', '>')
        pair(HIDKeys.letter('m'), ',', '?')
        pair(HIDKeys.COMMA, ';', '.')
        pair(HIDKeys.PERIOD, ':', '/')
        pair(HIDKeys.SLASH, '!', '§')

        key('#', HIDKeys.digit(3), ALTGR)
        key('{', HIDKeys.digit(4), ALTGR)
        key('[', HIDKeys.digit(5), ALTGR)
        key('|', HIDKeys.digit(6), ALTGR)
        key('\\', HIDKeys.digit(8), ALTGR)
        key('^', HIDKeys.digit(9), ALTGR)
        key('@', HIDKeys.digit(0), ALTGR)
        key(']', HIDKeys.MINUS, ALTGR)
        key('}', HIDKeys.EQUAL, ALTGR)
        key('¤', HIDKeys.RIGHT_BRACKET, ALTGR)
        key('€', HIDKeys.letter('e'), ALTGR)

        val tilde = KeyStroke(HIDKeys.digit(2), ALTGR)
        val backtick = KeyStroke(HIDKeys.digit(7), ALTGR)
        val space = KeyStroke(HIDKeys.SPACE)
        if (linux) {
            seq('~', tilde)
            seq('`', backtick)
        } else {
            seq('~', tilde, space)
            seq('`', backtick, space)
        }

        val circumflex = KeyStroke(HIDKeys.LEFT_BRACKET)
        val diaeresis = KeyStroke(HIDKeys.LEFT_BRACKET, SHIFT)
        seq('¨', diaeresis, space)
        for ((plain, accented) in listOf('a' to 'â', 'e' to 'ê', 'i' to 'î', 'o' to 'ô', 'u' to 'û')) {
            seq(accented, circumflex, KeyStroke(letterUsage(plain)))
            seq(accented.uppercaseChar(), circumflex, KeyStroke(letterUsage(plain), SHIFT))
        }
        for ((plain, accented) in listOf('a' to 'ä', 'e' to 'ë', 'i' to 'ï', 'o' to 'ö', 'u' to 'ü', 'y' to 'ÿ')) {
            seq(accented, diaeresis, KeyStroke(letterUsage(plain)))
        }
        for ((plain, accented) in listOf('a' to 'Ä', 'e' to 'Ë', 'i' to 'Ï', 'o' to 'Ö', 'u' to 'Ü')) {
            seq(accented, diaeresis, KeyStroke(letterUsage(plain), SHIFT))
        }
    }
}

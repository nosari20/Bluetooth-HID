package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/** Apple French AZERTY (ISO). Option is sent as Left Alt. */
object FrMacLayout : TableKeyboardLayout("fr-mac") {

    private const val OPT = HIDModifiers.L_ALT
    private const val SHIFT = HIDModifiers.L_SHIFT

    init {
        for (c in 'a'..'z') pair(FrAzertyLayout.letterUsage(c), c, c.uppercaseChar())

        val digitRow = "&é\"'(§è!çà" // keys 1..9,0 unshifted
        for (i in 0..9) {
            val d = (i + 1) % 10
            pair(HIDKeys.digit(d), digitRow[i], '0' + d)
        }

        pair(HIDKeys.MINUS, ')', '°')
        pair(HIDKeys.EQUAL, '-', '_')
        pair(HIDKeys.RIGHT_BRACKET, '$', '*')
        pair(HIDKeys.QUOTE, 'ù', '%')
        key('£', HIDKeys.BACKSLASH, SHIFT)
        pair(HIDKeys.letter('m'), ',', '?')
        pair(HIDKeys.COMMA, ';', '.')
        pair(HIDKeys.PERIOD, ':', '/')
        pair(HIDKeys.SLASH, '=', '+')
        pair(HIDKeys.NON_US_BACKSLASH, '@', '#')
        pair(HIDKeys.GRAVE, '<', '>')

        key('{', HIDKeys.digit(5), OPT)
        key('[', HIDKeys.digit(5), OPT or SHIFT)
        key('}', HIDKeys.MINUS, OPT)
        key(']', HIDKeys.MINUS, OPT or SHIFT)
        key('|', HIDKeys.letter('l'), OPT or SHIFT)
        key('\\', HIDKeys.PERIOD, OPT or SHIFT)
        key('€', HIDKeys.RIGHT_BRACKET, OPT)

        val space = KeyStroke(HIDKeys.SPACE)
        val circumflex = KeyStroke(HIDKeys.LEFT_BRACKET)
        val diaeresis = KeyStroke(HIDKeys.LEFT_BRACKET, SHIFT)
        seq('~', KeyStroke(HIDKeys.letter('n'), OPT), space)
        seq('`', KeyStroke(HIDKeys.BACKSLASH), space)
        seq('^', circumflex, space)
        seq('¨', diaeresis, space)
        for ((plain, accented) in listOf('a' to 'â', 'e' to 'ê', 'i' to 'î', 'o' to 'ô', 'u' to 'û')) {
            seq(accented, circumflex, KeyStroke(FrAzertyLayout.letterUsage(plain)))
            seq(accented.uppercaseChar(), circumflex, KeyStroke(FrAzertyLayout.letterUsage(plain), SHIFT))
        }
        for ((plain, accented) in listOf('a' to 'ä', 'e' to 'ë', 'i' to 'ï', 'o' to 'ö', 'u' to 'ü', 'y' to 'ÿ')) {
            seq(accented, diaeresis, KeyStroke(FrAzertyLayout.letterUsage(plain)))
        }
    }
}

package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys

/** US QWERTY. */
object UsLayout : TableKeyboardLayout("us") {
    init {
        for (c in 'a'..'z') pair(HIDKeys.letter(c), c, c.uppercaseChar())
        val shiftedDigits = ")!@#$%^&*("
        for (d in 0..9) pair(HIDKeys.digit(d), '0' + d, shiftedDigits[d])
        pair(HIDKeys.MINUS, '-', '_')
        pair(HIDKeys.EQUAL, '=', '+')
        pair(HIDKeys.LEFT_BRACKET, '[', '{')
        pair(HIDKeys.RIGHT_BRACKET, ']', '}')
        pair(HIDKeys.BACKSLASH, '\\', '|')
        pair(HIDKeys.SEMICOLON, ';', ':')
        pair(HIDKeys.QUOTE, '\'', '"')
        pair(HIDKeys.GRAVE, '`', '~')
        pair(HIDKeys.COMMA, ',', '<')
        pair(HIDKeys.PERIOD, '.', '>')
        pair(HIDKeys.SLASH, '/', '?')
    }
}

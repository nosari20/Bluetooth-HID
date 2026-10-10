package com.fnosari.bluetoothhid.connectivity.hid

/**
 * USB HID usage IDs (Keyboard/Keypad page 0x07). Names follow the US key
 * position, whatever character the host layout prints for that key.
 */
object HIDKeys {
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val NON_US_HASH = 0x32
    const val SEMICOLON = 0x33
    const val QUOTE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39
    const val F1 = 0x3A
    const val PRINT_SCREEN = 0x46
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val NON_US_BACKSLASH = 0x64
    const val MENU = 0x65

    /** Usage of the key at US position of letter [c] (case ignored). */
    fun letter(c: Char): Int = 0x04 + (c.lowercaseChar() - 'a')

    /** Usage of the top-row digit key [d] (0..9) at US position. */
    fun digit(d: Int): Int = if (d == 0) 0x27 else 0x1E + d - 1

    /** Usage of function key F[n] (1..12). */
    fun f(n: Int): Int = F1 + n - 1
}

/** Modifier bits of byte 0 of the keyboard report. */
object HIDModifiers {
    const val NONE = 0
    const val L_CTRL = 0x01
    const val L_SHIFT = 0x02
    const val L_ALT = 0x04
    const val L_GUI = 0x08
    const val R_CTRL = 0x10
    const val R_SHIFT = 0x20
    const val R_ALT = 0x40
    const val R_GUI = 0x80
}

/** Consumer page (0x0C) usages. */
object HIDConsumer {
    const val NEXT = 0xB5
    const val PREVIOUS = 0xB6
    const val PLAY_PAUSE = 0xCD
    const val MUTE = 0xE2
    const val VOLUME_UP = 0xE9
    const val VOLUME_DOWN = 0xEA
}

/** Mouse button bits. */
object HIDMouseButtons {
    const val LEFT = 1
    const val RIGHT = 2
    const val MIDDLE = 4
}

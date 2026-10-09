package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers

/** One key press: key usage plus modifier bits held with it. */
data class KeyStroke(val usage: Int, val modifiers: Int = 0)

/**
 * Maps characters to key strokes for a given host keyboard layout.
 * HID sends key positions; the host layout decides which character appears.
 */
interface KeyboardLayout {
    val id: String

    /**
     * @return strokes to type [c] in order (more than one for dead-key
     * sequences), or null when the layout cannot type it.
     */
    fun strokesFor(c: Char): List<KeyStroke>?
}

/** Layout backed by a char → strokes table filled by subclasses in `init`. */
abstract class TableKeyboardLayout(override val id: String) : KeyboardLayout {

    private val table = HashMap<Char, List<KeyStroke>>()

    init {
        key(' ', HIDKeys.SPACE)
        key('\n', HIDKeys.ENTER)
        key('\t', HIDKeys.TAB)
    }

    override fun strokesFor(c: Char): List<KeyStroke>? = table[c]

    protected fun key(c: Char, usage: Int, mods: Int = HIDModifiers.NONE) {
        table[c] = listOf(KeyStroke(usage, mods))
    }

    protected fun seq(c: Char, vararg strokes: KeyStroke) {
        table[c] = strokes.toList()
    }

    /** [normal] unshifted and [shifted] with Shift on key [usage]. */
    protected fun pair(usage: Int, normal: Char, shifted: Char) {
        key(normal, usage)
        key(shifted, usage, HIDModifiers.L_SHIFT)
    }
}

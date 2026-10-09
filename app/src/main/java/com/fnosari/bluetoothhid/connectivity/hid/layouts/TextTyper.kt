package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDReport

/**
 * Result of converting text to keyboard reports.
 *
 * @property text normalized text the reports type.
 * @property reportCharIndex for each report, index in [text] of the char it belongs to
 * (trailing release → text.length).
 * @property unmappable indices in [text] of skipped characters.
 */
class TypedText(
    val text: String,
    val reports: List<HIDReport.Keyboard>,
    val reportCharIndex: List<Int>,
    val unmappable: List<Int>
) {
    /** First char not fully typed when only [sentReports] reports were sent. */
    fun charIndexAt(sentReports: Int): Int =
        if (sentReports >= reportCharIndex.size) text.length else reportCharIndex[sentReports]
}

/** Converts text into the shortest safe keyboard report sequence for a layout. */
object TextTyper {

    fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    fun type(text: String, layout: KeyboardLayout): TypedText {
        val norm = normalize(text)
        val reports = ArrayList<HIDReport.Keyboard>(norm.length * 2)
        val charIndex = ArrayList<Int>(norm.length * 2)
        val unmappable = ArrayList<Int>()

        var prev: KeyStroke? = null
        var releaseNext = false

        norm.forEachIndexed { i, c ->
            val strokes = layout.strokesFor(c)
            if (strokes == null) {
                unmappable += i
                return@forEachIndexed
            }
            strokes.forEachIndexed { s, stroke ->
                val p = prev
                if (p != null && (releaseNext || p.usage == stroke.usage || p.modifiers != stroke.modifiers)) {
                    reports += HIDReport.Keyboard.RELEASE
                    charIndex += i
                }
                reports += HIDReport.Keyboard(stroke.modifiers, listOf(stroke.usage))
                charIndex += i
                prev = stroke
                releaseNext = s < strokes.lastIndex
            }
        }
        if (prev != null) {
            reports += HIDReport.Keyboard.RELEASE
            charIndex += norm.length
        }
        return TypedText(norm, reports, charIndex, unmappable)
    }
}

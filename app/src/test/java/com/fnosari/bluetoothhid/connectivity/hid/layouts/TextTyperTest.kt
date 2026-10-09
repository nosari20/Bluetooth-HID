package com.fnosari.bluetoothhid.connectivity.hid.layouts

import com.fnosari.bluetoothhid.connectivity.hid.HIDKeys
import com.fnosari.bluetoothhid.connectivity.hid.HIDModifiers.L_SHIFT
import com.fnosari.bluetoothhid.connectivity.hid.HIDReport.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TextTyperTest {

    private val release = Keyboard.RELEASE
    private fun k(c: Char, mods: Int = 0) = Keyboard(mods, listOf(HIDKeys.letter(c)))

    @Test
    fun differentKeysNoReleaseBetween() {
        assertEquals(listOf(k('a'), k('b'), release), TextTyper.type("ab", UsLayout).reports)
    }

    @Test
    fun repeatedCharsReleaseBetween() {
        assertEquals(listOf(k('a'), release, k('a'), release), TextTyper.type("aa", UsLayout).reports)
    }

    @Test
    fun modifierChangeInsertsRelease() {
        assertEquals(listOf(k('a'), release, k('a', L_SHIFT), release), TextTyper.type("aA", UsLayout).reports)
        assertEquals(listOf(k('a', L_SHIFT), release, k('b'), release), TextTyper.type("Ab", UsLayout).reports)
        assertEquals(listOf(k('a', L_SHIFT), k('b', L_SHIFT), release), TextTyper.type("AB", UsLayout).reports)
    }

    @Test
    fun crlfBecomesSingleEnter() {
        val t = TextTyper.type("a\r\nb\rc\n", UsLayout)
        assertEquals("a\nb\nc\n", t.text)
        assertEquals(3, t.reports.count { it.keys == listOf(HIDKeys.ENTER) })
    }

    @Test
    fun unmappableSkippedAndReported() {
        val t = TextTyper.type("a☃b", UsLayout)
        assertEquals(listOf(1), t.unmappable)
        assertEquals(listOf(k('a'), k('b'), release), t.reports)
    }

    @Test
    fun deadKeyReleasesBetweenStrokes() {
        val t = TextTyper.type("ê", Layouts.FR_WIN)
        assertEquals(
            listOf(Keyboard(0, listOf(HIDKeys.LEFT_BRACKET)), release, k('e'), release),
            t.reports
        )
    }

    @Test
    fun emptyTextHasNoReports() {
        assertTrue(TextTyper.type("", UsLayout).reports.isEmpty())
    }

    @Test
    fun charIndexAtMapsReportsToChars() {
        val t = TextTyper.type("abc", UsLayout) // a, b, c, R
        assertEquals(0, t.charIndexAt(0))
        assertEquals(2, t.charIndexAt(2))
        assertEquals(3, t.charIndexAt(3))
        assertEquals(3, t.charIndexAt(4))
        val r = TextTyper.type("aa", UsLayout) // a, R, a, R
        assertEquals(1, r.charIndexAt(1))
    }

    /**
     * Replays reports like a host: a character is produced when its key goes
     * from released to pressed, with the modifiers of that report.
     */
    private fun hostDecode(reports: List<Keyboard>, layout: KeyboardLayout): String {
        val reverse = HashMap<KeyStroke, Char>()
        for (code in 32..126) {
            val s = layout.strokesFor(code.toChar())
            if (s != null && s.size == 1) reverse[s[0]] = code.toChar()
        }
        val out = StringBuilder()
        var prev = Keyboard.RELEASE
        for (r in reports) {
            for (usage in r.keys) if (usage !in prev.keys) {
                out.append(reverse[KeyStroke(usage, r.modifiers)] ?: '?')
            }
            prev = r
        }
        return out.toString()
    }

    @Test
    fun randomPasswordsRoundTrip() {
        val rnd = Random(42)
        for (layout in listOf(UsLayout, Layouts.FR_WIN, Layouts.FR_LINUX, FrMacLayout)) {
            val alphabet = (32..126).map { it.toChar() }.filter { layout.strokesFor(it)?.size == 1 }
            repeat(50) {
                val pwd = String(CharArray(200) { alphabet[rnd.nextInt(alphabet.size)] })
                val decoded = hostDecode(TextTyper.type(pwd, layout).reports, layout)
                assertEquals("layout ${layout.id}", pwd, decoded)
            }
        }
    }
}

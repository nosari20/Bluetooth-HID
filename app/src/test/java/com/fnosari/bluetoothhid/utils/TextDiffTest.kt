package com.fnosari.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {
    @Test fun append() = assertEquals(TextEdit(0, "c"), TextDiff.diff("ab", "abc"))
    @Test fun delete() = assertEquals(TextEdit(1, ""), TextDiff.diff("abc", "ab"))
    @Test fun autocorrectReplace() = assertEquals(TextEdit(2, "he"), TextDiff.diff("teh", "the"))
    @Test fun same() = assertEquals(TextEdit(0, ""), TextDiff.diff("abc", "abc"))
    @Test fun clear() = assertEquals(TextEdit(3, ""), TextDiff.diff("abc", ""))
}

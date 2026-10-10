package com.fnosari.bluetoothhid.utils

/** Edit turning one text into another: [deleteCount] backspaces then [insert]. */
data class TextEdit(val deleteCount: Int, val insert: String)

/** Common-prefix diff used to forward IME edits as key strokes. */
object TextDiff {
    fun diff(old: String, new: String): TextEdit {
        val prefix = old.commonPrefixWith(new).length
        return TextEdit(old.length - prefix, new.substring(prefix))
    }
}

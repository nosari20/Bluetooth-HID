package com.fnosari.bluetoothhid.connectivity.hid.layouts

/** All supported host layouts, addressable by id (stored in preferences). */
object Layouts {
    val FR_WIN = FrAzertyLayout(linux = false)
    val FR_LINUX = FrAzertyLayout(linux = true)

    val ALL: List<KeyboardLayout> = listOf(UsLayout, FR_WIN, FR_LINUX, FrMacLayout)

    fun byId(id: String): KeyboardLayout = ALL.firstOrNull { it.id == id } ?: UsLayout

    /** Default layout id for a phone language code (e.g. "fr"). */
    fun defaultIdFor(language: String): String = if (language == "fr") FR_WIN.id else UsLayout.id
}

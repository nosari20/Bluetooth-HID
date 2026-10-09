package com.fnosari.bluetoothhid.persistance.preferences

/** Preference keys. */
object Preferences {
    const val TYPING_SPEED = "typing_speed"            // TypingSpeed.name, default NORMAL
    const val DEFAULT_LAYOUT = "default_layout"        // layout id, default from phone language
    const val AUTO_TYPE = "auto_type"                  // Boolean, default false
    const val ENTER_AT_END = "enter_at_end"            // Boolean, default false
    const val AUTO_RECONNECT = "auto_reconnect"        // Boolean, default true
    const val LAST_HOST = "last_host"                  // MAC address, default ""
    const val MOUSE_SENSITIVITY = "mouse_sensitivity"  // Double, default 1.0

    /** Layout id chosen for one host. */
    fun hostLayoutKey(address: String) = "host_layout_$address"
}

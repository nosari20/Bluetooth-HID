package com.fnosari.bluetoothhid.model

/** Pause between two HID reports. */
enum class TypingSpeed(val delayMs: Long) {
    FAST(6),
    NORMAL(10),
    SAFE(25);

    companion object {
        fun fromName(name: String): TypingSpeed = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

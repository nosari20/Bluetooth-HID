package com.fnosari.bluetoothhid.utils

/**
 * Two-step unlock (YouTube style): a tap on the lock overlay arms the gate,
 * and only a tap on Unlock within [windowMs] unlocks. Stray taps never unlock.
 */
class UnlockGate(private val windowMs: Long = 3000L) {

    private var armedAt: Long? = null

    fun isArmed(now: Long): Boolean = armedAt?.let { now - it < windowMs } ?: false

    /** Tap on the overlay background: shows the Unlock button. */
    fun tapBackground(now: Long) {
        armedAt = now
    }

    /** Tap on Unlock: true when it unlocks. Always disarms. */
    fun tapUnlock(now: Long): Boolean {
        val ok = isArmed(now)
        armedAt = null
        return ok
    }
}

package com.fnosari.bluetoothhid.services

/**
 * When the HID service may run as a foreground service (Play policy: only while doing
 * something the user notices) and when it should stop altogether.
 */
object ServicePolicy {

    /** Foreground (with notification) only while a host is connecting/connected or text is being typed. */
    fun needsForeground(state: HIDState, typing: Boolean): Boolean =
        typing || state is HIDState.Connecting || state is HIDState.Connected

    /** Stop the service when nothing needs it and no app screen is visible. */
    fun shouldStop(state: HIDState, typing: Boolean, appVisible: Boolean): Boolean =
        !appVisible && !needsForeground(state, typing)
}

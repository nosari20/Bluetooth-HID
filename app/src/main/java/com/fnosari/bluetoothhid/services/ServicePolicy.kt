package com.fnosari.bluetoothhid.services

/** When the HID service is busy, and when it should stop. */
object ServicePolicy {

    /** Busy while a host is connecting/connected or text is being typed. */
    fun isBusy(state: HIDState, typing: Boolean): Boolean =
        typing || state is HIDState.Connecting || state is HIDState.Connected

    /** Stop the service when nothing needs it and no app screen is visible. */
    fun shouldStop(state: HIDState, typing: Boolean, appVisible: Boolean): Boolean =
        !appVisible && !isBusy(state, typing)
}

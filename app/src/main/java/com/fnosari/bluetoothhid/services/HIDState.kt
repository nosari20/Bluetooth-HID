package com.fnosari.bluetoothhid.services

import com.fnosari.bluetoothhid.connectivity.hid.HIDDevice
import com.fnosari.bluetoothhid.model.Host

/** Connection state exposed by [HIDForegroundService]. */
sealed class HIDState {
    data object Starting : HIDState()
    data object Idle : HIDState()
    data class Connecting(val host: Host) : HIDState()
    data class Connected(val host: Host) : HIDState()
    data class Unavailable(val code: HIDDevice.HIDDeviceExitCode) : HIDState()
}

/** Progress of the current tracked typing job, in reports. */
data class TypingProgress(val sent: Int, val total: Int)

/** End of a tracked typing job. */
sealed class TypingEvent {
    data object Completed : TypingEvent()
    data object Cancelled : TypingEvent()

    /** Typing stopped early; [text] from [atChar] was not typed. */
    data class Interrupted(val atChar: Int, val text: String) : TypingEvent()
}

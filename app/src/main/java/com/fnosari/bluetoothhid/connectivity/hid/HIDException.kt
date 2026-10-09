package com.fnosari.bluetoothhid.connectivity.hid

/**
 * Signals a Bluetooth HID error.
 *
 * @param message optional detail about the error.
 */
class HIDException(message: String?) : Exception(message)

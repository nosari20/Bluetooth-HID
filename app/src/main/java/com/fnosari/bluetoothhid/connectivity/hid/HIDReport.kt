package com.fnosari.bluetoothhid.connectivity.hid

/**
 * One input report, matching [HIDDescriptor]. [toBytes] returns the payload
 * without the report ID (BluetoothHidDevice.sendReport takes the ID apart).
 */
sealed class HIDReport {
    abstract val id: Int
    abstract fun toBytes(): ByteArray

    /** Report ID 1: modifiers, reserved, up to 6 pressed key usages. */
    data class Keyboard(val modifiers: Int = 0, val keys: List<Int> = emptyList()) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray {
            val bytes = ByteArray(8)
            bytes[0] = modifiers.toByte()
            keys.take(6).forEachIndexed { i, k -> bytes[2 + i] = k.toByte() }
            return bytes
        }

        companion object {
            const val ID = 1
            val RELEASE = Keyboard()
        }
    }

    /** Report ID 2: buttons, relative X/Y, wheel, horizontal pan. */
    data class Mouse(
        val buttons: Int = 0,
        val dx: Int = 0,
        val dy: Int = 0,
        val wheel: Int = 0,
        val pan: Int = 0
    ) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray = byteArrayOf(
            (buttons and 0x07).toByte(),
            clamp(dx).toByte(),
            clamp(dy).toByte(),
            clamp(wheel).toByte(),
            clamp(pan).toByte()
        )

        companion object {
            const val ID = 2
            fun clamp(v: Int): Int = v.coerceIn(-127, 127)
        }
    }

    /** Report ID 3: one 16-bit consumer usage (0 = released). */
    data class Consumer(val usage: Int = 0) : HIDReport() {
        override val id: Int get() = ID

        override fun toBytes(): ByteArray =
            byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())

        companion object {
            const val ID = 3
            val RELEASE = Consumer()
        }
    }
}

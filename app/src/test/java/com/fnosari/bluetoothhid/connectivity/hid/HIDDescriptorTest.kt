package com.fnosari.bluetoothhid.connectivity.hid

import org.junit.Assert.assertEquals
import org.junit.Test

class HIDDescriptorTest {

    private data class Item(val tag: Int, val data: Int)

    /** Parse short items: prefix byte = tag(4) type(2) size(2). */
    private fun items(bytes: ByteArray): List<Item> {
        val out = mutableListOf<Item>()
        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i].toInt() and 0xFF
            val size = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            var data = 0
            for (b in 0 until size) data = data or ((bytes[i + 1 + b].toInt() and 0xFF) shl (8 * b))
            out += Item(prefix and 0xFC, data)
            i += 1 + size
        }
        return out
    }

    @Test
    fun collectionsAreBalanced() {
        val its = items(HIDDescriptor.BYTES)
        val open = its.count { it.tag == 0xA0 }
        val close = its.count { it.tag == 0xC0 }
        assertEquals(open, close)
    }

    @Test
    fun reportIdsInOrder() {
        val ids = items(HIDDescriptor.BYTES).filter { it.tag == 0x84 }.map { it.data }
        assertEquals(listOf(HIDReport.Keyboard.ID, HIDReport.Mouse.ID, HIDReport.Consumer.ID), ids)
    }

    @Test
    fun inputSizesMatchReports() {
        val bits = mutableMapOf<Int, Int>()
        var id = 0; var size = 0; var count = 0
        for (it in items(HIDDescriptor.BYTES)) {
            when (it.tag) {
                0x84 -> id = it.data
                0x74 -> size = it.data
                0x94 -> count = it.data
                0x80 -> bits[id] = (bits[id] ?: 0) + size * count
            }
        }
        assertEquals(8 * 8, bits[HIDReport.Keyboard.ID])
        assertEquals(5 * 8, bits[HIDReport.Mouse.ID])
        assertEquals(2 * 8, bits[HIDReport.Consumer.ID])
    }
}

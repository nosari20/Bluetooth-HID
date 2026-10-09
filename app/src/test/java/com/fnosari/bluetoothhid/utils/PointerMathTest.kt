package com.fnosari.bluetoothhid.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerMathTest {

    @Test
    fun tinyMovesAccumulate() {
        val acc = PointerAccumulator(1f)
        var total = 0
        repeat(10) { total += acc.move(0.5f, 0f).first }
        assertTrue("total=$total", total >= 5)
    }

    @Test
    fun fastMovesAccelerate() {
        val slow = PointerAccumulator(1f).move(2f, 0f).first
        val fast = PointerAccumulator(1f).move(50f, 0f).first
        assertTrue(fast > 25 * slow)
    }

    @Test
    fun sensitivityScales() {
        val a = PointerAccumulator(1f).move(10f, 0f).first
        val b = PointerAccumulator(2f).move(10f, 0f).first
        assertTrue(b >= 2 * a - 1)
    }

    @Test
    fun scrollSteps() {
        val s = ScrollAccumulator(40f)
        assertEquals(2, s.scroll(100f))
        assertEquals(0, s.scroll(10f))
        assertEquals(1, s.scroll(10f))
        assertEquals(-1, s.scroll(-45f))
    }

    @Test
    fun splitKeepsSumAndBounds() {
        val parts = PointerMath.split(300, -10)
        assertEquals(300, parts.sumOf { it.first })
        assertEquals(-10, parts.sumOf { it.second })
        assertTrue(parts.all { it.first in -127..127 && it.second in -127..127 })
        assertEquals(listOf(5 to 5), PointerMath.split(5, 5))
        assertEquals(emptyList<Pair<Int, Int>>(), PointerMath.split(0, 0))
    }
}

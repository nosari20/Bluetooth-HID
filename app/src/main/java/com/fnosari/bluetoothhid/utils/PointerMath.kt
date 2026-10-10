package com.fnosari.bluetoothhid.utils

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Converts touch deltas (px) to mouse counts with acceleration and sub-pixel carry. */
class PointerAccumulator(var sensitivity: Float = 1f) {
    private var rx = 0f
    private var ry = 0f

    fun move(dxPx: Float, dyPx: Float): Pair<Int, Int> {
        val accel = 1f + min(hypot(dxPx, dyPx) / 25f, 2f)
        val fx = dxPx * sensitivity * accel + rx
        val fy = dyPx * sensitivity * accel + ry
        val ix = fx.toInt()
        val iy = fy.toInt()
        rx = fx - ix
        ry = fy - iy
        return ix to iy
    }

    fun reset() { rx = 0f; ry = 0f }
}

/** Converts two-finger vertical drag (px) to wheel steps. */
class ScrollAccumulator(private val pxPerStep: Float = 40f) {
    private var acc = 0f

    fun scroll(dyPx: Float): Int {
        acc += dyPx
        val steps = (acc / pxPerStep).toInt()
        acc -= steps * pxPerStep
        return steps
    }

    fun reset() { acc = 0f }
}

object PointerMath {
    private const val LIMIT = 127

    /** Splits a relative move into report-sized chunks (each axis within ±127). */
    fun split(dx: Int, dy: Int): List<Pair<Int, Int>> {
        if (dx == 0 && dy == 0) return emptyList()
        val n = max((abs(dx) + LIMIT - 1) / LIMIT, (abs(dy) + LIMIT - 1) / LIMIT)
        val out = ArrayList<Pair<Int, Int>>(n)
        var sx = 0
        var sy = 0
        for (i in 1..n) {
            val tx = dx * i / n
            val ty = dy * i / n
            out += (tx - sx) to (ty - sy)
            sx = tx
            sy = ty
        }
        return out
    }
}

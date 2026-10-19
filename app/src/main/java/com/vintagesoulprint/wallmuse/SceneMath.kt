package com.vintagesoulprint.wallmuse

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Android-independent editor geometry, covered by lightweight smoke tests. */
object SceneMath {
    fun scaledFraction(before: Float, gestureScale: Float): Float =
        (before * gestureScale).coerceIn(.10f, .85f)

    fun normalizePosition(initial: Float, deltaPx: Float, roomDimension: Float): Float =
        (initial + deltaPx / max(1f, roomDimension)).coerceIn(0f, 1f)

    fun containsRotatedPoint(dx: Float, dy: Float, width: Float, height: Float,
                             angleDegrees: Float, touchPadding: Float): Boolean {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val localX = dx * cos(radians).toFloat() + dy * sin(radians).toFloat()
        val localY = -dx * sin(radians).toFloat() + dy * cos(radians).toFloat()
        return abs(localX) <= width / 2f + touchPadding &&
            abs(localY) <= height / 2f + touchPadding
    }
}

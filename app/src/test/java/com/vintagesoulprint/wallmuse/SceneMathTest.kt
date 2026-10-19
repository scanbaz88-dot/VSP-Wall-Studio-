package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test

/** Pure Kotlin geometry assertions executed by Gradle in CI. */
class SceneMathTest {
    @Test fun zoomLimits() {
        assertEquals(.30f, SceneMath.scaledFraction(.20f, 1.5f), .0001f)
        assertEquals(.85f, SceneMath.scaledFraction(.70f, 5f), .0001f)
        assertEquals(.10f, SceneMath.scaledFraction(.20f, .01f), .0001f)
    }

    @Test fun dragClampsToPhoto() {
        assertEquals(.75f, SceneMath.normalizePosition(.5f, 25f, 100f), .0001f)
        assertEquals(1f, SceneMath.normalizePosition(.5f, 200f, 100f), .0001f)
        assertEquals(0f, SceneMath.normalizePosition(.5f, -200f, 100f), .0001f)
    }

    @Test fun hitTestRotatedArt() {
        assertTrue(SceneMath.containsRotatedPoint(0f, 0f, 100f, 200f, 0f, 0f))
        assertTrue(SceneMath.containsRotatedPoint(40f, 0f, 100f, 100f, 45f, 0f))
        assertFalse(SceneMath.containsRotatedPoint(90f, 0f, 100f, 100f, 0f, 0f))
        assertTrue(SceneMath.containsRotatedPoint(0f, 90f, 200f, 100f, 90f, 0f))
    }

    @Test fun touchMargins() {
        assertTrue(SceneMath.containsRotatedPoint(52f, 0f, 100f, 100f, 0f, 3f))
        assertFalse(SceneMath.containsRotatedPoint(58f, 0f, 100f, 100f, 0f, 3f))
    }
}

package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test

class PerspectiveGeometryTest {
    @Test fun identityIsValid(){assertTrue(PerspectiveGeometry.valid(PerspectiveGeometry.IDENTITY))}
    @Test fun movingFourCornersCreatesValidPerspective(){
        val points=floatArrayOf(.16f,.08f,.92f,.02f,1.12f,.92f,-.09f,1.05f)
        assertTrue(PerspectiveGeometry.valid(points))
    }
    @Test fun preventsFoldedQuads(){
        assertFalse(PerspectiveGeometry.valid(floatArrayOf(0f,0f,1f,1f,1f,0f,0f,1f)))
        assertFalse(PerspectiveGeometry.valid(floatArrayOf(0f,0f,0f,0f,1f,1f,0f,1f)))
    }
    @Test fun everyTapRotatesNinetyDegrees(){
        var a=0f
        repeat(4){a=PerspectiveGeometry.quarterTurn(a)}
        assertEquals(0f,a,.0001f)
        assertEquals(90f,PerspectiveGeometry.quarterTurn(0f),.0001f)
    }
}

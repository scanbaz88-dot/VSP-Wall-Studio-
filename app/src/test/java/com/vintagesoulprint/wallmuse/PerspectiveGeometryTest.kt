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

    @Test fun eightDirectionsStayConvexAtEveryStrength(){
        for(x in -1..1)for(y in -1..1){
            for(strength in listOf(0,15,38,70,100)){
                assertTrue("x=$x y=$y strength=$strength",
                    PerspectiveGeometry.valid(PerspectiveGeometry.preset(x,y,strength)))
            }
        }
    }
    @Test fun zeroStrengthIsStraightForAllDirections(){
        for(x in -1..1)for(y in -1..1)
            assertArrayEquals(PerspectiveGeometry.IDENTITY,
                PerspectiveGeometry.preset(x,y,0),.00001f)
    }
    @Test fun presetsAreNotIdenticalForOppositeViews(){
        assertFalse(PerspectiveGeometry.preset(-1,0,75).contentEquals(
            PerspectiveGeometry.preset(1,0,75)))
        assertFalse(PerspectiveGeometry.preset(0,-1,75).contentEquals(
            PerspectiveGeometry.preset(0,1,75)))
    }

}

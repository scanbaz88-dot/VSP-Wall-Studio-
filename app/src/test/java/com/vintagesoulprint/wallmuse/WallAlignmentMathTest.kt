package com.vintagesoulprint.wallmuse
import org.junit.Assert.*
import org.junit.Test
class WallAlignmentMathTest{
    @Test fun simpleHorizontalReference(){
        assertEquals(0f,WallAlignmentMath.angleDegrees(.2f,.4f,.8f,.4f)!!,.0001f)
    }
    @Test fun identifiesInclineWithoutCroppingArtwork(){
        val angle=WallAlignmentMath.angleDegrees(.1f,.4f,.9f,.48f)
        assertTrue(angle!! in 5f..7f)
    }
    @Test fun rejectsShortOrVerticalReference(){
        assertNull(WallAlignmentMath.angleDegrees(.4f,.4f,.4f,.9f))
        assertNull(WallAlignmentMath.angleDegrees(.3f,.4f,.33f,.41f))
        assertNull(WallAlignmentMath.angleDegrees(.1f,.1f,.2f,.9f))
    }
    @Test fun reversingEndpointsKeepsSlope(){
        val a=WallAlignmentMath.angleDegrees(.15f,.3f,.85f,.42f)!!
        val b=WallAlignmentMath.angleDegrees(.85f,.42f,.15f,.3f)!!
        assertEquals(a,b,.0001f)
    }
}
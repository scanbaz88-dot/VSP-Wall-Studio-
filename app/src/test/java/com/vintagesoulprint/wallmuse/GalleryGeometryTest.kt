package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test

class GalleryGeometryTest{
    @Test fun layoutKeepsAspectAndStaysInsideWall(){
        for(style in GalleryPlanner.Layout.values())for(n in 1..12){
            val ratios=(0 until n).map{if(it%2==0)1.5f else .7f}
            val result=GalleryGeometry.layout(ratios,style,.022f)
            assertEquals(n,result.size)
            for(i in 0 until n){
                val p=result[i];val h=p.widthFraction*ratios[i]
                assertTrue("left",p.cx-p.widthFraction*.5f>=-.0001f)
                assertTrue("right",p.cx+p.widthFraction*.5f<=1.0001f)
                assertTrue("top",p.cy-h*.5f>=-.0001f)
                assertTrue("bottom",p.cy+h*.5f<=1.0001f)
            }
        }
    }
    @Test fun scalingMovesCentersAndSizesTogether(){
        val a=listOf(GalleryGeometry.Item(1,.3f,.5f,.2f,1f),
             GalleryGeometry.Item(2,.7f,.5f,.2f,1f))
        val b=GalleryGeometry.scale(a,.5f)
        assertEquals(.4f,b[0].cx,.0001f)
        assertEquals(.6f,b[1].cx,.0001f)
        assertEquals(.1f,b[0].width,.0001f)
    }
    @Test fun groupAlignmentPreservesIdentityAndDimensions(){
        val a=listOf(GalleryGeometry.Item(1,.2f,.3f,.1f,1f),GalleryGeometry.Item(2,.7f,.6f,.2f,2f))
        val b=GalleryGeometry.align(a,GalleryGeometry.Align.LEFT)
        assertEquals(a.map{it.id},b.map{it.id})
        assertEquals(b[0].cx-b[0].width*.5f,b[1].cx-b[1].width*.5f,.0001f)
    }
}

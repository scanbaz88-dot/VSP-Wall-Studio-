package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test
class GalleryPlannerTest {
    @Test fun layoutsAlwaysStayOnWall(){
        for(style in GalleryPlanner.Layout.values())for(count in 1..12){
            val p=GalleryPlanner.arrange(count,style)
            assertEquals(count,p.size)
            assertTrue(p.all{it.cx in 0f..1f && it.cy in 0f..1f && it.widthFraction in .08f.. .4f})
        }
    }
    @Test fun alignmentSnapsOnlyNearGuides(){
        assertEquals(.5f,GalleryPlanner.snap(.484f, emptyList()),.00001f)
        assertEquals(.7f,GalleryPlanner.snap(.687f,listOf(.7f)),.00001f)
        assertEquals(.63f,GalleryPlanner.snap(.63f,listOf(.7f)),.00001f)
    }
}

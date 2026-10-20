package com.vintagesoulprint.wallmuse

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CameraPreviewMathTest {
    private fun transformedDimensions(t:CameraPreviewMath.Transform,w:Int,h:Int):Pair<Float,Float>{
        val m=t.values
        // Intrinsic sensor pixels map to W×H first, then the view matrix.
        val srcW=w.toFloat();val srcH=h.toFloat()
        val transformedW=kotlin.math.abs(m[0])*srcW+kotlin.math.abs(m[1])*srcH
        val transformedH=kotlin.math.abs(m[3])*srcW+kotlin.math.abs(m[4])*srcH
        return transformedW to transformedH
    }
    @Test fun portraitPreviewDoesNotStretchSensorOrZoomHorizontally(){
        val result=CameraPreviewMath.create(1920,1080,708,1450,90,0)
        assertEquals(1080,result.intrinsicWidth)
        assertEquals(1920,result.intrinsicHeight)
        val (w,h)=transformedDimensions(result,708,1450)
        assertTrue(w>=708f-.01f && h>=1450f-.01f)
        assertEquals(1080f/1920f,w/h,.0001f)
        assertTrue(result.values[0]<1.30f)
        assertEquals(0f,result.values[1],.001f)
    }
    @Test fun sensor270AlsoMaintainsPortraitRatio(){
        val result=CameraPreviewMath.create(1280,720,720,1500,270,0)
        val (w,h)=transformedDimensions(result,720,1500)
        assertEquals(720f/1280f,w/h,.0001f)
        assertTrue(w>=720f && h>=1500f)
    }
    @Test fun fullscreenAndEditorKeepSameAspect(){
        val a=CameraPreviewMath.create(1920,1080,708,960,90,0)
        val b=CameraPreviewMath.create(1920,1080,708,1560,90,0)
        val (aw,ah)=transformedDimensions(a,708,960)
        val (bw,bh)=transformedDimensions(b,708,1560)
        assertEquals(aw/ah,bw/bh,.0001f)
    }
    @Test fun landscapeDoesNotStretchEither(){
        val a=CameraPreviewMath.create(1920,1080,1560,708,90,90)
        val (w,h)=transformedDimensions(a,1560,708)
        assertEquals(1920f/1080f,w/h,.0001f)
        assertTrue(w>=1560f && h>=708f)
    }
}

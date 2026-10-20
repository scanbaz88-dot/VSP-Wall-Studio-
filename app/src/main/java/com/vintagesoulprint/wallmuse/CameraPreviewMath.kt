package com.vintagesoulprint.wallmuse

import kotlin.math.abs
import kotlin.math.max

/**
 * Pixel-correct TextureView preview transform.
 *
 * Important Android Camera2 detail: TextureView/SurfaceTexture ALREADY aligns the
 * camera sensor buffer to the device's NATURAL orientation. The older code applied
 * SENSOR_ORIENTATION again, then combined the wrong landscape buffer dimensions
 * with the tall preview view; that produced the blurry, hugely zoomed picture.
 *
 * This pure Kotlin math receives the RAW stream dimensions, SENSOR_ORIENTATION,
 * current view dimensions, and DISPLAY rotation. It rotates only for the
 * DISPLAY, scales uniformly in camera-pixel space, then center-crops to fill.
 * Nothing stretches the sensor image. No hardcoded +90 / +270 degree hacks.
 */
object CameraPreviewMath {
    data class Transform(
        val values: FloatArray,
        val intrinsicWidth:Int,
        val intrinsicHeight:Int,
        val displayedWidth:Int,
        val displayedHeight:Int,
        val coverScale:Float
    )
    fun create(
        bufferWidth:Int, bufferHeight:Int,
        viewWidth:Int, viewHeight:Int,
        sensorOrientation:Int, displayDegrees:Int
    ):Transform {
        require(bufferWidth>0 && bufferHeight>0 && viewWidth>0 && viewHeight>0)
        val sensorSwapped=((sensorOrientation%180)+180)%180!=0
        val intrinsicW=if(sensorSwapped)bufferHeight else bufferWidth
        val intrinsicH=if(sensorSwapped)bufferWidth else bufferHeight
        val rotated=((displayDegrees%360)+360)%360
        require(rotated%90==0)
        val quarter=rotated==90 || rotated==270
        val outputW=if(quarter)intrinsicH else intrinsicW
        val outputH=if(quarter)intrinsicW else intrinsicH
        val cover=max(viewWidth.toFloat()/outputW,viewHeight.toFloat()/outputH)
        val scaleX=cover*intrinsicW/viewWidth
        val scaleY=cover*intrinsicH/viewHeight
        val cx=viewWidth/2f
        val cy=viewHeight/2f
        // Display rotation only. Buffer's sensor compensation is done by TextureView.
        val m=when(rotated) {
            90 -> floatArrayOf(0f,scaleY,cx-scaleY*cy,-scaleX,0f,cy+scaleX*cx,0f,0f,1f)
            180 -> floatArrayOf(-scaleX,0f,cx*(1+scaleX),0f,-scaleY,cy*(1+scaleY),0f,0f,1f)
            270 -> floatArrayOf(0f,-scaleY,cx+scaleY*cy,scaleX,0f,cy-scaleX*cx,0f,0f,1f)
            else -> floatArrayOf(scaleX,0f,cx*(1-scaleX),0f,scaleY,cy*(1-scaleY),0f,0f,1f)
        }
        return Transform(m,intrinsicW,intrinsicH,outputW,outputH,cover)
    }
    /** Compute whether the preview preserves physical camera-pixel proportions. */
    fun renderedAspect(transform:Transform):Float=
        transform.displayedWidth.toFloat()/transform.displayedHeight
}

package com.vintagesoulprint.wallmuse

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/**
 * Four-point projective transform in normalized artwork coordinates.
 * Stored as x,y positions for TL, TR, BR, BL, not an affine skew.
 * Kotlin-only validation is also exercised in unit tests.
 */
object PerspectiveGeometry {
    val IDENTITY = floatArrayOf(0f,0f, 1f,0f, 1f,1f, 0f,1f)
    fun valid(points:FloatArray):Boolean {
        if(points.size!=8 || points.any{it.isNaN()||it.isInfinite()||it < -.4f||it > 1.4f})return false
        var direction=0f
        for(i in 0..3){
            val j=(i+1)%4;val k=(i+2)%4
            val ux=points[j*2]-points[i*2];val uy=points[j*2+1]-points[i*2+1]
            val vx=points[k*2]-points[j*2];val vy=points[k*2+1]-points[j*2+1]
            val cross=ux*vy-uy*vx
            if(abs(cross)<.012f)return false
            if(i==0)direction=cross else if(cross*direction<=0f)return false
            if(ux*ux+uy*uy<.025f)return false
        }
        var twiceArea=0f
        for(i in 0..3){
            val j=(i+1)%4
            twiceArea+=points[2*i]*points[2*j+1]-points[2*j]*points[2*i+1]
        }
        val area=abs(twiceArea)/2f
        return area>.08f
    }
    fun rotate(angle:Float):Float=((angle%360f)+360f)%360f
    fun quarterTurn(angle:Float):Float=rotate(angle+90f)
    fun clampSize(size:Float)=size.coerceIn(.08f,.88f)
    fun matrix(rect:RectF,points:FloatArray):Matrix {
        val m=Matrix()
        if(!valid(points))return m
        val source=floatArrayOf(rect.left,rect.top,rect.right,rect.top,
            rect.right,rect.bottom,rect.left,rect.bottom)
        val target=FloatArray(8){i->
            if(i%2==0)rect.left+rect.width()*points[i]
            else rect.top+rect.height()*points[i]
        }
        if(!m.setPolyToPoly(source,0,target,0,4))m.reset()
        return m
    }
}

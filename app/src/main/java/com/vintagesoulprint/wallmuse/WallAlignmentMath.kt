package com.vintagesoulprint.wallmuse

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

/**
 * Assisted horizontal-line alignment. The user marks two points along the
 * SAME physical horizontal reference in a still wall/photo or live preview.
 * This is NOT automatic wall detection or AR spatial tracking.
 */
object WallAlignmentMath {
    fun angleDegrees(x1:Float,y1:Float,x2:Float,y2:Float):Float? {
        val dx=x2-x1
        val dy=y2-y1
        if(!dx.isFinite()||!dy.isFinite()||hypot(dx,dy)<.08f)return null
        // A mostly vertical reference is not a horizontal alignment hint.
        if(kotlin.math.abs(dx)<.10f)return null
        val deg=(atan2(dy,dx)*180f/PI.toFloat())
        // A line drawn right-to-left has the same physical slope.
        val folded=when {
            deg>90f->deg-180f
            deg< -90f->deg+180f
            else->deg
        }
        if(!folded.isFinite()||kotlin.math.abs(folded)>30f)return null
        return folded
    }
}

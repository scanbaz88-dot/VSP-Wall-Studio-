package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min
import kotlin.math.max

/** The picker renders with the SAME native frame engine as the wall canvas. */
class FrameSwatchView(context:Context,private val frame:Int):View(context) {
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        c.drawColor(0xFFF0ECE5.toInt())
        val density=resources.displayMetrics.density
        val pad=9f*density
        val ratio=if(FrameEngine.isRound(frame))1f else
            if(FrameEngine.isOval(frame)) .75f else .82f
        val availableW=max(1f,width-2f*pad)
        val availableH=max(1f,height-2f*pad)
        val h=min(availableH,availableW/ratio)
        val w=h*ratio
        val inner=RectF((width-w)*.5f,(height-h)*.5f,
            (width+w)*.5f,(height+h)*.5f)
        val outer=FrameEngine.exterior(inner,frame,55f)
        val frameShadow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0x26504936;setShadowLayer(2.5f*density,0f,1.2f*density,0x4441372A)
        }
        c.save()
        c.clipRect(RectF(0f,0f,width.toFloat(),height.toFloat()))
        c.drawRect(outer,frameShadow)
        // Restrained generic painting remains inside the frame aperture.
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val oval=FrameEngine.isOval(frame)||FrameEngine.isRound(frame)
        val aperture=Path().apply {
            if(oval)addOval(inner,Path.Direction.CW)
            else addRect(inner,Path.Direction.CW)
        }
        c.save();c.clipPath(aperture)
        p.shader=LinearGradient(inner.left,inner.top,inner.right,inner.bottom,
            intArrayOf(0xFFE1D7C2.toInt(),0xFFA7B0A1.toInt(),0xFF9D8171.toInt()),
            null,Shader.TileMode.CLAMP)
        c.drawRect(inner,p);p.shader=null
        p.color=0xFF647667.toInt()
        val landscape=Path().apply {
            moveTo(inner.left,inner.bottom)
            lineTo(inner.left+inner.width()*.31f,inner.top+inner.height()*.52f)
            lineTo(inner.left+inner.width()*.53f,inner.top+inner.height()*.66f)
            lineTo(inner.left+inner.width()*.77f,inner.top+inner.height()*.33f)
            lineTo(inner.right,inner.bottom);close()
        }
        c.drawPath(landscape,p)
        p.color=0xFFD2AD7D.toInt()
        c.drawCircle(inner.left+inner.width()*.77f,inner.top+inner.height()*.24f,
            inner.width()*.074f,p)
        c.restore()
        FrameEngine.draw(c,inner,frame,55f)
        c.restore()
    }
}

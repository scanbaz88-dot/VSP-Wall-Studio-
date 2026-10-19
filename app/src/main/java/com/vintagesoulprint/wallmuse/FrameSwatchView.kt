package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** Fast in-app frame library thumbnails without external assets. */
class FrameSwatchView(context: Context,private val style:Int):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas){super.onDraw(c)
        val pad=8*resources.displayMetrics.density
        c.drawColor(0xFFE5DCCB.toInt())
        val ratio=if(FrameCatalog.isRound(style))1f else 1.33f
        val h=height-2*pad;val w=min(width-2*pad,h*ratio)
        val outer=RectF((width-w)*.5f,(height-h)*.5f,(width+w)*.5f,(height+h)*.5f)
        val border=if(style==0)0f else if(FrameCatalog.isOrnate(style))w*.11f else w*.067f
        val inner=RectF(outer.left+border,outer.top+border,outer.right-border,outer.bottom-border)
        val rounded=FrameCatalog.isOval(style)||FrameCatalog.isRound(style)
        paint.shader=LinearGradient(outer.left,outer.top,outer.right,outer.bottom,
            colors(style),null,Shader.TileMode.CLAMP)
        if(rounded)c.drawOval(outer,paint)else c.drawRoundRect(outer,2f,2f,paint)
        paint.shader=null
        val clip=Path().apply {if(rounded)addOval(inner,Path.Direction.CW)else addRect(inner,Path.Direction.CW)}
        c.save();c.clipPath(clip)
        paint.color=0xFFAEA98E.toInt();c.drawRect(inner,paint)
        paint.color=0xFF68746A.toInt();c.drawRect(inner.left,inner.centerY(),inner.right,inner.bottom,paint)
        paint.color=0xFF7C4C35.toInt();c.drawCircle(inner.left+inner.width()*.71f,inner.top+inner.height()*.23f,inner.width()*.12f,paint)
        paint.color=0xFF334F44.toInt()
        val path=Path().apply {moveTo(inner.left,inner.bottom);lineTo(inner.centerX(),inner.top+inner.height()*.28f)
            lineTo(inner.right,inner.bottom);close()}
        c.drawPath(path,paint);c.restore()
    }
    private fun colors(style:Int)=when(style){
        0->intArrayOf(0xFFE5DCCB.toInt(),0xFFE5DCCB.toInt())
        2,10->intArrayOf(0xFF101212.toInt(),0xFF5D6260.toInt())
        4,11->intArrayOf(0xFF947E65.toInt(),0xFFF2E6CF.toInt())
        3->intArrayOf(0xFF876547.toInt(),0xFFDFC093.toInt())
        5,6,7,8,16->intArrayOf(0xFF705027.toInt(),0xFFE3C075.toInt())
        14->intArrayOf(0xFF4D5254.toInt(),0xFFE2DFDB.toInt())
        12,13->intArrayOf(0xFF161615.toInt(),0xFF77654D.toInt())
        else->intArrayOf(0xFF342219.toInt(),0xFFAD8055.toInt())
    }
}

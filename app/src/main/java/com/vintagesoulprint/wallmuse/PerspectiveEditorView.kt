package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max

/**
 * Actual CROPPED and FRAMED artwork preview (never the untrimmed source/mocked room).
 * Easy mode: eight preset viewpoints and a power slider.
 * Fine mode: drag individual corners after switching the option on.
 * No photo or artwork is modified until the Apply action is confirmed.
 */
class PerspectiveEditorView(
    context:Context, private val artwork:Bitmap, source:FloatArray
):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val density=resources.displayMetrics.density
    private val quad=if(PerspectiveGeometry.valid(source))source.copyOf() else PerspectiveGeometry.IDENTITY.copyOf()
    private val page=RectF()
    private var active=-1
    var fineAdjust=false
        set(value){field=value;active=-1;invalidate()}
    fun result():FloatArray=quad.copyOf()
    fun setPreset(x:Int,y:Int,strength:Int){
        setPoints(PerspectiveGeometry.preset(x,y,strength))
    }
    private fun setPoints(points:FloatArray){
        if(PerspectiveGeometry.valid(points)){
            System.arraycopy(points,0,quad,0,8);invalidate()
        }
    }
    fun reset(){setPoints(PerspectiveGeometry.IDENTITY)}
    override fun onMeasure(w:Int,h:Int){
        setMeasuredDimension(resolveSize((340*density).toInt(),w),resolveSize((290*density).toInt(),h))
    }
    private fun pageRect():RectF {
        // Enough margin for the strongest perspective trapezoid and corner handles.
        val availableWidth=(width-90*density).coerceAtLeast(45f)
        val availableHeight=(height-65*density).coerceAtLeast(45f)
        val aspect=artwork.width.toFloat()/max(1,artwork.height)
        val x=min(availableWidth,availableHeight*aspect)
        val y=x/aspect
        return RectF((width-x)/2f,(height-y)/2f,(width+x)/2f,(height+y)/2f)
    }
    private fun pointX(i:Int)=page.left+quad[2*i]*page.width()
    private fun pointY(i:Int)=page.top+quad[2*i+1]*page.height()
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        canvas.drawColor(0xFF262C29.toInt())
        page.set(pageRect())
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        // A light neutral background makes the transparent frame's contour visible.
        val field=RectF(10*density,10*density,
            width-10*density,height-10*density)
        paint.color=0xFF494D45.toInt()
        canvas.drawRoundRect(field,10*density,10*density,paint)
        paint.color=0xFFAAA495.toInt()
        val original=PerspectiveGeometry.matrix(page,quad)
        canvas.save()
        canvas.concat(original)
        canvas.drawBitmap(artwork,null,page,paint)
        canvas.restore()
        if(!fineAdjust)return
        paint.shader=null
        val border=Path().apply {
            moveTo(pointX(0),pointY(0))
            for(i in 1..3)lineTo(pointX(i),pointY(i))
            close()
        }
        paint.style=Paint.Style.STROKE
        paint.color=0xFFD6BA88.toInt()
        paint.strokeWidth=2*density
        canvas.drawPath(border,paint)
        paint.style=Paint.Style.FILL
        for(i in 0..3){
            paint.color=0xFF242D29.toInt()
            canvas.drawCircle(pointX(i),pointY(i),9*density,paint)
            paint.color=0xFFD6BA88.toInt()
            canvas.drawCircle(pointX(i),pointY(i),6*density,paint)
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!fineAdjust)return super.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                page.set(pageRect())
                active=(0..3).minByOrNull{hypot(e.x-pointX(it),e.y-pointY(it))}?:-1
                if(active>=0&&hypot(e.x-pointX(active),e.y-pointY(active))>36*density)active=-1
                return true
            }
            MotionEvent.ACTION_MOVE->{
                if(active>=0){
                    val test=quad.copyOf()
                    test[2*active]=((e.x-page.left)/page.width()).coerceIn(-.3f,1.3f)
                    test[2*active+1]=((e.y-page.top)/page.height()).coerceIn(-.3f,1.3f)
                    if(PerspectiveGeometry.valid(test))setPoints(test)
                }
                return true
            }
            MotionEvent.ACTION_UP->{active=-1;performClick();return true}
            MotionEvent.ACTION_CANCEL->{active=-1;return true}
        }
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}

package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive fixed-output-aspect crop. Unlike a border-percent preset, users can
 * move and resize the crop rectangle to choose precisely which part of the image
 * appears in their wall-art print. The source pixels remain unchanged.
 */
class CropEditorView(
    context: Context,
    private val bitmap: Bitmap,
    private val outputAspect: Float,
    layer: WallCanvasView.ArtLayer
) : View(context) {
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val picture=RectF()
    private val crop=RectF()
    private val original=RectF()
    private val shade=0xB4000000.toInt()
    private val stroke=0xFFD6BA88.toInt()
    private val cornersRadius=7f*density
    private val hitRadius=36f*density
    private var touchStartX=0f
    private var touchStartY=0f
    // 0 = none, 1 = move, 2..5 = TL,TR,BR,BL corner
    private var action=0

    init {
        val saved=RectF(layer.cropLeft,layer.cropTop,layer.cropRight,layer.cropBottom)
        val valid=saved.left>=0f&&saved.top>=0f&&saved.right<=1f&&saved.bottom<=1f&&
            saved.width()>.02f&&saved.height()>.02f&&
            abs(saved.width()/saved.height()-ratio())<.025f
        if(valid && (saved.left>.0001f||saved.top>.0001f||saved.right<.9999f||saved.bottom<.9999f)) {
            crop.set(saved)
        } else {
            crop.set(centerCrop())
            // Older projects used a symmetric percentage trim; start near that crop.
            val trim=layer.trim.coerceIn(0f,.24f)
            if(trim>0f){
                val rect=RectF(trim,trim,1f-trim,1f-trim)
                crop.set(coverInside(rect))
            }
        }
        isClickable=true
        contentDescription="Photo crop editor. Drag selection or corner handles."
    }
    private fun ratio()=outputAspect.coerceIn(.1f,10f)*bitmap.height.toFloat()/bitmap.width.coerceAtLeast(1)

    private fun coverInside(bounds:RectF):RectF {
        val r=ratio()
        val availableW=bounds.width()
        val availableH=bounds.height()
        val cropW=min(availableW,availableH*r)
        val cropH=cropW/r
        return RectF(bounds.centerX()-cropW/2f,bounds.centerY()-cropH/2f,
            bounds.centerX()+cropW/2f,bounds.centerY()+cropH/2f)
    }
    private fun centerCrop()=coverInside(RectF(0f,0f,1f,1f))

    fun resetCrop(){crop.set(centerCrop());invalidate()}
    fun cropRect():RectF=RectF(crop)

    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val desired=(390f*density).toInt()
        setMeasuredDimension(resolveSize(desired,widthMeasureSpec),resolveSize(desired,heightMeasureSpec))
    }
    private fun imageRect():RectF{
        val padding=12f*density
        val areaW=(width-2*padding).coerceAtLeast(1f)
        val areaH=(height-2*padding).coerceAtLeast(1f)
        val scale=min(areaW/bitmap.width.coerceAtLeast(1),areaH/bitmap.height.coerceAtLeast(1))
        val w=bitmap.width*scale
        val h=bitmap.height*scale
        return RectF(width/2f-w/2f,height/2f-h/2f,width/2f+w/2f,height/2f+h/2f)
    }
    private fun pixelX(nx:Float)=picture.left+nx*picture.width()
    private fun pixelY(ny:Float)=picture.top+ny*picture.height()
    private fun normalizedX(x:Float)=((x-picture.left)/picture.width()).coerceIn(0f,1f)
    private fun normalizedY(y:Float)=((y-picture.top)/picture.height()).coerceIn(0f,1f)

    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        canvas.drawColor(0xFF202423.toInt())
        picture.set(imageRect())
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true
        canvas.drawBitmap(bitmap,null,picture,paint)
        val left=pixelX(crop.left);val top=pixelY(crop.top)
        val right=pixelX(crop.right);val bottom=pixelY(crop.bottom)
        paint.color=shade
        canvas.drawRect(picture.left,picture.top,picture.right,top,paint)
        canvas.drawRect(picture.left,bottom,picture.right,picture.bottom,paint)
        canvas.drawRect(picture.left,top,left,bottom,paint)
        canvas.drawRect(right,top,picture.right,bottom,paint)
        paint.style=Paint.Style.STROKE
        paint.color=stroke
        paint.strokeWidth=2.3f*density
        canvas.drawRect(left,top,right,bottom,paint)
        paint.color=0x91FFFFFF.toInt()
        paint.strokeWidth=1f*density
        for(i in 1..2){
            val x=left+(right-left)*i/3f
            val y=top+(bottom-top)*i/3f
            canvas.drawLine(x,top,x,bottom,paint)
            canvas.drawLine(left,y,right,y,paint)
        }
        paint.style=Paint.Style.FILL
        for ((x,y) in listOf(left to top,right to top,right to bottom,left to bottom)){
            paint.color=0xFF292925.toInt()
            canvas.drawCircle(x,y,cornersRadius+2f*density,paint)
            paint.color=stroke
            canvas.drawCircle(x,y,cornersRadius,paint)
        }
    }

    override fun onTouchEvent(event:MotionEvent):Boolean{
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{
                picture.set(imageRect())
                val corners=listOf(
                    pixelX(crop.left) to pixelY(crop.top),
                    pixelX(crop.right) to pixelY(crop.top),
                    pixelX(crop.right) to pixelY(crop.bottom),
                    pixelX(crop.left) to pixelY(crop.bottom))
                val nearest=corners.withIndex().minByOrNull{
                    hypot(event.x-it.value.first,event.y-it.value.second)
                }
                action=if(nearest!=null&&hypot(event.x-nearest.value.first,event.y-nearest.value.second)<=hitRadius)
                    2+nearest.index
                    else if(event.x in pixelX(crop.left)..pixelX(crop.right)&&
                        event.y in pixelY(crop.top)..pixelY(crop.bottom))1 else 0
                touchStartX=normalizedX(event.x)
                touchStartY=normalizedY(event.y)
                original.set(crop)
                return true
            }
            MotionEvent.ACTION_MOVE->{
                if(action==0) return true
                val x=normalizedX(event.x)
                val y=normalizedY(event.y)
                if(action==1){
                    val left=(original.left+x-touchStartX).coerceIn(0f,1f-original.width())
                    val top=(original.top+y-touchStartY).coerceIn(0f,1f-original.height())
                    crop.set(left,top,left+original.width(),top+original.height())
                }else{
                    // Drag a corner, fixing its opposite corner and locking the print ratio.
                    val anchorX=when(action){2,5->original.right;else->original.left}
                    val anchorY=when(action){2,3->original.bottom;else->original.top}
                    val dx=abs(anchorX-x)
                    val dy=abs(anchorY-y)
                    val r=ratio()
                    val boundaryX=if(action==2||action==5)anchorX else 1f-anchorX
                    val boundaryY=if(action==2||action==3)anchorY else 1f-anchorY
                    val maxW=min(boundaryX,boundaryY*r).coerceAtLeast(.005f)
                    val minW=min(maxW*.5f,max(.045f,r*.045f))
                    val requested=if(abs(x-touchStartX)>=abs(y-touchStartY)*r)dx else dy*r
                    val newW=requested.coerceIn(minW,maxW)
                    val newH=newW/r
                    val left=if(action==2||action==5)anchorX-newW else anchorX
                    val top=if(action==2||action==3)anchorY-newH else anchorY
                    crop.set(left.coerceIn(0f,1f),top.coerceIn(0f,1f),
                        (left+newW).coerceIn(0f,1f),(top+newH).coerceIn(0f,1f))
                }
                invalidate();return true
            }
            MotionEvent.ACTION_UP->{action=0;performClick();return true}
            MotionEvent.ACTION_CANCEL->{action=0;return true}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}

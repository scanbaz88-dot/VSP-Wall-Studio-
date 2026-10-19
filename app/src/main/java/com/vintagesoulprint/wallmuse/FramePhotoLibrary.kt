package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

/** Original generated picture-frame image assets with transparent apertures.
 *  No downloads, tracking, or external assets. Nine-slice keeps rectangular
 *  frame corners natural when print sizes are switched between portrait and landscape.
 */
object FramePhotoLibrary {
    data class Photo(val filename:String,val left:Float,val top:Float,
                     val right:Float,val bottom:Float,val oval:Boolean=false)
    private val frames=listOf(
        Photo("black_metal.webp",.1361f,.1000f,.1124f,.0714f),
        Photo("white_classic.webp",.1620f,.1567f,.1564f,.1014f),
        Photo("natural_oak.webp",.1538f,.1502f,.1598f,.0798f),
        Photo("dark_walnut.webp",.1437f,.1532f,.1377f,.1216f),
        Photo("distressed_wood.webp",.1570f,.1667f,.1512f,.1157f),
        Photo("black_gallery.webp",.1726f,.2383f,.1607f,.1606f),
        Photo("antique_gold.webp",.1833f,.2127f,.1667f,.1357f),
        Photo("victorian_carved.webp",.1688f,.2548f,.1375f,.1442f),
        Photo("aged_bronze.webp",.1737f,.1923f,.1796f,.1490f),
        Photo("antique_oval.webp",.1845f,.1454f,.1250f,.1366f,true),
        Photo("round_gold.webp",.1556f,.1415f,.1444f,.1122f,true),
        Photo("oval_black.webp",.1726f,.1538f,.1845f,.1448f,true)
    )
    private val cache=mutableMapOf<Int,Bitmap>()
    fun info(id:Int):Photo?=frames.getOrNull(id-17)
    @Synchronized
    fun bitmap(context:Context,id:Int):Bitmap?{
        info(id)?:return null
        return cache[id]?:try {
            context.assets.open("frames/"+info(id)!!.filename).use { input ->
                BitmapFactory.decodeStream(input)
            }?.also { cache[id]=it }
        }catch (_:Exception){null}
    }
    fun outerBounds(inner:RectF,photo:Photo):RectF {
        val outW=inner.width()/(1f-photo.left-photo.right)
        val outH=inner.height()/(1f-photo.top-photo.bottom)
        return RectF(inner.left-outW*photo.left,inner.top-outH*photo.top,
            inner.right+outW*photo.right,inner.bottom+outH*photo.bottom)
    }
    fun innerBounds(outer:RectF,photo:Photo):RectF=RectF(
        outer.left+outer.width()*photo.left,outer.top+outer.height()*photo.top,
        outer.right-outer.width()*photo.right,outer.bottom-outer.height()*photo.bottom)
    fun draw(canvas:Canvas,context:Context,id:Int,inner:RectF,outer:RectF,paint:Paint){
        val asset=bitmap(context,id)?:return
        val photo=info(id)?:return
        paint.reset()
        paint.isAntiAlias=true
        paint.isFilterBitmap=true
        if(photo.oval) {
            canvas.drawBitmap(asset,null,outer,paint)
            return
        }
        val xs=intArrayOf(0,(photo.left*asset.width).roundToInt(),
            ((1f-photo.right)*asset.width).roundToInt(),asset.width)
        val ys=intArrayOf(0,(photo.top*asset.height).roundToInt(),
            ((1f-photo.bottom)*asset.height).roundToInt(),asset.height)
        val dx=floatArrayOf(outer.left,inner.left,inner.right,outer.right)
        val dy=floatArrayOf(outer.top,inner.top,inner.bottom,outer.bottom)
        for (y in 0..2) for (x in 0..2) {
            if(xs[x+1]<=xs[x]||ys[y+1]<=ys[y]||dx[x+1]<=dx[x]||dy[y+1]<=dy[y])continue
            canvas.drawBitmap(asset,Rect(xs[x],ys[y],xs[x+1],ys[y+1]),
                RectF(dx[x],dy[y],dx[x+1],dy[y+1]),paint)
        }
    }
}

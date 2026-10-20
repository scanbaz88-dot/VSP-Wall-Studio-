package com.vintagesoulprint.wallmuse

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Explainable, on-device artwork color analysis. No internet calls, no AI
 * inference or upload of photos. A recommendation, not a claim of taste.
 */
object FrameAdvisor {
    data class Suggestion(val frameId:Int,val explanation:String)
    fun suggest(bitmap:Bitmap):Suggestion {
        var warm=0.0;var bright=0.0;var chroma=0.0;var count=0
        val xs=12;val ys=12
        for(y in 0 until ys)for(x in 0 until xs){
            val px=bitmap.getPixel((bitmap.width-1)*x/(xs-1),(bitmap.height-1)*y/(ys-1))
            if(Color.alpha(px)<64)continue
            val r=Color.red(px).toDouble()
            val g=Color.green(px).toDouble()
            val b=Color.blue(px).toDouble()
            warm+=r-b
            bright+=(r+g+b)/3
            chroma+=maxOf(r,g,b)-minOf(r,g,b)
            count++
        }
        if(count==0)return Suggestion(17,"neutral")
        warm/=count;bright/=count;chroma/=count
        return when {
            bright<85->Suggestion(5,"gold for dark art")
            warm>35 && bright>165->Suggestion(20,"dark walnut contrast")
            warm>20->Suggestion(19,"natural oak for warm tones")
            chroma<35->Suggestion(17,"slim black for neutral art")
            bright>185->Suggestion(22,"gallery black for light colors")
            else->Suggestion(1,"classic dark walnut")
        }
    }
}

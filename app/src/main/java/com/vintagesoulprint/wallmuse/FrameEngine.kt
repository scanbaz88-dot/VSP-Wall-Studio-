package com.vintagesoulprint.wallmuse

import android.graphics.*
import kotlin.math.*

/**
 * VSP Native Frame Engine v2.4.
 * No extracted stock frame images, no low-resolution assets, no rectangular
 * photo stretched into a portrait/landscape opening.
 *
 * A frame is built around the selected artwork aperture. Each layer (body,
 * bevel, material grain, inner lip and contact shadow) uses the SAME geometry.
 * Profiles scale with print width in centimetres, not as a fixed percentage
 * of the bitmap, so the art remains the focal point at different print sizes.
 *
 * Stateless renderer: safe to use from thumbnails, live previews and exports.
 */
object FrameEngine {
    enum class Material { WOOD, METAL, GILT, PAINT, CANVAS, FLOAT }
    data class Style(
        val material:Material, val dark:Int, val light:Int,
        val accent:Int, val profileCm:Float, val ornament:Int=0
    )
    private fun color(hex:Long)=hex.toInt()
    // IDs preserve earlier draft compatibility. Old photo-based IDs 17..28
    // are mapped onto these new native vector/material profiles.
    val styles=listOf(
        Style(Material.CANVAS,color(0xFF8A8175),color(0xFFD3C9B9),color(0xFF5E5245),0f),
        Style(Material.WOOD,color(0xFF302019),color(0xFF856040),color(0xFF3C281A),2.0f),
        Style(Material.METAL,color(0xFF121416),color(0xFF505457),color(0xFF0A0B0C),.9f),
        Style(Material.WOOD,color(0xFF8D6945),color(0xFFD3B58B),color(0xFF684B2E),1.5f),
        Style(Material.PAINT,color(0xFFB8A992),color(0xFFF2EEE5),color(0xFF8C7F6D),2.0f),
        Style(Material.METAL,color(0xFF8C652F),color(0xFFF0D49C),color(0xFF5E431F),1.1f),
        Style(Material.GILT,color(0xFF624320),color(0xFFCCAD68),color(0xFF3E2815),2.1f,1),
        Style(Material.GILT,color(0xFF553316),color(0xFFE4C282),color(0xFF39230F),2.8f,3),
        Style(Material.GILT,color(0xFF6A4A26),color(0xFFDAB77B),color(0xFF432D17),2.3f,2),
        Style(Material.WOOD,color(0xFF442D20),color(0xFF9E7851),color(0xFF2E1C12),1.8f),
        Style(Material.METAL,color(0xFF0C0F11),color(0xFF555A5C),color(0xFF070808),1.2f),
        Style(Material.CANVAS,color(0xFF796653),color(0xFFD7C7AA),color(0xFF594738),.7f),
        Style(Material.FLOAT,color(0xFF201B17),color(0xFF76624C),color(0xFF11100F),1.2f),
        Style(Material.WOOD,color(0xFF2D1F17),color(0xFF88684C),color(0xFF201710),2.3f,2),
        Style(Material.METAL,color(0xFF555B63),color(0xFFDDE0E2),color(0xFF383C41),1.1f),
        Style(Material.METAL,color(0xFF5A3629),color(0xFFC58D6B),color(0xFF45281E),1.1f),
        Style(Material.GILT,color(0xFF674727),color(0xFFE1C180),color(0xFF432D17),2.2f,2),
        Style(Material.METAL,color(0xFF141516),color(0xFF656564),color(0xFF070809),.8f),
        Style(Material.PAINT,color(0xFFC4B9A6),color(0xFFF6F1E8),color(0xFF857E73),1.8f),
        Style(Material.WOOD,color(0xFF8D6847),color(0xFFDABE91),color(0xFF5A4129),1.8f),
        Style(Material.WOOD,color(0xFF37251B),color(0xFF926D4F),color(0xFF231912),2.0f),
        Style(Material.WOOD,color(0xFF66503D),color(0xFFB89E7F),color(0xFF443426),2.0f),
        Style(Material.METAL,color(0xFF101315),color(0xFF3D4143),color(0xFF090B0D),1.5f),
        Style(Material.GILT,color(0xFF67461C),color(0xFFE8C477),color(0xFF3A230B),2.5f,3),
        Style(Material.GILT,color(0xFF6A482B),color(0xFFD0A56F),color(0xFF392414),2.3f,2),
        Style(Material.GILT,color(0xFF4C3724),color(0xFF9F805A),color(0xFF2E2118),2.1f,1),
        Style(Material.GILT,color(0xFF70502C),color(0xFFD3AD70),color(0xFF443018),1.8f,1),
        Style(Material.GILT,color(0xFF745127),color(0xFFE1BF83),color(0xFF4A311A),1.8f,2),
        Style(Material.WOOD,color(0xFF101214),color(0xFF606366),color(0xFF08090B),1.8f),
        Style(Material.CANVAS,color(0xFF867B6C),color(0xFFDCD4C9),color(0xFF65594C),.65f),
        Style(Material.FLOAT,color(0xFF786044),color(0xFFC19F74),color(0xFF493422),1.4f),
        Style(Material.FLOAT,color(0xFF151515),color(0xFF49443F),color(0xFF090909),1.4f),
        Style(Material.CANVAS,color(0xFF7E7569),color(0xFFD6CCBB),color(0xFF4C4239),1.0f)
    )

    fun style(index:Int):Style=styles[index.coerceIn(0,styles.lastIndex)]
    fun isOval(id:Int)=id in listOf(8,10,26,28)
    fun isRound(id:Int)=id in listOf(9,16,27)
    fun isCanvas(id:Int)=style(id).material==Material.CANVAS
    fun isOrnate(id:Int)=style(id).ornament>0
    fun profilePx(id:Int, artWidth:Float, printWidthCm:Float):Float {
        val profile=style(id).profileCm
        if(profile<=0f)return 0f
        val px=artWidth*profile/printWidthCm.coerceAtLeast(8f)
        // Gallery frames are usually ~1-4 cm wide; avoid enormous bands.
        return px.coerceIn(.55f, min(artWidth*.082f,artWidth/7f))
    }
    fun exterior(aperture:RectF,id:Int,printWidthCm:Float):RectF {
        val stroke=profilePx(id,aperture.width(),printWidthCm)
        return RectF(aperture.left-stroke,aperture.top-stroke,
                     aperture.right+stroke,aperture.bottom+stroke)
    }
    private fun shape(path:Path,r:RectF,oval:Boolean):Path {
        path.reset()
        if(oval)path.addOval(r,Path.Direction.CW)
        else path.addRoundRect(r,max(0.2f,r.width()*.003f),
            max(0.2f,r.width()*.003f),Path.Direction.CW)
        return path
    }
    private fun lighten(a:Int,k:Float)=Color.rgb(
        (Color.red(a)+(255-Color.red(a))*k).toInt().coerceIn(0,255),
        (Color.green(a)+(255-Color.green(a))*k).toInt().coerceIn(0,255),
        (Color.blue(a)+(255-Color.blue(a))*k).toInt().coerceIn(0,255))
    private fun darken(a:Int,k:Float)=Color.rgb(
        (Color.red(a)*(1f-k)).toInt().coerceIn(0,255),
        (Color.green(a)*(1f-k)).toInt().coerceIn(0,255),
        (Color.blue(a)*(1f-k)).toInt().coerceIn(0,255))
    private fun inset(r:RectF,k:Float)=RectF(r.left+k,r.top+k,r.right-k,r.bottom-k)
    private fun stroke(canvas:Canvas,r:RectF,oval:Boolean,color:Int,w:Float) {
        if(r.width()<1||r.height()<1)return
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style=Paint.Style.STROKE;this.color=color;strokeWidth=w
        }
        if(oval)canvas.drawOval(r,p)else canvas.drawRect(r,p)
    }

    /** Outer frame, inner rebate and profile lit from upper left. Call after art. */
    fun draw(c:Canvas, aperture:RectF, id:Int, printWidthCm:Float) {
        if(id==0) return
        val st=style(id)
        val outer=exterior(aperture,id,printWidthCm)
        val band=aperture.left-outer.left
        val oval=isOval(id)||isRound(id)
        if(band<=0.01f)return
        val outerPath=shape(Path(),outer,oval)
        val insidePath=shape(Path(),aperture,oval)
        val ring=Path(outerPath).apply{op(insidePath,Path.Op.DIFFERENCE)}
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        c.save()
        c.clipPath(ring)
        // Body paint: continuous directional surface illumination, not a
        // bitmap stretched or a photo of a pre-existing frame.
        p.shader=LinearGradient(outer.left,outer.top,outer.right,outer.bottom,
            intArrayOf(lighten(st.light,.17f),st.light,st.dark,darken(st.dark,.30f)),
            floatArrayOf(0f,.24f,.71f,1f),Shader.TileMode.CLAMP)
        c.drawPath(outerPath,p)
        p.shader=null

        // Convex profiles: several extremely thin bands following the aperture,
        // with a clean dark recess and a very narrow bright inner bevel.
        stroke(c,inset(outer,band*.16f),oval,lighten(st.light,.18f),max(.65f,band*.13f))
        stroke(c,inset(outer,band*.37f),oval,darken(st.dark,.09f),max(.6f,band*.12f))
        stroke(c,inset(outer,band*.57f),oval,lighten(st.light,.11f),max(.5f,band*.15f))
        stroke(c,inset(outer,band*.83f),oval,darken(st.accent,.22f),max(.6f,band*.15f))

        // Material micro-detail, clipped to the frame's ring. A small set of
        // deterministic irregularities avoids a repeated tiled pattern.
        if(st.material==Material.WOOD || st.material==Material.PAINT){
            p.style=Paint.Style.STROKE
            val n=55
            for(i in 0 until n){
                val f=(i+.4f)/n
                val jitter=sin(i*7.3f+id*.93f)*band*.035f
                p.strokeWidth=max(.15f,band*.035f)
                p.color=if(i%3==0)0x22FFFFFF else 0x260A0603
                val y=outer.top+outer.height()*f+jitter
                val x=outer.left+outer.width()*f+jitter
                // Run grain along moulding, not across the artwork.
                c.drawLine(outer.left,y,outer.right,y, p)
                c.drawLine(x,outer.top,x,outer.bottom,p)
            }
            p.style=Paint.Style.FILL
        } else if(st.material==Material.METAL){
            val n=20
            p.style=Paint.Style.STROKE
            for(i in 0 until n){
                val x=outer.left+outer.width()*(i+.2f)/n
                p.color=if(i%2==0)0x19FFFFFF else 0x15000000
                p.strokeWidth=max(.15f,band*.025f)
                c.drawLine(x,outer.top,x,outer.bottom,p)
            }
            p.style=Paint.Style.FILL
        } else if(st.material==Material.GILT){
            val n=3+st.ornament
            for(i in 0 until n) {
                val f=(i+.5f)/n
                val x=outer.left+outer.width()*f
                val y=outer.top+outer.height()*f
                p.color=if(i%2==0)0x36FFF0C4 else 0x34724D21
                c.drawCircle(x,outer.top+band*.43f,max(.5f,band*.035f),p)
                c.drawCircle(x,outer.bottom-band*.43f,max(.5f,band*.035f),p)
                c.drawCircle(outer.left+band*.43f,y,max(.5f,band*.035f),p)
                c.drawCircle(outer.right-band*.43f,y,max(.5f,band*.035f),p)
            }
        }
        c.restore()

        // Sculpted, symmetrical ornament within the material frame.
        if(st.ornament>1 && !oval && band>=2.5f) {
            val ornaments=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                color=lighten(st.light,.20f);alpha=185
                style=Paint.Style.STROKE;strokeWidth=max(.55f,band*.105f)
            }
            val spacing=min(outer.width(),outer.height())
            for(i in 0..3) {
                c.save()
                c.rotate(i*90f,outer.centerX(),outer.centerY())
                val left=outer.left+band*.22f
                val top=outer.top+band*.22f
                val size=min(spacing*.14f,band*2.0f)
                c.drawArc(RectF(left,top,left+size,top+size),195f,125f,false,ornaments)
                c.restore()
            }
        }

        // Crucial: contact shadow and inner rebate overlap the artwork by < 1%.
        // This visually seats the art UNDER the inner lip instead of on the wall.
        val lip=max(.6f,min(band*.31f,min(aperture.width(),aperture.height())*.006f))
        stroke(c,inset(aperture,-lip*.40f),oval,darken(st.accent,.37f),lip*1.5f)
        stroke(c,inset(aperture,-lip*.10f),oval,lighten(st.light,.06f),lip*.80f)
        stroke(c,inset(aperture,lip*.55f),oval,0x63301F14, max(.45f,lip*.62f))

        if(st.material==Material.CANVAS){
            stroke(c,inset(aperture,lip*.6f),oval,0x355B4D3D,max(.45f,band*.16f))
        }
        if(st.material==Material.FLOAT){
            stroke(c,inset(aperture,-band*.22f),oval,0xFF121110.toInt(),
                max(.75f,band*.27f))
        }
    }
}

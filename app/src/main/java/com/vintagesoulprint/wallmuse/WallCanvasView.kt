package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/** Offline art preview; all geometry is independent of display resolution. */
class WallCanvasView(context: Context) : View(context) {
    data class ArtLayer(
        val id: Long, val bitmap: Bitmap,
        var cx: Float = .5f, var cy: Float = .46f, var widthFraction: Float = .3f,
        var angle: Float = 0f, var frame: Int = 1, val sourceUri: String? = null,
        var printW: Float = 60f, var printH: Float = 40f, var trim: Float = 0f
    ) { fun snapshot() = copy() }
    data class State(val elements: List<ArtLayer>, val selectedId: Long?)
    data class RenderSnapshot(val photo: Bitmap, val elements: List<ArtLayer>)

    var wallPhoto: Bitmap? = null; private set
    private val artworks = mutableListOf<ArtLayer>()
    var selectedId: Long? = null; private set
    var showArtwork = true; private set
    var showSelectionHandles = true
    var livePreview = false
        set(value){field=value;invalidate()}
    var onSelectionChanged: (() -> Unit)? = null
    var onGestureStart: (() -> Unit)? = null
    val artCount get() = artworks.size
    val selected get() = artworks.firstOrNull { it.id == selectedId }
    private var nextId = 1L
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photoRect = RectF()
    private var touchX = 0f; private var touchY = 0f
    private var originalX = 0f; private var originalY = 0f
    private var pinching = false; private var startDistance = 1f; private var startWidth = .3f
    private var midStartX = 0f; private var midStartY = 0f; private var gestureRecorded = false
    private val density = resources.displayMetrics.density

    /** An unobtrusive ivory plaster wall is ready before selecting any images. */
    init { isClickable = true; contentDescription = context.getString(R.string.drag_hint); setDefaultWall() }
    fun setDefaultWall() { setWall(createPlasterWall()) }
    private fun createPlasterWall(): Bitmap {
        val width = 1260; val height = 920
        val pixels = IntArray(width * height)
        val rnd = Random(4831)
        for (y in 0 until height) for (x in 0 until width) {
            val vignette = (8.0 * abs(x - width * .49) / width + 7.0 * abs(y - height * .42) / height).toInt()
            val grain = rnd.nextInt(-5, 6)
            val weave = if ((x % 61 < 2 || y % 73 < 2) && rnd.nextInt(8) == 0) -5 else 0
            val n = grain + weave - vignette
            pixels[y * width + x] = Color.rgb((236 + n).coerceIn(0,255),
                (231 + n).coerceIn(0,255), (217 + n).coerceIn(0,255))
        }
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    }
    fun setWall(bitmap: Bitmap) {
        wallPhoto = bitmap; artworks.clear(); selectedId = null; nextId=1L
        showArtwork = true; onSelectionChanged?.invoke(); invalidate()
    }
    /** Replace the photograph without discarding the artwork composed on the default wall. */
    fun replaceWallKeepArtwork(bitmap:Bitmap){
        wallPhoto=bitmap;showArtwork=true;onSelectionChanged?.invoke();invalidate()
    }
    fun clearWall() = setDefaultWall()
    fun addArtwork(bitmap: Bitmap, sourceUri: String): Boolean {
        if (artworks.size >= 12) return false
        val tallness = bitmap.height.toFloat() / max(1,bitmap.width)
        val widthCm = if (tallness > 1.08f) 40f else 60f
        val heightCm = widthCm * tallness
        val stagger = (artworks.size % 5) * .032f
        val initialWidth = min(.34f,.60f/max(.6f,tallness))
        val layer=ArtLayer(nextId++,bitmap,.49f+stagger,.44f+stagger,initialWidth,0f,1,
            sourceUri,widthCm,heightCm,0f)
        artworks.add(layer); selectedId=layer.id; showArtwork=true
        onSelectionChanged?.invoke();invalidate();return true
    }
    fun removeSelected(): Boolean {val removed=artworks.removeAll{it.id==selectedId};selectedId=artworks.lastOrNull()?.id
        if(removed){onSelectionChanged?.invoke();invalidate()};return removed}
    fun duplicateSelected(): Boolean {if(artworks.size>=12)return false
        val old=selected?:return false;val clone=old.copy(id=nextId++,cx=(old.cx+.07f).coerceIn(.1f,.9f),cy=(old.cy+.07f).coerceIn(.1f,.9f))
        artworks.add(clone);selectedId=clone.id;onSelectionChanged?.invoke();invalidate();return true}
    fun clearArtwork(){artworks.clear();selectedId=null;showArtwork=true;onSelectionChanged?.invoke();invalidate()}
    fun setFrame(frame:Int){selected?.frame=frame.coerceIn(0,FrameCatalog.frames.lastIndex);invalidate()}
    fun setPrintSize(wCm:Float,hCm:Float,wallCm:Float){selected?.let{it.printW=wCm;it.printH=hCm
        it.widthFraction=(wCm/wallCm).coerceIn(.10f,.85f)};onSelectionChanged?.invoke();invalidate()}
    fun recalibrate(wallCm:Float){artworks.forEach{it.widthFraction=(it.printW/wallCm).coerceIn(.10f,.85f)};invalidate()}
    fun setTrim(trim:Float){selected?.trim=trim.coerceIn(0f,.24f);invalidate()}
    fun setSize(frac:Float){selected?.widthFraction=frac.coerceIn(.08f,.88f);invalidate()}
    fun setAngle(degrees:Float){selected?.angle=degrees.coerceIn(-45f,45f);invalidate()}
    fun setBeforeMode(before:Boolean){showArtwork=!before;invalidate()}
    fun state()=State(artworks.map{it.snapshot()},selectedId)
    fun restore(state:State){artworks.clear();artworks.addAll(state.elements.map{it.snapshot()});selectedId=state.selectedId
        nextId=max(nextId,(artworks.maxOfOrNull{it.id}?:0L)+1);showArtwork=true;onSelectionChanged?.invoke();invalidate()}
    override fun onDraw(c:Canvas){super.onDraw(c);if(!livePreview)c.drawColor(Color.rgb(21,27,27))
        val room=wallPhoto?:return
        if(livePreview){
            // Transparent Canvas over the TextureView; match preview's view coordinates.
            photoRect.set(0f,0f,width.toFloat(),height.toFloat())
            if(showArtwork)artworks.forEach{drawArtwork(c,photoRect,it,
                showSelectionHandles&&it.id==selectedId,brush)}
        } else {
            val bound=RectF(10f*density,8f*density,width-10f*density,height-8f*density)
            photoRect.set(fit(room.width.toFloat(),room.height.toFloat(),bound))
            renderScene(c,photoRect,showArtwork&&showSelectionHandles)
        }
    }
    private fun fit(w:Float,h:Float,b:RectF):RectF {val s=min(b.width()/w,b.height()/h);val aw=w*s;val ah=h*s
        return RectF(b.centerX()-aw/2,b.centerY()-ah/2,b.centerX()+aw/2,b.centerY()+ah/2)}
    private fun renderScene(c:Canvas,room:RectF,handles:Boolean,force:Boolean=false,snapshot:RenderSnapshot?=null){
        val wall=snapshot?.photo?:wallPhoto?:return
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(wall,null,room,paint)
        if(!force&&!showArtwork)return
        (snapshot?.elements?:artworks).forEach{drawArtwork(c,room,it,handles&&it.id==selectedId,paint)}
    }
    private fun artworkDimensions(layer:ArtLayer,room:RectF):Pair<Float,Float>{
        val w=layer.widthFraction*room.width()
        val ratio=if(FrameCatalog.isRound(layer.frame))1f else layer.printH/max(layer.printW,1f)
        return Pair(w,w*ratio)
    }
    private fun drawArtwork(c:Canvas,room:RectF,l:ArtLayer,selected:Boolean,p:Paint){
        val(w,h)=artworkDimensions(l,room)
        val cx=room.left+l.cx*room.width();val cy=room.top+l.cy*room.height()
        val scale=min(w,h)
        val stroke=when{l.frame==0->0f;FrameCatalog.isCanvas(l.frame)->scale*.022f
            FrameCatalog.isOrnate(l.frame)->scale*.092f;else->scale*.055f}.coerceAtLeast(if(l.frame==0)0f else 2*density)
        val interior=RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2)
        val exterior=RectF(interior.left-stroke,interior.top-stroke,interior.right+stroke,interior.bottom+stroke)
        val oval=FrameCatalog.isOval(l.frame)||FrameCatalog.isRound(l.frame)
        c.save();c.rotate(l.angle,cx,cy)
        // A soft ambient shadow plus a short contact shadow, both follow the chosen silhouette.
        p.reset();p.isAntiAlias=true;p.color=Color.argb(25,15,13,11)
        shadowShape(c,exterior,oval,scale*.030f,scale*.042f,p)
        p.color=Color.argb(15,15,13,11)
        shadowShape(c,exterior,oval,scale*.045f,scale*.061f,p)
        if (l.frame!=0) drawFrameBody(c,l.frame,interior,exterior,oval,p)
        drawArtClipped(c,l,interior,oval,p)
        if(l.frame!=0) drawInsideLip(c,l.frame,interior,oval,p,scale)
        if(FrameCatalog.isCanvas(l.frame))drawCanvasDetails(c,interior,exterior,p,scale)
        if(FrameCatalog.isOrnate(l.frame))drawOrnaments(c,exterior,p,scale,oval,l.frame)
        if(selected){p.reset();p.isAntiAlias=true;p.color=Color.rgb(207,173,99)
            p.style=Paint.Style.STROKE;p.strokeWidth=1.5f*density
            val border=RectF(exterior.left-4*density,exterior.top-4*density,exterior.right+4*density,exterior.bottom+4*density)
            if(oval)c.drawOval(border,p) else c.drawRoundRect(border,2*density,2*density,p)
            p.style=Paint.Style.FILL
            for((x,y) in listOf(Pair(border.left,border.top),Pair(border.right,border.top),Pair(border.left,border.bottom),Pair(border.right,border.bottom))){
                if(!oval)c.drawCircle(x,y,3.5f*density,p)
            }
        }
        c.restore()
    }
    private fun shadowShape(c:Canvas,r:RectF,oval:Boolean,dx:Float,dy:Float,p:Paint){
        val soft=RectF(r.left+dx,r.top+dy,r.right+dx,r.bottom+dy)
        p.shader=RadialGradient(soft.centerX(),soft.centerY(),max(soft.width(),soft.height())*.76f,
            intArrayOf(Color.argb(66,8,7,5),Color.argb(33,8,7,5),Color.TRANSPARENT),floatArrayOf(0f,.65f,1f),Shader.TileMode.CLAMP)
        val outer=RectF(soft.left-soft.width()*.025f,soft.top-soft.height()*.025f,soft.right+soft.width()*.025f,soft.bottom+soft.height()*.025f)
        if(oval)c.drawOval(outer,p)else c.drawRoundRect(outer,3*density,3*density,p)
        p.shader=null
    }
    private fun styleColors(id:Int):IntArray= when(id){
        1->intArrayOf(0xFF382015.toInt(),0xFF976A43.toInt(),0xFF4B2E20.toInt()) // walnut
        2->intArrayOf(0xFF090B0B.toInt(),0xFF494A45.toInt(),0xFF101213.toInt()) // black
        3->intArrayOf(0xFF957254.toInt(),0xFFE1C6A0.toInt(),0xFF9B7758.toInt()) // oak
        4->intArrayOf(0xFFAC9E8C.toInt(),0xFFF1E7D7.toInt(),0xFFAC9D88.toInt()) // ivory
        5->intArrayOf(0xFF82612B.toInt(),0xFFF1D68B.toInt(),0xFF967138.toInt()) // brass
        6->intArrayOf(0xFF4E371A.toInt(),0xFFC19A4E.toInt(),0xFF60461E.toInt()) // antique
        7->intArrayOf(0xFF533915.toInt(),0xFFD1AE66.toInt(),0xFF5D401D.toInt()) // rococo
        8->intArrayOf(0xFF593D1E.toInt(),0xFFD4AE63.toInt(),0xFF503515.toInt()) // oval gold
        9->intArrayOf(0xFF321F16.toInt(),0xFF936640.toInt(),0xFF39291D.toInt()) // circle walnut
        10->intArrayOf(0xFF141516.toInt(),0xFF676763.toInt(),0xFF191A1A.toInt()) // oval black
        11->intArrayOf(0xFF877661.toInt(),0xFFDDCEB5.toInt(),0xFF9B8C77.toInt()) // canvas
        12->intArrayOf(0xFF1B1A18.toInt(),0xFF4A4031.toInt(),0xFF211F1A.toInt()) // floater
        13->intArrayOf(0xFF2D231C.toInt(),0xFF816749.toInt(),0xFF261B15.toInt()) // carved
        14->intArrayOf(0xFF52555A.toInt(),0xFFDDDADD.toInt(),0xFF606469.toInt()) // silver
        15->intArrayOf(0xFF513D2F.toInt(),0xFFB08D70.toInt(),0xFF523E31.toInt()) // bronze
        16->intArrayOf(0xFF51351D.toInt(),0xFFE4BE71.toInt(),0xFF69441B.toInt()) // round antique
        else->intArrayOf(0xFF262525.toInt(),0xFF6D5B48.toInt(),0xFF2B2826.toInt())
    }
    private fun drawShape(c:Canvas,r:RectF,oval:Boolean,p:Paint){if(oval)c.drawOval(r,p)else c.drawRoundRect(r,2*density,2*density,p)}
    private fun drawFrameBody(c:Canvas,id:Int,inside:RectF,outside:RectF,oval:Boolean,p:Paint){
        val col=styleColors(id)
        val angleShift=outside.width()*.13f
        p.reset();p.isAntiAlias=true
        p.shader=LinearGradient(outside.left,outside.top,outside.right+angleShift,outside.bottom,
            intArrayOf(col[0],col[1],col[2]),floatArrayOf(0f,.40f,1f),Shader.TileMode.CLAMP)
        drawShape(c,outside,oval,p);p.shader=null
        val band=(inside.left-outside.left)
        // Light hitting the upper/left bevel, and recessed dark bottom/right edge.
        val bevel=RectF(outside.left+band*.18f,outside.top+band*.18f,outside.right-band*.18f,outside.bottom-band*.18f)
        p.color=if(id==2||id==10)0xFF343839.toInt() else lighten(col[1],.16f)
        p.style=Paint.Style.STROKE;p.strokeWidth=max(.8f,band*.11f)
        drawShape(c,bevel,oval,p)
        val inner=RectF(inside.left-band*.18f,inside.top-band*.18f,inside.right+band*.18f,inside.bottom+band*.18f)
        p.color=darken(col[0],.25f);p.strokeWidth=max(1f,band*.19f);drawShape(c,inner,oval,p)
        if(id==1||id==3||id==4||id==9||id==13){
            // Restrained wood grain inside only the moulding, with a fixed seed
            // for stable output on every redraw and during JPEG export.
            val framePath=Path().apply{if(oval)addOval(outside,Path.Direction.CW)
                else addRoundRect(outside,2*density,2*density,Path.Direction.CW)}
            val openPath=Path().apply{if(oval)addOval(inside,Path.Direction.CW)
                else addRect(inside,Path.Direction.CW)}
            framePath.op(openPath,Path.Op.DIFFERENCE)
            c.save();c.clipPath(framePath)
            val rnd=Random(id*71+43)
            p.shader=null;p.style=Paint.Style.STROKE
            p.strokeWidth=max(.35f,density*.38f)
            val gap=max(2f,density*2.3f)
            for(j in 0..min(150,(outside.height()/gap).toInt())){
                val y=outside.top+j*gap
                p.color=if(j%4==0)0x22FFFFFF else 0x220B0806
                p.alpha=rnd.nextInt(8,39)
                val dx=rnd.nextFloat()*outside.width()*.05f
                c.drawLine(outside.left+dx,y,outside.right-dx,y+gap*.30f,p)
            }
            p.alpha=255;p.style=Paint.Style.FILL;c.restore()
        }
        if(id==12){ // a narrow dark floating gap between the canvas and outer rail
            p.color=0xFF100F0F.toInt();p.strokeWidth=max(2f,band*.47f);drawShape(c,inner,oval,p)
        }
        p.style=Paint.Style.FILL
    }
    private fun lighten(color:Int,amount:Float):Int=Color.rgb(
        (Color.red(color)+(255-Color.red(color))*amount).toInt().coerceIn(0,255),
        (Color.green(color)+(255-Color.green(color))*amount).toInt().coerceIn(0,255),
        (Color.blue(color)+(255-Color.blue(color))*amount).toInt().coerceIn(0,255))
    private fun darken(color:Int,amount:Float):Int=Color.rgb(
        (Color.red(color)*(1-amount)).toInt(),(Color.green(color)*(1-amount)).toInt(),
        (Color.blue(color)*(1-amount)).toInt())
    private fun drawArtClipped(c:Canvas,l:ArtLayer,dst:RectF,oval:Boolean,p:Paint){
        val image=l.bitmap
        val trim=l.trim.coerceIn(0f,.24f)
        val bounds=RectF(image.width*trim,image.height*trim,image.width*(1-trim),image.height*(1-trim))
        val imageRatio=bounds.width()/bounds.height();val targetRatio=dst.width()/dst.height()
        if(imageRatio>targetRatio){val wanted=bounds.height()*targetRatio;val extra=(bounds.width()-wanted)*.5f
            bounds.left+=extra;bounds.right-=extra
        }else{val wanted=bounds.width()/targetRatio;val extra=(bounds.height()-wanted)*.5f
            bounds.top+=extra;bounds.bottom-=extra}
        val source=Rect(bounds.left.roundToInt().coerceIn(0,image.width-1),bounds.top.roundToInt().coerceIn(0,image.height-1),
            bounds.right.roundToInt().coerceIn(1,image.width),bounds.bottom.roundToInt().coerceIn(1,image.height))
        c.save()
        val path=Path()
        if(oval)path.addOval(dst,Path.Direction.CW)else path.addRoundRect(dst,1*density,1*density,Path.Direction.CW)
        c.clipPath(path)
        p.reset();p.isAntiAlias=true;p.isFilterBitmap=true;p.color=Color.WHITE
        c.drawBitmap(image,source,dst,p)
        // Subtle protective glazing, never a flat opaque filter.
        if(!FrameCatalog.isCanvas(l.frame)){
            p.shader=LinearGradient(dst.left,dst.top,dst.right,dst.bottom,
                intArrayOf(0x19FFFFFF,0x00FFFFFF,0x10000000),null,Shader.TileMode.CLAMP)
            c.drawRect(dst,p);p.shader=null
        }
        c.restore()
    }
    private fun drawInsideLip(c:Canvas,id:Int,dst:RectF,oval:Boolean,p:Paint,size:Float){
        p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE
        p.color=if(id==11||id==12)0x8853483E.toInt() else 0xA81D1610.toInt()
        p.strokeWidth=max(.8f,size*.010f)
        drawShape(c,dst,oval,p)
        p.style=Paint.Style.FILL
    }
    private fun drawCanvasDetails(c:Canvas,inner:RectF,outer:RectF,p:Paint,scale:Float){
        p.reset();p.isAntiAlias=true
        // Very subtle linen crosshatch; the painting remains visually dominant.
        c.save();c.clipRect(inner)
        p.color=0x0BEEE7D9;p.strokeWidth=max(.3f,density*.32f)
        val pitch=max(3f,density*3.5f)
        var x=inner.left
        while(x<=inner.right){c.drawLine(x,inner.top,x,inner.bottom,p);x+=pitch}
        var y=inner.top
        while(y<=inner.bottom){c.drawLine(inner.left,y,inner.right,y,p);y+=pitch}
        c.restore()
        p.color=if(outer.width()>inner.width()*1.02)0x4D231F1C else 0x50837D71
        p.style=Paint.Style.STROKE;p.strokeWidth=max(.6f,scale*.006f)
        c.drawRoundRect(RectF(inner.left+1,inner.top+1,inner.right-1,inner.bottom-1),1f,1f,p)
        p.style=Paint.Style.FILL
    }
    private fun drawOrnaments(c:Canvas,outer:RectF,p:Paint,scale:Float,oval:Boolean,id:Int){
        val gold=if(id==13)0xFF998365.toInt() else 0xFFE5C584.toInt()
        p.reset();p.isAntiAlias=true;p.color=gold;p.alpha=135
        p.style=Paint.Style.STROKE;p.strokeWidth=max(.7f,scale*.007f)
        val inset=scale*.041f
        if(oval){
            val r=RectF(outer.left+inset,outer.top+inset,outer.right-inset,outer.bottom-inset)
            c.drawOval(r,p)
            val dots=30
            p.style=Paint.Style.FILL
            for(i in 0 until dots){val a=2*PI*i/dots;val x=r.centerX()+r.width()*.5f*cos(a).toFloat()
                val y=r.centerY()+r.height()*.5f*sin(a).toFloat()
                c.drawCircle(x,y,max(.65f,scale*.004f),p)}
        } else {
            for(i in 0..3){
                c.save();c.rotate(i*90f,outer.centerX(),outer.centerY())
                val left=outer.left+inset;val top=outer.top+inset
                c.drawArc(RectF(left,top,left+scale*.20f,top+scale*.20f),185f,93f,false,p)
                c.drawArc(RectF(left+scale*.019f,top+scale*.018f,left+scale*.16f,top+scale*.16f),192f,82f,false,p)
                c.restore()
            }
        }
        p.style=Paint.Style.FILL;p.alpha=255
    }
    private fun hitTest(x:Float,y:Float):ArtLayer?{for(i in artworks.size-1 downTo 0){val l=artworks[i]
        val(w,h)=artworkDimensions(l,photoRect);val cx=photoRect.left+l.cx*photoRect.width();val cy=photoRect.top+l.cy*photoRect.height()
        if(SceneMath.containsRotatedPoint(x-cx,y-cy,w,h,l.angle,18*density))return l};return null}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!isEnabled||!showArtwork)return super.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{val picked=hitTest(e.x,e.y);selectedId=picked?.id
                touchX=e.x;touchY=e.y;originalX=picked?.cx?:0f;originalY=picked?.cy?:0f
                gestureRecorded=false;pinching=false;onSelectionChanged?.invoke();invalidate();return true}
            MotionEvent.ACTION_POINTER_DOWN->{if(e.pointerCount>=2&&selected!=null){
                if(!gestureRecorded){onGestureStart?.invoke();gestureRecorded=true}
                startDistance=distance(e).coerceAtLeast(1f);startWidth=selected!!.widthFraction
                midStartX=(e.getX(0)+e.getX(1))/2;midStartY=(e.getY(0)+e.getY(1))/2
                originalX=selected!!.cx;originalY=selected!!.cy;pinching=true};return true}
            MotionEvent.ACTION_MOVE->{val l=selected?:return true
                if(pinching&&e.pointerCount>=2){l.widthFraction=SceneMath.scaledFraction(startWidth,distance(e)/startDistance)
                    val mx=(e.getX(0)+e.getX(1))/2;val my=(e.getY(0)+e.getY(1))/2
                    l.cx=SceneMath.normalizePosition(originalX,mx-midStartX,photoRect.width())
                    l.cy=SceneMath.normalizePosition(originalY,my-midStartY,photoRect.height())
                    onSelectionChanged?.invoke();invalidate();return true}
                if(e.pointerCount==1&&!pinching){val dx=e.x-touchX;val dy=e.y-touchY
                    if(!gestureRecorded&&hypot(dx,dy)>4*density){onGestureStart?.invoke();gestureRecorded=true}
                    if(gestureRecorded){l.cx=SceneMath.normalizePosition(originalX,dx,photoRect.width())
                        l.cy=SceneMath.normalizePosition(originalY,dy,photoRect.height());invalidate()};return true}}
            MotionEvent.ACTION_POINTER_UP->{val idx=if(e.actionIndex==0)1 else 0
                if(idx<e.pointerCount){touchX=e.getX(idx);touchY=e.getY(idx);originalX=selected?.cx?:originalX;originalY=selected?.cy?:originalY}
                pinching=false;return true}
            MotionEvent.ACTION_UP->{pinching=false;performClick();onSelectionChanged?.invoke();return true}
            MotionEvent.ACTION_CANCEL->{pinching=false;return true}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    private fun distance(e:MotionEvent)=hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1))
    fun captureForExport(wallReplacement:Bitmap?=null):RenderSnapshot?=(wallReplacement?:wallPhoto)?.let{RenderSnapshot(it,artworks.map{a->a.snapshot()})}
    fun exportBitmap(snapshot:RenderSnapshot,maxEdge:Int=3840):Bitmap{
        val room=snapshot.photo;val ratio=min(1f,maxEdge.toFloat()/max(room.width,room.height))
        val width=max(1,(room.width*ratio).toInt());val height=max(1,(room.height*ratio).toInt())
        val result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        renderScene(Canvas(result),RectF(0f,0f,width.toFloat(),height.toFloat()),false,true,snapshot)
        return result
    }
}

object FrameCatalog {
    data class Entry(val name:String,val category:String)
    val frames=listOf(
        Entry("Çerçevesiz","Modern"),Entry("Koyu ceviz · klasik","Ahşap"),
        Entry("Mat siyah · ince","Modern"),Entry("Doğal meşe","Ahşap"),
        Entry("Eskitilmiş fildişi","Ahşap"),Entry("Fırçalanmış altın","Metal"),
        Entry("Antika pirinç","Antika"),Entry("Rokoko altın oyma","Antika"),
        Entry("Oval antika altın","Oval & Yuvarlak"),Entry("Yuvarlak ceviz","Oval & Yuvarlak"),
        Entry("Oval mat siyah","Oval & Yuvarlak"),Entry("Galeri tuvali","Tuval"),
        Entry("Floating canvas","Tuval"),Entry("Viktoryen oyma koyu","Antika"),
        Entry("Fırçalanmış gümüş","Metal"),Entry("İnce bronz","Metal"),
        Entry("Yuvarlak antika altın","Oval & Yuvarlak")
    )
    val englishNames=listOf("Unframed","Classic dark walnut","Slim matte black","Natural oak",
        "Distressed ivory","Brushed gold","Antique brass","Rococo carved gold",
        "Antique gold oval","Round walnut","Matte black oval","Gallery canvas",
        "Floating canvas","Victorian dark carved","Brushed silver","Slim bronze","Round antique gold")
    fun displayName(id:Int):String=if(java.util.Locale.getDefault().language=="tr")frames[id].name else englishNames[id]
    fun categoryName(name: String): String =
        if (java.util.Locale.getDefault().language == "tr") name
        else when (name) {
            "Ahşap" -> "Wood"
            "Antika" -> "Antique"
            "Oval & Yuvarlak" -> "Oval & Round"
            "Tuval" -> "Canvas"
            else -> name
        }
    fun isOval(id:Int)=id==8||id==10
    fun isRound(id:Int)=id==9||id==16
    fun isCanvas(id:Int)=id==11||id==12
    fun isOrnate(id:Int)=id==7||id==8||id==13||id==16
}

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
        var printW: Float = 60f, var printH: Float = 40f, var trim: Float = 0f,
        var cropLeft: Float = 0f, var cropTop: Float = 0f,
        var cropRight: Float = 1f, var cropBottom: Float = 1f,
        var tlx:Float=0f, var tly:Float=0f,
        var trx:Float=1f, var tryY:Float=0f,
        var brx:Float=1f, var bry:Float=1f,
        var blx:Float=0f, var bly:Float=1f
    ) { fun snapshot() = copy() }
    data class State(val elements: List<ArtLayer>, val selectedId: Long?)
    data class RenderSnapshot(val photo: Bitmap, val elements: List<ArtLayer>)

    var wallPhoto: Bitmap? = null; private set
    private val artworks = mutableListOf<ArtLayer>()
    var selectedId: Long? = null; private set
    var showArtwork = true; private set
    var showSelectionHandles = true
    var focusMode=false
        set(value){field=value;focusHandlesVisible=false;invalidate()}
    private var focusHandlesVisible=false
    private val hideFocusHandles=Runnable {focusHandlesVisible=false;invalidate()}
    private fun perspective(layer:ArtLayer):FloatArray=floatArrayOf(
        layer.tlx,layer.tly,layer.trx,layer.tryY,
        layer.brx,layer.bry,layer.blx,layer.bly
    )
    fun selectedPerspective():FloatArray?=selected?.let{perspective(it)}
    /**
     * The editor must show precisely what the customer will buy/preview:
     * current free crop + chosen native frame, never the uncropped original.
     */
    fun framedCropForPerspective():Bitmap? {
        val l=selected?:return null
        val ratio=if(FrameEngine.isRound(l.frame))1f
            else (l.printH/l.printW.coerceAtLeast(1f)).coerceIn(.3f,5f)
        val innerW=kotlin.math.min(560f,820f/ratio)
        val innerH=innerW*ratio
        val inner=RectF(0f,0f,innerW,innerH)
        val outside=FrameEngine.exterior(inner,l.frame,l.printW)
        val margin=10f
        val result=Bitmap.createBitmap(
            (outside.width()+margin*2).roundToInt().coerceAtLeast(8),
            (outside.height()+margin*2).roundToInt().coerceAtLeast(8),
            Bitmap.Config.ARGB_8888
        )
        val dx=margin-outside.left;val dy=margin-outside.top
        val aperture=RectF(inner.left+dx,inner.top+dy,inner.right+dx,inner.bottom+dy)
        val frame=RectF(outside.left+dx,outside.top+dy,outside.right+dx,outside.bottom+dy)
        val c=Canvas(result)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val oval=FrameEngine.isOval(l.frame)||FrameEngine.isRound(l.frame)
        drawArtClipped(c,l,aperture,oval,paint)
        FrameEngine.draw(c,aperture,l.frame,l.printW)
        if(FrameEngine.isCanvas(l.frame))drawCanvasDetails(c,aperture,frame,paint,kotlin.math.min(innerW,innerH))
        return result
    }

    fun setPerspective(points:FloatArray){
        if(!PerspectiveGeometry.valid(points))return
        selected?.apply{
            tlx=points[0];tly=points[1];trx=points[2];tryY=points[3]
            brx=points[4];bry=points[5];blx=points[6];bly=points[7]
        }
        invalidate()
    }
    fun rotateQuarterTurn(){
        selected?.let{it.angle=PerspectiveGeometry.quarterTurn(it.angle)}
        onSelectionChanged?.invoke();invalidate()
    }
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
        val layer=ArtLayer(nextId++,bitmap,.49f+stagger,.44f+stagger,initialWidth,0f,20,
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
    /** Persist the user-selected image rectangle in normalized original-image coordinates. */
    fun setCrop(rect: RectF) {
        selected?.apply {
            cropLeft = rect.left.coerceIn(0f, 1f)
            cropTop = rect.top.coerceIn(0f, 1f)
            cropRight = rect.right.coerceIn(cropLeft + .001f, 1f)
            cropBottom = rect.bottom.coerceIn(cropTop + .001f, 1f)
            trim = 0f
        }
        invalidate()
    }
    fun setSize(frac:Float){selected?.widthFraction=frac.coerceIn(.08f,.88f);invalidate()}
    fun setAngle(degrees:Float){selected?.angle=PerspectiveGeometry.rotate(degrees);invalidate()}
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
                (showSelectionHandles||(focusMode&&focusHandlesVisible))&&it.id==selectedId,brush)}
        } else {
            val bound=RectF(10f*density,8f*density,width-10f*density,height-8f*density)
            photoRect.set(fit(room.width.toFloat(),room.height.toFloat(),bound))
            renderScene(c,photoRect,showArtwork&&(showSelectionHandles||(focusMode&&focusHandlesVisible)))
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
        val interior=RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2)
        val exterior=FrameEngine.exterior(interior,l.frame,l.printW)
        val oval=FrameEngine.isOval(l.frame)||FrameEngine.isRound(l.frame)
        c.save();c.rotate(l.angle,cx,cy)
        c.concat(PerspectiveGeometry.matrix(exterior,perspective(l)))
        // A soft ambient shadow plus a short contact shadow, both follow the chosen silhouette.
        p.reset();p.isAntiAlias=true;p.color=Color.argb(25,15,13,11)
        shadowShape(c,exterior,oval,scale*.030f,scale*.042f,p)
        p.color=Color.argb(15,15,13,11)
        shadowShape(c,exterior,oval,scale*.045f,scale*.061f,p)
        // Paint is inset underneath the frame's rebate, never pasted on top.
        // 100% vector frame geometry: identical in preview, live camera, export.
        drawArtClipped(c,l,interior,oval,p)
        FrameEngine.draw(c,interior,l.frame,l.printW)
        if(FrameEngine.isCanvas(l.frame))drawCanvasDetails(c,interior,exterior,p,scale)
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
        // A stack of translucent offset silhouettes simulates penumbra
        // without a harsh black rectangular drop shadow behind the artwork.
        val small=min(r.width(),r.height())
        val step=max(.45f,small*.006f)
        p.reset();p.isAntiAlias=true
        p.style=Paint.Style.STROKE
        for(k in 6 downTo 1){
            val alpha=(7-k)*3+2
            p.color=Color.argb(alpha,16,13,10)
            p.strokeWidth=step*(1f+k*.34f)
            val shadow=RectF(r.left+dx*k/6f-step*k*.35f,r.top+dy*k/6f-step*k*.35f,
                r.right+dx*k/6f+step*k*.35f,r.bottom+dy*k/6f+step*k*.35f)
            if(oval)c.drawOval(shadow,p) else c.drawRoundRect(shadow,2*density,2*density,p)
        }
        p.style=Paint.Style.FILL
    }
    private fun drawArtClipped(c:Canvas,l:ArtLayer,dst:RectF,oval:Boolean,p:Paint){
        val image=l.bitmap
        val trim=l.trim.coerceIn(0f,.24f)
        val left = (l.cropLeft + trim).coerceIn(0f, .98f)
        val top = (l.cropTop + trim).coerceIn(0f, .98f)
        val right = (l.cropRight - trim).coerceIn(left + .01f, 1f)
        val bottom = (l.cropBottom - trim).coerceIn(top + .01f, 1f)
        // Freeform manual selection must be respected exactly. Never perform
        // a hidden second, ratio-locked center crop after the user presses Apply.
        // If print proportions differ, scale the chosen rectangle to fill the
        // frame; the crop editor itself never adjusts or locks X/Y together.
        val bounds=RectF(image.width*left,image.height*top,image.width*right,image.height*bottom)
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
    private fun hitTest(x:Float,y:Float):ArtLayer?{
        for(i in artworks.size-1 downTo 0){
            val l=artworks[i]
            val (w,h)=artworkDimensions(l,photoRect)
            val cx=photoRect.left+l.cx*photoRect.width()
            val cy=photoRect.top+l.cy*photoRect.height()
            val inside=RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2)
            val outside=FrameEngine.exterior(inside,l.frame,l.printW)
            val rad=Math.toRadians(l.angle.toDouble())
            val dx=x-cx;val dy=y-cy
            val q=floatArrayOf(
                cx+dx*cos(rad).toFloat()+dy*sin(rad).toFloat(),
                cy-dx*sin(rad).toFloat()+dy*cos(rad).toFloat()
            )
            val inverse=Matrix()
            if(PerspectiveGeometry.matrix(outside,perspective(l)).invert(inverse))inverse.mapPoints(q)
            val pad=18*density
            if(q[0] in (outside.left-pad)..(outside.right+pad) &&
               q[1] in (outside.top-pad)..(outside.bottom+pad))return l
        }
        return null
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!isEnabled||!showArtwork)return super.onTouchEvent(e)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{val picked=hitTest(e.x,e.y);selectedId=picked?.id
                touchX=e.x;touchY=e.y;originalX=picked?.cx?:0f;originalY=picked?.cy?:0f
                if(picked!=null)parent?.requestDisallowInterceptTouchEvent(true)
                if(focusMode){removeCallbacks(hideFocusHandles);focusHandlesVisible=picked!=null}
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
            MotionEvent.ACTION_UP->{pinching=false;performClick();onSelectionChanged?.invoke()
                if(focusMode)postDelayed(hideFocusHandles,1300L)
                return true}
            MotionEvent.ACTION_CANCEL->{pinching=false
                if(focusMode)postDelayed(hideFocusHandles,300L)
                return true}
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
    /** Stable frame ids: previously saved projects keep their selected frame. */
    val frames=listOf(
        Entry("Çerçevesiz · fine art","Modern"),
        Entry("Koyu ceviz klasik","Ahşap"),
        Entry("Mat siyah ince","Modern"),
        Entry("Doğal meşe","Ahşap"),
        Entry("Eskitilmiş fildişi","Ahşap"),
        Entry("Fırçalanmış ince altın","Modern"),
        Entry("Antika pirinç","Antika"),
        Entry("Rokoko altın oyma","Antika"),
        Entry("Oval antika altın","Oval & Yuvarlak"),
        Entry("Yuvarlak ceviz","Oval & Yuvarlak"),
        Entry("Oval mat siyah","Oval & Yuvarlak"),
        Entry("Galeri tuvali","Tuval"),
        Entry("Floating canvas","Tuval"),
        Entry("Viktoryen koyu oyma","Antika"),
        Entry("Fırçalanmış gümüş","Modern"),
        Entry("İnce bronz metal","Modern"),
        Entry("Yuvarlak antika altın","Oval & Yuvarlak"),
        Entry("İnce siyah metal","Modern"),
        Entry("Klasik beyaz ahşap","Ahşap"),
        Entry("Açık doğal meşe","Ahşap"),
        Entry("Koyu ceviz derin profil","Ahşap"),
        Entry("Eskitilmiş rustik ahşap","Ahşap"),
        Entry("Siyah galeri","Modern"),
        Entry("Antika altın işleme","Antika"),
        Entry("Viktoryen sıcak altın","Antika"),
        Entry("Eskitilmiş bronz","Antika"),
        Entry("Oval antika bronz","Oval & Yuvarlak"),
        Entry("Yuvarlak altın","Oval & Yuvarlak"),
        Entry("Oval siyah ahşap","Oval & Yuvarlak"),
        Entry("Geniş galeri kanvas","Tuval"),
        Entry("Floating doğal meşe","Tuval"),
        Entry("Floating siyah","Tuval"),
        Entry("Eskitilmiş keten kanvas","Tuval")
    )
    val englishNames=listOf(
        "Unframed fine art","Classic dark walnut","Slim matte black","Natural oak",
        "Distressed ivory","Slim brushed gold","Antique brass","Rococo carved gold",
        "Antique gold oval","Round walnut","Matte black oval","Gallery canvas",
        "Floating canvas","Victorian dark carving","Brushed silver","Slim bronze",
        "Round antique gold","Slim black metal","Classic white wood","Light natural oak",
        "Deep dark walnut","Distressed rustic wood","Black gallery",
        "Antique gold carved","Victorian warm gold","Aged bronze",
        "Antique bronze oval","Round gold","Black wood oval",
        "Gallery wrap canvas","Floating natural oak","Floating black","Linen canvas"
    )
    fun displayName(id:Int):String = if(java.util.Locale.getDefault().language=="tr")
        frames[id.coerceIn(0,frames.lastIndex)].name
        else englishNames[id.coerceIn(0,englishNames.lastIndex)]
    fun categoryName(name:String):String=if(java.util.Locale.getDefault().language=="tr")name else when(name){
        "Ahşap" -> "Wood"
        "Antika" -> "Antique"
        "Oval & Yuvarlak" -> "Oval & Round"
        "Tuval" -> "Canvas"
        else -> name
    }
    fun isOval(id:Int)=FrameEngine.isOval(id)
    fun isRound(id:Int)=FrameEngine.isRound(id)
    fun isCanvas(id:Int)=FrameEngine.isCanvas(id)
    fun isOrnate(id:Int)=FrameEngine.isOrnate(id)
}

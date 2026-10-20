package com.vintagesoulprint.wallmuse

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface

/**
 * Native, compact, single-surface controls. Editor remains visible behind the
 * panel so people learn from the result rather than navigating nested dialogs.
 */
internal object StudioUi {
    const val CREAM=0xFFF6F1E7.toInt()
    const val GOLD=0xFFD6BA88.toInt()
    const val GREEN=0xEE202C27.toInt()
    fun dp(context:Context,i:Int)=(i*context.resources.displayMetrics.density+.5f).toInt()
    fun tr(context:Context,t:String,e:String)=
        if(context.resources.configuration.locales[0].language=="tr")t else e
    fun text(context:Context,value:String,size:Float=12f,bold:Boolean=false)=TextView(context).apply{
        text=value;textSize=size
        setTextColor(CREAM)
        if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
        gravity=Gravity.CENTER_VERTICAL
    }
    fun button(context:Context,caption:String,highlight:Boolean=false,click:()->Unit):Button =
        Button(context).apply{
            text=caption
            textSize=12f;isAllCaps=false
            minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
            setPadding(dp(context,8),0,dp(context,8),0)
            setTextColor(if(highlight)0xFF1B2921.toInt() else CREAM)
            background=GradientDrawable().apply{
                setColor(if(highlight)GOLD else 0xFF39483F.toInt())
                cornerRadius=dp(context,10).toFloat()
                setStroke(dp(context,1),if(highlight)GOLD else 0xFF64766C.toInt())
            }
            setOnClickListener{click()}
        }
}

class GalleryPreviewTile(
    context:Context,
    private val layout:GalleryPlanner.Layout,
    private val aspectRatios:List<Float>
):View(context) {
    var activeChoice=false
        set(v){field=v;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val margin=6f*resources.displayMetrics.density
        paint.color=if(activeChoice)0xFF70654D.toInt()else 0xFF5D695F.toInt()
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),9f,9f,paint)
        paint.color=0xFFE5DECE.toInt()
        val available=RectF(margin,margin,width-margin,height-margin)
        canvas.drawRoundRect(available,6f,6f,paint)
        val current=GalleryGeometry.layout(aspectRatios.take(8),layout,.04f)
        for((i,placement)in current.withIndex()){
            val rx=available.left+placement.cx*available.width()
            val ry=available.top+placement.cy*available.height()
            val halfW=placement.widthFraction*available.width()*.5f
            val halfH=halfW*(aspectRatios.getOrElse(i){1f}).coerceIn(.25f,4f)
            val rect=RectF(rx-halfW,ry-halfH,rx+halfW,ry+halfH)
            paint.color=0xFF3D3933.toInt()
            canvas.drawRect(rect,paint)
            rect.inset(2f,2f)
            paint.color=listOf(0xFFC5A88C,0xFFA4AFA3,0xFFB5B2A1,0xFF92795F)[i%4].toInt()
            canvas.drawRect(rect,paint)
        }
        if(activeChoice){
            paint.color=StudioUi.GOLD
            paint.style=Paint.Style.STROKE
            paint.strokeWidth=3f*resources.displayMetrics.density
            canvas.drawRoundRect(1f,1f,width-1f,height-1f,8f,8f,paint)
            paint.style=Paint.Style.FILL
        }
    }
}

class GalleryDesignerPanel(
    context:Context,
    ratios:List<Float>,
    initialGap:Int,
    private val onPreview:(GalleryPlanner.Layout,Int)->Unit,
    private val onCancel:()->Unit,
    private val onApply:()->Unit
):LinearLayout(context){
    private val gapViews=mutableListOf<GalleryPreviewTile>()
    private var layout=GalleryPlanner.Layout.GRID
    private var gapCm=initialGap.coerceIn(1,30)
    init{
        orientation=VERTICAL
        setPadding(StudioUi.dp(context,11),StudioUi.dp(context,9),
            StudioUi.dp(context,11),StudioUi.dp(context,12))
        background=GradientDrawable().apply{
            setColor(StudioUi.GREEN);cornerRadius=StudioUi.dp(context,16).toFloat()
            setStroke(StudioUi.dp(context,1),0xFF5A6A60.toInt())
        }
        addView(StudioUi.text(context,StudioUi.tr(context,"Galeri düzeni","Gallery Layout"),17f,true),
            LayoutParams(-1,StudioUi.dp(context,34)))
        addView(StudioUi.text(context,StudioUi.tr(context,
            "Bir düzen seç • Sonucu hemen duvarda gör","Pick a layout • See it directly on your wall"),12f),
            LayoutParams(-1,StudioUi.dp(context,32)))
        val scroll=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false}
        val strip=LinearLayout(context).apply{orientation=HORIZONTAL}
        val titles=listOf(
            StudioUi.tr(context,"Izgara","Grid"),
            StudioUi.tr(context,"Yan yana","Row"),
            StudioUi.tr(context,"Alt alta","Column"),
            StudioUi.tr(context,"Serbest","Salon"),
            StudioUi.tr(context,"Kademeli","Stagger")
        )
        GalleryPlanner.Layout.values().forEachIndexed {i,style->
            val tile=GalleryPreviewTile(context,style,ratios).apply{activeChoice=i==0}
            gapViews.add(tile)
            val col=LinearLayout(context).apply{
                orientation=VERTICAL
                gravity=Gravity.CENTER_HORIZONTAL
                addView(tile,LayoutParams(StudioUi.dp(context,99),StudioUi.dp(context,70)))
                addView(StudioUi.text(context,titles[i],11f,true).apply{
                    gravity=Gravity.CENTER
                },LayoutParams(-1,StudioUi.dp(context,24)))
                setOnClickListener{
                    layout=style
                    gapViews.forEachIndexed{idx,v->v.activeChoice=idx==i}
                    onPreview(layout,gapCm)
                }
                contentDescription=titles[i]
                isClickable=true;isFocusable=true
            }
            strip.addView(col,LayoutParams(StudioUi.dp(context,104),-2).apply{
                marginEnd=StudioUi.dp(context,7)
            })
        }
        scroll.addView(strip)
        addView(scroll,LayoutParams(-1,StudioUi.dp(context,105)))
        val gapText=StudioUi.text(context,"",12f,true)
        fun updateGapText(){
            gapText.text=StudioUi.tr(context,"Tablolar arası boşluk: ","Spacing: ")+"$gapCm cm"
        }
        updateGapText()
        addView(gapText,LayoutParams(-1,StudioUi.dp(context,29)))
        addView(SeekBar(context).apply{
            max=29;progress=gapCm-1
            progressTintList=ColorStateList.valueOf(StudioUi.GOLD)
            thumbTintList=ColorStateList.valueOf(StudioUi.GOLD)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(bar:SeekBar)=Unit
                override fun onStopTrackingTouch(bar:SeekBar)=Unit
                override fun onProgressChanged(bar:SeekBar,p:Int,fromUser:Boolean){
                    if(fromUser){
                        gapCm=p+1;updateGapText()
                        onPreview(layout,gapCm)
                    }
                }
            })
        },LayoutParams(-1,StudioUi.dp(context,43)))
        val actions=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        actions.addView(StudioUi.button(context,StudioUi.tr(context,"Vazgeç","Cancel")){
            onCancel()
        },LayoutParams(0,StudioUi.dp(context,47),1f).apply{
            marginEnd=StudioUi.dp(context,7)
        })
        actions.addView(StudioUi.button(context,StudioUi.tr(context,"✓ Uygula","✓ Apply"),true){
            onApply()
        },LayoutParams(0,StudioUi.dp(context,47),1f))
        addView(actions,LayoutParams(-1,StudioUi.dp(context,50)))
        post{onPreview(layout,gapCm)}
    }
}

class GroupStudioPanel(
    context:Context,
    private val onSelectAll:()->Unit,
    private val onPicking:(Boolean)->Unit,
    private val onAction:(GalleryGeometry.Align? ,Float?)->Unit,
    private val onClose:()->Unit
):LinearLayout(context) {
    private val count=StudioUi.text(context,"",12f,true)
    private lateinit var pickButton:Button
    private var choosing=true
    init {
        orientation=VERTICAL
        setPadding(StudioUi.dp(context,11),StudioUi.dp(context,9),
            StudioUi.dp(context,11),StudioUi.dp(context,12))
        background=GradientDrawable().apply{
            setColor(StudioUi.GREEN);cornerRadius=StudioUi.dp(context,16).toFloat()
            setStroke(StudioUi.dp(context,1),0xFF607368.toInt())
        }
        addView(StudioUi.text(context,StudioUi.tr(context,"Tabloları birlikte düzenle","Edit Artworks Together"),
            16f,true),LayoutParams(-1,StudioUi.dp(context,31)))
        addView(count,LayoutParams(-1,StudioUi.dp(context,25)))
        val row=LinearLayout(context)
        pickButton=StudioUi.button(context,StudioUi.tr(context,"✓ Seçimi bitir","✓ Finish selecting"),true){
            choosing=!choosing
            updatePickingUi()
            onPicking(choosing)
        }
        row.addView(pickButton,LayoutParams(0,StudioUi.dp(context,43),1.4f).apply{marginEnd=StudioUi.dp(context,7)})
        row.addView(StudioUi.button(context,StudioUi.tr(context,"Tümünü seç","Select all")){
            onSelectAll()
        },LayoutParams(0,StudioUi.dp(context,43),1f))
        addView(row,LayoutParams(-1,StudioUi.dp(context,48)))
        addView(StudioUi.text(context,StudioUi.tr(context,
            "İstemediklerine dokunarak seçimden çıkar. Sonra hizala veya sürükle.",
            "Tap artworks to deselect them, then align or drag."),11f),
            LayoutParams(-1,StudioUi.dp(context,32)))
        val scroll=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false}
        val ribbon=LinearLayout(context).apply{orientation=HORIZONTAL}
        data class Action(val text:String,val alignment:GalleryGeometry.Align?,val scale:Float?)
        val acts=listOf(
            Action(StudioUi.tr(context,"Sola","Left"),GalleryGeometry.Align.LEFT,null),
            Action(StudioUi.tr(context,"Ortala ↔","Center ↔"),GalleryGeometry.Align.CENTER_X,null),
            Action(StudioUi.tr(context,"Sağa","Right"),GalleryGeometry.Align.RIGHT,null),
            Action(StudioUi.tr(context,"Üste","Top"),GalleryGeometry.Align.TOP,null),
            Action(StudioUi.tr(context,"Ortala ↕","Center ↕"),GalleryGeometry.Align.CENTER_Y,null),
            Action(StudioUi.tr(context,"Alta","Bottom"),GalleryGeometry.Align.BOTTOM,null),
            Action(StudioUi.tr(context,"Eşit ↔","Space ↔"),GalleryGeometry.Align.SPACE_X,null),
            Action(StudioUi.tr(context,"Eşit ↕","Space ↕"),GalleryGeometry.Align.SPACE_Y,null),
            Action("－ 10%",null,.90f),
            Action("＋ 10%",null,1.10f)
        )
        acts.forEach{a->
            ribbon.addView(StudioUi.button(context,a.text){
                if(choosing){
                    choosing=false;updatePickingUi();onPicking(false)
                }
                onAction(a.alignment,a.scale)
            },LayoutParams(StudioUi.dp(context,98),StudioUi.dp(context,47)).apply{
                marginEnd=StudioUi.dp(context,6)
            })
        }
        scroll.addView(ribbon)
        addView(scroll,LayoutParams(-1,StudioUi.dp(context,52)))
        addView(StudioUi.button(context,StudioUi.tr(context,"Bitti — normal düzenlemeye dön",
            "Done — return to editor"),true){onClose()},
            LayoutParams(-1,StudioUi.dp(context,48)))
        updateCount(0)
        updatePickingUi()
    }
    fun updateCount(n:Int){
        count.text=StudioUi.tr(context,"Seçili tablo: ","Selected: ")+"$n  ·  "+
            if(n>=2)StudioUi.tr(context,"Hazır","Ready")
            else StudioUi.tr(context,"En az 2 tablo seç","Choose at least 2")
    }
    private fun updatePickingUi(){
        pickButton.text=if(choosing)StudioUi.tr(context,"✓ Seçimi bitir","✓ Finish selecting")
            else StudioUi.tr(context,"Tabloları seç","Select artworks")
    }
}

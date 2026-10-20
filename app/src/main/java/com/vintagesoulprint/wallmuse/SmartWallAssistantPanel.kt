package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Actual offline, step-by-step wall design assistant. Buttons call editor
 * functions; this is not a static tutorial, cloud service or simulated AI.
 * The wall and art remain visible and touchable outside this bottom panel.
 */
class SmartWallAssistantPanel(
    context:Context,
    private val action:(Action)->Unit,
    private val changeStep:(Int)->Unit,
    private val close:()->Unit
):LinearLayout(context) {
    enum class Action {
        PICK_WALL, ROOM_LIBRARY, LIVE_CAMERA, ADD_ART, CROP, PRINT_SIZE,
        MEASURE, ALIGN_LINE, PERSPECTIVE, FRAME_ADVICE, SAVE_PROJECT,
        EXPORT, GALLERY
    }
    private val d=resources.displayMetrics.density
    private fun dp(i:Int)=(i*d+.5f).toInt()
    private fun tr(t:String,e:String)=StudioUi.tr(context,t,e)
    var step=0
        private set
    fun update(stepIndex:Int,hasArt:Boolean,artCount:Int,live:Boolean){
        step=stepIndex.coerceIn(0,4)
        removeAllViews()
        val bar=LinearLayout(context).apply{
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        bar.addView(StudioUi.text(context,tr("DUVAR ASİSTANI","WALL ASSISTANT"),14f,true),
            LayoutParams(0,dp(27),1f))
        bar.addView(StudioUi.button(context,"✕"){close()},LayoutParams(dp(44),dp(35)))
        addView(bar,LayoutParams(-1,dp(38)))

        val progress=LinearLayout(context).apply{orientation=HORIZONTAL}
        for(i in 0..4){
            val v=View(context).apply{
                background=GradientDrawable().apply{
                    setColor(if(i<=step)StudioUi.GOLD else 0xFF556158.toInt())
                    cornerRadius=dp(5).toFloat()
                }
            }
            progress.addView(v,LayoutParams(0,dp(5),1f).apply{
                if(i<4)marginEnd=dp(6)
            })
        }
        addView(progress,LayoutParams(-1,dp(9)))
        val titles=listOf(
            tr("1. Duvarını seç","1. Choose your wall"),
            tr("2. Eser ekle","2. Add your artwork"),
            tr("3. Boyutu ayarla","3. Set real-world size"),
            tr("4. Duvara hizala","4. Align with your wall"),
            tr("5. Son görünüm","5. Final touches")
        )
        val subtitle=listOf(
            tr("Fotoğraf yükle, canlı kamerayı aç veya mevcut duvarı kullan.",
                "Choose a wall photo, live camera or keep the current wall."),
            tr("Görsel seç. Tek parmakla taşı, iki parmakla boyutlandır.",
                "Choose art. Drag with one finger and pinch to resize."),
            tr("Baskı boyutunu seç. Kesin ölçü için bir referans kullan.",
                "Choose print size. Calibrate a reference for precision."),
            tr("İki noktadan yatay çizgiyi göster veya perspektifi düzenle.",
                "Mark a horizontal reference with two taps, or adjust perspective."),
            tr("Çerçeve seç, projeni sakla veya görseli dışa aktar.",
                "Choose a frame, save your project or export an image.")
        )
        addView(StudioUi.text(context,titles[step],17f,true),
            LayoutParams(-1,dp(32)))
        addView(StudioUi.text(context,subtitle[step],12f),
            LayoutParams(-1,dp(42)))
        val actions=LinearLayout(context).apply{orientation=HORIZONTAL}
        fun addAction(label:String,kind:Action,main:Boolean=false){
            val button=StudioUi.button(context,label,main){action(kind)}
            actions.addView(button,LayoutParams(0,dp(49),1f).apply{
                if(actions.childCount>0)marginStart=dp(6)
            })
        }
        when(step){
            0->{
                addAction(tr("Hazır oda","Ready rooms"),Action.ROOM_LIBRARY,true)
                addAction(tr("Fotoğraf seç","Wall photo"),Action.PICK_WALL)
                addAction(if(live)tr("Kamera açık","Camera on")
                    else tr("Canlı","Live"),Action.LIVE_CAMERA)
            }
            1->{
                addAction(tr("＋ Eser ekle","＋ Add artwork"),Action.ADD_ART,true)
                if(artCount>1)addAction(tr("Galeri düzeni","Gallery layout"),Action.GALLERY)
                else if(hasArt)addAction(tr("Kırp","Crop"),Action.CROP)
            }
            2->{
                addAction(tr("Baskı boyutu","Print size"),Action.PRINT_SIZE,true)
                addAction(tr("Ölçü kalibrasyonu","Calibrate"),Action.MEASURE)
            }
            3->{
                addAction(tr("İki noktayla hizala","Align with 2 taps"),Action.ALIGN_LINE,true)
                addAction(tr("Perspektif","Perspective"),Action.PERSPECTIVE)
            }
            4->{
                addAction(tr("Çerçeve öner","Suggest frame"),Action.FRAME_ADVICE,true)
                addAction(tr("Projeyi kaydet","Save project"),Action.SAVE_PROJECT)
            }
        }
        addView(actions,LayoutParams(-1,dp(55)))
        if(step==4){
            addView(StudioUi.button(context,tr("↓ Sonucu kaydet / paylaş","↓ Export or share"),
                true){action(Action.EXPORT)},LayoutParams(-1,dp(45)).apply{
                    bottomMargin=dp(6)
                })
        }
        val navigation=LinearLayout(context).apply{
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        navigation.addView(StudioUi.button(context,tr("← Geri","← Back")){
            changeStep((step-1).coerceAtLeast(0))
        },LayoutParams(0,dp(43),1f).apply{marginEnd=dp(8)})
        val nextText=if(step==4)tr("✓ Bitir","✓ Finish")else tr("İleri →","Next →")
        navigation.addView(StudioUi.button(context,nextText,true){
            if(step==4)close()else changeStep((step+1).coerceAtMost(4))
        },LayoutParams(0,dp(43),1.4f))
        addView(navigation,LayoutParams(-1,dp(45)))
    }
    init{
        orientation=VERTICAL
        setPadding(dp(13),dp(9),dp(13),dp(12))
        background=GradientDrawable().apply{
            setColor(0xF4222D28.toInt())
            cornerRadius=dp(17).toFloat()
            setStroke(dp(1),0xFF637368.toInt())
        }
    }
}

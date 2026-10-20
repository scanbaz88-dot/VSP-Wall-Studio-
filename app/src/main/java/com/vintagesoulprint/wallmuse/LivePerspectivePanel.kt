package com.vintagesoulprint.wallmuse

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.min

/**
 * Transparent-to-camera, non-modal perspective controls. Frame/wall remain
 * touchable outside the panel. No AlertDialog, black scrim or preview clone.
 */
class LivePerspectivePanel(
    context:Context,
    private val onChange:(FloatArray)->Unit,
    private val onFine:(Boolean)->Unit,
    private val onCompare:(Boolean)->Unit,
    private val onFreeze:()->Unit,
    private val onReset:()->Unit,
    private val onCancel:()->Unit,
    private val onApply:()->Unit
):LinearLayout(context) {
    private val dark=0xEE1F2A26.toInt()
    private val gold=0xFFD6BA88.toInt()
    private val white=0xFFF6F1E7.toInt()
    private var dx=0;private var dy=0;private var strength=38
    private var comparing=false
    private lateinit var compareButton:Button
    private lateinit var freezeButton:Button
    private lateinit var stateLabel:TextView

    private val turkish=resources.configuration.locales[0].language=="tr"
    private fun tr(t:String,e:String)=if(turkish)t else e
    private fun dp(i:Int)= (i*resources.displayMetrics.density+.5f).toInt()
    private fun text(value:String,weight:Boolean=false,size:Float=12f):TextView=
        TextView(context).apply{
            text=value;textSize=size
            setTextColor(white)
            gravity=Gravity.CENTER_VERTICAL
            if(weight)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
        }
    private fun button(value:String,accent:Boolean=false,handler:()->Unit):Button=
        Button(context).apply{
            text=value;textSize=12f;isAllCaps=false
            minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0
            setPadding(dp(6),0,dp(6),0)
            setTextColor(if(accent)0xFF161D1B.toInt() else white)
            background=GradientDrawable().apply{
                setColor(if(accent)gold else 0xFF313C37.toInt())
                cornerRadius=dp(9).toFloat()
                setStroke(dp(1),if(accent)gold else 0xFF65736B.toInt())
            }
            setOnClickListener{handler()}
        }
    private fun clearCompareSilently(){
        if(comparing){
            comparing=false
            compareButton.text=tr("Önce / Sonra","Before / After")
        }
    }
    fun setFrozen(frozen:Boolean){
        freezeButton.text=if(frozen)tr("▶ Canlıya dön","▶ Resume live")
            else tr("❚❚ Dondur","❚❚ Freeze")
    }
    private fun applyPreset(){
        clearCompareSilently()
        onChange(PerspectiveGeometry.preset(dx,dy,strength))
        stateLabel.text=if(dx==0&&dy==0)
            tr("Düz görünüm","Straight view")
            else tr("Bakış yönü: ","Direction: ")+when{
                dx==-1&&dy==-1->"↖";dx==0&&dy==-1->"↑"
                dx==1&&dy==-1->"↗";dx==-1&&dy==0->"←"
                dx==1&&dy==0->"→";dx==-1&&dy==1->"↙"
                dx==0&&dy==1->"↓";else->"↘"
            }
    }
    init{
        orientation=VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
        setPadding(dp(10),dp(10),dp(10),dp(12))
        background=GradientDrawable().apply{
            setColor(dark)
            cornerRadius=dp(17).toFloat()
            setStroke(dp(1),0xFF607369.toInt())
        }
        val title=LinearLayout(context).apply{
            gravity=Gravity.CENTER_VERTICAL
        }
        title.addView(text(tr("Canlı perspektif","Live perspective"),true,15f),
            LayoutParams(0,dp(32),1f))
        title.addView(text(tr("Duvara göre ayarla","Adjust on your wall"),false,10f))
        addView(title,LayoutParams(-1,dp(33)))

        val row=LinearLayout(context).apply{
            gravity=Gravity.CENTER_VERTICAL
            orientation=HORIZONTAL
        }
        val grid=GridLayout(context).apply{columnCount=3}
        val directions=arrayOf(
            Triple("↖",-1,-1),Triple("↑",0,-1),Triple("↗",1,-1),
            Triple("←",-1,0),Triple("⊙",0,0),Triple("→",1,0),
            Triple("↙",-1,1),Triple("↓",0,1),Triple("↘",1,1)
        )
        val gridWidth=dp(190)
        val w=gridWidth/3-dp(6)
        for((symbol,x,y) in directions){
            grid.addView(button(symbol){dx=x;dy=y;applyPreset()}.apply{
                textSize=19f
                contentDescription=tr("Bakış yönü ","Viewing angle ")+symbol
            },GridLayout.LayoutParams().apply{
                width=w;height=dp(43)
                setMargins(dp(2),dp(2),dp(2),dp(2))
            })
        }
        row.addView(grid,LayoutParams(gridWidth,dp(143)))
        val controls=LinearLayout(context).apply{
            orientation=VERTICAL
            gravity=Gravity.CENTER_VERTICAL
        }
        controls.addView(text(tr("Perspektif gücü","Perspective strength"),true,12f),
            LayoutParams(-1,dp(25)))
        val power=text("38%",true,12f)
        controls.addView(power,LayoutParams(-1,dp(20)))
        controls.addView(SeekBar(context).apply{
            max=100
            progress=strength
            progressTintList=ColorStateList.valueOf(gold)
            thumbTintList=ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(s:SeekBar)=Unit
                override fun onStopTrackingTouch(s:SeekBar)=Unit
                override fun onProgressChanged(s:SeekBar,value:Int,fromUser:Boolean){
                    if(fromUser){
                        strength=value
                        power.text="$value%"
                        if(dx!=0||dy!=0)applyPreset()
                    }
                }
            })
        },LayoutParams(-1,dp(47)))
        stateLabel=text(tr("Mevcut ayar","Current adjustment"),false,10f)
        controls.addView(stateLabel,LayoutParams(-1,dp(24)))
        row.addView(controls,LayoutParams(0,-1,1f))
        addView(row,LayoutParams(-1,dp(146)))

        val fine=CheckBox(context).apply{
            text=tr("Köşeleri elle ayarla","Fine-tune corners")
            setTextColor(white)
            textSize=12f
            minHeight=dp(39)
            buttonTintList=ColorStateList.valueOf(gold)
            setOnCheckedChangeListener{_,enabled->
                if(enabled&&comparing){
                    clearCompareSilently()
                    onCompare(false)
                }
                onFine(enabled)
            }
        }
        addView(fine,LayoutParams(-1,dp(39)))

        val featureRow=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        compareButton=button(tr("Önce / Sonra","Before / After")){
            comparing=!comparing
            compareButton.text=if(comparing)tr("Sonra göster","Show after")
                else tr("Önce / Sonra","Before / After")
            onCompare(comparing)
        }
        featureRow.addView(compareButton,LayoutParams(0,dp(42),1f).apply{marginEnd=dp(6)})
        freezeButton=button(tr("❚❚ Dondur","❚❚ Freeze")){onFreeze()}
        featureRow.addView(freezeButton,LayoutParams(0,dp(42),1f))
        addView(featureRow,LayoutParams(-1,dp(43)))

        val commands=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL}
        commands.addView(button(tr("Sıfırla","Reset")) {
            dx=0;dy=0;clearCompareSilently();onReset()
            fine.isChecked=false
            stateLabel.text=tr("Perspektif sıfırlandı","Perspective reset")
        },LayoutParams(0,dp(46),1f).apply{marginEnd=dp(5)})
        commands.addView(button(tr("Vazgeç","Cancel")){onCancel()},
            LayoutParams(0,dp(46),1f).apply{marginEnd=dp(5)})
        commands.addView(button(tr("✓ Uygula","✓ Apply"),true){onApply()},
            LayoutParams(0,dp(46),1.12f))
        addView(commands,LayoutParams(-1,dp(49)))
    }
}

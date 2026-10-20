package com.vintagesoulprint.wallmuse

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*

/**
 * Native 2-column ready-room gallery with 6 categories, source credits and
 * free/premium-preview labels. Selecting a scene opens it in the actual editor.
 * Premium content remains test-unlocked: NO payment is processed in preview.
 */
object RoomScenePicker {
    private const val INK=0xFF1D2623.toInt()
    private const val CREAM=0xFFF7F2E9.toInt()
    private const val GOLD=0xFFC2A474.toInt()
    fun show(context:Context,onChoose:(RoomSceneCatalog.Scene)->Unit){
        val all=try{RoomSceneCatalog.scenes(context)}
            catch(e:Exception){
                Toast.makeText(context,context.getString(R.string.room_library_error),
                    Toast.LENGTH_LONG).show()
                return
            }
        fun dp(i:Int)=(i*context.resources.displayMetrics.density+.5f).toInt()
        fun tr(tr:String,en:String)=StudioUi.tr(context,tr,en)
        val groups=linkedMapOf(
            "all" to context.getString(R.string.room_all),
            "living" to context.getString(R.string.room_living),
            "bedroom" to context.getString(R.string.room_bedroom),
            "dining" to context.getString(R.string.room_dining),
            "hallway" to context.getString(R.string.room_hallway),
            "office" to context.getString(R.string.room_office),
            "studio" to context.getString(R.string.room_studio)
        )
        val outer=LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(11),dp(7),dp(11),dp(7))
        }
        val intro=TextView(context).apply{
            text=context.getString(R.string.room_library_intro)
            setTextColor(INK);textSize=12f
            setPadding(dp(2),dp(2),dp(2),dp(9))
        }
        outer.addView(intro)
        val navigation=HorizontalScrollView(context).apply{
            isHorizontalScrollBarEnabled=false
        }
        val navButtons=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
        navigation.addView(navButtons)
        outer.addView(navigation,LinearLayout.LayoutParams(-1,dp(47)))
        val scroll=ScrollView(context)
        val tileGrid=GridLayout(context).apply{
            columnCount=2
        }
        scroll.addView(tileGrid)
        outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val attribution=TextView(context).apply{
            text=context.getString(R.string.room_library_credit)
            textSize=11f
            setTextColor(INK)
            gravity=Gravity.CENTER
            setPadding(dp(4),dp(7),dp(4),dp(4))
        }
        outer.addView(attribution,LinearLayout.LayoutParams(-1,dp(48)))
        var currentCategory="all"
        lateinit var dialog:AlertDialog
        val buttons=mutableMapOf<String,Button>()
        fun updateTabAppearance(){
            buttons.forEach{(key,btn)->
                val selected=key==currentCategory
                btn.setTextColor(if(selected)INK else CREAM)
                btn.background=GradientDrawable().apply{
                    setColor(if(selected)GOLD else 0xFF3B4741.toInt())
                    cornerRadius=dp(11).toFloat()
                }
            }
        }
        val usableWidth=(context.resources.displayMetrics.widthPixels-dp(80))
            .coerceAtLeast(dp(270))
        val tileWidth=usableWidth/2
        fun redraw(){
            tileGrid.removeAllViews()
            val filtered=all.filter{currentCategory=="all"||it.category==currentCategory}
            for((index,scene)in filtered.withIndex()){
                val card=LinearLayout(context).apply{
                    orientation=LinearLayout.VERTICAL
                    setPadding(dp(5),dp(6),dp(5),dp(7))
                    background=GradientDrawable().apply{
                        setColor(0xFFF0EBE3.toInt())
                        cornerRadius=dp(12).toFloat()
                        setStroke(dp(1),0xFFE0D9CC.toInt())
                    }
                    isClickable=true;isFocusable=true
                    setOnClickListener{
                        dialog.dismiss()
                        onChoose(scene)
                    }
                }
                val image=ImageView(context).apply{
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFE7DED2.toInt())
                    contentDescription=scene.title(context)
                    val bmp=RoomSceneCatalog.thumbnail(context,scene.id)
                    if(bmp!=null)setImageBitmap(bmp)
                }
                card.addView(image,LinearLayout.LayoutParams(-1,dp(122)))
                card.addView(TextView(context).apply{
                    text=scene.title(context)
                    typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
                    setTextColor(INK);textSize=12f;maxLines=2
                    setPadding(dp(4),dp(7),dp(2),0)
                },LinearLayout.LayoutParams(-1,dp(40)))
                val classification=if(scene.free)context.getString(R.string.room_free)
                    else context.getString(R.string.room_premium_preview)
                card.addView(TextView(context).apply{
                    text=classification
                    setTextColor(if(scene.free)0xFF497355.toInt()else 0xFF9B763E.toInt())
                    textSize=10f
                    setPadding(dp(4),0,dp(2),0)
                })
                card.addView(TextView(context).apply{
                    text=scene.photographer+" · Pexels"
                    textSize=9f;maxLines=1
                    setTextColor(0xFF737B71.toInt())
                    setPadding(dp(4),dp(2),dp(4),dp(3))
                    setOnClickListener{
                        try{
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse(scene.photoUrl)))
                        }catch(_:Exception){}
                    }
                })
                tileGrid.addView(card,GridLayout.LayoutParams().apply{
                    width=tileWidth;height=dp(207)
                    rowSpec=GridLayout.spec(index/2)
                    columnSpec=GridLayout.spec(index%2)
                    setMargins(dp(3),dp(3),dp(3),dp(6))
                })
            }
            scroll.scrollTo(0,0)
            updateTabAppearance()
        }
        for((id,title)in groups){
            val button=Button(context).apply{
                text=title
                isAllCaps=false
                textSize=11f
                minimumWidth=0;minimumHeight=0;minWidth=0;minHeight=0
                setPadding(dp(12),0,dp(12),0)
                setOnClickListener{
                    currentCategory=id
                    redraw()
                }
            }
            buttons[id]=button
            navButtons.addView(button,LinearLayout.LayoutParams(-2,dp(40)).apply{
                marginEnd=dp(6)
            })
        }
        val holder=LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL
            addView(outer,LinearLayout.LayoutParams(-1,-1))
        }
        dialog=AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.ready_rooms)+" · "+all.size)
            .setView(holder)
            .setNegativeButton(R.string.cancel,null)
            .create()
        dialog.setOnShowListener{
            val window=dialog.window
            window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels*.86f).toInt()
            )
        }
        redraw()
        dialog.show()
    }
}

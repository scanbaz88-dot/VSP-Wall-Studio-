package com.vintagesoulprint.wallmuse

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Bitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V3.2 entry experience: four clear creation choices, visual room library,
 * locally saved projects and tab-like navigation. No account or network.
 */
class HomeActivity:Activity() {
    private val dark=0xFF181C1C.toInt()
    private val panel=0xFF272C2C.toInt()
    private val gold=0xFFE6BE7A.toInt()
    private val cream=0xFFF8F3EC.toInt()
    private val gray=0xFFAFB5AF.toInt()
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun t(t:String,e:String)=StudioUi.tr(this,t,e)
    private fun shape(bg:Int,r:Int=16,stroke:Int?=null)=GradientDrawable().apply {
        setColor(bg);cornerRadius=dp(r).toFloat()
        stroke?.let{setStroke(dp(1),it)}
    }
    private fun label(value:String,sp:Float=14f,color:Int=cream,bold:Boolean=false)=
        TextView(this).apply{
            text=value;textSize=sp;setTextColor(color)
            if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
            includeFontPadding=true
        }
    private fun editor(mode:String?=null,scene:String?=null,project:String?=null){
        startActivity(Intent(this,MainActivity::class.java).apply{
            mode?.let{putExtra("home_mode",it)}
            scene?.let{putExtra("scene_id",it)}
            project?.let{putExtra("project_id",it)}
        })
    }
    private fun roomLibrary(category:String="all"){
        startActivity(Intent(this,RoomLibraryActivity::class.java).apply{
            putExtra("category",category)
        })
    }
    private lateinit var recentSection:LinearLayout
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=dark
        window.navigationBarColor=dark
        render()
    }
    override fun onResume(){
        super.onResume()
        if(::recentSection.isInitialized)fillRecent()
    }
    private fun render(){
        val outer=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(dark)
        }
        val scroll=ScrollView(this).apply{
            isFillViewport=true;clipToPadding=false
        }
        val page=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(18),dp(16),dp(24))
        }
        scroll.addView(page)
        outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val title=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        brand.addView(label("VSP Wall Studio",24f,cream,true))
        brand.addView(label(t("SANATIN, SENİN DUVARINDA","ART, IN YOUR SPACE"),10f,gold,true))
        title.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        title.addView(navIcon("⚙"){editor("tools")},LinearLayout.LayoutParams(dp(47),dp(47)))
        page.addView(title,LinearLayout.LayoutParams(-1,dp(72)))
        page.addView(label(t("Nasıl başlamak istersin?","How would you like to start?"),
            15f,cream,true).apply{setPadding(0,dp(17),0,dp(11))})

        val starts=listOf(
            Triple("◉",t("Kamerayla Başla","Live Camera"),"camera"),
            Triple("▧",t("Fotoğraf Yükle","Upload Photo"),"wall_photo"),
            Triple("⌂",t("Hazır Odalar","Ready Rooms"),"ready_rooms"),
            Triple("▣",t("Projelerim","My Projects"),"projects")
        )
        val actionGrid=GridLayout(this).apply{columnCount=2}
        val itemWidth=((resources.displayMetrics.widthPixels-dp(44))/2).coerceAtLeast(dp(135))
        starts.forEachIndexed{index,(symbol,text,action)->
            val card=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                background=shape(panel,18,if(index==2)gold else 0xFF3B4240.toInt())
                elevation=dp(2).toFloat()
                isClickable=true;isFocusable=true
                contentDescription=text
                setOnClickListener{
                    when(action){
                        "ready_rooms"->roomLibrary()
                        "projects"->showProjectPicker()
                        else->editor(action)
                    }
                }
            }
            card.addView(label(symbol,33f,if(index==2)gold else cream,true).apply{
                gravity=Gravity.CENTER
            },LinearLayout.LayoutParams(-1,dp(62)))
            card.addView(label(text,13f,cream,true).apply{
                gravity=Gravity.CENTER
            },LinearLayout.LayoutParams(-1,dp(36)))
            actionGrid.addView(card,GridLayout.LayoutParams().apply{
                width=itemWidth;height=dp(132)
                rowSpec=GridLayout.spec(index/2)
                columnSpec=GridLayout.spec(index%2)
                setMargins(dp(3),dp(4),dp(3),dp(5))
            })
        }
        page.addView(actionGrid)
        val recentHeader=LinearLayout(this).apply{
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,dp(21),0,dp(8))
        }
        recentHeader.addView(label(t("Son Projeler","Recent Projects"),17f,cream,true),
            LinearLayout.LayoutParams(0,-2,1f))
        val all=label(t("Tümünü Gör  ›","See All  ›"),12f,gold,true).apply{
            setPadding(dp(8),dp(7),dp(1),dp(7))
            setOnClickListener{showProjectPicker()}
        }
        recentHeader.addView(all)
        page.addView(recentHeader)
        recentSection=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val recentScroll=HorizontalScrollView(this).apply{
            isHorizontalScrollBarEnabled=false
            addView(recentSection)
        }
        page.addView(recentScroll,LinearLayout.LayoutParams(-1,dp(149)))
        fillRecent()

        val discover=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,dp(16),0,dp(9))
        }
        discover.addView(label(t("Mekânına Göre Keşfet","Explore by Room"),17f,cream,true))
        page.addView(discover)
        val groups=listOf(
            Triple("living",t("Salon","Living Room"),"6"),
            Triple("bedroom",t("Yatak Odası","Bedroom"),"4"),
            Triple("dining",t("Mutfak & Yemek","Kitchen & Dining"),"4"),
            Triple("hallway",t("Koridor","Hallway"),"3"),
            Triple("office",t("Çalışma Odası","Home Office"),"3"),
            Triple("studio",t("Boş Duvar","Blank Wall"),"4")
        )
        val sceneGrid=GridLayout(this).apply{columnCount=2}
        val sceneList=try{RoomSceneCatalog.scenes(this)}catch(_:Exception){emptyList()}
        groups.forEachIndexed{index,(key,name,count)->
            val box=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                background=shape(panel,13)
                isClickable=true;isFocusable=true
                setOnClickListener{roomLibrary(key)}
            }
            val scene=sceneList.firstOrNull{it.category==key}
            val photo=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF807A6F.toInt())
                if(scene!=null)RoomSceneCatalog.thumbnail(this@HomeActivity,scene.id)
                    ?.let{setImageBitmap(it)}
            }
            box.addView(photo,LinearLayout.LayoutParams(-1,dp(109)))
            box.addView(label(name,13f,cream,true).apply{
                setPadding(dp(9),dp(7),dp(5),0)
            })
            box.addView(label(t("$count sahne","$count scenes"),11f,gray).apply{
                setPadding(dp(9),dp(1),0,dp(5))
            })
            sceneGrid.addView(box,GridLayout.LayoutParams().apply{
                width=itemWidth;height=dp(170)
                rowSpec=GridLayout.spec(index/2)
                columnSpec=GridLayout.spec(index%2)
                setMargins(dp(3),dp(4),dp(3),dp(6))
            })
        }
        page.addView(sceneGrid)
        outer.addView(bottomNav(),LinearLayout.LayoutParams(-1,dp(74)))
        setContentView(outer)
        outer.setOnApplyWindowInsetsListener{v,insets->
            val i=if(android.os.Build.VERSION.SDK_INT>=30)
                insets.getInsets(android.view.WindowInsets.Type.systemBars()) else null
            v.setPadding(0,i?.top?:insets.systemWindowInsetTop,0,
                i?.bottom?:insets.systemWindowInsetBottom)
            insets
        }
    }
    private fun fillRecent(){
        recentSection.removeAllViews()
        val entries=ProjectLibrary.list(this).take(7)
        if(entries.isEmpty()){
            recentSection.addView(label(t("Henüz kayıtlı proje yok. Bir tablo ekleyerek başla.",
                "No saved projects yet. Start by adding some art."),12f,gray).apply{
                gravity=Gravity.CENTER_VERTICAL
            },LinearLayout.LayoutParams(-1,dp(105)))
            return
        }
        for(entry in entries){
            val item=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                background=shape(panel,10)
                setOnClickListener{editor(project=entry.id)}
                isClickable=true
            }
            val cover=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF7B766A.toInt())
                ProjectLibrary.getThumbnail(this@HomeActivity,entry.id)?.let{setImageBitmap(it)}
            }
            item.addView(cover,LinearLayout.LayoutParams(-1,dp(101)))
            item.addView(label(entry.name,11f,cream,true).apply{
                gravity=Gravity.CENTER
                maxLines=1
            },LinearLayout.LayoutParams(-1,dp(30)))
            recentSection.addView(item,LinearLayout.LayoutParams(dp(126),dp(137)).apply{
                marginEnd=dp(9)
            })
        }
    }
    private fun showProjectPicker(){
        val entries=ProjectLibrary.list(this)
        if(entries.isEmpty()){
            Toast.makeText(this,t("Henüz kayıtlı proje bulunmuyor.","No saved projects yet."),
                Toast.LENGTH_LONG).show()
            editor()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(t("Projelerim","My Projects"))
            .setItems(entries.map{it.name}.toTypedArray()){_,index->
                editor(project=entries[index].id)
            }
            .setNegativeButton(t("Vazgeç","Cancel"),null)
            .show()
    }
    private fun navIcon(symbol:String,click:()->Unit)=Button(this).apply{
        text=symbol;textSize=19f;isAllCaps=false
        setTextColor(cream);minWidth=0;minHeight=0;minimumWidth=0;minimumHeight=0
        background=shape(panel,12)
        setOnClickListener{click()}
    }
    private fun bottomNav():LinearLayout{
        val nav=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            background=shape(0xFF1D2121.toInt(),0,0xFF313635.toInt())
        }
        val entries=listOf(
            Triple("⌂",t("Ana Sayfa","Home"),"home"),
            Triple("▧",t("Kütüphane","Library"),"library"),
            Triple("✦",t("Asistan","Assistant"),"assistant"),
            Triple("♛",t("Premium","Premium"),"premium")
        )
        entries.forEach{(symbol,text,kind)->
            val column=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                setOnClickListener{
                    when(kind){
                        "library"->roomLibrary()
                        "assistant"->editor("assistant")
                        "premium"->editor("premium")
                    }
                }
                isClickable=true
            }
            column.addView(label(symbol,25f,if(kind=="home")gold else gray).apply{
                gravity=Gravity.CENTER
            })
            column.addView(label(text,10f,if(kind=="home")gold else gray).apply{
                gravity=Gravity.CENTER
            })
            nav.addView(column,LinearLayout.LayoutParams(0,-1,1f))
        }
        return nav
    }
}

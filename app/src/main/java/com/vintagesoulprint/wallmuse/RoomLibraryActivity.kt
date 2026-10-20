package com.vintagesoulprint.wallmuse

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

/** V3.2 full-screen, photo-first six-category ready room library. */
class RoomLibraryActivity:Activity(){
    private val dark=0xFF1B201F.toInt()
    private val white=0xFFF9F5ED.toInt()
    private val gold=0xFFE5BE7B.toInt()
    private val paper=0xFFF8F5EE.toInt()
    private val ink=0xFF252C2A.toInt()
    private fun dp(v:Int)=(v*resources.displayMetrics.density+.5f).toInt()
    private fun t(tr:String,en:String)=StudioUi.tr(this,tr,en)
    private fun shape(color:Int,r:Int)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(r).toFloat()
    }
    private fun label(v:String,s:Float=13f,color:Int=ink,bold:Boolean=false)=
        TextView(this).apply{
            text=v;textSize=s;setTextColor(color)
            if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
        }
    private val groups=listOf("all","living","bedroom","dining","hallway","office","studio")
    private lateinit var body:LinearLayout
    private lateinit var tabs:LinearLayout
    private lateinit var description:TextView
    private lateinit var scroll:ScrollView
    private var category="all"
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=dark
        window.navigationBarColor=dark
        category=intent.getStringExtra("category")?.takeIf{it in groups}?:"all"
        build()
    }
    private fun catName(s:String)=when(s){
        "living"->t("Salon","Living Room")
        "bedroom"->t("Yatak Odası","Bedroom")
        "dining"->t("Mutfak ve Yemek","Kitchen & Dining")
        "hallway"->t("Koridor","Hallway")
        "office"->t("Çalışma Odası","Home Office")
        "studio"->t("Boş Duvar","Blank Wall")
        else->t("Tümü","All Rooms")
    }
    private fun build(){
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(dark)
            setPadding(dp(11),dp(7),dp(14),dp(10))
        }
        val back=Button(this).apply{
            text="←";textSize=22f;isAllCaps=false
            setTextColor(white);background=shape(0xFF353D39.toInt(),13)
            minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
            setOnClickListener{finish()}
        }
        header.addView(back,LinearLayout.LayoutParams(dp(47),dp(45)).apply{marginEnd=dp(12)})
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        title.addView(label(t("Hazır Odalar","Ready Rooms"),19f,white,true))
        title.addView(label(t("Duvarına uygun sahneyi seç","Choose a scene for your art"),11f,gold))
        header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(header)
        val intro=label(t("24 özgün oda fotoğrafı · 6 kategori","24 distinct room photos · 6 categories"),
            13f,ink,true).apply{setPadding(dp(17),dp(18),dp(16),dp(9))}
        root.addView(intro)
        description=label(t("İstediğin odaya dokun ve tablonu yerleştir.",
            "Tap a room to start placing your artwork."),12f,0xFF686C68.toInt()).apply{
            setPadding(dp(17),0,dp(17),dp(12))
        }
        root.addView(description)
        val tabScroller=HorizontalScrollView(this).apply{
            isHorizontalScrollBarEnabled=false
        }
        tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        tabScroller.addView(tabs)
        root.addView(tabScroller,LinearLayout.LayoutParams(-1,dp(57)))
        scroll=ScrollView(this).apply{isFillViewport=true}
        body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(11),dp(8),dp(11),dp(16))
        }
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setPadding(dp(12),dp(7),dp(12),dp(9))
        }
        footer.addView(label(t("6 ücretsiz sahne · 18 Premium önizleme (testte açık)",
            "6 free scenes · 18 Premium previews (unlocked for testing)"),
            11f,0xFF7C6850.toInt()).apply{gravity=Gravity.CENTER})
        footer.addView(label(t("Fotoğraflar çevrimdışı kullanılabilir. Kaynak: Pexels",
            "All photos available offline. Photo credits: Pexels"),
            10f,0xFF85857B.toInt()).apply{gravity=Gravity.CENTER})
        root.addView(footer,LinearLayout.LayoutParams(-1,dp(49)))
        setContentView(root)
        root.setOnApplyWindowInsetsListener{view,insets->
            val edges=if(android.os.Build.VERSION.SDK_INT>=30)
                insets.getInsets(android.view.WindowInsets.Type.systemBars())else null
            view.setPadding(0,edges?.top?:insets.systemWindowInsetTop,0,
                edges?.bottom?:insets.systemWindowInsetBottom)
            insets
        }
        refresh()
    }
    private fun refresh(){
        val entries=try{RoomSceneCatalog.scenes(this)}
            catch(_:Exception){emptyList()}
        tabs.removeAllViews()
        for(key in groups){
            val active=category==key
            val caption=catName(key)
            val button=Button(this).apply{
                text=caption
                textSize=12f;isAllCaps=false
                setTextColor(if(active)ink else white)
                background=shape(if(active)gold else 0xFF3A4540.toInt(),14)
                minWidth=0;minHeight=0;minimumWidth=0;minimumHeight=0
                setPadding(dp(15),0,dp(15),0)
                setOnClickListener{category=key;refresh()}
            }
            tabs.addView(button,LinearLayout.LayoutParams(-2,dp(40)).apply{
                setMargins(dp(7),dp(5),0,dp(4))
            })
        }
        body.removeAllViews()
        val filtered=entries.filter{category=="all"||it.category==category}
        val title=label(catName(category)+" · "+filtered.size,17f,ink,true)
        title.setPadding(dp(7),dp(8),0,dp(11))
        body.addView(title)
        if(entries.isEmpty()){
            body.addView(label(t("Hazır oda dosyaları bulunamadı.",
                "Room photos are not available on this build."),13f))
            return
        }
        val grid=GridLayout(this).apply{columnCount=2}
        val tileW=((resources.displayMetrics.widthPixels-dp(43))/2).coerceAtLeast(dp(115))
        for((index,scene)in filtered.withIndex()){
            val column=LinearLayout(this).apply{
                orientation=LinearLayout.VERTICAL
                background=shape(0xFFEFEBE3.toInt(),11)
                isClickable=true
                contentDescription=scene.title(this@RoomLibraryActivity)
                setOnClickListener{choose(scene.id)}
            }
            val preview=ImageView(this).apply{
                scaleType=ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFCBC3B4.toInt())
                RoomSceneCatalog.thumbnail(this@RoomLibraryActivity,scene.id)
                    ?.let{setImageBitmap(it)}
            }
            column.addView(preview,LinearLayout.LayoutParams(-1,dp(124)))
            column.addView(label(scene.title(this),13f,ink,true).apply{
                setPadding(dp(9),dp(8),dp(7),0);maxLines=2
            },LinearLayout.LayoutParams(-1,dp(37)))
            column.addView(label(if(scene.free)t("Ücretsiz","Free")
                else t("Premium · Testte açık","Premium · Preview"),
                11f,if(scene.free)0xFF44744D.toInt()else 0xFF98703F.toInt()).apply{
                setPadding(dp(9),0,0,0)
            },LinearLayout.LayoutParams(-1,dp(23)))
            grid.addView(column,GridLayout.LayoutParams().apply{
                width=tileW;height=dp(187)
                rowSpec=GridLayout.spec(index/2)
                columnSpec=GridLayout.spec(index%2)
                setMargins(dp(3),dp(4),dp(3),dp(5))
            })
        }
        body.addView(grid)
        scroll.post{scroll.scrollTo(0,0)}
    }
    private fun choose(id:String){
        val scene=RoomSceneCatalog.scenes(this).firstOrNull{it.id==id}?:return
        // Free tier retains all six free categories. Preview APK unlocks all
        // backgrounds for testing, but real release requires verified receipt.
        if(!scene.free && !BuildConfig.DEBUG && !PremiumSession.verified){
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.premium_center)
                .setMessage(R.string.premium_scene_locked)
                .setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.premium_buy_lifetime){_,_->
                    startActivity(Intent(this,MainActivity::class.java)
                        .putExtra("home_mode","premium"))
                }.show()
            return
        }
        if(intent.getBooleanExtra("return_selection",false)){
            setResult(RESULT_OK,Intent().putExtra("scene_id",id))
            finish()
        }else{
            startActivity(Intent(this,MainActivity::class.java).putExtra("scene_id",id))
        }
    }
}

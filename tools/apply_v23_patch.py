from pathlib import Path
import re
root=Path('.')
src=root/'app/src/main/java/com/vintagesoulprint/wallmuse'

def patch_file(name, edits):
    f=src/name
    t=f.read_text()
    for old,new in edits:
        if old not in t:
            raise RuntimeError(f'Missing patch anchor in {name}: {old[:95]}')
        t=t.replace(old,new,1)
    f.write_text(t)
    print('Updated',name)

main=[
('    private lateinit var root: LinearLayout\n',
 '''    private lateinit var root: LinearLayout
    private lateinit var headerBar: LinearLayout
    private lateinit var fullscreenExitButton: Button
'''),
('''            v.setPadding(dp(10), (system?.top ?: insets.systemWindowInsetTop) + dp(6),
                dp(10), (system?.bottom ?: insets.systemWindowInsetBottom) + dp(5))''',
 '''            if (editing) {
                v.setPadding(dp(10), (system?.top ?: insets.systemWindowInsetTop) + dp(6),
                    dp(10), (system?.bottom ?: insets.systemWindowInsetBottom) + dp(5))
            } else {
                // Immersive preview really extends behind both system bars.
                v.setPadding(0, 0, 0, 0)
            }'''),
('''        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))''',
 '''        headerBar = header
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))'''),
('''button(getString(R.string.focus), "quiet") { setFocusMode(!editing) }''',
 '''button(getString(R.string.focus), "quiet") { setFocusMode(editing) }'''),
('''        canvasHolder.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        root.addView(canvasHolder, LinearLayout.LayoutParams(-1, 0, 1f))''',
 '''        canvasHolder.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        // The ordinary header is hidden in fullscreen, so keep this floating
        // exit control above the preview: fullscreen never traps the user.
        fullscreenExitButton = button("✕  " + getString(R.string.edit), "outlined") {
            setFocusMode(false)
        }.apply { visibility = View.GONE }
        canvasHolder.addView(fullscreenExitButton,
            FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.END).apply {
                setMargins(dp(8),dp(14),dp(12),dp(8))
            })
        root.addView(canvasHolder, LinearLayout.LayoutParams(-1, 0, 1f))'''),
('''    private fun setFocusMode(active: Boolean) {
        editing = !active
        val visibility = if (active) View.GONE else View.VISIBLE
        tools.visibility = visibility
        bottom.visibility = visibility
        actionsRow.visibility = visibility
        headlineLabel.visibility = visibility
        focusButton.text = getString(if (active) R.string.edit else R.string.focus)
        canvas.showSelectionHandles = !active
        canvas.isEnabled = !active
        canvas.invalidate()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (active) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else 0
    }''',
 '''    private fun setFocusMode(active: Boolean) {
        editing = !active
        val visibility = if (active) View.GONE else View.VISIBLE
        headerBar.visibility = visibility
        tools.visibility = visibility
        bottom.visibility = visibility
        actionsRow.visibility = visibility
        headlineLabel.visibility = visibility
        fullscreenExitButton.visibility = if (active) View.VISIBLE else View.GONE
        focusButton.text = getString(if (active) R.string.edit else R.string.focus)
        canvas.showSelectionHandles = !active
        canvas.isEnabled = !active
        canvas.invalidate()
        applyImmersiveBars(active)
        root.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveBars(active: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { ctl ->
                ctl.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (active) ctl.hide(WindowInsets.Type.systemBars())
                else ctl.show(WindowInsets.Type.systemBars())
            }
        } else {
            window.decorView.systemUiVisibility = if (active)
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            else 0
        }
    }'''),
('''    override fun onResume(){super.onResume();if(liveMode)liveCamera?.start()}''',
 '''    override fun onResume(){super.onResume();if(liveMode)liveCamera?.start();if(!editing)applyImmersiveBars(true)}'''),
('''        val groups = listOf("Modern", "Ahşap", "Metal", "Antika", "Oval & Yuvarlak", "Tuval")''',
 '''        // Photo-based styles are the primary library. Legacy vector frames
        // remain readable in older saved projects, but are hidden in the picker.
        val groups = listOf("Modern", "Ahşap", "Antika", "Oval & Yuvarlak", "Tuval")'''),
('''                if (frame.category != category) return@forEachIndexed''',
 '''                if (frame.category != category ||
                    (index !in 17..28 && index != 0 && index != 11 && index != 12)) return@forEachIndexed'''),
('''        val alternatives = FrameCatalog.frames.indices.filter { it != current && it != 0 }''',
 '''        val alternatives = (17..28).filter { it != current }'''),
]
patch_file('MainActivity.kt',main)

wall=[
('''        val layer=ArtLayer(nextId++,bitmap,.49f+stagger,.44f+stagger,initialWidth,0f,1,
''',
 '''        val layer=ArtLayer(nextId++,bitmap,.49f+stagger,.44f+stagger,initialWidth,0f,20,
'''),
('''        val stroke=when{l.frame==0->0f;FrameCatalog.isCanvas(l.frame)->scale*.022f''',
 '''        val photoFrame = FramePhotoLibrary.info(l.frame)
        val stroke=when{l.frame==0->0f;FrameCatalog.isCanvas(l.frame)->scale*.022f'''),
('''        val exterior=RectF(interior.left-stroke,interior.top-stroke,interior.right+stroke,interior.bottom+stroke)''',
 '''        val exterior=if(photoFrame!=null)FramePhotoLibrary.outerBounds(interior,photoFrame)
            else RectF(interior.left-stroke,interior.top-stroke,interior.right+stroke,interior.bottom+stroke)'''),
('''        if (l.frame!=0) drawFrameBody(c,l.frame,interior,exterior,oval,p)
        drawArtClipped(c,l,interior,oval,p)
        if(l.frame!=0) drawInsideLip(c,l.frame,interior,oval,p,scale)
        if(FrameCatalog.isCanvas(l.frame))drawCanvasDetails(c,interior,exterior,p,scale)
        if(FrameCatalog.isOrnate(l.frame))drawOrnaments(c,exterior,p,scale,oval,l.frame)''',
 '''        if (photoFrame != null) {
            // Art goes under the genuinely textured, transparent frame asset.
            drawArtClipped(c,l,interior,oval,p)
            FramePhotoLibrary.draw(c,context,l.frame,interior,exterior,p)
        } else {
            // Legacy and canvas frames are preserved for existing drafts.
            if (l.frame!=0) drawFrameBody(c,l.frame,interior,exterior,oval,p)
            drawArtClipped(c,l,interior,oval,p)
            if(l.frame!=0) drawInsideLip(c,l.frame,interior,oval,p,scale)
            if(FrameCatalog.isCanvas(l.frame))drawCanvasDetails(c,interior,exterior,p,scale)
            if(FrameCatalog.isOrnate(l.frame))drawOrnaments(c,exterior,p,scale,oval,l.frame)
        }'''),
('''        Entry("Yuvarlak antika altın","Oval & Yuvarlak")
    )''',
 '''        Entry("Yuvarlak antika altın","Oval & Yuvarlak"),
        // Photographic PNG/WebP silhouettes from the 12-frame image library.
        Entry("İnce siyah metal fotoğraf","Modern"),
        Entry("Klasik beyaz fotoğraf","Modern"),
        Entry("Doğal meşe fotoğraf","Ahşap"),
        Entry("Koyu ceviz fotoğraf","Ahşap"),
        Entry("Eskitilmiş ahşap fotoğraf","Ahşap"),
        Entry("Siyah galeri fotoğraf","Modern"),
        Entry("Antika altın oyma fotoğraf","Antika"),
        Entry("Viktoryen oyma fotoğraf","Antika"),
        Entry("Eski bronz fotoğraf","Antika"),
        Entry("Antika oval fotoğraf","Oval & Yuvarlak"),
        Entry("Yuvarlak altın fotoğraf","Oval & Yuvarlak"),
        Entry("Oval siyah fotoğraf","Oval & Yuvarlak")
    )'''),
('''        "Floating canvas","Victorian dark carved","Brushed silver","Slim bronze","Round antique gold")''',
 '''        "Floating canvas","Victorian dark carved","Brushed silver","Slim bronze","Round antique gold",
        "Photo slim black metal", "Photo classic white", "Photo natural oak",
        "Photo dark walnut", "Photo distressed wood", "Photo black gallery",
        "Photo antique gold", "Photo Victorian carved", "Photo aged bronze",
        "Photo antique oval", "Photo round gold", "Photo black oval")'''),
('''    fun isOval(id:Int)=id==8||id==10
    fun isRound(id:Int)=id==9||id==16''',
 '''    fun isOval(id:Int)=id==8||id==10||id==26||id==28
    fun isRound(id:Int)=id==9||id==16||id==27''')
]
patch_file('WallCanvasView.kt',wall)

patch_file('FrameSwatchView.kt',[
('''    override fun onDraw(c:Canvas){super.onDraw(c)
''',
 '''    override fun onDraw(c:Canvas){super.onDraw(c)
        val photo=FramePhotoLibrary.info(style)
        if (photo!=null) {
            // Render the real image asset in the library thumbnail, not a
            // placeholder gradient swatch from the old frame library.
            c.drawColor(0xFFF0ECE5.toInt())
            val bmp=FramePhotoLibrary.bitmap(context,style)
            if (bmp!=null) {
                val pad=5f*resources.displayMetrics.density
                val scale=min((width-2f*pad)/bmp.width,(height-2f*pad)/bmp.height)
                val ow=bmp.width*scale;val oh=bmp.height*scale
                val outer=RectF((width-ow)*.5f,(height-oh)*.5f,
                    (width+ow)*.5f,(height+oh)*.5f)
                val inner=FramePhotoLibrary.innerBounds(outer,photo)
                paint.shader=LinearGradient(inner.left,inner.top,inner.right,inner.bottom,
                    0xFFD3C6AD.toInt(),0xFF726E63.toInt(),Shader.TileMode.CLAMP)
                c.save()
                if(photo.oval) {
                    val path=Path().apply{addOval(inner,Path.Direction.CW)}
                    c.clipPath(path)
                } else c.clipRect(inner)
                c.drawRect(inner,paint)
                c.restore()
                paint.shader=null
                FramePhotoLibrary.draw(c,context,style,inner,outer,paint)
            }
            return
        }
''')
])

print('Frame patch sources applied.')

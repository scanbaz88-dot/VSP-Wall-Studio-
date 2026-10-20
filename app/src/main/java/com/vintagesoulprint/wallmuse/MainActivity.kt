package com.vintagesoulprint.wallmuse

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.TextureView
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.text.InputType
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.math.ceil
import kotlin.math.max

/**
 * VSP Wall Studio: offline artwork visualization/editor, without network, account or ad SDKs.
 * Build Android App Bundle (AAB) for Play; APK is useful for local testing only.
 */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_WALL = 101
        private const val REQUEST_ART = 102
        private const val REQUEST_CAMERA = 103
        private const val REQUEST_CAMERA_PERMISSION = 104
        private const val BG = 0xFF151B1B.toInt()
        private const val PANEL = 0xFF222B2A.toInt()
        private const val GOLD = 0xFFD6BA88.toInt()
        private const val PAPER = 0xFFF6F1E7.toInt()
        private const val MUTED = 0xFFAFBBB5.toInt()
    }

    private lateinit var root: LinearLayout
    private lateinit var editorScroll: ScrollView
    private var normalPreviewHeight=0
    private var savedEditorScroll=0
    private lateinit var headerBar: LinearLayout
    private lateinit var fullscreenExitButton: Button
    private lateinit var canvas: WallCanvasView
    private lateinit var preview: TextureView
    private lateinit var canvasHolder: FrameLayout
    private lateinit var cameraButton: Button
    private var liveCamera: LiveCamera? = null
    private var liveMode = false
    private lateinit var tools: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var sizeSlider: SeekBar
    private lateinit var focusToolbar: LinearLayout
    private lateinit var focusHeaderLabel: TextView
    private lateinit var focusSizeLabel: TextView
    private lateinit var sizeValue: TextView
    private lateinit var angleValue: TextView
    private lateinit var selectedLabel: TextView
    private lateinit var focusButton: Button
    private lateinit var actionsRow: HorizontalScrollView
    private lateinit var headlineLabel: TextView
    private lateinit var beforeButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private var wallWidthCm = 300f
    private var editing = true
    private var exporting = false
    private var pendingCamera: Uri? = null
    private var wallUri: Uri? = null
    private val undo = ArrayDeque<WallCanvasView.State>()
    private val redo = ArrayDeque<WallCanvasView.State>()

    private data class MemorySession(
        val wall: Bitmap,
        val source: Uri?,
        val state: WallCanvasView.State,
        val undoStates: List<WallCanvasView.State>,
        val redoStates: List<WallCanvasView.State>,
        val artworkVisible: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        wallWidthCm = getPreferences(MODE_PRIVATE).getFloat("wall_width_cm", 300f)
        buildUi()
        pendingCamera = savedInstanceState?.getString("camera_output_uri")?.let(Uri::parse)
        val retained = lastNonConfigurationInstance as? MemorySession
        if (retained != null) restoreMemorySession(retained)
        else restoreSavedDraft()
        // API 36 edge-to-edge: inset all interactive controls out of the system bar areas.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val system = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else null
            if (editing) {
                v.setPadding(dp(10), (system?.top ?: insets.systemWindowInsetTop) + dp(6),
                    dp(10), (system?.bottom ?: insets.systemWindowInsetBottom) + dp(5))
            } else {
                // Immersive preview really extends behind both system bars.
                v.setPadding(0, 0, 0, 0)
            }
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCamera?.let { outState.putString("camera_output_uri", it.toString()) }
        wallUri?.let { outState.putString("wall_source_uri", it.toString()) }
    }

    override fun onStop() {
        // Store the tiny edit manifest, not the photo pixels, inside private app storage.
        if (::canvas.isInitialized) {
            try {
                ProjectStore.write(this, wallUri?.toString(), canvas.state(), canvas.showArtwork)
            } catch (_: Exception) { /* An autosave error must never crash or block the editor. */ }
        }
        super.onStop()
    }

    override fun onRetainNonConfigurationInstance(): Any? {
        if (!::canvas.isInitialized) return null
        val room = canvas.wallPhoto ?: return null
        return MemorySession(room, wallUri, canvas.state(), undo.toList(), redo.toList(), canvas.showArtwork)
    }

    private fun restoreMemorySession(session: MemorySession) {
        wallUri = session.source
        canvas.setWall(session.wall)
        canvas.restore(session.state)
        undo.clear()
        redo.clear()
        undo.addAll(session.undoStates)
        redo.addAll(session.redoStates)
        canvas.setBeforeMode(!session.artworkVisible)
        beforeButton.text = getString(if (session.artworkVisible) R.string.before else R.string.after)
        syncControls()
    }

    private fun restoreSavedDraft() {
        // Called only on a fresh Activity, never over an in-memory rotation session.
        try {
            val draft = ProjectStore.read(this) ?: return
            if (draft.wallUri != null) {
                val roomUri = Uri.parse(draft.wallUri)
                val room = decodeBitmap(roomUri, 4096)
                canvas.setWall(room)
                wallUri = roomUri
            } else {
                canvas.setDefaultWall()
                wallUri = null
            }
            // Duplicated layers share one bitmap; decode each unique selected URI once.
            val cache = mutableMapOf<String, Bitmap>()
            val layers = draft.layers.mapNotNull { saved ->
                val artworkBitmap = try {
                    cache.getOrPut(saved.sourceUri) { decodeBitmap(Uri.parse(saved.sourceUri), 2400) }
                } catch (_: Exception) { null }
                artworkBitmap?.let {
                    WallCanvasView.ArtLayer(saved.id, it, saved.cx, saved.cy,
                        saved.widthFraction, saved.angle, saved.frame, saved.sourceUri,
                        saved.printW,
                        if(saved.printH==0f) saved.printW * it.height / max(1,it.width) else saved.printH,
                        saved.trim,
                        saved.cropLeft, saved.cropTop, saved.cropRight, saved.cropBottom,
                        saved.tlx,saved.tly,saved.trx,saved.tryY,
                        saved.brx,saved.bry,saved.blx,saved.bly)
                }
            }
            canvas.restore(WallCanvasView.State(layers, draft.selectedId))
            canvas.setBeforeMode(!draft.showArtwork)
            beforeButton.text = getString(if (draft.showArtwork) R.string.before else R.string.after)
            syncControls()
        } catch (_: Exception) {
            // The owner may have moved or removed a source photo since the last session.
            wallUri = null
            toast(R.string.draft_restore_error)
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        editorScroll=ScrollView(this).apply{
            isFillViewport=true
            clipToPadding=false
            overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(root,ScrollView.LayoutParams(-1,-2))
        }
        setContentView(editorScroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(4), dp(6), dp(10))
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(label("VSP WALL\nSTUDIO", 23f, PAPER, true).apply { letterSpacing = .11f })
        titleBox.addView(label(getString(R.string.subtitle), 9f, GOLD, true).apply {
            letterSpacing = .14f
        })
        headerBar = header
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        focusButton = button(getString(R.string.focus), "quiet") { setFocusMode(editing) }
        header.addView(focusButton)
        header.addView(button("ⓘ", "quiet") {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.about))
                .setMessage(getString(R.string.about_message) + "\n\n" +
                    getString(R.string.draft_hint))
                .setPositiveButton("OK", null).show()
        })
        root.addView(header)

        actionsRow = hRow().apply {
            // The primary action is visible first on narrow mobile screens.
            addAction(button(getString(R.string.add_art), "gold") {
                if (canvas.artCount >= 12) toast(R.string.max_art)
                else pickImage(REQUEST_ART)
            })
            addAction(button(getString(R.string.pick_wall), "outlined") { pickImage(REQUEST_WALL) })
            addAction(button(getString(R.string.take_photo), "outlined") { takeCameraPhoto() })
            addAction(button(getString(R.string.live_camera), "outlined") {toggleLiveCamera()}.also{cameraButton=it})
            addAction(button(getString(R.string.new_project), "outlined") { confirmNewProject() })
        }
        root.addView(actionsRow)
        headlineLabel = label(getString(R.string.headline), 15f, PAPER, true).apply {
            setPadding(dp(10), dp(10), 0, dp(7))
        }
        root.addView(headlineLabel)

        canvasHolder = FrameLayout(this)
        preview = TextureView(this).apply {
            visibility = View.GONE
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if(right-left!=oldRight-oldLeft || bottom-top!=oldBottom-oldTop) {
                    liveCamera?.updateTransform()
                }
            }
        }
        canvasHolder.addView(preview,FrameLayout.LayoutParams(-1,-1))
        canvas = WallCanvasView(this)
        canvas.onSelectionChanged = { syncControls() }
        canvas.onGestureStart = { checkpoint() }
        canvasHolder.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        // The ordinary header is hidden in fullscreen, so keep this floating
        // exit control above the preview: fullscreen never traps the user.
        fullscreenExitButton = button("✕  " + getString(R.string.edit), "outlined") {
            setFocusMode(false)
        }.apply { visibility = View.GONE }
        canvasHolder.addView(fullscreenExitButton,
            FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.END).apply {
                setMargins(dp(8),dp(14),dp(12),dp(8))
            })
        val focusHeader=label("FOCUS VIEW",12f,PAPER,true).apply{
            gravity=Gravity.CENTER_VERTICAL
            letterSpacing=.14f
            setPadding(dp(14),0,0,0)
            visibility=View.GONE
        }
        canvasHolder.addView(focusHeader,
            FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP or Gravity.START).apply{
                setMargins(dp(10),dp(14),0,0)
            })
        focusHeaderLabel=focusHeader
        // Website-style Focus View: editing remains active while chrome is hidden.
        focusToolbar = LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(dp(9),dp(9),dp(9),dp(12))
            background=panelBackground()
            visibility=View.GONE
        }
        focusSizeLabel=label("—",12f,PAPER,true).apply{
            gravity=Gravity.CENTER
            setPadding(0,0,0,dp(8))
        }
        focusToolbar.addView(focusSizeLabel)
        val focusButtons=LinearLayout(this).apply{
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
        }
        focusButtons.addView(button("－","outlined"){ adjustFocusSize(-.035f) },
            LinearLayout.LayoutParams(0,dp(50),1f).apply{marginEnd=dp(6)})
        focusButtons.addView(button("＋","outlined"){ adjustFocusSize(.035f) },
            LinearLayout.LayoutParams(0,dp(50),1f).apply{marginEnd=dp(6)})
        focusButtons.addView(button("⟳ 90°","outlined"){ rotateArtwork() },
            LinearLayout.LayoutParams(0,dp(50),1.2f).apply{marginEnd=dp(6)})
        focusButtons.addView(button("⚙ "+getString(R.string.focus_settings),"outlined"){
            showFocusSettings()
        },LinearLayout.LayoutParams(0,dp(50),2.7f).apply{marginEnd=dp(6)})
        focusButtons.addView(button("↓","gold"){exportImage(false)},
            LinearLayout.LayoutParams(0,dp(50),1f))
        focusToolbar.addView(focusButtons)
        canvasHolder.addView(focusToolbar,
            FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply{
                setMargins(dp(12),0,dp(12),dp(15))
            })
        // Full-height editor preview: approximately 75% of the device screen.
        // The editing tools remain accessible by scrolling, not by shrinking video.
        normalPreviewHeight=(resources.displayMetrics.heightPixels*.75f).toInt().coerceAtLeast(dp(300))
        root.addView(canvasHolder,LinearLayout.LayoutParams(-1,normalPreviewHeight))

        tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = panelBackground()
        }
        val toolbar = hRow().apply {
            addAction(button(getString(R.string.print_size), "quiet") { showPrintSizes() })
            addAction(button(getString(R.string.crop), "quiet") { showCropSettings() })
            addAction(button(getString(R.string.perspective), "quiet") { showPerspectiveSettings() })
            addAction(button("⟳ 90°", "quiet") { rotateArtwork() })
            addAction(button(getString(R.string.frame_library), "quiet") { showFrames() })
            addAction(button(getString(R.string.wall_width), "quiet") { showWallWidth() })
            addAction(button(getString(R.string.random_frame), "quiet") { randomFrame() })
            addAction(button(getString(R.string.duplicate), "quiet") {
                if (canvas.selected == null) toast(R.string.no_selection)
                else if (canvas.artCount >= 12) toast(R.string.max_art)
                else { checkpoint(); canvas.duplicateSelected() }
            })
            addAction(button(getString(R.string.remove), "quiet") {
                if (canvas.selected == null) toast(R.string.no_selection)
                else { checkpoint(); canvas.removeSelected() }
            })
            addAction(button(getString(R.string.reset), "quiet") { confirmClear() })
            addAction(button(getString(R.string.undo), "quiet") { performUndo() }.also { undoButton = it })
            addAction(button(getString(R.string.redo), "quiet") { performRedo() }.also { redoButton = it })
        }
        tools.addView(toolbar)

        selectedLabel = label(getString(R.string.selection_hint), 11f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(3))
        }
        tools.addView(selectedLabel)

        val sizeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sizeRow.addView(label(getString(R.string.size), 12f, PAPER, true),
            LinearLayout.LayoutParams(dp(47), -2))
        sizeSlider = slider().apply { max = 73; progress = 20 }
        sizeRow.addView(sizeSlider, LinearLayout.LayoutParams(0, dp(33), 1f))
        sizeValue = label("32%", 11f, GOLD, true).apply { gravity = Gravity.END }
        sizeRow.addView(sizeValue, LinearLayout.LayoutParams(dp(43), -2))
        tools.addView(sizeRow)

        val angleRow = LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        angleRow.addView(button("⟳ 90°", "quiet"){ rotateArtwork() },
            LinearLayout.LayoutParams(dp(105),dp(40)))
        angleRow.addView(button(getString(R.string.perspective), "quiet"){
            showPerspectiveSettings()
        },LinearLayout.LayoutParams(0,dp(40),1f))
        angleValue=label("0°",11f,GOLD,true).apply{gravity=Gravity.END}
        angleRow.addView(angleValue,LinearLayout.LayoutParams(dp(48),-2))
        tools.addView(angleRow)
        root.addView(tools)

        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { if (canvas.selected != null) checkpoint() }
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    canvas.setSize((12 + progress) / 100f)
                    sizeValue.text = "${12 + progress}%"
                }
            }
        })

        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(2))
        }
        val beforeSave = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        beforeButton = button(getString(R.string.before), "outlined") {
            canvas.setBeforeMode(canvas.showArtwork)
            beforeButton.text = getString(if (canvas.showArtwork) R.string.before else R.string.after)
        }
        beforeSave.addView(beforeButton)
        val hint = label(getString(R.string.export_note), 10f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
        }
        beforeSave.addView(hint, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(beforeSave)

        val exportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        saveButton = button(getString(R.string.save), "gold") { exportImage(false) }
        shareButton = button(getString(R.string.share), "outlined") { exportImage(true) }
        exportRow.addView(saveButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(8)
        })
        exportRow.addView(shareButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(exportRow)
        root.addView(bottom)
        syncControls()
    }

    private fun toggleLiveCamera() {
        if(liveMode){disableLiveCamera();return}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.CAMERA),REQUEST_CAMERA_PERMISSION)
            return
        }
        liveMode=true
        preview.visibility=View.VISIBLE
        canvas.livePreview=true
        cameraButton.text=getString(R.string.stop_live_camera)
        liveCamera=LiveCamera(this,preview){message->
            runOnUiThread {Toast.makeText(this,message,Toast.LENGTH_LONG).show();disableLiveCamera()}
        }
        liveCamera?.start()
    }
    private fun disableLiveCamera() {
        liveMode=false
        liveCamera?.stop();liveCamera=null
        preview.visibility=View.GONE
        canvas.livePreview=false
        if(::cameraButton.isInitialized)cameraButton.text=getString(R.string.live_camera)
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==REQUEST_CAMERA_PERMISSION){
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)toggleLiveCamera()
            else toast(R.string.camera_permission_denied)
        }
    }
    override fun onPause(){liveCamera?.stop();super.onPause()}
    override fun onResume(){super.onResume();if(liveMode)liveCamera?.start();if(!editing)applyImmersiveBars(true)}

    private fun setFocusMode(active: Boolean) {
        if(active)savedEditorScroll=editorScroll.scrollY
        editing = !active
        canvasHolder.layoutParams=canvasHolder.layoutParams.apply{
            height=if(active)resources.displayMetrics.heightPixels.coerceAtLeast(dp(360)) else normalPreviewHeight
        }
        val visibility = if(active)View.GONE else View.VISIBLE
        headerBar.visibility=visibility
        tools.visibility=visibility
        bottom.visibility=visibility
        actionsRow.visibility=visibility
        headlineLabel.visibility=visibility
        fullscreenExitButton.visibility=if(active)View.VISIBLE else View.GONE
        focusToolbar.visibility=if(active)View.VISIBLE else View.GONE
        focusHeaderLabel.visibility=if(active)View.VISIBLE else View.GONE
        focusButton.text=getString(if(active)R.string.edit else R.string.focus)
        canvas.focusMode=active
        canvas.showSelectionHandles=!active
        // IMPORTANT: Focus View is an EDITOR, never a locked preview.
        canvas.isEnabled=true
        canvas.invalidate()
        syncControls()
        applyImmersiveBars(active)
        root.requestApplyInsets()
        if(active)editorScroll.post{editorScroll.scrollTo(0,0)}
        else editorScroll.post{editorScroll.scrollTo(0,savedEditorScroll)}
    }

    private fun adjustFocusSize(delta:Float) {
        val layer=canvas.selected?:run{toast(R.string.no_selection);return}
        checkpoint()
        canvas.setSize((layer.widthFraction+delta).coerceIn(.08f,.88f))
        syncControls()
    }

    private fun rotateArtwork(){
        if(canvas.selected==null){toast(R.string.no_selection);return}
        checkpoint()
        canvas.rotateQuarterTurn()
        syncControls()
    }

    /**
     * Guided perspective: select one of eight viewpoints, adjust strength, Apply.
     * The preview uses the actual CROPPED image + frame, not the original photo.
     * Four-corner fine tuning is an optional advanced control.
     */
    private fun showPerspectiveSettings(){
        val layer=canvas.selected?:run{toast(R.string.no_selection);return}
        val previewArt=canvas.framedCropForPerspective()?:layer.bitmap
        val editor=PerspectiveEditorView(this,previewArt,
            canvas.selectedPerspective()?:PerspectiveGeometry.IDENTITY)
        var selectedX=0
        var selectedY=0
        var strength=38
        val scroll=ScrollView(this).apply {isFillViewport=false}
        val wrapper=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(8),dp(14),dp(8))
        }
        scroll.addView(wrapper)

        wrapper.addView(label(getString(R.string.perspective_easy_hint),13f,BG,true).apply{
            setPadding(dp(6),0,dp(5),dp(8))
        })
        wrapper.addView(editor,LinearLayout.LayoutParams(-1,dp(264)))
        wrapper.addView(label(getString(R.string.perspective_direction),13f,BG,true).apply{
            setPadding(dp(5),dp(10),dp(5),dp(5))
        })
        val directionLabel=label(getString(R.string.perspective_straight),11f,BG).apply{
            gravity=Gravity.CENTER
            setPadding(0,dp(2),0,dp(6))
        }
        val directions=listOf(
            Triple("↖",-1,-1),Triple("↑",0,-1),Triple("↗",1,-1),
            Triple("←",-1,0),Triple("⊙",0,0),Triple("→",1,0),
            Triple("↙",-1,1),Triple("↓",0,1),Triple("↘",1,1)
        )
        val grid=GridLayout(this).apply{
            columnCount=3
            alignmentMode=GridLayout.ALIGN_BOUNDS
        }
        directions.forEach{(symbol,dx,dy)->
            val btn=button(symbol,"outlined"){
                selectedX=dx;selectedY=dy
                editor.setPreset(dx,dy,strength)
                directionLabel.text=if(dx==0&&dy==0)getString(R.string.perspective_straight)
                    else getString(R.string.perspective_direction_selected)+" "+symbol
            }.apply {textSize=21f;contentDescription=getString(R.string.perspective_direction)+" "+symbol}
            grid.addView(btn,GridLayout.LayoutParams().apply{
                width=dp(76);height=dp(48)
                setMargins(dp(4),dp(2),dp(4),dp(2))
            })
        }
        wrapper.addView(grid,LinearLayout.LayoutParams(-2,-2).apply {gravity=Gravity.CENTER_HORIZONTAL})
        wrapper.addView(directionLabel)
        val strengthValue=label(getString(R.string.perspective_strength)+" 38%",12f,BG,true).apply{
            setPadding(dp(5),dp(8),0,0)
        }
        wrapper.addView(strengthValue)
        wrapper.addView(SeekBar(this).apply{
            max=100
            progress=strength
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(s:SeekBar) = Unit
                override fun onStopTrackingTouch(s:SeekBar) = Unit
                override fun onProgressChanged(s:SeekBar,p:Int,fromUser:Boolean){
                    if(fromUser){
                        strength=p
                        strengthValue.text=getString(R.string.perspective_strength)+" "+p+"%"
                        if(selectedX!=0||selectedY!=0)editor.setPreset(selectedX,selectedY,strength)
                    }
                }
            })
        },LinearLayout.LayoutParams(-1,dp(48)))
        wrapper.addView(CheckBox(this).apply{
            text=getString(R.string.perspective_fine_adjust)
            setTextColor(BG)
            textSize=13f
            minHeight=dp(48)
            setOnCheckedChangeListener{_,checked->editor.fineAdjust=checked}
        })
        wrapper.addView(label(getString(R.string.perspective_fine_hint),11f,BG).apply{
            setPadding(dp(6),0,dp(5),dp(8))
        })

        val dialog=AlertDialog.Builder(this).setTitle(R.string.perspective)
            .setView(scroll)
            .setPositiveButton(R.string.apply){_,_->
                checkpoint()
                canvas.setPerspective(editor.result())
                syncControls()
            }.setNegativeButton(R.string.cancel,null)
            .setNeutralButton(R.string.perspective_reset,null).create()
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener{
                selectedX=0;selectedY=0
                editor.reset()
                directionLabel.text=getString(R.string.perspective_straight)
            }
        }
        dialog.show()
    }

    /** Bottom-anchored editor settings, available without leaving Focus View. */
    private fun showFocusSettings(){
        val items=arrayOf(
            getString(R.string.print_size),
            getString(R.string.wall_width),
            getString(R.string.frame_library),
            getString(R.string.crop),
            getString(R.string.perspective),
            "⟳ 90°",
            getString(R.string.duplicate),
            getString(R.string.remove),
            getString(R.string.reset)
        )
        val dialog=AlertDialog.Builder(this)
            .setTitle(R.string.focus_settings)
            .setItems(items){_,index->
                when(index){
                    0->showPrintSizes()
                    1->showWallWidth()
                    2->showFrames()
                    3->showCropSettings()
                    4->showPerspectiveSettings()
                    5->rotateArtwork()
                    6->{
                        if(canvas.selected==null)toast(R.string.no_selection)
                        else if(canvas.artCount>=12)toast(R.string.max_art)
                        else{checkpoint();canvas.duplicateSelected();syncControls()}
                    }
                    7->{
                        if(canvas.selected==null)toast(R.string.no_selection)
                        else{checkpoint();canvas.removeSelected();syncControls()}
                    }
                    8->confirmClear()
                }
            }.setNegativeButton(R.string.cancel,null).create()
        dialog.setOnShowListener{
            dialog.window?.apply{
                setGravity(Gravity.BOTTOM)
                setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
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
    }

    @Deprecated("Use system back callback when upgrading Android runtime dependencies")
    override fun onBackPressed() {
        if (!editing) setFocusMode(false) else super.onBackPressed()
    }

    private fun syncControls() {
        if (!::canvas.isInitialized || !::sizeSlider.isInitialized) return
        val layer = canvas.selected
        sizeSlider.isEnabled = layer != null
        sizeSlider.progress = if (layer == null) 20 else (layer.widthFraction * 100).toInt() - 12
        sizeValue.text = if (layer == null) "—" else "${(layer.widthFraction * 100).toInt()}%"
        angleValue.text = if (layer == null) "—" else "${layer.angle.toInt()}°"
        if(::focusSizeLabel.isInitialized) {
            focusSizeLabel.text=if(layer==null)getString(R.string.no_selection)
            else "${PrintCatalog.num(layer.printW.toInt())} × ${PrintCatalog.num(layer.printH.toInt())} cm  ·  ${(layer.widthFraction*100).toInt()}%"
        }
        selectedLabel.text = if(layer==null) getString(R.string.selection_hint)
            else getString(R.string.selected) + "  ·  " +
                "${PrintCatalog.num(layer.printW.toInt())} × ${PrintCatalog.num(layer.printH.toInt())} cm" +
                "  ·  " + FrameCatalog.displayName(layer.frame)
        undoButton.isEnabled = undo.isNotEmpty()
        redoButton.isEnabled = redo.isNotEmpty()
        undoButton.alpha = if (undo.isEmpty()) .43f else 1f
        redoButton.alpha = if (redo.isEmpty()) .43f else 1f
    }

    private fun checkpoint() {
        undo.push(canvas.state())
        if (undo.size > 30) undo.removeLast()
        redo.clear()
        syncControls()
    }

    private fun performUndo() {
        if (undo.isEmpty()) return
        redo.push(canvas.state())
        canvas.restore(undo.pop())
        syncControls()
    }

    private fun performRedo() {
        if (redo.isEmpty()) return
        undo.push(canvas.state())
        canvas.restore(redo.pop())
        syncControls()
    }

    /** Categorised frame library; tapping a swatch applies the style immediately. */
    private fun showFrames() {
        if (canvas.selected == null) { toast(R.string.no_selection); return }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(8),dp(14),dp(8)) }
        scroll.addView(content)
        // Every swatch and every saved artwork uses the same native frame engine.
        val groups = listOf("Modern", "Ahşap", "Antika", "Oval & Yuvarlak", "Tuval")
        val dialog = AlertDialog.Builder(this).setTitle(R.string.frame_library)
            .setView(scroll).setNegativeButton(R.string.cancel, null).create()
        groups.forEach { category ->
            content.addView(label(FrameCatalog.categoryName(category), 16f, BG, true).apply {setPadding(dp(6),dp(16),0,dp(8))})
            val grid = GridLayout(this).apply { columnCount = 2 }
            FrameCatalog.frames.forEachIndexed { index, frame ->
                if (frame.category != category) return@forEachIndexed
                val sample = FrameSwatchView(this,index)
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(5),dp(5),dp(5),dp(8))
                    setBackgroundColor(0xFFF1EDE7.toInt())
                    addView(sample, LinearLayout.LayoutParams(-1,dp(76)))
                    addView(label(FrameCatalog.displayName(index),11f,BG).apply { gravity=Gravity.CENTER;maxLines=2 },
                        LinearLayout.LayoutParams(-1,dp(37)))
                    setOnClickListener {checkpoint();canvas.setFrame(index);syncControls();dialog.dismiss()}
                }
                grid.addView(col,GridLayout.LayoutParams().apply {
                    width=dp(140);height=dp(125);setMargins(dp(3),dp(3),dp(3),dp(3))
                })
            }
            content.addView(grid)
        }
        dialog.show()
    }

    private fun randomFrame() {
        if (canvas.selected == null) { toast(R.string.no_selection); return }
        val current = canvas.selected!!.frame
        val alternatives = FrameCatalog.frames.indices.filter { it != current }
        checkpoint();canvas.setFrame(alternatives.random());syncControls()
    }

    private fun showPrintSizes() {
        if (canvas.selected == null) { toast(R.string.no_selection); return }
        AlertDialog.Builder(this).setTitle(R.string.print_size)
            .setItems(PrintCatalog.categories.toTypedArray()) { _, category ->
                if (category == 6) { showCustomSize();return@setItems }
                val sizes=PrintCatalog.inCategory(category)
                AlertDialog.Builder(this).setTitle(PrintCatalog.categories[category])
                    .setItems(sizes.map { it.name }.toTypedArray()) { _, index ->
                        val choice=sizes[index]
                        checkpoint();canvas.setPrintSize(choice.widthCm,choice.heightCm,wallWidthCm)
                        syncControls()
                    }.setNegativeButton(R.string.cancel,null).show()
            }.setNegativeButton(R.string.cancel,null).show()
    }

    private fun showCustomSize() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(7),dp(24),dp(3)) }
        val inputW=EditText(this).apply { hint=getString(R.string.width_cm);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val inputH=EditText(this).apply { hint=getString(R.string.height_cm);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val old=canvas.selected
        inputW.setText("${old?.printW?.toInt() ?: 40}");inputH.setText("${old?.printH?.toInt() ?: 60}")
        box.addView(inputW);box.addView(inputH)
        AlertDialog.Builder(this).setTitle(R.string.custom_size).setView(box)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.apply) { _,_ ->
                val w=inputW.text.toString().replace(',','.').toFloatOrNull()
                val h=inputH.text.toString().replace(',','.').toFloatOrNull()
                if(w==null||h==null||w !in 5f..300f||h !in 5f..300f){toast(R.string.invalid_size)}
                else{checkpoint();canvas.setPrintSize(w,h,wallWidthCm);syncControls()}
            }.show()
    }

    private fun showWallWidth() {
        val input=EditText(this).apply {inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(wallWidthCm.toInt().toString());selectAll();setSingleLine(true)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(12),dp(22),0)
            addView(label(getString(R.string.wall_width_explain),13f,BG));addView(input)}
        AlertDialog.Builder(this).setTitle(R.string.wall_width).setView(box)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.apply){_,_->
                val width=input.text.toString().replace(',','.').toFloatOrNull()
                if(width==null||width !in 50f..1500f){toast(R.string.invalid_size)}
                else{checkpoint();wallWidthCm=width
                    getPreferences(MODE_PRIVATE).edit().putFloat("wall_width_cm",wallWidthCm).apply()
                    canvas.recalibrate(wallWidthCm)
                    syncControls()}
            }.show()
    }

    /** Real, touch-based photo crop: drag the crop box or its four corners. */
    private fun showCropSettings() {
        val layer=canvas.selected?:run{toast(R.string.no_selection);return}
        val cropEditor=CropEditorView(this,layer.bitmap,layer).apply {
            layoutParams=LinearLayout.LayoutParams(-1,dp(390))
        }
        val wrapper=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(8),dp(12),0)
            addView(label(getString(R.string.crop_hint),12f,BG).apply {
                setPadding(dp(6),dp(2),dp(6),dp(9))
            })
            addView(cropEditor)
        }
        val dialog=AlertDialog.Builder(this).setTitle(R.string.crop)
            .setView(wrapper)
            .setPositiveButton(R.string.apply) { _,_ ->
                checkpoint()
                canvas.setCrop(cropEditor.cropRect())
                syncControls()
            }.setNegativeButton(R.string.cancel,null)
            .setNeutralButton(R.string.crop_reset,null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { cropEditor.resetCrop() }
        }
        dialog.show()
    }

    private fun confirmClear() {
        if (canvas.artCount == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_reset_title)
            .setMessage(R.string.confirm_reset_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_yes) { _, _ -> checkpoint(); canvas.clearArtwork() }
            .show()
    }

    private fun confirmNewProject() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_new_title)
            .setMessage(R.string.confirm_new_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm_new_yes) { _, _ ->
                if(liveMode)disableLiveCamera()
                wallUri = null
                canvas.clearWall()
                undo.clear()
                redo.clear()
                beforeButton.text = getString(R.string.before)
                syncControls()
                try {
                    ProjectStore.write(this, null, canvas.state(), canvas.showArtwork)
                } catch (_: Exception) { /* Never block creating a fresh wall. */ }
            }.show()
    }

    private fun pickImage(request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(intent, request) }
        catch (_: Exception) { toast(R.string.image_error) }
    }

    private fun takeCameraPhoto() {
        val name = "WM_CAMERA_${timestamp()}.jpg"
        val content = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VSP Wall Studio")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val photo = try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, content)
        } catch (_: Exception) { null }
        if (photo == null) { toast(R.string.camera_error); return }
        pendingCamera = photo
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photo)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "VSP Wall Studio wall photo", photo)
        }
        try { startActivityForResult(intent, REQUEST_CAMERA) }
        catch (_: Exception) {
            contentResolver.delete(photo, null, null)
            pendingCamera = null
            toast(R.string.camera_error)
        }
    }

    @Deprecated("Framework Activity results are used to avoid external runtime dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAMERA) {
            val uri = pendingCamera
            pendingCamera = null
            if (uri != null && resultCode == RESULT_OK) {
                try {
                    val change = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    contentResolver.update(uri, change, null, null)
                    loadWall(uri)
                } catch (_: Exception) { toast(R.string.image_error) }
            } else if (uri != null) contentResolver.delete(uri, null, null)
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val flags = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (flags != 0) contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) { /* Session grant still works */ }
        when (requestCode) {
            REQUEST_WALL -> loadWall(uri)
            REQUEST_ART -> loadArt(uri)
        }
    }

    private fun loadWall(uri: Uri) {
        if(liveMode)disableLiveCamera()
        try {
            val bitmap = decodeBitmap(uri, 4096)
            canvas.replaceWallKeepArtwork(bitmap)
            wallUri = uri
            undo.clear(); redo.clear()
            beforeButton.text = getString(R.string.before)
            syncControls()
        } catch (_: Exception) { toast(R.string.image_error) }
    }

    private fun loadArt(uri: Uri) {
        try {
            val bitmap = decodeBitmap(uri, 2400)
            checkpoint()
            if (!canvas.addArtwork(bitmap, uri.toString())) toast(R.string.max_art)
        } catch (_: Exception) { toast(R.string.image_error) }
    }

    private fun decodeBitmap(uri: Uri, maxDimension: Int): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            val sample = max(1, ceil(longest.toDouble() / maxDimension).toInt())
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Normalize EXIF rotation through the Android decoder.
            decoder.isMutableRequired = false
        }
    }

    private fun exportImage(share: Boolean) {
        if (canvas.wallPhoto == null) { toast(R.string.choose_wall_first); return }
        if (canvas.artCount == 0) { toast(R.string.choose_art_first); return }
        if (exporting) return
        // Capture immutable geometry on the UI thread before any background drawing starts.
        val currentCameraFrame = if(liveMode) preview.bitmap else null
        if(liveMode && currentCameraFrame == null){toast(R.string.camera_not_ready);return}
        val renderSnapshot = canvas.captureForExport(currentCameraFrame) ?: return
        exporting = true
        saveButton.isEnabled = false
        shareButton.isEnabled = false
        val previous = saveButton.text
        saveButton.text = getString(R.string.exporting)
        // Prevent changing composition while the asynchronous export draws bitmaps.
        canvas.isEnabled = false
        Thread {
            var bitmap: Bitmap? = null
            var result: Uri? = null
            try {
                bitmap = canvas.exportBitmap(renderSnapshot)
                result = writeJpeg(bitmap)
            } catch (_: Throwable) {
                // MediaStore is rolled back by writeJpeg when its output operation fails.
            } finally {
                bitmap?.recycle()
                runOnUiThread {
                    exporting = false
                    canvas.isEnabled = true
                    saveButton.isEnabled = true
                    shareButton.isEnabled = true
                    saveButton.text = previous
                    if (result != null) {
                        if (share) shareUri(result!!)
                        else toast(R.string.saved)
                    } else toast(R.string.save_error)
                }
            }
        }.start()
    }

    private fun writeJpeg(bitmap: Bitmap): Uri {
        val metadata = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "VSP_Wall_Studio_${timestamp()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/VSP Wall Studio")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metadata)
            ?: throw IOException("MediaStore refused insertion")
        try {
            val stream = contentResolver.openOutputStream(uri)
                ?: throw IOException("Output stream unavailable")
            stream.use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it))
                    throw IOException("JPEG compression failed")
            }
            contentResolver.update(uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun shareUri(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "VSP Wall Studio preview", uri)
        }
        startActivity(Intent.createChooser(share, getString(R.string.share)))
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density + .5f).toInt()
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            this.textSize = sp
            setTextColor(color)
            includeFontPadding = true
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    private fun button(text: String, type: String, handler: () -> Unit): Button =
        buttonView(text, type) { handler() }

    private fun buttonView(text: String, type: String, handler: (View) -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 11.5f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            elevation = 0f
            setPadding(dp(13), dp(9), dp(13), dp(9))
            val backgroundColor = when (type) {
                "gold" -> GOLD
                "outlined" -> PANEL
                else -> 0xFF303A38.toInt()
            }
            val stroke = if (type == "outlined") 0xFF67716B.toInt() else backgroundColor
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), stroke)
            }
            setTextColor(if (type == "gold") BG else PAPER)
            setOnClickListener { handler(this) }
        }

    private fun hRow(): HorizontalScrollView = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(row)
    }

    private fun HorizontalScrollView.addAction(button: Button) {
        (getChildAt(0) as LinearLayout).addView(button,
            LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(6) })
    }

    private fun slider(): SeekBar = SeekBar(this).apply {
        progressTintList = android.content.res.ColorStateList.valueOf(GOLD)
        thumbTintList = android.content.res.ColorStateList.valueOf(GOLD)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF5B6761.toInt())
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(PANEL)
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), 0xFF3C4641.toInt())
    }
}

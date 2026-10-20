package com.vintagesoulprint.wallmuse

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Small local draft manifest. The source pixels stay in their original device photos;
 * the editor stores only user-picked document URIs and visual geometry.
 * No server, database or broad storage permissions are required.
 */
object ProjectStore {
    private const val FILE_NAME = "last_wallmuse_draft.json"
    private const val FORMAT_VERSION = 4

    data class StoredLayer(
        val id: Long, val sourceUri: String, val cx: Float, val cy: Float,
        val widthFraction: Float, val angle: Float, val frame: Int,
        val printW: Float, val printH: Float, val trim: Float,
        val cropLeft: Float, val cropTop: Float, val cropRight: Float, val cropBottom: Float,
        val tlx:Float, val tly:Float, val trx:Float, val tryY:Float,
        val brx:Float, val bry:Float, val blx:Float, val bly:Float
    )
    data class Draft(
        val wallUri: String?, val layers: List<StoredLayer>,
        val selectedId: Long?, val showArtwork: Boolean
    )

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))

    fun write(context: Context, wallUri: String?, state: WallCanvasView.State,
              showArtwork: Boolean) {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("wall", wallUri ?: JSONObject.NULL)
        root.put("selected", state.selectedId ?: JSONObject.NULL)
        root.put("showArtwork", showArtwork)
        val layers = JSONArray()
        state.elements.forEach { layer ->
            val source = layer.sourceUri ?: return@forEach
            val json = JSONObject()
            json.put("id", layer.id)
            json.put("uri", source)
            json.put("cx", layer.cx.toDouble())
            json.put("cy", layer.cy.toDouble())
            json.put("width", layer.widthFraction.toDouble())
            json.put("angle", layer.angle.toDouble())
            json.put("frame", layer.frame)
            json.put("printW", layer.printW.toDouble())
            json.put("printH", layer.printH.toDouble())
            json.put("trim", layer.trim.toDouble())
            json.put("cropLeft", layer.cropLeft.toDouble())
            json.put("cropTop", layer.cropTop.toDouble())
            json.put("cropRight", layer.cropRight.toDouble())
            json.put("cropBottom", layer.cropBottom.toDouble())
            json.put("perspective",JSONArray().apply{
                put(layer.tlx.toDouble());put(layer.tly.toDouble())
                put(layer.trx.toDouble());put(layer.tryY.toDouble())
                put(layer.brx.toDouble());put(layer.bry.toDouble())
                put(layer.blx.toDouble());put(layer.bly.toDouble())
            })
            layers.put(json)
        }
        root.put("layers", layers)
        val atom = file(context)
        val stream = atom.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            atom.finishWrite(stream)
        } catch (t: Throwable) {
            atom.failWrite(stream)
            throw t
        }
    }

    fun read(context: Context): Draft? {
        val atom = file(context)
        if (!atom.baseFile.exists()) return null
        val json = JSONObject(atom.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        if (json.optInt("version") !in 1..FORMAT_VERSION) return null
        val wall = if (json.isNull("wall")) null else json.optString("wall").takeIf { it.isNotBlank() }
        val layers = mutableListOf<StoredLayer>()
        val array = json.optJSONArray("layers") ?: JSONArray()
        for (index in 0 until minOf(array.length(), 12)) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("uri")
            if (uri.isBlank()) continue
            val perspective=item.optJSONArray("perspective")
            val coords=if(perspective!=null && perspective.length()==8)
                FloatArray(8){j->perspective.optDouble(j,PerspectiveGeometry.IDENTITY[j].toDouble()).toFloat()}
                else PerspectiveGeometry.IDENTITY.copyOf()
            val corners=if(PerspectiveGeometry.valid(coords))coords else PerspectiveGeometry.IDENTITY
            layers.add(StoredLayer(
                id = item.optLong("id", index.toLong() + 1), sourceUri = uri,
                cx = item.optDouble("cx", .5).toFloat().coerceIn(0f, 1f),
                cy = item.optDouble("cy", .5).toFloat().coerceIn(0f, 1f),
                widthFraction = item.optDouble("width", .3).toFloat().coerceIn(.10f, .85f),
                angle = PerspectiveGeometry.rotate(item.optDouble("angle", 0.0).toFloat()),
                frame = item.optInt("frame", 1).coerceIn(0, FrameCatalog.frames.lastIndex),
                printW = item.optDouble("printW",60.0).toFloat().coerceIn(5f,300f),
                printH = item.optDouble("printH",0.0).toFloat().coerceIn(0f,300f),
                trim = item.optDouble("trim",0.0).toFloat().coerceIn(0f,.24f),
                cropLeft = item.optDouble("cropLeft",0.0).toFloat().coerceIn(0f,.98f),
                cropTop = item.optDouble("cropTop",0.0).toFloat().coerceIn(0f,.98f),
                cropRight = item.optDouble("cropRight",1.0).toFloat().coerceIn(.01f,1f),
                cropBottom = item.optDouble("cropBottom",1.0).toFloat().coerceIn(.01f,1f),
                tlx=corners[0],tly=corners[1],trx=corners[2],tryY=corners[3],
                brx=corners[4],bry=corners[5],blx=corners[6],bly=corners[7]
            ))
        }
        val selected = if (json.isNull("selected")) null else json.optLong("selected")
        return Draft(wall, layers, selected, json.optBoolean("showArtwork", true))
    }
}

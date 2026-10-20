package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local-first, offline named project library. Existing "last draft" still works
 * exactly as before; each named project is an independent AtomicFile manifest.
 * Original photos remain on-device via persisted document grants.
 * Never copy private user photos to a server.
 */
object ProjectLibrary {
    private const val INDEX = "wall_studio_project_index.json"
    data class Entry(val id:String,val name:String,val modified:Long)
    private fun index(context:Context)=AtomicFile(File(context.filesDir,INDEX))
    private fun filename(id:String):String {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        return "project_${id}.json"
    }
    fun list(context:Context):List<Entry> {
        val atom=index(context)
        if(!atom.baseFile.exists())return emptyList()
        return try{
            val items=JSONArray(atom.openRead().bufferedReader(Charsets.UTF_8).use {it.readText()})
            (0 until items.length()).mapNotNull { i->
                val entry=items.optJSONObject(i)?:return@mapNotNull null
                val id=entry.optString("id")
                if(!id.matches(Regex("[a-zA-Z0-9_-]{1,64}")))return@mapNotNull null
                Entry(id,entry.optString("name","My Wall").take(75),entry.optLong("modified",0L))
            }.sortedByDescending {it.modified}
        }catch(_:Exception){emptyList()}
    }
    private fun writeIndex(context:Context,items:List<Entry>){
        val arr=JSONArray()
        items.forEach {
            arr.put(JSONObject().apply{
                put("id",it.id);put("name",it.name);put("modified",it.modified)
            })
        }
        val atom=index(context)
        val stream=atom.startWrite()
        try {
            stream.write(arr.toString().toByteArray(Charsets.UTF_8))
            atom.finishWrite(stream)
        }catch(e:Exception){
            atom.failWrite(stream)
            throw e
        }
    }
    /** Save a new named snapshot. Never silently overwrite an existing design. */
    fun save(context:Context,name:String,wallUri:String?,
             state:WallCanvasView.State,showArtwork:Boolean):Entry {
        require(name.isNotBlank())
        val id=UUID.randomUUID().toString().replace("-","")
        val entry=Entry(id,name.trim().take(75),System.currentTimeMillis())
        ProjectStore.write(context,wallUri,state,showArtwork,filename(id))
        try{writeIndex(context,list(context)+entry)}
        catch(e:Exception){File(context.filesDir,filename(id)).delete();throw e}
        return entry
    }
    /** Small private JPEG cover; full-size source images remain at source URIs. */
    private fun thumbnail(context:Context,id:String)=File(context.filesDir,"thumb_${id}.jpg")
    fun putThumbnail(context:Context,id:String,image:Bitmap){
        val atom=AtomicFile(thumbnail(context,id))
        val stream=atom.startWrite()
        try{
            if(!image.compress(Bitmap.CompressFormat.JPEG,80,stream))
                throw IllegalStateException("Thumbnail compression failed")
            atom.finishWrite(stream)
        }catch(e:Exception){
            atom.failWrite(stream)
            throw e
        }
    }
    fun getThumbnail(context:Context,id:String):Bitmap?{
        val file=thumbnail(context,id)
        if(!file.exists())return null
        return try{BitmapFactory.decodeFile(file.path)}catch(_:Exception){null}
    }
    fun open(context:Context,id:String):ProjectStore.Draft? =
        ProjectStore.read(context,filename(id))
    fun delete(context:Context,id:String) {
        val before=list(context)
        val after=before.filterNot{it.id==id}
        writeIndex(context,after)
        File(context.filesDir,filename(id)).delete()
        thumbnail(context,id).delete()
    }
    fun rename(context:Context,id:String,name:String) {
        if(name.isBlank())return
        writeIndex(context,list(context).map{
            if(it.id==id)it.copy(name=name.trim().take(75),modified=System.currentTimeMillis())else it
        })
    }
}

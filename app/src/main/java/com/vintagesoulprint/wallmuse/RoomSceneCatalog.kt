package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Bundled, fully OFFLINE Pexels scene backgrounds, with source attribution.
 * The APK embeds a full image + low-res thumbnail for each scene.
 *
 * Never collect or upload a user photo to show a sample scene.
 * Selected scenes are copied into private app storage so existing
 * auto-save and named projects reopen even when the APK is updated.
 */
object RoomSceneCatalog {
    data class Scene(
        val id:String,
        val category:String,
        val titleEn:String,
        val titleTr:String,
        val photographer:String,
        val photoUrl:String,
        val free:Boolean,
        val suggestedX:Float,
        val suggestedY:Float,
        val suggestedWidth:Float
    ) {
        fun title(context:Context)=
            if(context.resources.configuration.locales[0].language=="tr")titleTr else titleEn
    }
    private var saved:List<Scene>?=null
    private var assetRevision=1
    fun scenes(context:Context):List<Scene>{
        saved?.let{return it}
        val text=context.assets.open("room_scenes.json")
            .bufferedReader(Charsets.UTF_8).use{it.readText()}
        val document=JSONObject(text)
        assetRevision=document.optInt("asset_revision",1).coerceIn(1,999)
        val items=document.getJSONArray("scenes")
        val values=(0 until items.length()).map{i->
            val item=items.getJSONObject(i)
            Scene(
                item.getString("id"),
                item.getString("category"),
                item.getString("title_en"),
                item.getString("title_tr"),
                item.getString("photographer"),
                item.getString("photo_url"),
                item.getBoolean("free"),
                item.optJSONArray("anchor")?.optDouble(0,.50)?.toFloat()?.coerceIn(.10f,.90f)?:.50f,
                item.optJSONArray("anchor")?.optDouble(1,.42)?.toFloat()?.coerceIn(.10f,.90f)?:.42f,
                item.optJSONArray("anchor")?.optDouble(2,.25)?.toFloat()?.coerceIn(.10f,.50f)?:.25f
            )
        }
        require(values.size==24)
        require(values.count{it.free}==6)
        require(values.map{it.id}.toSet().size==24)
        saved=values
        return values
    }
    private fun validName(id:String)=id.matches(Regex("room_\\d\\d"))
    fun bitmap(context:Context,id:String):Bitmap{
        require(validName(id))
        return context.assets.open("rooms/$id.jpg").use{
            BitmapFactory.decodeStream(it)
                ?:throw IllegalStateException("Scene image unavailable: $id")
        }
    }
    fun thumbnail(context:Context,id:String):Bitmap?{
        require(validName(id))
        return try{
            context.assets.open("rooms/thumb_$id.jpg").use{
                BitmapFactory.decodeStream(it)
            }
        }catch(_:Exception){null}
    }
    fun persistentUri(context:Context,id:String):Uri{
        require(validName(id))
        // A new asset revision uses a NEW private path: old saved projects
        // retain their original wallpaper instead of silently changing.
        if(saved==null)scenes(context)
        val folder=context.filesDir
        val file=File(folder,"ready_v${assetRevision}_$id.jpg")
        if(!file.exists()||file.length()<8_000){
            val staging=File(folder,"ready_$id.tmp")
            try{
                context.assets.open("rooms/$id.jpg").use{source->
                    FileOutputStream(staging).use {target->
                        source.copyTo(target,16*1024)
                        target.fd.sync()
                    }
                }
                if(!staging.renameTo(file))
                    throw IllegalStateException("Unable to store scene background")
            }catch(e:Exception){
                staging.delete()
                throw e
            }
        }
        return Uri.fromFile(file)
    }
    fun hasLocalAsset(context:Context,id:String):Boolean =try{
        context.assets.open("rooms/$id.jpg").use{it.read()!=-1}
    }catch(_:Exception){false}
}

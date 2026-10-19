package com.vintagesoulprint.wallmuse

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Camera2 preview; art overlay is screen-anchored, not spatial AR tracking. */
class LiveCamera(private val activity:Activity,private val preview:TextureView,
                 private val onError:(String)->Unit){
    private var device:CameraDevice?=null
    private var session:CameraCaptureSession?=null
    private var cameraId:String?=null
    private var previewSize:Size?=null
    private var starting=false
    private var running=false
    private var requested=false
    private val manager=activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun start(){requested=true
        if(preview.isAvailable)open() else preview.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(st:SurfaceTexture,w:Int,h:Int){open()}
            override fun onSurfaceTextureSizeChanged(st:SurfaceTexture,w:Int,h:Int){configureTransform(w,h)}
            override fun onSurfaceTextureDestroyed(st:SurfaceTexture):Boolean{stop();return true}
            override fun onSurfaceTextureUpdated(st:SurfaceTexture){}
        }
    }
    fun updateTransform(){configureTransform(preview.width,preview.height)}

    @SuppressLint("MissingPermission")
    private fun open(){if(!requested||starting||device!=null||!preview.isAvailable)return
        try{
            val chosen=manager.cameraIdList.firstOrNull{
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK
            }?:manager.cameraIdList.firstOrNull()?:return onError("No camera")
            cameraId=chosen;starting=true
            manager.openCamera(chosen,object:CameraDevice.StateCallback(){
                override fun onOpened(camera:CameraDevice){starting=false
                    if(!requested){camera.close();return};device=camera;createPreview()}
                override fun onDisconnected(camera:CameraDevice){starting=false;camera.close();device=null}
                override fun onError(camera:CameraDevice,error:Int){starting=false;camera.close();device=null
                    onError("Camera could not open ($error)")}
            },null)
        }catch(t:Exception){starting=false;onError(t.message?:"Camera unavailable")}
    }

    @Suppress("DEPRECATION")
    private fun createPreview(){val camera=device?:return
        try{
            val st=preview.surfaceTexture?:return
            val size=chooseSize(cameraId!!,preview.width,preview.height)
            previewSize=size
            st.setDefaultBufferSize(size.width,size.height)
            configureTransform(preview.width,preview.height)
            val surface=Surface(st)
            val builder=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            camera.createCaptureSession(listOf(surface),object:CameraCaptureSession.StateCallback(){
                override fun onConfigured(s:CameraCaptureSession){
                    if(device==null){s.close();return};session=s
                    try{s.setRepeatingRequest(builder.build(),null,null);running=true}
                    catch(e:Exception){onError("Preview: ${e.message}")}
                }
                override fun onConfigureFailed(s:CameraCaptureSession){onError("Preview configuration failed")}
            },null)
        }catch(t:Exception){onError(t.message?:"Preview error")}
    }

    private fun chooseSize(id:String,w:Int,h:Int):Size{
        val chars=manager.getCameraCharacteristics(id)
        val map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes=map?.getOutputSizes(SurfaceTexture::class.java)?.toList()?:return Size(1280,720)
        val desired=max(w,h).toFloat()/max(1,min(w,h))
        return sizes.filter{it.width<=1920&&it.height<=1080}.minByOrNull{
            abs(it.width.toFloat()/it.height-desired)*1200+abs(it.width-1280)*.02
        }?:sizes.first()
    }

    @Suppress("DEPRECATION")
    private fun displayDegrees():Int {
        val rotation=if(Build.VERSION.SDK_INT>=30)activity.display?.rotation?:Surface.ROTATION_0
            else (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        return when(rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
    }

    /**
     * TextureView initially non-uniformly maps the raw landscape camera buffer
     * to the view bounds. Cancel that distortion, rotate by the actual sensor
     * metadata relative to screen, and center-cover preserving original ratio.
     * Matrix is in TextureView's VIEW coordinates, not buffer-pixel coordinates.
     */
    private fun configureTransform(w:Int,h:Int){
        val size=previewSize ?:return
        val id=cameraId ?:return
        if(w<=0||h<=0)return
        val chars=manager.getCameraCharacteristics(id)
        val sensor=chars.get(CameraCharacteristics.SENSOR_ORIENTATION)?:90
        val front=chars.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT
        // V2.2.1: The previous correction left the rear camera upside-down
        // on the tested handset. Apply the requested additional 180 degrees
        // automatically, without an on-screen rotation control.
        // This maps the old +90 correction to +270 (modulo 360).
        val degrees=((if(front) sensor+displayDegrees() else sensor-displayDegrees())+270+720)%360
        val rotated=degrees==90||degrees==270
        val sourceWidth=if(rotated)size.height.toFloat() else size.width.toFloat()
        val sourceHeight=if(rotated)size.width.toFloat() else size.height.toFloat()
        val cover=max(w/sourceWidth,h/sourceHeight)
        val scaleX=cover*size.width/w
        val scaleY=cover*size.height/h
        val cx=w/2f;val cy=h/2f
        val values=when(degrees){
            90->floatArrayOf(0f,-scaleY,cx+scaleY*cy,scaleX,0f,cy-scaleX*cx,0f,0f,1f)
            180->floatArrayOf(-scaleX,0f,cx*(1+scaleX),0f,-scaleY,cy*(1+scaleY),0f,0f,1f)
            270->floatArrayOf(0f,scaleY,cx-scaleY*cy,-scaleX,0f,cy+scaleX*cx,0f,0f,1f)
            else->floatArrayOf(scaleX,0f,cx*(1-scaleX),0f,scaleY,cy*(1-scaleY),0f,0f,1f)
        }
        val transform=Matrix();transform.setValues(values)
        preview.setTransform(transform)
    }

    fun stop(){requested=false;running=false
        try{session?.close()}catch(_:Exception){}
        session=null
        try{device?.close()}catch(_:Exception){}
        device=null
        starting=false
    }
    fun isRunning()=running
}

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
            builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
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
        val sizes=map?.getOutputSizes(SurfaceTexture::class.java)?.toList()
            ?.filter{it.width>0&&it.height>0}?:return Size(1920,1080)
        val desired=max(w,h).toFloat()/max(1,min(w,h))
        // Prefer a sharp, landscape 1080p stream. Avoid the device's first,
        // frequently tiny stream which becomes badly blurred in fullscreen.
        val bounded=sizes.filter{
            val long=max(it.width,it.height)
            val short=min(it.width,it.height)
            long<=1920&&short<=1080&&long>=1280
        }.ifEmpty{
            sizes.filter{max(it.width,it.height)<=1920}
        }.ifEmpty{sizes}
        return bounded.minByOrNull{
            val long=max(it.width,it.height)
            val short=min(it.width,it.height)
            val ratio=long.toFloat()/short.coerceAtLeast(1)
            val area=it.width.toLong()*it.height.toLong()
            abs(ratio-desired)*1300f+
                ((2073600L-area).coerceAtLeast(0L)/100000f)
        }?:Size(1920,1080)
    }

    @Suppress("DEPRECATION")
    private fun displayDegrees():Int {
        val rotation=if(Build.VERSION.SDK_INT>=30)activity.display?.rotation?:Surface.ROTATION_0
            else (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        return when(rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
    }

    /**
     * TextureView already applies natural sensor orientation. Only the DISPLAY
     * rotation and a physical-pixel-correct center crop are required here.
     * IMPORTANT: never add SENSOR_ORIENTATION or +270 again; that was the V2.6
     * stretch/zoom bug. Layout changes call this function again without opening
     * or restarting the camera, preserving the active session in Focus View.
     */
    private fun configureTransform(w:Int,h:Int){
        val size=previewSize?:return
        val id=cameraId?:return
        if(w<=0||h<=0)return
        try{
            val chars=manager.getCameraCharacteristics(id)
            val sensor=chars.get(CameraCharacteristics.SENSOR_ORIENTATION)?:90
            val transformData=CameraPreviewMath.create(
                size.width,size.height,w,h,sensor,displayDegrees()
            )
            preview.setTransform(Matrix().apply{
                setValues(transformData.values)
            })
        }catch(e:Exception){onError("Preview geometry: ${e.message}")}
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

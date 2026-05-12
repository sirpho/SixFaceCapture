package com.vrpanorama.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Camera2 API 相机封装工具类
 * 功能：相机预览、拍照、锁定曝光/白平衡/对焦
 * 严格遵循 Camera2 生命周期，防止资源泄漏
 */
class CameraHelper(private val context: Context) {

    // ========== 相机相关对象 ==========
    private var cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null

    // ========== 线程管理 ==========
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // 信号量：防止相机资源竞争
    private val cameraLock = Semaphore(1)

    // ========== 状态标识 ==========
    // 是否已锁定 AE/AWB/AF
    private var isLocked = false

    // 当前使用的相机ID（后置摄像头）
    private var cameraId: String = "0"

    // 当前预览尺寸
    private var previewSize: Size = Size(1920, 1080)

    // 拍照回调
    private var onPhotoTaken: ((File) -> Unit)? = null

    // 曝光补偿（第一张拍完后锁定）
    private var lockedExposure: Long = 0

    // ========== 回调接口 ==========
    interface CameraCallback {
        fun onCameraReady()
        fun onPhotoSaved(file: File)
        fun onError(error: String)
    }

    private var callback: CameraCallback? = null

    /** 初始化并打开相机 */
    fun openCamera(textureView: TextureView, cb: CameraCallback) {
        this.callback = cb
        startBackgroundThread()
        // 等待 TextureView 就绪后打开相机
        if (textureView.isAvailable) {
            setupCamera(textureView)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    setupCamera(textureView)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean =
                    true

                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
            }
        }
    }

    /** 配置相机参数并打开 */
    private fun setupCamera(textureView: TextureView) {
        try {
            // 获取后置摄像头ID
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    // 获取推荐的预览尺寸
                    val map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    map?.let {
                        val sizes = it.getOutputSizes(SurfaceTexture::class.java)
                        if (sizes != null && sizes.isNotEmpty()) {
                            previewSize = sizes[0]
                        }
                    }
                    break
                }
            }

            // 检查权限后打开相机
            if (cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraLock.release()
                        cameraDevice = device
                        createCameraSession(textureView)
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        cameraLock.release()
                        device.close()
                        cameraDevice = null
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        cameraLock.release()
                        device.close()
                        cameraDevice = null
                        callback?.onError("相机打开失败: error=$error")
                    }
                }, backgroundHandler)
            }
        } catch (e: SecurityException) {
            callback?.onError("相机权限未授权")
        } catch (e: Exception) {
            callback?.onError("相机初始化异常 ${e.message}")
        }
    }

    /** 创建相机预览会话 */
    private fun createCameraSession(textureView: TextureView) {
        val device = cameraDevice ?: return
        val surface = Surface(textureView.surfaceTexture)
        previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder?.addTarget(surface)

        // 设置自动对焦、自动曝光、自动白平衡
        previewRequestBuilder?.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        previewRequestBuilder?.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON
        )
        previewRequestBuilder?.set(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ 使用 SessionConfiguration
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(surface)),
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        startPreview()
                        callback?.onCameraReady()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        callback?.onError("相机会话配置失败")
                    }
                })
            device.createCaptureSession(sessionConfig)
        } else {
            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        startPreview()
                        callback?.onCameraReady()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        callback?.onError("相机会话配置失败")
                    }
                },
                backgroundHandler
            )
        }
    }

    /** 启动预览 */
    private fun startPreview() {
        val session = cameraCaptureSession ?: return
        val requestBuilder = previewRequestBuilder ?: return
        // 持续重复预览请求
        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
    }

    /** 拍照：第一张后锁定曝光，后续使用相同参数 */
    fun takePhoto(onPhotoSaved: (File) -> Unit) {
        val device = cameraDevice ?: run {
            callback?.onError("相机未就绪")
            return
        }
        this.onPhotoTaken = onPhotoSaved

        // 配置 ImageReader 用于接收照片数据
        imageReader = ImageReader.newInstance(
            previewSize.width, previewSize.height,
            ImageFormat.JPEG, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let { saveImageToFile(it) }
        }, backgroundHandler)

        // 构建拍照请求
        captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder?.addTarget(imageReader!!.surface)

        // 如果已锁定，使用锁定参数
        if (isLocked) {
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_LOCK, true)
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            captureRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_IDLE
            )
        }

        val session = cameraCaptureSession ?: return
        captureRequestBuilder?.build()?.let {
            session.capture(
                it,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        // 第一次拍照后锁定 AE/AWB
                        if (!isLocked) {
                            lockExposure()
                            isLocked = true
                        }
                    }
                },
                backgroundHandler
            )
        }
    }

    /** 锁定曝光和白平衡（第一张照片拍完后调用） */
    private fun lockExposure() {
        val session = cameraCaptureSession ?: return
        val builder = previewRequestBuilder ?: return
        builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        // 保存当前曝光时间用于后续拍照
        lockedExposure = System.currentTimeMillis()
        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
    }

    /** 保存 JPEG 图片到文件 */
    private fun saveImageToFile(image: Image) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // 创建临时文件名（按拍摄时间命名）
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "PANORAMA_$timestamp.jpg")

            FileOutputStream(file).use { it.write(bytes) }
            image.close()
            imageReader?.close()

            onPhotoTaken?.invoke(file)
            callback?.onPhotoSaved(file)
        } catch (e: Exception) {
            image.close()
            callback?.onError("保存照片失败: ${e.message}")
        }
    }

    /** 启动后台线程 */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /** 停止后台线程 */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            // 忽略中断
        }
        backgroundThread = null
        backgroundHandler = null
    }

    /** 释放相机资源（Activity onPause/onDestroy 时必须调用） */
    fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
            isLocked = false
        } catch (e: Exception) {
            // 关闭时忽略异常
        }
    }
}
package com.vrpanorama.app.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.stitching.Stitcher
import java.io.File
import java.io.FileOutputStream

/**
 * OpenCV 全景拼接工具类
 * 功能：将每个方向的多张照片拼接为单张方向图，再合成最终 2:1 等距柱状全景图
 * 依赖：需要将 OpenCV Android SDK 放置在 app/libs/ 目录下
 */
object PanoramaStitcher {

    /**
     * 拼接单个方向的多张照片
     * @param imagePaths 同一方向的照片路径列表（一般 2~3 张）
     * @param onProgress 进度回调
     * @return 拼接完成后的 Bitmap，失败返回 null
     */
    suspend fun stitchImages(
        imagePaths: List<String>,
        onProgress: (Int) -> Unit
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            if (imagePaths.isEmpty()) return@withContext null
            // 只有一张照片时直接返回
            if (imagePaths.size == 1) {
                onProgress(100)
                return@withContext BitmapFactory.decodeFile(imagePaths[0])
            }

            onProgress(10)

            // 加载所有图片并转为 OpenCV Mat
            val mats = mutableListOf<Mat>()
            for (path in imagePaths) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)
                    mats.add(mat)
                    bitmap.recycle()
                }
            }

            if (mats.size < 2) {
                onProgress(100)
                return@withContext BitmapFactory.decodeFile(imagePaths[0])
            }

            onProgress(30)

            // 创建 OpenCV 全景拼接器
            val stitcher = Stitcher.create(Stitcher.PANORAMA)
            val resultMat = Mat()
            onProgress(40)

            // 执行拼接
            val status = stitcher.stitch(mats, resultMat)
            onProgress(80)

            // 释放资源
            mats.forEach { it.release() }

            if (status != Stitcher.OK) {
                resultMat.release()
                val errorMsg = when (status) {
                    Stitcher.ERR_NEED_MORE_IMGS -> "纹理不足，需要更多特征点"
                    Stitcher.ERR_HOMOGRAPHY_EST_FAIL -> "图像匹配失败，请调整角度重拍"
                    Stitcher.ERR_CAMERA_PARAMS_ADJUST_FAIL -> "相机参数调整失败"
                    else -> "拼接失败 (错误码：$status)"
                }
                throw StitchException(errorMsg)
            }

            // Mat 转 Bitmap
            val resultBitmap =
                Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)
            resultMat.release()

            onProgress(100)
            resultBitmap
        } catch (e: StitchException) {
            throw e
        } catch (e: Exception) {
            throw StitchException("拼接失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 将 6 个方向的拼接结果合成为 2:1 等距柱状全景图
     * 简化版：前/右/后/左 水平排列，上/下 分别放置顶部区域
     * 正式项目可替换为专业立方体转等距柱状投影算法
     */
    suspend fun composeEquirectangular(
        directionBitmaps: Map<String, Bitmap>,
        outputWidth: Int = 4096
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            // 必须包含 6 个方向
            if (directionBitmaps.size < 6) return@withContext null

            val order = listOf("front", "right", "back", "left", "up", "down")
            val bitmaps = order.mapNotNull { directionBitmaps[it] }
            if (bitmaps.size < 6) return@withContext null

            // 等距柱状全景比例 2:1
            val height = outputWidth / 2
            val result = Bitmap.createBitmap(outputWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)

            val sectionWidth = outputWidth / 4
            val halfHeight = height / 2

            val front = directionBitmaps["front"]
            val right = directionBitmaps["right"]
            val back = directionBitmaps["back"]
            val left = directionBitmaps["left"]
            val up = directionBitmaps["up"]
            val down = directionBitmaps["down"]

            // 绘制前、右、后、左四个方向（水平 360°）
            front?.let {
                val scaled = Bitmap.createScaledBitmap(it, sectionWidth, halfHeight, true)
                canvas.drawBitmap(scaled, 0f, halfHeight / 2f, null)
            }
            right?.let {
                val scaled = Bitmap.createScaledBitmap(it, sectionWidth, halfHeight, true)
                canvas.drawBitmap(scaled, sectionWidth.toFloat(), halfHeight / 2f, null)
            }
            back?.let {
                val scaled = Bitmap.createScaledBitmap(it, sectionWidth, halfHeight, true)
                canvas.drawBitmap(scaled, (sectionWidth * 2).toFloat(), halfHeight / 2f, null)
            }
            left?.let {
                val scaled = Bitmap.createScaledBitmap(it, sectionWidth, halfHeight, true)
                canvas.drawBitmap(scaled, (sectionWidth * 3).toFloat(), halfHeight / 2f, null)
            }

            // 绘制上、下方向
            up?.let {
                val scaled = Bitmap.createScaledBitmap(it, outputWidth / 2, halfHeight, true)
                canvas.drawBitmap(scaled, 0f, 0f, null)
            }
            down?.let {
                val scaled = Bitmap.createScaledBitmap(it, outputWidth / 2, halfHeight, true)
                canvas.drawBitmap(scaled, outputWidth / 2f, 0f, null)
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存最终全景图为 JPEG 文件
     */
    suspend fun savePanorama(
        bitmap: Bitmap,
        outputFile: File,
        quality: Int = 95
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 拼接专用异常类
     */
    class StitchException(message: String) : Exception(message)
}
package com.vrpanorama.app.stitching

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.stitching.Stitcher
import java.io.File
import java.io.FileOutputStream

/**
 * OpenCV 全景拼接工具�?
 * 将多张同方向照片拼接为一组全景图，再合成最�?2:1 等距柱状全景
 * �? OpenCV Android SDK 需放置�?app/libs/ 目录�?
 */
object PanoramaStitcher {

 /**
 * 拼接单方向的多张照片
 * @param imagePaths 照片文件路径列表：~3张）
 * @return 拼接后的 Bitmap，失败返�?null
 */
 suspend fun stitchImages(
 imagePaths: List<String>,
 onProgress: (Int) -> Unit
 ): Bitmap? = withContext(Dispatchers.Default) {
 try {
 if (imagePaths.isEmpty()) return@withContext null
 // 只有1张照片时直接返回
 if (imagePaths.size == 1) {
 onProgress(100)
 return@withContext BitmapFactory.decodeFile(imagePaths[0])
 }

 onProgress(10)

 // 加载所有图像为 OpenCV Mat
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

 // 使用 OpenCV Stitcher 进行拼接
 val stitcher = Stitcher.create(Stitcher.PANORAMA)
 val resultMat = Mat()
 onProgress(40)

 // 执行拼接，返回状态码
 val status = stitcher.stitch(mats, resultMat)
 onProgress(80)

 // 释放原始 Mat
 mats.forEach { it.release() }

 if (status != Stitcher.OK) {
 resultMat.release()
 val errorMsg = when (status) {
 Stitcher.ERR_NEED_MORE_IMGS -> "纹理不足，需要更多特征点"
 Stitcher.ERR_HOMOGRAPHY_EST_FAIL -> "图像匹配失败，请调整角度重拍"
 Stitcher.ERR_CAMERA_PARAMS_ADJUST_FAIL -> "相机参数调整失败"
 else -> "拼接失败 (错误 $status)"
 }
 throw StitchException(errorMsg)
 }

 // 转换 Mat �?Bitmap
 val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
 Utils.matToBitmap(resultMat, resultBitmap)
 resultMat.release()

 onProgress(100)
 resultBitmap
 } catch (e: StitchException) {
 throw e
 } catch (e: Exception) {
 throw StitchException("拼接失败: ${e.message ?: "未知错误"}")
 }
 }

 /**
 * �?个方向的拼接结果合成一�?2:1 等距柱状全景�?
 * 简化版: 水平排列6张图（前、右、后、左、上、下：
 * 实际项目中可替换为更精确的投影变换算�?
 */
 suspend fun composeEquirectangular(
 directionBitmaps: Map<String, Bitmap>,
 outputWidth: Int = 4096
 ): Bitmap? = withContext(Dispatchers.Default) {
 try {
 // 预期: 6 张拼接后的方向图
 if (directionBitmaps.size < 6) return@withContext null

 val order = listOf("front", "right", "back", "left", "up", "down")
 val bitmaps = order.mapNotNull { directionBitmaps[it] }
 if (bitmaps.size < 6) return@withContext null

 // 等距柱状全景: 宽高�?2:1
 val height = outputWidth / 2
 val result = Bitmap.createBitmap(outputWidth, height, Bitmap.Config.ARGB_8888)

 // 简单拼�? �?张方向图平铺到全景图�?
 // �?张（前、右、后、左）水平排列，覆盖360�?
 // 上、下各占一�?
 val canvas = android.graphics.Canvas(result)
 val sectionWidth = outputWidth / 4

 val front = directionBitmaps["front"]
 val right = directionBitmaps["right"]
 val back = directionBitmaps["back"]
 val left = directionBitmaps["left"]
 val up = directionBitmaps["up"]
 val down = directionBitmaps["down"]

 // 缩放各方向图到合适尺寸并绘制
 front?.let {
 val scaled = Bitmap.createScaledBitmap(it, sectionWidth, height / 2, true)
 canvas.drawBitmap(scaled, 0f, height / 4f, null)
 }
 right?.let {
 val scaled = Bitmap.createScaledBitmap(it, sectionWidth, height / 2, true)
 canvas.drawBitmap(scaled, sectionWidth.toFloat(), height / 4f, null)
 }
 back?.let {
 val scaled = Bitmap.createScaledBitmap(it, sectionWidth, height / 2, true)
 canvas.drawBitmap(scaled, (sectionWidth * 2).toFloat(), height / 4f, null)
 }
 left?.let {
 val scaled = Bitmap.createScaledBitmap(it, sectionWidth, height / 2, true)
 canvas.drawBitmap(scaled, (sectionWidth * 3).toFloat(), height / 4f, null)
 }
 up?.let {
 val scaled = Bitmap.createScaledBitmap(it, outputWidth / 2, height / 2, true)
 canvas.drawBitmap(scaled, 0f, 0f, null)
 }
 down?.let {
 val scaled = Bitmap.createScaledBitmap(it, outputWidth / 2, height / 2, true)
 canvas.drawBitmap(scaled, outputWidth / 2f, 0f, null)
 }

 result
 } catch (e: Exception) {
 null
 }
 }

 /** 保存全景图为 JPEG 文件 */
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

 /** 拼接异常�?*/
 class StitchException(message: String) : Exception(message)
}

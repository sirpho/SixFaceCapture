package com.vrpanorama.app.cubemap

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 等距柱状全景图转立方体贴图工具类
 * �?2:1 等距柱状全景图转换为6张标准正方形贴图（立方体6个面：
 *
 * 算法原理:
 * 1. 对于立方体每个面的每个像素，计算�?D方向向量
 * 2. 将方向向量转换为球面坐标（theta, phi：
 * 3. 从等距柱状全景图中采样对应像�?
 * 4. 写入立方体贴�?
 */
object CubemapConverter {

 // 6个面的名称和输出文件�?
 private val FACES = listOf(
 CubeFace("front", "pano_f.jpg"),
 CubeFace("right", "pano_r.jpg"),
 CubeFace("back", "pano_b.jpg"),
 CubeFace("left", "pano_l.jpg"),
 CubeFace("up", "pano_u.jpg"),
 CubeFace("down", "pano_d.jpg")
 )

 data class CubeFace(val name: String, val fileName: String)

 /**
 * 执行转换
 * @param equirectBitmap 输入�?2:1 等距柱状全景�?
 * @param faceSize 输出每个面的尺寸（默�?024，可�?048：
 * @param onProgress 进度回调 (0~100)
 * @return Map<面名�? Bitmap> 6张立方体贴图
 */
 suspend fun convert(
 equirectBitmap: Bitmap,
 faceSize: Int = 1024,
 onProgress: (Int) -> Unit
 ): Map<String, Bitmap> = withContext(Dispatchers.Default) {
 val result = mutableMapOf<String, Bitmap>()

 FACES.forEachIndexed { index, face ->
 val faceBitmap = generateFace(equirectBitmap, face.name, faceSize)
 result[face.name] = faceBitmap
 // 更新进度 (每完成一个面�?16.7%)
 onProgress(((index + 1) * 100) / 6)
 }

 result
 }

 /**
 * 生成单个面的贴图
 * 核心算法: 对立方体面的每个像素，计算其在等距柱状全景图中的对应位置
 */
 private fun generateFace(
 source: Bitmap,
 face: String,
 size: Int
 ): Bitmap {
 val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
 val srcWidth = source.width
 val srcHeight = source.height

 // 预计算步长（每个像素对应的UV增量：
 val step = 2.0f / size // 立方体面坐标范围 [-1, 1]

 for (y in 0 until size) {
 for (x in 0 until size) {
 // 步骤1: 计算像素在立方体面上的归一化坐�?[-1, 1]
 val u = -1.0f + step * (x + 0.5f)
 val v = -1.0f + step * (y + 0.5f)

 // 步骤2: 根据面方向，计算3D方向向量 (dx, dy, dz)
 val (dx, dy, dz) = getDirectionVector(face, u, v)

 // 步骤3: �?D方向向量转换为球面坐�?(theta, phi)
 // theta: 水平览[0, 2*PI] (对应全景图的 x �?
 // phi: 垂直览[0, PI] (对应全景图的 y �?
 val theta = atan2(dz, dx) // 范围 [-PI, PI]
 val phi = acos(dy) // 范围 [0, PI]

 // 步骤4: 映射到等距柱状全景图的像素坐�?
 val srcX = ((theta / (2.0 * PI) + 0.5) * srcWidth).toInt()
 val srcY = ((phi / PI) * srcHeight).toInt()

 // 边界裁剪
 val px = srcX.coerceIn(0, srcWidth - 1)
 val py = srcY.coerceIn(0, srcHeight - 1)

 // 步骤5: 采样颜色
 val color = source.getPixel(px, py)
 result.setPixel(x, y, color)
 }
 }

 return result
 }

 private fun getDirectionVector(face: String, u: Float, v: Float): Triple<Float, Float, Float> {
 return when (face) {
 "front" -> {
 Triple(u, v, 1.0f)
 }
 "right" -> {
 Triple(1.0f, v, -u)
 }
 "back" -> {
 Triple(-u, v, -1.0f)
 }
 "left" -> {
 Triple(-1.0f, v, u)
 }
 "up" -> {
 Triple(u, 1.0f, -v)
 }
 "down" -> {
 Triple(u, -1.0f, v)
 }
 else -> Triple(0f, 0f, 1f)
 }.normalize()
 }

 private fun Triple<Float, Float, Float>.normalize(): Triple<Float, Float, Float> {
 val length = sqrt(first * first + second * second + third * third)
 if (length < 0.0001f) return this
 return Triple(first / length, second / length, third / length)
 }
}

package com.vrpanorama.app.cubemap

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 等距柱状全景图转立方体贴图工具类
 * 功能：将 2:1 等距柱状全景图 转换为 6 张标准正方形贴图（立方体 6 个面）
 *
 * 算法原理：
 * 1. 对立方体每个面的每个像素，计算对应的 3D 方向向量
 * 2. 将 3D 方向向量转换为球面坐标（theta, phi）
 * 3. 从等距柱状全景图中采样对应像素
 * 4. 将颜色写入立方体贴图的对应位置
 */
object CubemapConverter {

    // 6 个面的名称与输出文件名
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
     * 执行全景图转立方体转换
     * @param equirectBitmap 输入：2:1 等距柱状全景图
     * @param faceSize 输出每个面的尺寸（默认 1024，可设置 2048）
     * @param onProgress 进度回调 (0~100)
     * @return Map<面名称, Bitmap> 6 张立方体贴图
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
            // 更新进度（每完成一个面增加约 16.7%）
            onProgress(((index + 1) * 100) / 6)
        }

        result
    }

    /**
     * 生成单个立方体面的贴图
     * 核心算法：对立方体面的每个像素，计算其在等距柱状全景图中的对应位置
     */
    private fun generateFace(
        source: Bitmap,
        face: String,
        size: Int
    ): Bitmap {
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val srcWidth = source.width
        val srcHeight = source.height

        // 预计算步长（每个像素对应的 UV 增量，立方体面坐标范围 [-1, 1]）
        val step = 2.0f / size

        for (y in 0 until size) {
            for (x in 0 until size) {
                // 步骤 1：计算像素在立方体面上的归一化坐标 [-1, 1]
                val u = -1.0f + step * (x + 0.5f)
                val v = -1.0f + step * (y + 0.5f)

                // 步骤 2：根据面方向，计算 3D 方向向量 (dx, dy, dz)
                val (dx, dy, dz) = getDirectionVector(face, u, v)

                // 步骤 3：将 3D 方向向量转换为球面坐标 (theta, phi)
                // theta：水平视角 [0, 2*PI]（对应全景图 X 轴）
                // phi：垂直视角 [0, PI]（对应全景图 Y 轴）
                val theta = atan2(dz, dx) // 范围 [-PI, PI]
                val phi = acos(dy)       // 范围 [0, PI]

                // 步骤 4：映射到等距柱状全景图的像素坐标
                val srcX = ((theta / (2.0 * PI) + 0.5) * srcWidth).toInt()
                val srcY = ((phi / PI) * srcHeight).toInt()

                // 边界安全裁剪
                val px = srcX.coerceIn(0, srcWidth - 1)
                val py = srcY.coerceIn(0, srcHeight - 1)

                // 步骤 5：采样颜色并设置到目标贴图
                val color = source.getPixel(px, py)
                result.setPixel(x, y, color)
            }
        }

        return result
    }

    /**
     * 根据立方体面方向，获取 3D 方向向量
     */
    private fun getDirectionVector(face: String, u: Float, v: Float): Triple<Float, Float, Float> {
        return when (face) {
            "front" -> Triple(u, v, 1.0f)
            "right" -> Triple(1.0f, v, -u)
            "back" -> Triple(-u, v, -1.0f)
            "left" -> Triple(-1.0f, v, u)
            "up" -> Triple(u, 1.0f, -v)
            "down" -> Triple(u, -1.0f, v)
            else -> Triple(0f, 0f, 1f)
        }.normalize()
    }

    /**
     * 对 3D 向量进行单位化（归一化）
     */
    private fun Triple<Float, Float, Float>.normalize(): Triple<Float, Float, Float> {
        val length = sqrt(first * first + second * second + third * third)
        if (length < 0.0001f) return this
        return Triple(first / length, second / length, third / length)
    }
}
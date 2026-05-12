package com.vrpanorama.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vrpanorama.app.stitching.PanoramaStitcher
import com.vrpanorama.app.cubemap.CubemapConverter
import com.vrpanorama.app.R
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 拼接处理页
 * 功能: 将6个方向的照片拼接为全景图，再转换为6张立方体贴图
 */
class StitchActivity : AppCompatActivity() {

    private lateinit var tvStitchStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvStitchDetail: TextView

    // 6个方向的照片路径 Map<方向名, List<文件路径>>
    private lateinit var photoPaths: HashMap<String, ArrayList<String>>

    // 6个方向的拼接后 Bitmap
    private val stitchedBitmaps = mutableMapOf<String, Bitmap>()

    // 6个方向的立方体贴图 Bitmap
    private val cubemapBitmaps = mutableMapOf<String, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stitch)

        tvStitchStatus = findViewById(R.id.tvStitchStatus)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvStitchDetail = findViewById(R.id.tvStitchDetail)

        // 获取传递的照片路径
        photoPaths =
            intent.getSerializableExtra("photo_paths") as? HashMap<String, ArrayList<String>>
                ?: hashMapOf()

        if (photoPaths.isEmpty()) {
            Toast.makeText(this, "没有照片数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 开始拼接流程
        startStitching()
    }

    /** 执行拼接流程 */
    private fun startStitching() {
        lifecycleScope.launch {
            try {
                tvStitchStatus.text = "正在拼接各方向照片..."
                progressBar.max = 100

                // 第一步: 拼接每个方向的照片
                stitchAllDirections()

                // 第二步: 将6个方向拼接结果合成为等距柱状全景图
                tvStitchStatus.text = "正在生成全景图..."
                updateProgress(50, "合成全景图")
                val equirectBitmap = PanoramaStitcher.composeEquirectangular(
                    stitchedBitmaps
                )

                if (equirectBitmap == null) {
                    showError("全景图合成失败")
                    return@launch
                }

                // 保存全景图到缓存
                val panoFile = File(cacheDir, "PANORAMA_EQUIRECT.jpg")
                PanoramaStitcher.savePanorama(equirectBitmap, panoFile)

                updateProgress(60, "开始立方体贴图转换")

                // 第三步: 转换为6张立方体贴图
                tvStitchStatus.text = "正在生成立方体贴图..."
                val cubemaps = CubemapConverter.convert(
                    equirectBitmap,
                    faceSize = 1024
                ) { faceProgress ->
                    // 每个面完成时更新进度（60~100区间）
                    val overallProgress = 60 + (faceProgress * 40 / 100)
                    updateProgress(overallProgress, "立方体贴图: $faceProgress%")
                }

                cubemapBitmaps.putAll(cubemaps)

                // 保存6张立方体贴图到缓存
                saveCubemapsToCache()

                updateProgress(100, "处理完成！")

                // 跳转到导出页面
                navigateToExport()

            } catch (e: PanoramaStitcher.StitchException) {
                showError(e.message ?: "拼接失败")
            } catch (e: Exception) {
                showError("处理异常: ${e.message}")
            }
        }
    }

    /** 拼接所有6个方向的照片 */
    private suspend fun stitchAllDirections() {
        val directionOrder = listOf("front", "right", "back", "left", "up", "down")
        var completedCount = 0

        for (dir in directionOrder) {
            val paths = photoPaths[dir]
            if (paths.isNullOrEmpty()) {
                showError("缺少${dir}方向的照片")
                continue
            }

            updateProgress(
                (completedCount * 100) / 6,
                "拼接 ${dir} 方向 (${completedCount + 1}/6)"
            )

            try {
                val bitmap = PanoramaStitcher.stitchImages(paths) { innerProgress ->
                    // 单个方向拼接进度
                }

                if (bitmap != null) {
                    stitchedBitmaps[dir] = bitmap
                } else {
                    // 降级: 使用第一张照片
                    val fallback = BitmapFactory.decodeFile(paths[0])
                    if (fallback != null) {
                        stitchedBitmaps[dir] = fallback
                    }
                }
            } catch (e: Exception) {
                // 降级策略
                val fallback = BitmapFactory.decodeFile(paths[0])
                if (fallback != null) {
                    stitchedBitmaps[dir] = fallback
                }
            }

            completedCount++
        }

        updateProgress(40, "方向拼接完成")
    }

    /** 保存6张立方体贴图到缓存目录 */
    private fun saveCubemapsToCache() {
        val names = listOf(
            "front" to "pano_f.jpg",
            "right" to "pano_r.jpg",
            "back" to "pano_b.jpg",
            "left" to "pano_l.jpg",
            "up" to "pano_u.jpg",
            "down" to "pano_d.jpg"
        )
        for ((name, fileName) in names) {
            val bitmap = cubemapBitmaps[name] ?: continue
            val file = File(cacheDir, "CUBEMAP_$fileName")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
        }
    }

    /** 更新进度条和文字 */
    private fun updateProgress(percent: Int, detail: String) {
        runOnUiThread {
            progressBar.progress = percent
            tvProgressPercent.text = "${percent}%"
            tvStitchDetail.text = detail
        }
    }

    /** 显示错误并关闭 */
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            tvStitchStatus.text = message
        }
    }

    /** 跳转到导出页面 */
    private fun navigateToExport() {
        val intent = Intent(this, ExportActivity::class.java)
        startActivity(intent)
        finish()
    }
}

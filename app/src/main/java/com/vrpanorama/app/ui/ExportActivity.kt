package com.vrpanorama.app.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vrpanorama.app.storage.StorageHelper
import com.vrpanorama.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全景图导出页面
 * 功能：展示 6 张立方体贴图网格预览，一键批量导出到系统相册
 */
class ExportActivity : AppCompatActivity() {

    private lateinit var gridCubemap: GridLayout
    private lateinit var btnExport: Button
    private lateinit var tvExportResult: TextView

    // 立方体贴图文件列表
    private val cubemapFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)

        gridCubemap = findViewById(R.id.gridCubemap)
        btnExport = findViewById(R.id.btnExport)
        tvExportResult = findViewById(R.id.tvExportResult)

        // 从缓存加载立方体贴图
        loadCubemapFiles()

        // 导出按钮点击事件
        btnExport.setOnClickListener {
            lifecycleScope.launch {
                exportToGallery()
            }
        }
    }

    /**
     * 加载缓存中的立方体贴图文件
     * 并动态添加到网格布局中预览
     */
    private fun loadCubemapFiles() {
        val names = listOf(
            "pano_f.jpg",
            "pano_r.jpg",
            "pano_b.jpg",
            "pano_l.jpg",
            "pano_u.jpg",
            "pano_d.jpg"
        )

        for (name in names) {
            val file = File(cacheDir, "CUBEMAP_$name")
            if (file.exists()) {
                cubemapFiles.add(file)

                // 在 GridLayout 中添加图片预览
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val iv = ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(4, 4, 4, 4)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }
                gridCubemap.addView(iv)

                // 添加方向名称标签
                val label = TextView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                    text = name.removeSuffix(".jpg").uppercase()
                    gravity = Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 12f
                }
                gridCubemap.addView(label)
            }
        }

        // 无图片时提示
        if (cubemapFiles.isEmpty()) {
            tvExportResult.text = "未找到立方体贴图文件，请重新生成"
            btnExport.isEnabled = false
        }
    }

    /**
     * 批量导出 6 张立方体贴图到系统相册
     * 自动清理缓存
     */
    private suspend fun exportToGallery() = withContext(Dispatchers.IO) {
        try {
            var successCount = 0
            val totalCount = cubemapFiles.size

            for ((index, file) in cubemapFiles.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val displayName = file.nameWithoutExtension

                val uri = StorageHelper.saveToGallery(
                    this@ExportActivity,
                    bitmap,
                    displayName
                )

                if (uri != null) {
                    successCount++
                }
                bitmap.recycle()
            }

            // 导出完成后清理临时缓存
            StorageHelper.clearCache(this@ExportActivity)

            // 切换到 UI 线程更新界面
            runOnUiThread {
                if (successCount == totalCount) {
                    tvExportResult.text = "导出成功！6 张图片已保存到相册"
                    Toast.makeText(this@ExportActivity, "导出成功！", Toast.LENGTH_SHORT).show()
                } else {
                    tvExportResult.text = "部分失败：$successCount/$totalCount 张保存成功"
                }
            }

        } catch (e: Exception) {
            runOnUiThread {
                tvExportResult.text = "导出失败：${e.message}"
                Toast.makeText(this@ExportActivity, "导出失败，请重试", Toast.LENGTH_LONG).show()
            }
        }
    }
}
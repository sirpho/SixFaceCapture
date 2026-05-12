package com.vrpanorama.app.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vrpanorama.app.storage.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 导出页面
 * 功能: 展示6张立方体贴图网格预览，一键导出到系统相册
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

 // 导出按钮
 btnExport.setOnClickListener {
 lifecycleScope.launch {
 exportToGallery()
 }
 }
 }

 /** 加载缓存中的立方体贴图文件 */
 private fun loadCubemapFiles() {
 val names = listOf("pano_f.jpg", "pano_r.jpg", "pano_b.jpg",
 "pano_l.jpg", "pano_u.jpg", "pano_d.jpg")
 for (name in names) {
 val file = File(cacheDir, "CUBEMAP_$name")
 if (file.exists()) {
 cubemapFiles.add(file)

 // 在 GridLayout 中添加预览
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

 // 添加文件名标签
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

 if (cubemapFiles.isEmpty()) {
 tvExportResult.text = "未找到立方体贴图文件，请重新生成"
 btnExport.isEnabled = false
 }
 }

 /** 导出6张立方体贴图到系统相册 */
 private suspend fun exportToGallery() = withContext(Dispatchers.IO) {
 try {
 var successCount = 0
 val totalCount = cubemapFiles.size

 for ((index, file) in cubemapFiles.withIndex()) {
 val bitmap = BitmapFactory.decodeFile(file.absolutePath)
 if (bitmap == null) continue

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

 // 清除临时缓存
 StorageHelper.clearCache(this@ExportActivity)

 runOnUiThread {
 if (successCount == totalCount) {
 tvExportResult.text = "导出成功！6张图片已保存到相册"
 Toast.makeText(this@ExportActivity, "导出成功！", Toast.LENGTH_SHORT).show()
 } else {
 tvExportResult.text = "部分失败: $successCount/$totalCount 张保存成功"
 }
 }

 } catch (e: Exception) {
 runOnUiThread {
 tvExportResult.text = "导出失败: ${e.message}"
 Toast.makeText(this@ExportActivity, "导出失败，请重试", Toast.LENGTH_LONG).show()
 }
 }
 }
}

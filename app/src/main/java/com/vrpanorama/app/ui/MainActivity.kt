package com.vrpanorama.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vrpanorama.app.camera.CameraHelper
import com.vrpanorama.app.permission.PermissionHelper
import com.vrpanorama.app.sensor.GyroHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主拍摄页�?
 * 功能: 全屏相机预览、十字准星引导�?方向顺序拍摄、陀螺仪角度检�?
 */
class MainActivity : AppCompatActivity() {

 // ========== UI 组件 ==========
 private lateinit var cameraPreview: TextureView
 private lateinit var crosshairView: CrosshairView
 private lateinit var tvDirectionHint: TextView
 private lateinit var directionBar: LinearLayout
 private lateinit var btnShoot: Button
 private lateinit var btnStitch: Button

 // ========== 工具类实�?==========
 private val cameraHelper = CameraHelper(this)
 private lateinit var gyroHelper: GyroHelper

 // ========== 拍摄状�?==========
 // 当前拍摄方向 (0~5)
 private var currentDirectionIndex = 0
 // 6个方向已拍照片路�? Map<方向�? List<文件路径>>
 private val capturedPhotos = mutableMapOf<String, MutableList<String>>()
 // 方向缩略�?ImageView 列表
 private val directionThumbnails = mutableListOf<ImageView>()

 // ========== 曝光锁定相关 ==========
 private var isExposureLocked = false
 private val SIX_DIRECTIONS = listOf("front", "right", "back", "left", "up", "down")
    private val DIRECTION_LABELS = listOf("前", "右", "后", "左", "上", "下")

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 setContentView(R.layout.activity_main)

 // 初始�?UI 组件
 cameraPreview = findViewById(R.id.cameraPreview)
 crosshairView = findViewById(R.id.crosshairView)
 tvDirectionHint = findViewById(R.id.tvDirectionHint)
 directionBar = findViewById(R.id.directionBar)
 btnShoot = findViewById(R.id.btnShoot)
 btnStitch = findViewById(R.id.btnStitch)

 // 初始化陀螺仪
 gyroHelper = GyroHelper(this)

 // 初始�?个方向缩略图
 initDirectionBar()

 // 检查权�?
 checkPermissionsAndStart()

 // 快门按钮点击
 btnShoot.setOnClickListener {
 if (PermissionHelper.hasCameraPermission(this)) {
 takeCurrentPhoto()
 } else {
 PermissionHelper.requestCamera(this)
 }
 }

 // 拼接按钮点击
 btnStitch.setOnClickListener {
 lifecycleScope.launch {
 navigateToStitch()
 }
 }

 // 按钮长按: 重拍当前方向
 btnShoot.setOnLongClickListener {
 reshootCurrentDirection()
 true
 }
 }

 /** 初始化顶�?方向缩略�?*/
 private fun initDirectionBar() {
 directionThumbnails.clear()
 directionBar.removeAllViews()
 for (i in 0..5) {
 val iv = ImageView(this).apply {
 layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
 scaleType = ImageView.ScaleType.CENTER_CROP
 setPadding(4, 4, 4, 4)
 // 未拍: 灰色背景
 setBackgroundColor(Color.GRAY)
 }
 directionBar.addView(iv)
 directionThumbnails.add(iv)
 }
 }

 /** 检查并申请权限 */
 private fun checkPermissionsAndStart() {
 if (PermissionHelper.hasCameraPermission(this) && PermissionHelper.hasStoragePermission(this)) {
 startCameraAndGyro()
 } else {
 PermissionHelper.requestAllPermissions(this)
 }
 }

 /** 权限结果回调 */
 override fun onRequestPermissionsResult(
 requestCode: Int,
 permissions: Array<out String>,
 grantResults: IntArray
 ) {
 super.onRequestPermissionsResult(requestCode, permissions, grantResults)
 if (PermissionHelper.allGranted(grantResults)) {
 startCameraAndGyro()
 } else {
 Toast.makeText(this, "权限被拒绝，无法使用相机", Toast.LENGTH_LONG).show()
 finish()
 }
 }

 /** 启动相机和陀螺仪 */
 private fun startCameraAndGyro() {
 // 启动相机
 cameraHelper.openCamera(cameraPreview, object : CameraHelper.CameraCallback {
 override fun onCameraReady() {
 runOnUiThread {
 Toast.makeText(this@MainActivity, "相机就绪", Toast.LENGTH_SHORT).show()
 }
 }
 override fun onPhotoSaved(file: File) {
 // 照片保存回调
 }
 override fun onError(error: String) {
 runOnUiThread {
 Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
 }
 }
 })

 // 启动陀螺仪
 gyroHelper.start(object : GyroHelper.OnDirectionChangedListener {
 override fun onDirectionChanged(direction: GyroHelper.SixDirection, isAligned: Boolean) {
 runOnUiThread {
 updateGuidance(direction, isAligned)
 }
 }
 })

 // 初始显示第一方向
 updateDirectionHint(0)
 }

 /** 更新准星和方向提�?*/
 private fun updateGuidance(direction: GyroHelper.SixDirection, isAligned: Boolean) {
 crosshairView.isAligned = isAligned
 crosshairView.directionText = direction.label

 // 判断是否对准了当前需要拍摄的方向
 if (isAligned && direction.order == currentDirectionIndex) {
 tvDirectionHint.text = "${DIRECTION_LABELS[currentDirectionIndex]} - 已对准！可以拍摄"
 tvDirectionHint.setBackgroundColor(Color.argb(100, 0, 255, 0))
 btnShoot.isEnabled = true
 } else if (direction.order == currentDirectionIndex) {
 tvDirectionHint.text = "${DIRECTION_LABELS[currentDirectionIndex]} - 请微调角度"
 tvDirectionHint.setBackgroundColor(Color.argb(100, 255, 0, 0))
 btnShoot.isEnabled = false
 } else {
 tvDirectionHint.text = "请转向${DIRECTION_LABELS[currentDirectionIndex]}"
 tvDirectionHint.setBackgroundColor(Color.argb(100, 255, 0, 0))
 btnShoot.isEnabled = false
 }
 }

 /** 拍摄当前方向照片 */
 private fun takeCurrentPhoto() {
 val directionName = SIX_DIRECTIONS[currentDirectionIndex]
 cameraHelper.takePhoto { photoFile ->
 runOnUiThread {
 // 保存照片路径
 if (!capturedPhotos.containsKey(directionName)) {
 capturedPhotos[directionName] = mutableListOf()
 }
 capturedPhotos[directionName]?.add(photoFile.absolutePath)

 // 更新缩略�?
 updateThumbnail(currentDirectionIndex, photoFile)

 // 移动到下一个方�?
 advanceDirection()
 }
 }
 }

 /** 更新方向缩略�?*/
 private fun updateThumbnail(index: Int, photoFile: File) {
 if (index < directionThumbnails.size) {
 val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
 directionThumbnails[index].setImageBitmap(bitmap)
 directionThumbnails[index].setBackgroundColor(
 ContextCompat.getColor(this, R.color.direction_done)
 )
 }
 }

 /** 前进到下一个拍摄方�?*/
 private fun advanceDirection() {
 currentDirectionIndex++
 if (currentDirectionIndex >= 6) {
 // 全部拍完，显示拼接按�?
 btnShoot.visibility = View.GONE
 btnStitch.visibility = View.VISIBLE
 tvDirectionHint.text = "拍摄完成！请进行拼接"
 tvDirectionHint.setBackgroundColor(Color.argb(100, 0, 255, 0))
 } else {
 updateDirectionHint(currentDirectionIndex)
 }
 }

 /** 更新方向提示 */
 private fun updateDirectionHint(index: Int) {
 val label = DIRECTION_LABELS[index]
 val instruction = when (index) {
 0 -> "请将手机对准前方（初始方向）"
 1 -> "请将手机向右旋转 90°"
 2 -> "请将手机向后旋转 180°"
 3 -> "请将手机向左旋转 90°"
 4 -> "请将手机向上仰起 90°（对准天花板：
 5 -> "请将手机向下 90°（对准地板）"
 else -> ""
 }
 tvDirectionHint.text = "[$label] - $instruction"
 tvDirectionHint.setBackgroundColor(Color.argb(100, 255, 255, 0))
 }

 /** 重拍当前方向（长按快门触发） */
 private fun reshootCurrentDirection() {
 val directionName = SIX_DIRECTIONS[currentDirectionIndex]
 // 清除该方向已拍照片路�?
 capturedPhotos[directionName]?.clear()
 // 重置缩略图为灰色
 if (currentDirectionIndex < directionThumbnails.size) {
 directionThumbnails[currentDirectionIndex].setImageDrawable(null)
 directionThumbnails[currentDirectionIndex].setBackgroundColor(Color.GRAY)
 }
 Toast.makeText(this, "已清�?{DIRECTION_LABELS[currentDirectionIndex]}方向照片，请重新拍摄", Toast.LENGTH_SHORT).show()
 }

 /** 跳转到拼接页�?*/
 private fun navigateToStitch() {
 val intent = Intent(this, StitchActivity::class.java)
 // 传递所有照片路�?
 val photoPaths = HashMap<String, ArrayList<String>>()
 capturedPhotos.forEach { (dir, paths) ->
 photoPaths[dir] = ArrayList(paths)
 }
 intent.putExtra("photo_paths", photoPaths)
 startActivity(intent)
 }

 // ========== 生命周期管理 ==========

 override fun onPause() {
 super.onPause()
 // 释放相机（后台不占用：
 cameraHelper.closeCamera()
 // 释放陀螺仪
 gyroHelper.stop()
 }

 override fun onResume() {
 super.onResume()
 // 重新初始�?
 if (PermissionHelper.hasCameraPermission(this)) {
 startCameraAndGyro()
 }
 }

 override fun onDestroy() {
 super.onDestroy()
 cameraHelper.closeCamera()
 gyroHelper.stop()
 }
}

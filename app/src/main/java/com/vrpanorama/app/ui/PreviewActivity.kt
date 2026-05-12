package com.vrpanorama.app.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 拍摄预览页
 * 功能：查看当前方向拍摄的照片，可选择重拍或继续
 */
class PreviewActivity : AppCompatActivity() {

 private lateinit var ivPreview: ImageView
 private lateinit var btnReshoot: Button
 private lateinit var btnNext: Button

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 setContentView(R.layout.activity_preview)

 ivPreview = findViewById(R.id.ivPreview)
 btnReshoot = findViewById(R.id.btnReshoot)
 btnNext = findViewById(R.id.btnNext)

 val photoPath = intent.getStringExtra("photo_path")
 if (photoPath != null) {
 val bitmap = BitmapFactory.decodeFile(photoPath)
 ivPreview.setImageBitmap(bitmap)
 }

 btnReshoot.setOnClickListener {
 setResult(RESULT_CANCELED)
 finish()
 }

 btnNext.setOnClickListener {
 setResult(RESULT_OK)
 finish()
 }
 }
}

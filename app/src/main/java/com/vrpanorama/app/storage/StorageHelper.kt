package com.vrpanorama.app.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface

/**
 * 存储工具类
 * 功能：适配 Android 11+ 分区存储，保存图片到系统相册、写入 EXIF 信息、清理缓存
 */
object StorageHelper {

    /**
     * 保存 Bitmap 到系统相册（适配 MediaStore）
     * @param bitmap 要保存的图片
     * @param displayName 文件显示名称（不含扩展名）
     * @param location 可选地理位置（写入 EXIF）
     * @param timestamp 拍摄时间戳
     * @return 成功返回 content:// URI，失败返回 null
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        location: Location? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Uri? {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

                // Android 10+ 使用分区存储路径
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/VRPanorama"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)

                // 写入地理位置
                location?.let {
                    put(MediaStore.Images.Media.LATITUDE, it.latitude)
                    put(MediaStore.Images.Media.LONGITUDE, it.longitude)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            // 写入图片数据
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            // Android 10+ 标记文件写入完成
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // 写入 EXIF 信息
            writeExif(context, uri, location, timestamp)

            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 写入 EXIF 信息（拍摄时间、GPS 坐标）
     */
    private fun writeExif(
        context: Context,
        uri: Uri,
        location: Location?,
        timestamp: Long
    ) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                // 设置拍摄时间
                val dateStr = ExifInterface.TAG_DATETIME
                exif.setAttribute(
                    dateStr,
                    android.text.format.DateFormat.format("yyyy:MM:dd HH:mm:ss", timestamp)
                        .toString()
                )

                // 设置 GPS 信息
                location?.let {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(it.latitude))
                    exif.setAttribute(
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        if (it.latitude >= 0) "N" else "S"
                    )
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(it.longitude))
                    exif.setAttribute(
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        if (it.longitude >= 0) "E" else "W"
                    )
                }

                exif.saveAttributes()
            }
        } catch (e: Exception) {
            // EXIF 写入失败不影响主流程
        }
    }

    /**
     * 将十进制度数转换为度分秒（DMS）格式，用于 EXIF
     */
    private fun convertToDMS(coord: Double): String {
        val absCoord = Math.abs(coord)
        val degrees = absCoord.toInt()
        val minutesFloat = (absCoord - degrees) * 60
        val minutes = minutesFloat.toInt()
        val seconds = ((minutesFloat - minutes) * 60 * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }

    /**
     * 清除 APP 临时缓存图片
     * 只删除全景拍摄相关的缓存文件
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("PANORAMA_") || file.name.startsWith("CUBEMAP_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 缓存清理失败不影响主流程
        }
    }
}
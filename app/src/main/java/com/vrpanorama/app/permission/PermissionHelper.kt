package com.vrpanorama.app.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 动态权限申请工具类
 * 功能：统一处理相机、存储、定位权限的检查与申请
 * 适配：Android 6.0+ 动态权限 / Android 13+ 新存储权限
 */
object PermissionHelper {

    // ==================== 权限请求码 ====================
    /** 相机权限请求码 */
    const val REQ_CAMERA = 100

    /** 存储权限请求码 */
    const val REQ_STORAGE = 101

    /** 定位权限请求码 */
    const val REQ_LOCATION = 102

    /** 全部权限请求码 */
    const val REQ_ALL = 200

    // ==================== 权限检查方法 ====================

    /**
     * 检查是否拥有相机权限
     */
    fun hasCameraPermission(activity: Activity): Boolean {
        return isGranted(activity, Manifest.permission.CAMERA)
    }

    /**
     * 检查是否拥有存储权限（适配 Android 13）
     */
    fun hasStoragePermission(activity: Activity): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用 READ_MEDIA_IMAGES
            isGranted(activity, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            // Android 13 以下使用 READ_EXTERNAL_STORAGE
            isGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 检查是否拥有定位权限（精确定位）
     */
    fun hasLocationPermission(activity: Activity): Boolean {
        return isGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // ==================== 权限申请方法 ====================

    /**
     * 单独申请相机权限
     */
    fun requestCamera(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            REQ_CAMERA
        )
    }

    /**
     * 单独申请存储权限（适配 Android 13）
     */
    fun requestStorage(activity: Activity) {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        ActivityCompat.requestPermissions(activity, permissions, REQ_STORAGE)
    }

    /**
     * 单独申请定位权限
     */
    fun requestLocation(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOCATION
        )
    }

    /**
     * 一次性申请所有必要权限（相机 + 存储 + 定位）
     * 全景拍摄必备权限
     */
    fun requestAllPermissions(activity: Activity) {
        val permissions = mutableListOf<String>()

        // 添加相机权限
        permissions.add(Manifest.permission.CAMERA)

        // 添加存储权限（区分 Android 13）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // 添加定位权限
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // 发起请求
        ActivityCompat.requestPermissions(
            activity,
            permissions.toTypedArray(),
            REQ_ALL
        )
    }

    // ==================== 工具方法 ====================

    /**
     * 判断单个权限是否已授权
     * @param activity 上下文
     * @param permission 权限名称
     * @return true=已授权 false=未授权
     */
    private fun isGranted(activity: Activity, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 判断权限申请结果是否全部授权
     * @param grantResults 权限申请结果数组
     * @return true=全部通过 false=存在未授权
     */
    fun allGranted(grantResults: IntArray): Boolean {
        return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }
}
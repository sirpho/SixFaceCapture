package com.vrpanorama.app.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限工具�?- 动态申请运行时权限
 * 适配 Android 6.0+ 动态权限机�?
 */
object PermissionHelper {

 // 请求码常�?
 const val REQ_CAMERA = 100
 const val REQ_STORAGE = 101
 const val REQ_LOCATION = 102
 const val REQ_ALL = 200

 fun hasLocationPermission(activity: Activity): Boolean {
 return isGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) ||
 isGranted(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
 }

 fun requestCamera(activity: Activity) {
 ActivityCompat.requestPermissions(
 activity,
 arrayOf(Manifest.permission.CAMERA),
 REQ_CAMERA
 )
 }

 fun requestStorage(activity: Activity) {
 val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
 arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
 } else {
 arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
 }
 ActivityCompat.requestPermissions(activity, permissions, REQ_STORAGE)
 }

 fun requestLocation(activity: Activity) {
 ActivityCompat.requestPermissions(
 activity,
 arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
 REQ_LOCATION
 )
 }

 fun requestAllPermissions(activity: Activity) {
 val permissions = mutableListOf<String>(
 Manifest.permission.CAMERA
 )
 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
 permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
 } else {
 permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
 permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
 }
 permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
 permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
 ActivityCompat.requestPermissions(
 activity,
 permissions.toTypedArray(),
 REQ_ALL
 )
 }

 fun allGranted(grantResults: IntArray): Boolean {
 return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
 }
}

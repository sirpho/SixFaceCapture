# 六面全景采集助手 (SixFace VR Panorama Capture)

VR看房室内全景拍摄APP，手机离线生成标准6面立方体贴图。

## 环境要求
- Android Studio Flamingo (2022.2.1+) 或更高版本
- JDK 11+
- Android SDK 33+
- Gradle 7.6.3 (wrapper已包含)

## OpenCV 配置步骤（关键！）
1. 下载 OpenCV Android SDK: https://opencv.org/releases/ (推荐 4.8.0+)
2. 解压后获取以下文件:
 - `OpenCV-android-sdk/sdk/java/opencv-4.x.x.jar`
 - `OpenCV-android-sdk/sdk/native/libs/arm64-v8a/libopencv_java4.so`
 - `OpenCV-android-sdk/sdk/native/libs/armeabi-v7a/libopencv_java4.so`
3. 将这些文件放到本项目对应目录:
 - 复制 `opencv-4.x.x.jar` 到 `app/libs/`
 - 复制 `libopencv_java4.so` 到 `app/src/main/jniLibs/arm64-v8a/`
 - 复制 `libopencv_java4.so` 到 `app/src/main/jniLibs/armeabi-v7a/`
4. 同步 Gradle (Sync Project with Gradle Files)

## 快速开始
1. 用 Android Studio 打开项目根目录 `SixFaceCapture/`
2. 等待 Gradle 同步完成
3. 连接手机 (开启USB调试) 或使用模拟器
4. 点击 Run 按钮编译运行

## 项目结构
```
app/src/main/java/com/vrpanorama/app/
 permission/PermissionHelper.kt # 权限申请
 sensor/GyroHelper.kt # 陀螺仪角度检测
 camera/CameraHelper.kt # Camera2封装
 stitching/PanoramaStitcher.kt # OpenCV全景拼接
 cubemap/CubemapConverter.kt # 全景转立方体
 storage/StorageHelper.kt # 相册保存+EXIF
 ui/MainActivity.kt # 主拍摄页
 ui/PreviewActivity.kt # 预览页
 ui/StitchActivity.kt # 拼接处理页
 ui/ExportActivity.kt # 导出页
 ui/CrosshairView.kt # 十字准星控件
```

## 技术栈
- 语言: Kotlin
- 相机: Camera2 API
- 传感器: 旋转矢量传感器 (ROTATION_VECTOR)
- 图像处理: OpenCV Android SDK
- 异步: Kotlin Coroutines
- 存储: MediaStore (兼容 Android 11+)
- 最低系统: Android 7.0 (API 24)

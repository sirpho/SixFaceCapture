# ProGuard 规则文件
# 保留 OpenCV 类
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# 保留 Kotlin 协程
-keepnames class kotlinx.coroutines.** {}
-dontwarn kotlinx.coroutines.**

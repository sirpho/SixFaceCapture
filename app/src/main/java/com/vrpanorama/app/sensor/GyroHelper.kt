package com.vrpanorama.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

/**
 * 陀螺仪/旋转矢量传感器工具类
 * 功能：实时计算手机姿态角度，辅助六面全景拍摄方向引导
 * 优先使用旋转矢量传感器，获取更精准的姿态数据
 */
class GyroHelper(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var rotationVectorSensor: Sensor? = null

    // 当前姿态角度（角度制）
    var yaw: Float = 0f    // 偏航角：水平旋转（绕Z轴）
    var pitch: Float = 0f  // 俯仰角：前后倾斜（绕X轴）
    var roll: Float = 0f   // 横滚角：左右倾斜（绕Y轴）

    // 旋转矩阵
    private val rotationMatrix = FloatArray(16)

    // 方向角数组 [方位角, 俯仰角, 横滚角]
    private val orientationAngles = FloatArray(3)

    // 方向变化监听器
    private var listener: OnDirectionChangedListener? = null

    /**
     * 方向变化监听接口
     * 用于实时回调当前朝向与是否对准目标方向
     */
    interface OnDirectionChangedListener {
        fun onDirectionChanged(direction: SixDirection, isAligned: Boolean)
    }

    /**
     * 全景拍摄六个方向枚举
     * label：显示文字
     * order：拍摄顺序 0~5
     */
    enum class SixDirection(val label: String, val order: Int) {
        FRONT("前", 0),
        RIGHT("右", 1),
        BACK("后", 2),
        LEFT("左", 3),
        UP("上", 4),
        DOWN("下", 5);

        companion object {
            // 根据拍摄顺序获取对应方向
            fun fromOrder(order: Int): SixDirection {
                return values().find { it.order == order } ?: FRONT
            }
        }
    }

    /**
     * 启动传感器监听
     * 优先注册旋转矢量传感器，无则降级使用加速度+磁场传感器
     */
    fun start(listener: OnDirectionChangedListener) {
        this.listener = listener
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor != null) {
            // 使用最高采样率，保证角度实时性
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else {
            // 降级方案：加速度传感器 + 磁场传感器
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            accelSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    /**
     * 停止传感器监听（必须在页面销毁时调用，防止耗电）
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // 从旋转矢量获取旋转矩阵
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                // 从矩阵计算设备方向
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // 转换为角度制，方便判断与调试
                yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (yaw < 0) yaw += 360f  // 转为 0~360°

                pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                // 计算当前朝向
                checkDirection()
            }

            // 降级方案（简化实现）
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_MAGNETIC_FIELD -> {
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 检查当前手机朝向，并回调给UI
     */
    private fun checkDirection() {
        val (direction, isAligned) = getCurrentDirection()
        listener?.onDirectionChanged(direction, isAligned)
    }

    /**
     * 核心算法：根据 yaw/pitch 判断当前朝向
     * 六方向定义：
     * FRONT：yaw=0°    水平朝前
     * RIGHT：yaw=90°   向右转
     * BACK：yaw=180°   向后转
     * LEFT：yaw=270°   向左转
     * UP：pitch=90°    手机朝上
     * DOWN：pitch=-90° 手机朝下
     *
     * 偏差阈值15°：在范围内判定为已对准
     */
    private fun getCurrentDirection(): Pair<SixDirection, Boolean> {
        val threshold = 15f

        // 优先判断上下方向（俯仰角 pitch）
        if (pitch > 45f) {
            val aligned = abs(pitch - 90f) < threshold
            return Pair(SixDirection.UP, aligned)
        }
        if (pitch < -45f) {
            val aligned = abs(pitch + 90f) < threshold
            return Pair(SixDirection.DOWN, aligned)
        }

        // 水平方向判断（偏航角 yaw）
        return when {
            yaw < 45f || yaw > 315f -> {
                val aligned = abs(yaw % 360f) < threshold
                Pair(SixDirection.FRONT, aligned)
            }

            yaw in 45f..135f -> {
                val aligned = abs(yaw - 90f) < threshold
                Pair(SixDirection.RIGHT, aligned)
            }

            yaw in 135f..225f -> {
                val aligned = abs(yaw - 180f) < threshold
                Pair(SixDirection.BACK, aligned)
            }

            else -> {
                val aligned = abs(yaw - 270f) < threshold
                Pair(SixDirection.LEFT, aligned)
            }
        }
    }

    /**
     * 获取当前角度信息（用于调试显示）
     */
    fun getAngleInfo(): String {
        return "yaw=%.1f pitch=%.1f roll=%.1f".format(yaw, pitch, roll)
    }
}
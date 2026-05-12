package com.vrpanorama.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*

/**
 * 陀螺仪角度计算工具�?
 * 用于实时判断手机朝向，辅�?面全景拍�?
 * 使用旋转矢量传感器（ROTATION_VECTOR）获取精确姿�?
 */
class GyroHelper(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var rotationVectorSensor: Sensor? = null

    // 当前姿态角度（弧度： yaw偏航, pitch俯仰, roll横滚
    var yaw: Float = 0f // 水平旋转角（绕Z轴）
    var pitch: Float = 0f // 前后倾斜角（绕X轴）
    var roll: Float = 0f // 左右倾斜角（绕Y轴）

    // 旋转矩阵：x4：
    private val rotationMatrix = FloatArray(16)

    // 方向角数�?[azimuth, pitch, roll]
    private val orientationAngles = FloatArray(3)

    // 监听器回调
    private var listener: OnDirectionChangedListener? = null

    /** 方向变化监听�?*/
    interface OnDirectionChangedListener {
        fun onDirectionChanged(direction: SixDirection, isAligned: Boolean)
    }

    /** 六个拍摄方向枚举 */
    enum class SixDirection(val label: String, val order: Int) {
        FRONT("\u524d", 0), // 前
        RIGHT("\u53f3", 1), // 右
        BACK("\u540e", 2), // 后
        LEFT("\u5de6", 3), // 左
        UP("\u4e0a", 4),  // 上
        DOWN("\u4e0b", 5);  // 下


        companion object {
            fun fromOrder(order: Int): SixDirection {
                return values().find { it.order == order } ?: FRONT
            }
        }
    }

    /** 注册传感器监听器 */
    fun start(listener: OnDirectionChangedListener) {
        this.listener = listener
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor != null) {
            // 以最快采样率注册，确保实时�?
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else {
            // 降级：使用加速度�?磁场传感�?
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            if (magSensor != null) {
                sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // 旋转矢量传感�? 转为旋转矩阵后提取欧拉角
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                // azimuth: 绕Z轴角�?[-PI, PI], 转为 0~360 �?
                yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (yaw < 0) yaw += 360f
                // pitch: 绕X轴角�?
                pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                // roll: 绕Y轴角�?
                roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                checkDirection()
            }

            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // 降级方案: 使用加速度+磁场融合（简化实现）
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** 根据当前角度判断手机朝向 */
    private fun checkDirection() {
        val (direction, isAligned) = getCurrentDirection()
        listener?.onDirectionChanged(direction, isAligned)
    }

    /**
     * 核心算法: 根据 yaw/pitch/roll 判断朝向
     * 6个方向的目标角度定义:
     * FRONT: yaw=0, pitch=0, roll=0 (手机竖屏正对前方)
     * RIGHT: yaw=90, pitch=0, roll=0
     * BACK: yaw=180, pitch=0, roll=0
     * LEFT: yaw=270, pitch=0, roll=0
     * UP: yaw=0, pitch=90, roll=0 (手机仰头)
     * DOWN: yaw=0, pitch=-90, roll=0 (手机低头)
     */
    private fun getCurrentDirection(): Pair<SixDirection, Boolean> {
        // 角度偏差阈�? 15度以内认为对�?
        val threshold = 15f

        // 判断上下方向 (pitch 决定)
        if (pitch > 45f) {
            // 手机明显仰头 �?上方�?
            val aligned = abs(pitch - 90f) < threshold
            return Pair(SixDirection.UP, aligned)
        }
        if (pitch < -45f) {
            // 手机明显低头 �?下方�?
            val aligned = abs(pitch + 90f) < threshold
            return Pair(SixDirection.DOWN, aligned)
        }

        // 水平方向判断 (yaw 决定)
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

    /** 获取当前角度信息字符串（调试用） */
    fun getAngleInfo(): String {
        return "yaw=%.1f pitch=%.1f roll=%.1f".format(yaw, pitch, roll)
    }
}

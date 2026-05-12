package com.vrpanorama.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 十字准星自定义 View
 * 功能：半透明十字引导框，用于辅助用户对准拍摄方向
 * 颜色规则：绿色=已对准，红色=未对准
 */
class CrosshairView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 是否对准（绿色=true，红色=false）
    var isAligned: Boolean = false
        set(value) {
            field = value
            invalidate() // 触发重绘
        }

    // 当前方向文字（前/后/左/右/上/下）
    var directionText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    // 十字线画笔
    private val crosshairPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // 文字画笔
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    // 文字背景半透明画笔
    private val bgPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 0)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val size = Math.min(width, height) * 0.15f

        // 设置颜色：绿色=对准，红色=未对准
        crosshairPaint.color = if (isAligned) Color.argb(200, 0, 255, 0)
        else Color.argb(200, 255, 0, 0)

        // 绘制十字线（水平 + 垂直）
        val halfLine = size * 0.6f
        val gap = size * 0.25f // 中心缺口

        // 上
        canvas.drawLine(cx, cy - gap, cx, cy - halfLine, crosshairPaint)
        // 下
        canvas.drawLine(cx, cy + gap, cx, cy + halfLine, crosshairPaint)
        // 左
        canvas.drawLine(cx - gap, cy, cx - halfLine, cy, crosshairPaint)
        // 右
        canvas.drawLine(cx + gap, cy, cx + halfLine, cy, crosshairPaint)

        // 绘制外框圆角矩形
        val rectSize = size
        val rect = RectF(cx - rectSize, cy - rectSize, cx + rectSize, cy + rectSize)
        canvas.drawRoundRect(rect, 8f, 8f, crosshairPaint)

        // 绘制四角标记
        val cornerLen = size * 0.2f
        // 左上
        canvas.drawLine(
            cx - rectSize,
            cy - rectSize + cornerLen,
            cx - rectSize,
            cy - rectSize,
            crosshairPaint
        )
        canvas.drawLine(
            cx - rectSize,
            cy - rectSize,
            cx - rectSize + cornerLen,
            cy - rectSize,
            crosshairPaint
        )
        // 右上
        canvas.drawLine(
            cx + rectSize - cornerLen,
            cy - rectSize,
            cx + rectSize,
            cy - rectSize,
            crosshairPaint
        )
        canvas.drawLine(
            cx + rectSize,
            cy - rectSize,
            cx + rectSize,
            cy - rectSize + cornerLen,
            crosshairPaint
        )
        // 左下
        canvas.drawLine(
            cx - rectSize,
            cy + rectSize - cornerLen,
            cx - rectSize,
            cy + rectSize,
            crosshairPaint
        )
        canvas.drawLine(
            cx - rectSize,
            cy + rectSize,
            cx - rectSize + cornerLen,
            cy + rectSize,
            crosshairPaint
        )
        // 右下
        canvas.drawLine(
            cx + rectSize - cornerLen,
            cy + rectSize,
            cx + rectSize,
            cy + rectSize,
            crosshairPaint
        )
        canvas.drawLine(
            cx + rectSize,
            cy + rectSize - cornerLen,
            cx + rectSize,
            cy + rectSize,
            crosshairPaint
        )

        // 绘制方向文字与背景
        if (directionText.isNotEmpty()) {
            val textWidth = textPaint.measureText(directionText)
            canvas.drawRect(
                cx - textWidth / 2 - 20, cy + rectSize + 10,
                cx + textWidth / 2 + 20, cy + rectSize + 60, bgPaint
            )
            canvas.drawText(directionText, cx, cy + rectSize + 50, textPaint)
        }
    }
}
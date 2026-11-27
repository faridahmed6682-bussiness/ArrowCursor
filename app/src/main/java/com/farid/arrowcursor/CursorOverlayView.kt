package com.farid.arrowcursor

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class CursorOverlayView(context: Context) : View(context) {

    var cursorX: Float
    var cursorY: Float
    var cursorSizeDp: Float = 36f
        set(value) {
            field = value
            invalidate()
        }
    var cursorColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 2f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        val metrics = Resources.getSystem().displayMetrics
        cursorX = metrics.widthPixels / 2f
        cursorY = metrics.heightPixels / 2f
    }

    fun moveBy(dx: Float, dy: Float) {
        val metrics = Resources.getSystem().displayMetrics
        val displayWidth = metrics.widthPixels.toFloat()
        val displayHeight = metrics.heightPixels.toFloat()
        cursorX = (cursorX + dx).coerceIn(0f, displayWidth)
        cursorY = (cursorY + dy).coerceIn(0f, displayHeight)
        invalidate()
    }

    fun moveTo(x: Float, y: Float) {
        val metrics = Resources.getSystem().displayMetrics
        cursorX = x.coerceIn(0f, metrics.widthPixels.toFloat())
        cursorY = y.coerceIn(0f, metrics.heightPixels.toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = cursorSizeDp / 28f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 28f)
            lineTo(6f, 21.5f)
            lineTo(9.5f, 29f)
            lineTo(12f, 27.8f)
            lineTo(8.8f, 20.2f)
            lineTo(15.5f, 20.2f)
            close()
        }
        // Scale the 20x28 box to the requested size, then translate to the cursor position.
        path.transform(
            android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(cursorX, cursorY)
            },
        )
        fillPaint.color = cursorColor
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, outlinePaint)
    }
}

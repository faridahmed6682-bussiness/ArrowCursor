package com.farid.arrowcursor

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout

/**
 * Floating on-screen D-pad for moving the pointer by touch alone.
 * Hold an arrow to keep moving (like holding a real mouse), tap the
 * centre button to click. Drag it by the handle, collapse it to a dot
 * with the "—" button. Works with no physical keyboard at all.
 */
class ControlPanelView(
    context: Context,
    private val stepPx: () -> Float,
    private val onMove: (dx: Float, dy: Float) -> Unit,
    private val onClick: () -> Unit,
    private val onScroll: (direction: Float) -> Unit,
) : LinearLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var heldDx = 0f
    private var heldDy = 0f
    private var moving = false

    private val moveRunnable = object : Runnable {
        override fun run() {
            if (!moving) return
            onMove(heldDx * stepPx(), heldDy * stepPx())
            handler.postDelayed(this, 32)
        }
    }

    private var scrollDirection = 0f
    private var scrolling = false

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (!scrolling) return
            onScroll(scrollDirection)
            handler.postDelayed(this, 300)
        }
    }

    private val expandedBox: LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
    }
    private val collapsedButton: Button = Button(context).apply {
        text = "✥"
        textSize = 20f
        visibility = View.GONE
        setOnClickListener {
            visibility = View.GONE
            expandedBox.visibility = View.VISIBLE
        }
    }

    private var panelParams: WindowManager.LayoutParams? = null
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(170, 25, 25, 25))
        setPadding(dp(6), dp(4), dp(6), dp(6))

        // Header: drag handle + collapse button.
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = Button(context).apply {
            text = "⠿"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.LTGRAY)
            setOnTouchListener { _, event -> handleDrag(event) }
        }
        val collapse = Button(context).apply {
            text = "—"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.LTGRAY)
            setOnClickListener {
                stopMove()
                stopScroll()
                expandedBox.visibility = View.GONE
                collapsedButton.visibility = View.VISIBLE
            }
        }
        header.addView(
            handle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            collapse,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        expandedBox.addView(header)

        // Arrow cross: up / left-click-right / down.
        expandedBox.addView(row(View(context), arrowButton("↑", 0f, -1f), View(context)))
        expandedBox.addView(row(arrowButton("←", -1f, 0f), clickButton(), arrowButton("→", 1f, 0f)))
        expandedBox.addView(row(View(context), arrowButton("↓", 0f, 1f), View(context)))
        // Scroll row: up shows content above, down shows content below.
        expandedBox.addView(row(scrollButton("⇑", 1f), scrollButton("⇓", -1f)))

        addView(expandedBox)
        addView(collapsedButton)
    }

    fun bindParams(params: WindowManager.LayoutParams) {
        panelParams = params
    }

    fun release() {
        stopMove()
        stopScroll()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun row(vararg views: View): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            for (v in views) {
                val params = LinearLayout.LayoutParams(dp(56), dp(56))
                params.setMargins(dp(3), dp(3), dp(3), dp(3))
                addView(v, params)
            }
        }
    }

    private fun arrowButton(label: String, dx: Float, dy: Float): Button {
        return Button(context).apply {
            text = label
            textSize = 22f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startMove(dx, dy)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopMove()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun clickButton(): Button {
        return Button(context).apply {
            text = "●"
            textSize = 22f
            setOnClickListener { onClick() }
        }
    }

    private fun scrollButton(label: String, direction: Float): Button {
        return Button(context).apply {
            text = label
            textSize = 22f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startScroll(direction)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopScroll()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun startScroll(direction: Float) {
        scrollDirection = direction
        if (!scrolling) {
            scrolling = true
            onScroll(scrollDirection)
            handler.postDelayed(scrollRunnable, 300)
        }
    }

    private fun stopScroll() {
        scrolling = false
        scrollDirection = 0f
        handler.removeCallbacks(scrollRunnable)
    }

    private fun startMove(dx: Float, dy: Float) {
        heldDx = dx
        heldDy = dy
        if (!moving) {
            moving = true
            onMove(heldDx * stepPx(), heldDy * stepPx())
            handler.postDelayed(moveRunnable, 32)
        }
    }

    private fun stopMove() {
        moving = false
        heldDx = 0f
        heldDy = 0f
        handler.removeCallbacks(moveRunnable)
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        val params = panelParams ?: return false
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartX = params.x
                dragStartY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Gravity is BOTTOM|END: x grows leftwards, y grows upwards.
                params.x = dragStartX - (event.rawX - dragStartRawX).toInt()
                params.y = dragStartY - (event.rawY - dragStartRawY).toInt()
                wm.updateViewLayout(this, params)
                return true
            }
            else -> return false
        }
    }
}

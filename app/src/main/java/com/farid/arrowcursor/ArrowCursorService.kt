package com.farid.arrowcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ArrowCursorService : AccessibilityService() {

    private lateinit var settings: CursorSettings
    private var overlayView: CursorOverlayView? = null
    private var windowManager: WindowManager? = null
    private var overlayAdded = false
    private var controlPanelView: ControlPanelView? = null
    private var panelAdded = false
    private var pointerHandleView: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var handleAdded = false

    // Pointer-drag state (finger on the pointer itself).
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragDownTime = 0L
    private var dragStartCursorX = 0f
    private var dragStartCursorY = 0f

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == CursorSettings.KEY_CURSOR_ENABLED ||
                key == CursorSettings.KEY_SPEED ||
                key == CursorSettings.KEY_SIZE ||
                key == CursorSettings.KEY_COLOR ||
                key == CursorSettings.KEY_CONTROL_PANEL
            ) {
                updateOverlayState()
            }
        }

    // Commands from the ArrowCursor keyboard (IME) — it receives the
    // hardware keys directly, so it tells this service to move the pointer.
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_POINTER_COMMAND) return
            when (intent.getStringExtra(EXTRA_COMMAND)) {
                "move" -> moveCursor(
                    intent.getFloatExtra(EXTRA_DX, 0f),
                    intent.getFloatExtra(EXTRA_DY, 0f),
                )
                "click" -> performClick()
                "scroll" -> performScroll(intent.getFloatExtra(EXTRA_DIRECTION, 0f))
                "hscroll" -> performHorizontalScroll(
                    intent.getFloatExtra(EXTRA_DIRECTION, 0f),
                )
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = CursorSettings(this)
        settings.registerListener(prefsListener)
        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            IntentFilter(ACTION_POINTER_COMMAND),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = CursorOverlayView(this)
        applySettingsToOverlay()

        if (settings.cursorEnabled) {
            addOverlayView()
        }
        showNotification()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!settings.cursorEnabled) return false

        // Typing keys pause while a text field is focused so typing still works,
        // but arrow keys ALWAYS move the pointer — like a real mouse.
        val typingPaused = settings.autoPause && isTypingTargetFocused()

        val speed = settings.speedPx.toFloat()
        // `when` used as an expression: every branch ends in Boolean.
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursor(0f, -speed)
                sendKeyTest("UP")
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(0f, speed)
                sendKeyTest("DOWN")
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursor(-speed, 0f)
                sendKeyTest("LEFT")
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursor(speed, 0f)
                sendKeyTest("RIGHT")
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (typingPaused) {
                    false
                } else if (settings.clickKey == "space" || settings.clickKey == "both") {
                    performClick()
                    sendKeyTest("SPACE")
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (typingPaused) {
                    false
                } else if (settings.clickKey == "enter" || settings.clickKey == "both") {
                    performClick()
                    sendKeyTest("ENTER")
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                performScroll(1f)
                sendKeyTest("PGUP")
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                performScroll(-1f)
                sendKeyTest("PGDN")
                true
            }
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: focus is checked at key-event time.
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onDestroy() {
        removeOverlayView()
        removeControlPanel()
        removePointerHandle()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered.
        }
        if (::settings.isInitialized) {
            settings.unregisterListener(prefsListener)
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlayView()
        removeControlPanel()
        removePointerHandle()
        return super.onUnbind(intent)
    }

    private fun moveCursor(dx: Float, dy: Float) {
        overlayView?.moveBy(dx, dy)
        updateHandlePosition()
    }

    private fun moveCursorTo(x: Float, y: Float) {
        overlayView?.moveTo(x, y)
        updateHandlePosition()
    }

    private fun performClick() {
        val x = overlayView?.cursorX ?: return
        val y = overlayView?.cursorY ?: return
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchPointerGesture(gesture, 150)
    }

    /**
     * Dispatches an injected gesture at the pointer. The pointer's drag
     * handle is a touchable window sitting exactly on the pointer, so a
     * gesture starting there would land on our own handle instead of the
     * app below — hide the handle for the gesture, then put it back.
     */
    private val gestureHandler = Handler(Looper.getMainLooper())

    private fun dispatchPointerGesture(gesture: GestureDescription, restoreAfterMs: Long) {
        val hadHandle = handleAdded
        if (hadHandle) removePointerHandle()
        dispatchGesture(gesture, null, null)
        if (hadHandle) {
            gestureHandler.postDelayed({
                if (::settings.isInitialized && settings.cursorEnabled) {
                    addPointerHandle()
                }
            }, restoreAfterMs)
        }
    }

    /**
     * Scrolls the app under the pointer with a swipe gesture.
     * direction +1 = scroll up (see content above), -1 = scroll down.
     */
    private fun performScroll(direction: Float) {
        val distance = resources.displayMetrics.heightPixels * 0.4f * direction
        performSwipe(0f, distance)
    }

    private fun performHorizontalScroll(direction: Float) {
        val distance = resources.displayMetrics.widthPixels * 0.4f * direction
        performSwipe(distance, 0f)
    }

    private fun performSwipe(dx: Float, dy: Float) {
        val x = overlayView?.cursorX ?: return
        val y = overlayView?.cursorY ?: return
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val startX = x.coerceIn(120f, width - 120f)
        val startY = y.coerceIn(120f, height - 120f)
        val endX = (startX + dx).coerceIn(0f, width)
        val endY = (startY + dy).coerceIn(0f, height)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
            .build()
        dispatchPointerGesture(gesture, 250)
    }

    private fun isTypingTargetFocused(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            focused.isEditable ||
                (focused.className?.toString()?.contains("EditText") == true)
        } catch (e: Exception) {
            false
        }
    }

    private fun sendKeyTest(name: String) {
        sendBroadcast(Intent(ACTION_KEY_TEST).putExtra(EXTRA_KEY_NAME, name))
    }

    private fun applySettingsToOverlay() {
        overlayView?.let { view ->
            view.cursorSizeDp = settings.sizeDp.toFloat()
            view.cursorColor = settings.cursorColor
        }
    }

    private fun addOverlayView() {
        if (overlayAdded) return
        val view = overlayView ?: return
        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(view, params)
        overlayAdded = true
    }

    private fun removeOverlayView() {
        if (!overlayAdded) return
        val view = overlayView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(view)
        } catch (e: IllegalArgumentException) {
            // View was already removed.
        }
        overlayAdded = false
    }

    private fun updateOverlayState() {
        if (!::settings.isInitialized) return
        if (settings.cursorEnabled) {
            applySettingsToOverlay()
            addOverlayView()
            addPointerHandle()
            if (settings.showControlPanel) addControlPanel() else removeControlPanel()
        } else {
            removeOverlayView()
            removeControlPanel()
            removePointerHandle()
        }
        showNotification()
    }

    private fun addControlPanel() {
        if (panelAdded) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val panel = ControlPanelView(
            this,
            stepPx = { settings.speedPx.toFloat() },
            onMove = { dx, dy -> moveCursor(dx, dy) },
            onClick = { performClick() },
            onScroll = { direction -> performScroll(direction) },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }
        panel.bindParams(params)
        wm.addView(panel, params)
        controlPanelView = panel
        panelAdded = true
    }

    private fun removeControlPanel() {
        if (!panelAdded) return
        val panel = controlPanelView ?: return
        val wm = windowManager ?: return
        panel.release()
        try {
            wm.removeView(panel)
        } catch (e: IllegalArgumentException) {
            // View was already removed.
        }
        controlPanelView = null
        panelAdded = false
    }

    /**
     * Invisible touch zone around the pointer itself, so the pointer can be
     * moved with a finger and no panel has to stay on screen:
     * slow drag = move, quick tap = click, fast flick = scroll.
     */
    private fun addPointerHandle() {
        if (handleAdded) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val size = (72 * density).toInt()
        val handle = View(this).apply {
            setOnTouchListener { _, event ->
                handlePointerTouch(event)
                true
            }
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        wm.addView(handle, params)
        pointerHandleView = handle
        handleParams = params
        handleAdded = true
        updateHandlePosition()
    }

    private fun removePointerHandle() {
        if (!handleAdded) return
        val handle = pointerHandleView ?: return
        val wm = windowManager ?: return
        try {
            wm.removeView(handle)
        } catch (e: IllegalArgumentException) {
            // View was already removed.
        }
        pointerHandleView = null
        handleParams = null
        handleAdded = false
    }

    private fun updateHandlePosition() {
        if (!handleAdded) return
        val wm = windowManager ?: return
        val handle = pointerHandleView ?: return
        val params = handleParams ?: return
        val half = (36 * resources.displayMetrics.density).toInt()
        val x = overlayView?.cursorX ?: return
        val y = overlayView?.cursorY ?: return
        params.x = (x - half).toInt()
        params.y = (y - half).toInt()
        try {
            wm.updateViewLayout(handle, params)
        } catch (e: IllegalArgumentException) {
            // Handle was already removed.
        }
    }

    private fun handlePointerTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragDownX = event.rawX
                dragDownY = event.rawY
                dragDownTime = event.eventTime
                dragStartCursorX = overlayView?.cursorX ?: event.rawX
                dragStartCursorY = overlayView?.cursorY ?: event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragDownX
                val dy = event.rawY - dragDownY
                moveCursorTo(dragStartCursorX + dx, dragStartCursorY + dy)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.rawX - dragDownX
                val dy = event.rawY - dragDownY
                val duration = (event.eventTime - dragDownTime).coerceAtLeast(1L)
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val velocity = distance / duration * 1000f
                when {
                    distance < 30f && duration < 300L -> performClick()
                    velocity > 1000f && duration < 350L && distance > 120f -> {
                        // Fast flick: scroll, pointer stays where it was.
                        moveCursorTo(dragStartCursorX, dragStartCursorY)
                        if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                            performScroll(if (dy < 0) -1f else 1f)
                        } else {
                            performHorizontalScroll(if (dx < 0) -1f else 1f)
                        }
                    }
                    else -> {
                        // Slow drag: pointer stays where the finger left it.
                    }
                }
            }
        }
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW,
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val enabled = if (::settings.isInitialized) settings.cursorEnabled else false
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cursor)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (enabled) {
                    getString(R.string.notification_text_on)
                } else {
                    getString(R.string.notification_text_off)
                },
            )
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                0,
                if (enabled) {
                    getString(R.string.notification_action_disable)
                } else {
                    getString(R.string.notification_action_enable)
                },
                PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(this, CursorToggleReceiver::class.java).apply {
                        action = "com.farid.arrowcursor.TOGGLE_CURSOR"
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_KEY_TEST = "com.farid.arrowcursor.KEY_TEST"
        const val EXTRA_KEY_NAME = "key_name"
        const val ACTION_POINTER_COMMAND = "com.farid.arrowcursor.POINTER_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_DX = "dx"
        const val EXTRA_DY = "dy"
        const val EXTRA_DIRECTION = "direction"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "arrow_cursor_channel"
    }
}

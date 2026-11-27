package com.farid.arrowcursor

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ArrowCursor keyboard (an input method). A hardware keyboard's keys reach
 * an input method directly through [onKeyDown], so this is the route that
 * works when the accessibility service's [ArrowCursorService.onKeyEvent]
 * never receives the keys even though its key-filter flag is set.
 *
 * Behaviour mirrors the accessibility service: arrow keys ALWAYS move the
 * pointer (like a real mouse); Space/Enter click, except while a text field
 * is focused, where they type normally so typing still works. Letters pass
 * through to the input connection as text.
 *
 * The user switches to this keyboard for mouse mode and back to
 * Bijoy/Gboard for typing (Ctrl+Space or the keyboard icon in the
 * navigation bar switches input methods).
 */
class ArrowCursorIME : InputMethodService() {

    private lateinit var settings: CursorSettings

    override fun onCreate() {
        super.onCreate()
        settings = CursorSettings(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyDown(keyCode, event)
        }
        val speed = settings.speedPx.toFloat()
        val typing = isTextFieldFocused()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                sendCommand("move", dy = -speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                sendCommand("move", dy = speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                sendCommand("move", dx = -speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                sendCommand("move", dx = speed)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (typing) {
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (typing) {
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                sendCommand("scroll", direction = 1f)
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                sendCommand("scroll", direction = -1f)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> true
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (isTextFieldFocused()) {
                    super.onKeyUp(keyCode, event)
                } else {
                    true
                }
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val label = TextView(this).apply {
            text = getString(R.string.ime_mode_label)
            textSize = 16f
        }
        val switchButton = Button(this).apply {
            text = getString(R.string.ime_switch_keyboard)
            setOnClickListener {
                val manager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                manager.showInputMethodPicker()
            }
        }
        layout.addView(label)
        layout.addView(switchButton)
        return layout
    }

    private fun isTextFieldFocused(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return (info.inputType and InputType.TYPE_MASK_CLASS) ==
            InputType.TYPE_CLASS_TEXT
    }

    private fun sendCommand(
        command: String,
        dx: Float = 0f,
        dy: Float = 0f,
        direction: Float = 0f,
    ) {
        val intent = android.content.Intent(
            ArrowCursorService.ACTION_POINTER_COMMAND,
        ).apply {
            setPackage(packageName)
            putExtra(ArrowCursorService.EXTRA_COMMAND, command)
            putExtra(ArrowCursorService.EXTRA_DX, dx)
            putExtra(ArrowCursorService.EXTRA_DY, dy)
            putExtra(ArrowCursorService.EXTRA_DIRECTION, direction)
        }
        sendBroadcast(intent)
    }
}

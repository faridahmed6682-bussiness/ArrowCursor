package com.farid.arrowcursor

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
 * Two jobs in one keyboard:
 * 1. Mouse mode — arrow keys ALWAYS move the pointer (like a real mouse);
 *    Space/Enter click, except while a text field is focused, where they
 *    type normally so typing still works.
 * 2. Typing — an on-screen English + Bengali keyboard, and Bengali
 *    phonetic typing from the hardware keyboard (Avro style: type "ami"
 *    and get "আমি"). The language toggle switches both.
 *
 * The user switches to this keyboard for mouse + typing, and back to
 * Bijoy/Gboard with Ctrl+Space or the keyboard icon in the navigation bar.
 */
class ArrowCursorIME : InputMethodService() {

    private lateinit var settings: CursorSettings

    // Typing language: "en" or "bn". Persisted so it survives restarts.
    private var language = "en"
    private var shiftOn = false

    // Phonetic typing state (Bengali hardware mode): the Latin letters of
    // the word being typed, and the length of the Bengali text already
    // committed for it (so each new letter can re-convert the whole word).
    private val latinBuffer = StringBuilder()
    private var committedBengaliLength = 0
    // Committing text moves the cursor, which fires onUpdateSelection.
    // Ignore selection updates shortly after our own commit, or the
    // buffer would be cleared after every letter and words could never
    // re-convert (ami -> অম instead of আমি).
    private var lastPhoneticCommitMs = 0L

    private var keyboardArea: LinearLayout? = null
    private var modeLabel: TextView? = null

    override fun onCreate() {
        super.onCreate()
        settings = CursorSettings(this)
        language = getSharedPreferences("arrow_cursor_ime", MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en") ?: "en"
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearPhoneticBuffer()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        clearPhoneticBuffer()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd,
        )
        if (SystemClock.uptimeMillis() - lastPhoneticCommitMs < 500) {
            // This selection change came from our own phonetic commit.
            return
        }
        // The cursor moved (tap / arrow in the app): the word being typed
        // phonetically is no longer under the cursor.
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            clearPhoneticBuffer()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyDown(keyCode, event)
        }
        // Never swallow system shortcuts (Ctrl+Space switches keyboards,
        // Ctrl+C/V copy/paste, ...).
        if (event.isCtrlPressed || event.isMetaPressed) {
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
                    clearPhoneticBuffer()
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (typing) {
                    clearPhoneticBuffer()
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
            KeyEvent.KEYCODE_DEL -> {
                if (language == "bn" && latinBuffer.isNotEmpty()) {
                    latinBuffer.deleteCharAt(latinBuffer.length - 1)
                    reconvertPhoneticWord()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> {
                // A letter key in Bengali mode -> phonetic typing.
                if (language == "bn" && typing) {
                    val ch = event.unicodeChar.toChar()
                    if (ch in 'a'..'z' || ch in 'A'..'Z') {
                        latinBuffer.append(ch)
                        reconvertPhoneticWord()
                        return true
                    }
                    // Punctuation / digits end the phonetic word.
                    clearPhoneticBuffer()
                }
                super.onKeyDown(keyCode, event)
            }
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        modeLabel = TextView(this).apply {
            text = getString(R.string.ime_mode_label)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
            )
        }
        val switchButton = Button(this).apply {
            text = getString(R.string.ime_switch_keyboard)
            setOnClickListener {
                val manager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                manager.showInputMethodPicker()
            }
        }
        topRow.addView(modeLabel)
        topRow.addView(switchButton)
        root.addView(topRow)
        keyboardArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(keyboardArea)
        rebuildKeyboard()
        return root
    }

    // ---------- On-screen keyboard ----------

    private data class KeyDef(
        val label: String,
        val kind: Kind,
        val value: String = label,
    ) {
        enum class Kind { CHAR, SPACE, BACKSPACE, ENTER, SHIFT, LANGUAGE }
    }

    private fun keyboardRows(): List<List<KeyDef>> {
        return if (language == "bn") {
            listOf(
                "অ আ ই ঈ উ ঊ ঋ এ ঐ ও ঔ".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                "া ি ী ু ূ ৃ ে ৈ ো ৌ ্".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                "ক খ গ ঘ ঙ চ ছ জ ঝ ঞ ট ঠ".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                "ড ঢ ণ ত থ দ ধ ন প ফ ব ভ".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                "ম য র ল শ ষ স হ \u09DC \u09DD \u09DF ৎ".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                "ং ঃ ঁ ০ ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                listOf(
                    KeyDef("EN", KeyDef.Kind.LANGUAGE),
                    KeyDef("Space", KeyDef.Kind.SPACE, " "),
                    KeyDef("⌫", KeyDef.Kind.BACKSPACE),
                    KeyDef("⏎", KeyDef.Kind.ENTER),
                ),
            )
        } else {
            val letters = { row: String ->
                row.map { ch ->
                    val shown = if (shiftOn) ch.uppercaseChar().toString() else ch.toString()
                    KeyDef(shown, KeyDef.Kind.CHAR, shown)
                }
            }
            listOf(
                "1 2 3 4 5 6 7 8 9 0".split(" ").map { KeyDef(it, KeyDef.Kind.CHAR) },
                letters("qwertyuiop"),
                letters("asdfghjkl"),
                listOf(KeyDef("⇧", KeyDef.Kind.SHIFT)) +
                    letters("zxcvbnm") +
                    listOf(KeyDef(",", KeyDef.Kind.CHAR), KeyDef(".", KeyDef.Kind.CHAR)),
                listOf(
                    KeyDef("বাংলা", KeyDef.Kind.LANGUAGE),
                    KeyDef("Space", KeyDef.Kind.SPACE, " "),
                    KeyDef("⌫", KeyDef.Kind.BACKSPACE),
                    KeyDef("⏎", KeyDef.Kind.ENTER),
                ),
            )
        }
    }

    private fun rebuildKeyboard() {
        val area = keyboardArea ?: return
        area.removeAllViews()
        val density = resources.displayMetrics.density
        val keyHeight = (52 * density).toInt()
        for (row in keyboardRows()) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (key in row) {
                val weight = when (key.kind) {
                    KeyDef.Kind.SPACE -> 4f
                    else -> 1f
                }
                val button = Button(this).apply {
                    text = key.label
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(
                        0, keyHeight, weight,
                    ).apply {
                        val m = (2 * density).toInt()
                        setMargins(m, m, m, m)
                    }
                    setOnClickListener { onKeyboardKey(key) }
                }
                rowView.addView(button)
            }
            area.addView(rowView)
        }
    }

    private fun onKeyboardKey(key: KeyDef) {
        val ic = currentInputConnection ?: return
        when (key.kind) {
            KeyDef.Kind.CHAR -> {
                clearPhoneticBuffer()
                ic.commitText(key.value, 1)
            }
            KeyDef.Kind.SPACE -> {
                clearPhoneticBuffer()
                ic.commitText(" ", 1)
            }
            KeyDef.Kind.BACKSPACE -> {
                if (language == "bn" && latinBuffer.isNotEmpty()) {
                    latinBuffer.deleteCharAt(latinBuffer.length - 1)
                    reconvertPhoneticWord()
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            KeyDef.Kind.ENTER -> {
                clearPhoneticBuffer()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            KeyDef.Kind.SHIFT -> {
                shiftOn = !shiftOn
                rebuildKeyboard()
            }
            KeyDef.Kind.LANGUAGE -> {
                language = if (language == "bn") "en" else "bn"
                shiftOn = false
                clearPhoneticBuffer()
                getSharedPreferences("arrow_cursor_ime", MODE_PRIVATE).edit()
                    .putString(KEY_LANGUAGE, language).apply()
                rebuildKeyboard()
            }
        }
    }

    // ---------- Phonetic typing ----------

    private fun reconvertPhoneticWord() {
        val ic = currentInputConnection ?: return
        val bengali = AvroPhonetic.transliterate(latinBuffer.toString())
        if (committedBengaliLength > 0) {
            ic.deleteSurroundingText(committedBengaliLength, 0)
        }
        if (bengali.isNotEmpty()) {
            ic.commitText(bengali, 1)
        }
        committedBengaliLength = bengali.length
        lastPhoneticCommitMs = SystemClock.uptimeMillis()
    }

    private fun clearPhoneticBuffer() {
        latinBuffer.clear()
        committedBengaliLength = 0
    }

    // ---------- Helpers ----------

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

    companion object {
        private const val KEY_LANGUAGE = "language"
    }
}

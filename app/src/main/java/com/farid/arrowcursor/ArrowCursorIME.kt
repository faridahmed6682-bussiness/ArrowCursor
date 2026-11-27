package com.farid.arrowcursor

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast

/**
 * ArrowCursor keyboard (an input method). A hardware keyboard's keys reach
 * an input method directly through [onKeyDown], so this is the route that
 * works when the accessibility service's [ArrowCursorService.onKeyEvent]
 * never receives the keys even though its key-filter flag is set.
 *
 * Hardware keyboard ONLY — there is no on-screen keyboard, nothing to
 * touch. The phone works exactly like a computer:
 * - Arrow keys move the pointer, like a mouse. Space/Enter click, except
 *   while a text field is focused, where they type normally.
 * - Letters type, in English or in Bengali with the Jatiya (National,
 *   BDS 1738:2018 = Bijoy) fixed layout: each key gives one Bengali
 *   character, Shift gives the second (j = ক, Shift+J = খ, g = ্ link
 *   key, f = া, ...), exactly like a computer keyboard.
 * - Alt+Shift switches the language (English / Bengali), like Windows.
 *
 * The user switches to this keyboard for mouse + typing, and back to
 * Bijoy/Gboard with Ctrl+Space or the keyboard icon in the navigation bar.
 */
class ArrowCursorIME : InputMethodService() {

    private lateinit var settings: CursorSettings

    // Typing language: "en" or "bn" (Bengali, Jatiya layout). Persisted.
    private var language = "en"

    // Jatiya typing state: a pre-kar (ি/ী/ে/ৈ) is typed BEFORE its
    // consonant, so it is held here until the next key arrives, then
    // committed AFTER the consonant (the Unicode order: কি, not িক).
    // If it followed a hasant it is ambiguous: it is a full vowel
    // (g d = ই) if no consonant follows, or the pre-kar of a conjunct
    // (শক্তি = ... g d k) if one does — resolved when the next key comes.
    private var pendingPreKar: String? = null
    private var pendingPreKarAfterHasant = false

    override fun onCreate() {
        super.onCreate()
        settings = CursorSettings(this)
        language = getSharedPreferences("arrow_cursor_ime", MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en") ?: "en"
    }

    // No on-screen keyboard: the hardware keyboard is the only input.
    // Returning null means nothing pops up over the screen when a text
    // field is focused.
    override fun onCreateInputView(): View? = null

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        pendingPreKar = null
        pendingPreKarAfterHasant = false
    }

    override fun onFinishInput() {
        super.onFinishInput()
        pendingPreKar = null
        pendingPreKarAfterHasant = false
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
        // The cursor moved (tap / arrow in the app): a held pre-kar no
        // longer belongs to the word under the cursor.
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            pendingPreKar = null
            pendingPreKarAfterHasant = false
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
        // Alt+Shift switches the language, like Windows. Either order.
        if ((keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) && event.isAltPressed ||
            (keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_RIGHT) && event.isShiftPressed ||
            keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH
        ) {
            toggleLanguage()
            return true
        }
        val speed = settings.speedPx.toFloat()
        val typing = isTextFieldFocused()

        // Bengali (Jatiya) typing from the hardware keyboard.
        if (language == "bn" && typing) {
            val jatiya = jatiyaForKey(keyCode, event.isShiftPressed)
            if (jatiya != null) {
                commitJatiya(jatiya)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (pendingPreKar != null) {
                    // Backspace cancels a held pre-kar (it was never typed).
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
            // Any other key that types a character (punctuation, ...):
            // flush a held pre-kar first, then let the key type normally.
            // Mouse keys are excluded here; the when below handles them.
            val mouseKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_SPACE ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN
            val ch = event.unicodeChar
            if (!mouseKey && ch != 0 && !Character.isISOControl(ch)) {
                flushPendingPreKar()
                return super.onKeyDown(keyCode, event)
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                sendCommand("move", dy = -speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                sendCommand("move", dy = speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                sendCommand("move", dx = -speed)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                sendCommand("move", dx = speed)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (typing) {
                    flushPendingPreKar()
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (typing) {
                    flushPendingPreKar()
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                sendCommand("scroll", direction = 1f)
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
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

    private fun toggleLanguage() {
        language = if (language == "bn") "en" else "bn"
        pendingPreKar = null
        pendingPreKarAfterHasant = false
        getSharedPreferences("arrow_cursor_ime", MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language).apply()
        val msg = if (language == "bn") "বাংলা (জাতীয়)" else "English"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------- Jatiya (National) layout ----------

    /**
     * The Jatiya character for a hardware key, or null if the key is not
     * on the Jatiya map (arrows, Space, punctuation, ...).
     * (ড়/ঢ়/য় are precomposed escapes: they decompose if typed literally
     * in source, which breaks backspace counts.)
     */
    private fun jatiyaForKey(keyCode: Int, shift: Boolean): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> if (shift) "ং" else "ঙ"
            KeyEvent.KEYCODE_W -> if (shift) "\u09DF" else "য"
            KeyEvent.KEYCODE_E -> if (shift) "ঢ" else "ড"
            KeyEvent.KEYCODE_R -> if (shift) "ফ" else "প"
            KeyEvent.KEYCODE_T -> if (shift) "ঠ" else "ট"
            KeyEvent.KEYCODE_Y -> if (shift) "ছ" else "চ"
            KeyEvent.KEYCODE_U -> if (shift) "ঝ" else "জ"
            KeyEvent.KEYCODE_I -> if (shift) "ঞ" else "হ"
            KeyEvent.KEYCODE_O -> if (shift) "ঘ" else "গ"
            KeyEvent.KEYCODE_P -> if (shift) "\u09DD" else "\u09DC"
            KeyEvent.KEYCODE_A -> if (shift) "র্" else "ৃ"
            KeyEvent.KEYCODE_S -> if (shift) "ূ" else "ু"
            KeyEvent.KEYCODE_D -> if (shift) "ী" else "ি"
            KeyEvent.KEYCODE_F -> if (shift) "অ" else "া"
            KeyEvent.KEYCODE_G -> if (shift) "।" else HASANT
            KeyEvent.KEYCODE_H -> if (shift) "ভ" else "ব"
            KeyEvent.KEYCODE_J -> if (shift) "খ" else "ক"
            KeyEvent.KEYCODE_K -> if (shift) "থ" else "ত"
            KeyEvent.KEYCODE_L -> if (shift) "ধ" else "দ"
            KeyEvent.KEYCODE_Z -> if (shift) "্য" else "্র"
            KeyEvent.KEYCODE_X -> if (shift) "ৗ" else "ও"
            KeyEvent.KEYCODE_C -> if (shift) "ৈ" else "ে"
            KeyEvent.KEYCODE_V -> if (shift) "ল" else "র"
            KeyEvent.KEYCODE_B -> if (shift) "ণ" else "ন"
            KeyEvent.KEYCODE_N -> if (shift) "ষ" else "স"
            KeyEvent.KEYCODE_M -> if (shift) "শ" else "ম"
            KeyEvent.KEYCODE_BACKSLASH -> if (shift) "ঃ" else "ৎ"
            KeyEvent.KEYCODE_1 -> "১"
            KeyEvent.KEYCODE_2 -> "২"
            KeyEvent.KEYCODE_3 -> "৩"
            KeyEvent.KEYCODE_4 -> "৪"
            KeyEvent.KEYCODE_5 -> "৫"
            KeyEvent.KEYCODE_6 -> "৬"
            KeyEvent.KEYCODE_7 -> if (shift) "ঁ" else "৭"
            KeyEvent.KEYCODE_8 -> "৮"
            KeyEvent.KEYCODE_9 -> "৯"
            KeyEvent.KEYCODE_0 -> "০"
            else -> null
        }
    }

    /**
     * Commits one Jatiya character (or string: "্র", "্য", "র্"), applying
     * the Jatiya typing rules so the result is correct Unicode:
     * - ি/ী/ে/ৈ are typed before their consonant: held, then committed
     *   AFTER the consonant (কি = ক + ি, কে = ক + ে).
     * - A pre-kar after a hasant is ambiguous (g d = ই, but শক্তি needs
     *   ... g d k): it is held, and resolved when the next key comes —
     *   a consonant takes it as its pre-kar, anything else makes it the
     *   full vowel (see [flushPendingPreKar]).
     * - A post-kar (া/ু/ূ/ৃ) after a hasant is a full vowel (g f = আ).
     * - ে + া = ো, ে + ৗ = ৌ (the way কো/কৌ are typed in Bijoy).
     * - hasant + consonant = conjunct (j g k = ক্ত).
     */
    private fun commitJatiya(value: String) {
        val ic = currentInputConnection ?: return
        if (value in PRE_KARS) {
            // Hold the pre-kar for its consonant; resolve any previously
            // held one first.
            flushPendingPreKar()
            pendingPreKar = value
            pendingPreKarAfterHasant =
                ic.getTextBeforeCursor(1, 0)?.toString() == HASANT
            return
        }
        val isConsonant = value in JATIYA_CONSONANTS
        if (!isConsonant) {
            // Not a consonant: a held pre-kar cannot attach to it, so
            // flush it (resolving hasant+kar to a vowel if it applies).
            flushPendingPreKar()
        }
        val prev = ic.getTextBeforeCursor(1, 0)?.toString()
        when {
            value == "া" && prev == "ে" -> {
                ic.deleteSurroundingText(1, 0)
                ic.commitText("ো", 1)
            }
            value == "ৗ" && prev == "ে" -> {
                ic.deleteSurroundingText(1, 0)
                ic.commitText("ৌ", 1)
            }
            value == "ৗ" && prev == HASANT -> {
                ic.deleteSurroundingText(1, 0)
                ic.commitText("ঔ", 1)
            }
            value in POST_KARS && prev == HASANT -> {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(KAR_TO_VOWEL[value]!!, 1)
            }
            else -> {
                ic.commitText(value, 1)
            }
        }
        if (isConsonant) {
            // The held pre-kar goes after its consonant.
            pendingPreKar?.let { ic.commitText(it, 1) }
            pendingPreKar = null
            pendingPreKarAfterHasant = false
        }
    }

    /**
     * Commits a held pre-kar. If it followed a hasant and no consonant
     * came, it was a full vowel (g d, then Space = ই): the hasant is
     * replaced by that vowel. Otherwise it is committed as-is.
     */
    private fun flushPendingPreKar() {
        val held = pendingPreKar ?: return
        val wasAfterHasant = pendingPreKarAfterHasant
        pendingPreKar = null
        pendingPreKarAfterHasant = false
        val ic = currentInputConnection ?: return
        if (wasAfterHasant) {
            val prev = ic.getTextBeforeCursor(1, 0)?.toString()
            if (prev == HASANT) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(KAR_TO_VOWEL[held]!!, 1)
                return
            }
        }
        ic.commitText(held, 1)
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
        private const val HASANT = "্"

        // Pre-kars: typed BEFORE their consonant, held until the next key.
        private val PRE_KARS = setOf("ি", "ী", "ে", "ৈ")
        // Post-kars: typed AFTER their consonant.
        private val POST_KARS = setOf("া", "ু", "ূ", "ৃ")
        // Kar -> full vowel, when it follows a hasant (g + f = আ).
        private val KAR_TO_VOWEL = mapOf(
            "া" to "আ", "ি" to "ই", "ী" to "ঈ",
            "ু" to "উ", "ূ" to "ঊ", "ৃ" to "ঋ",
            "ে" to "এ", "ৈ" to "ঐ",
        )
        // Jatiya consonant values (normal + shifted). A held pre-kar is
        // committed AFTER one of these; after anything else it is flushed
        // first (it has no consonant to attach to).
        private val JATIYA_CONSONANTS = setOf(
            "ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "\u09DC",
            "ব", "ক", "ত", "দ", "র", "ন", "স", "ম",
            "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "\u09DD",
            "ভ", "খ", "থ", "ধ", "ল", "ণ", "ষ", "শ",
            "\u09DF", "্র", "্য", "র্",
        )
    }
}

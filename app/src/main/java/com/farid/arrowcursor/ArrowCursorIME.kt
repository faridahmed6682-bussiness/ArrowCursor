package com.farid.arrowcursor

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
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
 *   HOLD Space (half a second) = LONG-PRESS at the pointer: it selects
 *   text on a page, opens a context menu, or starts a drag — the plain
 *   tap cannot do these, which is why "page select" did not work.
 *   Enter always clicks immediately (no long-press).
 * - Shift+arrow and Ctrl+arrow do what Settings says (defaults:
 *   Shift = fine step, a tiny move for small buttons; Ctrl = scroll
 *   in that direction, Up/Down = up/down, Left/Right = sideways).
 *   In a text field Shift+arrow selects text and Ctrl+arrow jumps by
 *   word, as on a computer.
 * - Letters type, in English or in Bengali with the Jatiya (National,
 *   BDS 1738:2018 = Bijoy) fixed layout: each key gives one Bengali
 *   character, Shift gives the second (j = ক, Shift+J = খ, g = ্ link
 *   key, f = া, ...), exactly like a computer keyboard.
 * - Alt+Shift switches the language (English / Bengali), like Windows.
 *   Ctrl+B does the same, so the language can be switched with one
 *   hand while typing (user, 2026-11-28).
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

    // Hold-Space long-press: a quick Space tap clicks, holding Space
    // past LONG_PRESS_TIMEOUT_MS long-presses instead (selects text on
    // a page, opens a context menu, starts a drag). The click is sent
    // on key-UP for a short press, so a hold is never also a click.
    private val keyHandler = Handler(Looper.getMainLooper())
    private var spaceLongPressFired = false
    private val spaceLongPressRunnable = Runnable {
        spaceLongPressFired = true
        sendCommand("longPress")
    }

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
        // Safety: if Space was held when the input session ended, its
        // key-up may never arrive — drop the pending long-press so it
        // cannot fire later, and stop any smooth glide.
        keyHandler.removeCallbacks(spaceLongPressRunnable)
        spaceLongPressFired = false
        sendCommand("moveStop", dx = 1f, dy = 1f)
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
        // Ctrl+B toggles the typing language (Bengali / English), so
        // it can be switched with one hand while typing (user,
        // 2026-11-28). Checked BEFORE the Ctrl pass-through below, so
        // it works even while a text field is focused.
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_B) {
            toggleLanguage()
            return true
        }
        val speed = settings.speedPx.toFloat()
        val typing = isTextFieldFocused()
        // Step sizes: plain arrow = normal step, Shift+arrow = fine
        // step (for small buttons) by default. What Shift+arrow and
        // Ctrl+arrow DO comes from Settings (defaults: Shift = fine,
        // Ctrl = scroll) — the user changes it in Settings, and the
        // DPAD / numpad branches below call doArrowAction with it.
        val step = speed
        val fineStep = maxOf(2f, speed / 6f)
        val normalTick = 7f
        val fineTick = 2f
        // What an arrow key does, from Settings. "scroll" = scroll in
        // that direction (vertical for Up/Down, sideways for Left/Right).
        // "fine" = tiny move (+ fine glide). "move" = normal move (+
        // normal glide). When glide is OFF in Settings, only the
        // immediate move is sent (on every key-down, repeats included)
        // and no glide starts — so a held key still moves the pointer,
        // one step per repeat, instead of gliding.
        fun doArrowAction(action: String, dirX: Float, dirY: Float, repeatCount: Int) {
            when (action) {
                "scroll" -> {
                    if (dirY != 0f) sendCommand("scroll", direction = -dirY)
                    else sendCommand("hscroll", direction = -dirX)
                }
                "fine" -> {
                    if (settings.glideEnabled) {
                        if (repeatCount == 0) {
                            sendCommand("move", dx = dirX * fineStep, dy = dirY * fineStep)
                        }
                        sendCommand("moveStart", dx = dirX * fineTick, dy = dirY * fineTick)
                    } else {
                        sendCommand("move", dx = dirX * fineStep, dy = dirY * fineStep)
                    }
                }
                else -> { // "move"
                    if (settings.glideEnabled) {
                        if (repeatCount == 0) {
                            sendCommand("move", dx = dirX * step, dy = dirY * step)
                        }
                        sendCommand("moveStart", dx = dirX * normalTick, dy = dirY * normalTick)
                    } else {
                        sendCommand("move", dx = dirX * step, dy = dirY * step)
                    }
                }
            }
        }
        // Which modifier action applies to this arrow press (Settings;
        // plain arrow is always "move"). Only used when NOT typing — in
        // a text field Shift+arrow selects text and Ctrl+arrow jumps by
        // word, as on a computer (handled by the pass-through above and
        // the Shift pre-check below).
        fun arrowAction(event: KeyEvent): String = when {
            event.isShiftPressed -> settings.shiftArrowAction
            event.isCtrlPressed -> settings.ctrlArrowAction
            else -> "move"
        }
        // Ctrl+arrow (and Ctrl+numpad-arrow) while NOT typing is a
        // pointer action from Settings (default: scroll), not a system
        // shortcut — let it fall through to the when below. In a text
        // field Ctrl+arrow still passes through (word jump / selection),
        // as before.
        val isCtrlArrow = event.isCtrlPressed && !typing && (
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_8 ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_2 ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_4 ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_6
            )
        // Never swallow system shortcuts (Ctrl+Space switches keyboards,
        // Ctrl+C/V copy/paste, ...).
        if ((event.isCtrlPressed || event.isMetaPressed) && !isCtrlArrow) {
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
        // Diagnostic: report Space/Enter receipt to the Home screen, so
        // it is visible whether the key reaches this keyboard at all
        // (Space-click in v1.0.11 did not work, and the cause could be
        // the key never arriving here, not the click itself).
        if (keyCode == KeyEvent.KEYCODE_SPACE) sendKeyTest("SPACE")
        if (keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        ) {
            sendKeyTest("ENTER")
        }
        // Same diagnostic for the scroll keys: PgUp/PgDn/Home/End did not
        // scroll on the user's phone in v1.0.14, and this shows whether
        // the key reaches the keyboard at all.
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) sendKeyTest("PGUP")
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) sendKeyTest("PGDN")
        if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) sendKeyTest("HOME")
        if (keyCode == KeyEvent.KEYCODE_MOVE_END) sendKeyTest("END")
        // Shift+arrow in a text field selects text (computer-like); it
        // does not scroll or move the pointer. Same for Shift+PgUp/PgDn
        // (selects to the top/bottom of the visible text).
        if (event.isShiftPressed && typing) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

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
                // Up: what it does (fine / scroll / move) comes from
                // Settings via arrowAction; while held the service
                // glides the pointer (moveStart) instead of jumping on
                // every key repeat — unless glide is off in Settings.
                doArrowAction(arrowAction(event), 0f, -1f, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                doArrowAction(arrowAction(event), 0f, 1f, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                doArrowAction(arrowAction(event), -1f, 0f, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                doArrowAction(arrowAction(event), 1f, 0f, event.repeatCount)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (typing) {
                    flushPendingPreKar()
                    super.onKeyDown(keyCode, event)
                } else if (event.repeatCount == 0) {
                    // Do NOT click yet: a quick tap clicks on key-UP, a
                    // hold past 500 ms long-presses instead. Scheduling
                    // only on the first down (repeats while held do
                    // nothing — the long-press is already scheduled).
                    spaceLongPressFired = false
                    keyHandler.postDelayed(
                        spaceLongPressRunnable,
                        LONG_PRESS_TIMEOUT_MS,
                    )
                    true
                } else {
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
                // Shift+PgUp = scroll sideways (left), like Shift+wheel
                // on a computer. Plain PgUp = scroll up.
                if (event.isShiftPressed) {
                    sendCommand("hscroll", direction = 1f)
                } else {
                    sendCommand("scroll", direction = 1f)
                }
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                pendingPreKar = null
                pendingPreKarAfterHasant = false
                // Shift+PgDn = scroll sideways (right). Plain PgDn =
                // scroll down.
                if (event.isShiftPressed) {
                    sendCommand("hscroll", direction = -1f)
                } else {
                    sendCommand("scroll", direction = -1f)
                }
                true
            }
            // Home / End: in a text field they move the text cursor
            // (computer-like); anywhere else they scroll to the very
            // top / bottom of the page (a long swipe).
            KeyEvent.KEYCODE_MOVE_HOME -> {
                if (typing) {
                    super.onKeyDown(keyCode, event)
                } else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    sendCommand("scrollTop")
                    true
                }
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                if (typing) {
                    super.onKeyDown(keyCode, event)
                } else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    sendCommand("scrollBottom")
                    true
                }
            }
            // Numpad as a second mouse (NumLock off): 8/2/4/6 move the
            // pointer, 5 clicks, 7/1 jump to top/bottom, 9/3 page up/down.
            // In a text field they are left alone, so numpad digits
            // still type normally.
            KeyEvent.KEYCODE_NUMPAD_8 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    doArrowAction(arrowAction(event), 0f, -1f, event.repeatCount)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_2 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    doArrowAction(arrowAction(event), 0f, 1f, event.repeatCount)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_4 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    doArrowAction(arrowAction(event), -1f, 0f, event.repeatCount)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_6 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    doArrowAction(arrowAction(event), 1f, 0f, event.repeatCount)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_5 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_7 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    sendCommand("scrollTop")
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_1 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    sendCommand("scrollBottom")
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    if (event.isShiftPressed) {
                        sendCommand("hscroll", direction = 1f)
                    } else {
                        sendCommand("scroll", direction = 1f)
                    }
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_3 -> {
                if (typing || !settings.numpadMouse) super.onKeyDown(keyCode, event)
                else {
                    pendingPreKar = null
                    pendingPreKarAfterHasant = false
                    if (event.isShiftPressed) {
                        sendCommand("hscroll", direction = -1f)
                    } else {
                        sendCommand("scroll", direction = -1f)
                    }
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (typing || !settings.numpadMouse) {
                    flushPendingPreKar()
                    super.onKeyDown(keyCode, event)
                } else {
                    sendCommand("click")
                    true
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            // Releasing an arrow stops the smooth glide on that axis.
            KeyEvent.KEYCODE_DPAD_UP -> {
                sendCommand("moveStop", dy = -1f)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                sendCommand("moveStop", dy = 1f)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                sendCommand("moveStop", dx = -1f)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                sendCommand("moveStop", dx = 1f)
                true
            }
            KeyEvent.KEYCODE_NUMPAD_8 -> {
                if (isTextFieldFocused() || !settings.numpadMouse) super.onKeyUp(keyCode, event)
                else {
                    sendCommand("moveStop", dy = -1f)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_2 -> {
                if (isTextFieldFocused() || !settings.numpadMouse) super.onKeyUp(keyCode, event)
                else {
                    sendCommand("moveStop", dy = 1f)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_4 -> {
                if (isTextFieldFocused() || !settings.numpadMouse) super.onKeyUp(keyCode, event)
                else {
                    sendCommand("moveStop", dx = -1f)
                    true
                }
            }
            KeyEvent.KEYCODE_NUMPAD_6 -> {
                if (isTextFieldFocused() || !settings.numpadMouse) super.onKeyUp(keyCode, event)
                else {
                    sendCommand("moveStop", dx = 1f)
                    true
                }
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> true
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> {
                // In a text field these keys belong to the app (text
                // cursor); anywhere else this keyboard owns them.
                if (isTextFieldFocused()) super.onKeyUp(keyCode, event) else true
            }
            KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_NUMPAD_9,
            KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // In a text field these keys belong to the app (digits,
                // text cursor); anywhere else this keyboard owns them —
                // unless the numpad mouse is off in Settings, in which
                // case they pass through to the app as well.
                if (isTextFieldFocused() || !settings.numpadMouse) super.onKeyUp(keyCode, event) else true
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (isTextFieldFocused()) {
                    super.onKeyUp(keyCode, event)
                } else {
                    // Short tap = click (the long-press, if it fired,
                    // already did its work — do not also click).
                    keyHandler.removeCallbacks(spaceLongPressRunnable)
                    if (!spaceLongPressFired) sendCommand("click")
                    true
                }
            }
            KeyEvent.KEYCODE_ENTER,
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

    private fun sendKeyTest(name: String) {
        sendBroadcast(
            android.content.Intent(ArrowCursorService.ACTION_KEY_TEST)
                .putExtra(ArrowCursorService.EXTRA_KEY_NAME, name),
        )
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
            // অ + া = আ (the independent vowel আ). Without this, typing
            // অ then া left a detached া after the অ instead of
            // combining into আ (user, 2026-11-28, Messenger screenshot).
            value == "া" && prev == "অ" -> {
                ic.deleteSurroundingText(1, 0)
                ic.commitText("আ", 1)
            }
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
        // Hold-Space long-press timeout: a Space held past this long
        // long-presses instead of clicking (see the fields above).
        private const val LONG_PRESS_TIMEOUT_MS = 500L
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

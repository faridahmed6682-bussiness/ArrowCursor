package com.farid.arrowcursor

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Key Map: what every keyboard key does. The rows are built from the
 * current Settings, so if the user changes what Shift+arrow or
 * Ctrl+arrow does in Settings, this page shows the new mapping.
 * (User, 2026-11-28: "কি ম্যাপ বাটনে কি ম্যাপ রাখো" — put the key
 * map on the Key Map button.)
 */
class KeyMapFragment : Fragment(R.layout.fragment_keymap) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settings = CursorSettings(requireContext())
        val container: LinearLayout = view.findViewById(R.id.keymap_container)

        // The action text for a Shift / Ctrl arrow, from Settings.
        fun modifierAction(action: String): String = when (action) {
            "scroll" -> getString(R.string.keymap_scroll)
            "fine" -> getString(R.string.keymap_fine_step)
            else -> getString(R.string.keymap_move_pointer)
        }

        // (key label, what it does). Key labels are key names, so they
        // are the same in every language; the action is localized.
        val rows = listOf(
            "↑ ↓ ← →" to getString(R.string.keymap_move_pointer),
            "Shift + ↑ ↓ ← →" to modifierAction(settings.shiftArrowAction),
            "Ctrl + ↑ ↓ ← →" to modifierAction(settings.ctrlArrowAction),
            "Space (tap)" to getString(R.string.keymap_click),
            "Space (hold 0.5 s)" to getString(R.string.keymap_long_press),
            "Enter" to getString(R.string.keymap_click),
            "PgUp / PgDn" to getString(R.string.keymap_scroll_up_down),
            "Shift + PgUp / PgDn" to getString(R.string.keymap_scroll_sideways),
            "Home / End" to getString(R.string.keymap_scroll_top_bottom) +
                " • " + getString(R.string.keymap_text_cursor),
            "Numpad 8 / 2 / 4 / 6" to getString(R.string.keymap_numpad_move),
            "Numpad 5" to getString(R.string.keymap_click),
            "Numpad 7 / 1" to getString(R.string.keymap_scroll_top_bottom),
            "Numpad 9 / 3" to getString(R.string.keymap_scroll_up_down) +
                " • Shift: " + getString(R.string.keymap_scroll_sideways),
            "Numpad Enter" to getString(R.string.keymap_click),
            "Alt + Shift" to getString(R.string.keymap_switch_language),
            "Ctrl + B" to getString(R.string.keymap_switch_language),
            "Ctrl + C / V" to getString(R.string.keymap_copy_paste),
            "A–Z, 0–9" to getString(R.string.keymap_type),
        )

        for ((key, action) in rows) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }
            val keyView = TextView(requireContext()).apply {
                text = key
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            }
            val actionView = TextView(requireContext()).apply {
                text = action
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
            }
            row.addView(keyView)
            row.addView(actionView)
            container.addView(row)
        }
    }
}

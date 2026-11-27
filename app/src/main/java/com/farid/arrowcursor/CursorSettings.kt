package com.farid.arrowcursor

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class CursorSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speedPx: Int
        get() = prefs.getInt(KEY_SPEED, 24)
        set(value) = prefs.edit().putInt(KEY_SPEED, value).apply()

    var sizeDp: Int
        get() = prefs.getInt(KEY_SIZE, 36)
        set(value) = prefs.edit().putInt(KEY_SIZE, value).apply()

    var cursorColor: Int
        get() = prefs.getInt(KEY_COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(KEY_COLOR, value).apply()

    var clickKey: String
        get() = prefs.getString(KEY_CLICK_KEY, "both") ?: "both"
        set(value) = prefs.edit().putString(KEY_CLICK_KEY, value).apply()

    var autoPause: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PAUSE, value).apply()

    var showControlPanel: Boolean
        get() = prefs.getBoolean(KEY_CONTROL_PANEL, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTROL_PANEL, value).apply()

    var cursorEnabled: Boolean
        get() = prefs.getBoolean(KEY_CURSOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CURSOR_ENABLED, value).apply()

    // Magnetic pointer: when the pointer stops moving, settle it onto
    // the centre of the nearest clickable button (within 80 px), so
    // small buttons are easy to hit. Off by default? No — on, because
    // the user asked for it; it can be switched off in Settings.
    var snapToButton: Boolean
        get() = prefs.getBoolean(KEY_SNAP_TO_BUTTON, true)
        set(value) = prefs.edit().putBoolean(KEY_SNAP_TO_BUTTON, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    // What Shift+arrow does: "fine" = tiny step (for small buttons),
    // "scroll" = scroll in that direction, "move" = normal step.
    // Default "fine" — the setting the user was given in v1.0.16/22.
    var shiftArrowAction: String
        get() = prefs.getString(KEY_SHIFT_ARROW, "fine") ?: "fine"
        set(value) = prefs.edit().putString(KEY_SHIFT_ARROW, value).apply()

    // What Ctrl+arrow does (when not typing): "scroll" = scroll in
    // that direction, "fine" = tiny step, "move" = normal step.
    // Default "scroll" — the setting the user was given in v1.0.22.
    var ctrlArrowAction: String
        get() = prefs.getString(KEY_CTRL_ARROW, "scroll") ?: "scroll"
        set(value) = prefs.edit().putString(KEY_CTRL_ARROW, value).apply()

    // Magnetic snap: how long the pointer must sit still near a
    // button before it settles onto it (ms). Default 1000 (1 s).
    var snapDelayMs: Int
        get() = prefs.getInt(KEY_SNAP_DELAY_MS, 1000)
        set(value) = prefs.edit().putInt(KEY_SNAP_DELAY_MS, value).apply()

    // Magnetic snap: how close (px) is "near" a button. Default 80.
    var snapRadiusPx: Int
        get() = prefs.getInt(KEY_SNAP_RADIUS_PX, 80)
        set(value) = prefs.edit().putInt(KEY_SNAP_RADIUS_PX, value).apply()

    // Smooth pointer glide: while an arrow key is held, glide
    // continuously and accelerate (Windows Mouse Keys style).
    // Off = one step per key press / repeat, no glide. Default on.
    var glideEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_GLIDE, value).apply()

    // Numpad as a second mouse (8/2/4/6 move, 5 clicks, 7/1 top/bottom,
    // 9/3 page up/down). Off = numpad keys always type digits.
    // Default on.
    var numpadMouse: Boolean
        get() = prefs.getBoolean(KEY_NUMPAD_MOUSE, true)
        set(value) = prefs.edit().putBoolean(KEY_NUMPAD_MOUSE, value).apply()

    // Restore every setting to the defaults above (the settings the
    // user was given). The cursor on/off state is left alone.
    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_SPEED, 24)
            .putInt(KEY_SIZE, 36)
            .putInt(KEY_COLOR, Color.WHITE)
            .putString(KEY_CLICK_KEY, "both")
            .putBoolean(KEY_AUTO_PAUSE, true)
            .putBoolean(KEY_CONTROL_PANEL, false)
            .putBoolean(KEY_SNAP_TO_BUTTON, true)
            .putString(KEY_LANGUAGE, "system")
            .putString(KEY_SHIFT_ARROW, "fine")
            .putString(KEY_CTRL_ARROW, "scroll")
            .putInt(KEY_SNAP_DELAY_MS, 1000)
            .putInt(KEY_SNAP_RADIUS_PX, 80)
            .putBoolean(KEY_GLIDE, true)
            .putBoolean(KEY_NUMPAD_MOUSE, true)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "arrow_cursor_prefs"
        const val KEY_SPEED = "cursor_speed_px"
        const val KEY_SIZE = "cursor_size_dp"
        const val KEY_COLOR = "cursor_color"
        const val KEY_CLICK_KEY = "click_key"
        const val KEY_AUTO_PAUSE = "auto_pause"
        const val KEY_CONTROL_PANEL = "show_control_panel"
        const val KEY_CURSOR_ENABLED = "cursor_enabled"
        const val KEY_SNAP_TO_BUTTON = "snap_to_button"
        const val KEY_LANGUAGE = "language"
        const val KEY_SHIFT_ARROW = "shift_arrow_action"
        const val KEY_CTRL_ARROW = "ctrl_arrow_action"
        const val KEY_SNAP_DELAY_MS = "snap_delay_ms"
        const val KEY_SNAP_RADIUS_PX = "snap_radius_px"
        const val KEY_GLIDE = "glide_enabled"
        const val KEY_NUMPAD_MOUSE = "numpad_mouse"
    }
}

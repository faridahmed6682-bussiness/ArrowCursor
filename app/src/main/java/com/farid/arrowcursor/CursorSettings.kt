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

    var cursorEnabled: Boolean
        get() = prefs.getBoolean(KEY_CURSOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CURSOR_ENABLED, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

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
        const val KEY_CURSOR_ENABLED = "cursor_enabled"
        const val KEY_LANGUAGE = "language"
    }
}

package com.farid.arrowcursor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CursorToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.farid.arrowcursor.TOGGLE_CURSOR") {
            val settings = CursorSettings(context)
            settings.cursorEnabled = !settings.cursorEnabled
        }
    }
}

package com.farid.arrowcursor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var textStatus: TextView
    private lateinit var buttonToggle: Button
    private lateinit var textLastKey: TextView

    private val keyTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val keyName = intent?.getStringExtra(ArrowCursorService.EXTRA_KEY_NAME) ?: return
            if (::textLastKey.isInitialized) {
                textLastKey.text = getString(R.string.last_key_prefix) + " " + keyName
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textStatus = view.findViewById(R.id.text_status)
        buttonToggle = view.findViewById(R.id.button_toggle_cursor)
        val buttonOpenAccessibility: Button = view.findViewById(R.id.button_open_accessibility)
        val textChecklist: TextView = view.findViewById(R.id.text_checklist)
        val textTestArea: TextView = view.findViewById(R.id.text_test_area)
        textLastKey = view.findViewById(R.id.text_last_key)

        textChecklist.text = getString(R.string.checklist_step1) + "\n" +
            getString(R.string.checklist_step2) + "\n" +
            getString(R.string.checklist_step3)
        textTestArea.text = getString(R.string.test_area_hint)

        buttonOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        buttonToggle.setOnClickListener {
            if (!isServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toggle_toast_service_not_enabled),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val settings = CursorSettings(requireContext())
                settings.cursorEnabled = !settings.cursorEnabled
                refreshStatus()
            }
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // Android 13+ requires an explicit exported flag for non-system broadcasts;
        // the 2-arg registerReceiver() throws SecurityException on launch otherwise.
        ContextCompat.registerReceiver(
            requireContext(),
            keyTestReceiver,
            IntentFilter(ArrowCursorService.ACTION_KEY_TEST),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(keyTestReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered; nothing to unregister.
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = ComponentName(requireContext(), ArrowCursorService::class.java)
            .flattenToString()
        return enabledServices?.contains(serviceName) == true
    }

    private fun refreshStatus() {
        if (!::textStatus.isInitialized || !::buttonToggle.isInitialized) {
            return
        }
        val serviceOn = isServiceEnabled()
        val settings = CursorSettings(requireContext())
        textStatus.text = if (!serviceOn) {
            getString(R.string.status_service_off)
        } else if (settings.cursorEnabled) {
            getString(R.string.status_cursor_on)
        } else {
            getString(R.string.status_service_on)
        }
        buttonToggle.text = if (settings.cursorEnabled) {
            getString(R.string.button_disable_cursor)
        } else {
            getString(R.string.button_enable_cursor)
        }
    }
}

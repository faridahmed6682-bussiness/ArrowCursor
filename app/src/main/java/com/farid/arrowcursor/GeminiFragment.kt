package com.farid.arrowcursor

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GeminiFragment : Fragment(R.layout.fragment_gemini), GeminiAssistant.Tools {

    private lateinit var assistant: GeminiAssistant
    private val history = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: ChatAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        assistant = GeminiAssistant(requireContext(), this)
        adapter = ChatAdapter()

        val recyclerChat: RecyclerView = view.findViewById(R.id.recycler_chat)
        recyclerChat.adapter = adapter
        recyclerChat.layoutManager = LinearLayoutManager(requireContext())

        val editApiKey: EditText = view.findViewById(R.id.edit_api_key)
        val buttonSaveKey: Button = view.findViewById(R.id.button_save_key)
        val textKeyStatus: TextView = view.findViewById(R.id.text_key_status)
        val editMessage: EditText = view.findViewById(R.id.edit_message)
        val buttonSend: Button = view.findViewById(R.id.button_send)

        textKeyStatus.text = if (KeyStore.hasApiKey(requireContext())) {
            getString(R.string.key_saved)
        } else {
            getString(R.string.key_not_saved)
        }

        if (adapter.itemCount == 0) {
            adapter.addMessage(ChatAdapter.Message(getString(R.string.gemini_welcome), false))
        }

        buttonSaveKey.setOnClickListener {
            val k = editApiKey.text.toString().trim()
            if (k.isBlank()) {
                textKeyStatus.text = getString(R.string.key_empty_error)
            } else {
                try {
                    KeyStore.saveApiKey(requireContext(), k)
                    editApiKey.setText("")
                    textKeyStatus.text = getString(R.string.key_saved)
                } catch (e: Exception) {
                    textKeyStatus.text = getString(R.string.gemini_error_generic)
                }
            }
        }

        buttonSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isBlank()) {
                return@setOnClickListener
            }
            adapter.addMessage(ChatAdapter.Message(text, true))
            editMessage.setText("")
            recyclerChat.scrollToPosition(adapter.itemCount - 1)
            adapter.addMessage(
                ChatAdapter.Message(getString(R.string.gemini_thinking), false)
            )
            recyclerChat.scrollToPosition(adapter.itemCount - 1)
            assistant.sendMessage(text, history.toList(), object : GeminiAssistant.Callback {
                override fun onResult(result: String) {
                    adapter.removeLastMessage()
                    adapter.addMessage(ChatAdapter.Message(result, false))
                    recyclerChat.scrollToPosition(adapter.itemCount - 1)
                    history.add(text to result)
                }

                override fun onError(message: String) {
                    adapter.removeLastMessage()
                    adapter.addMessage(ChatAdapter.Message(message, false))
                    recyclerChat.scrollToPosition(adapter.itemCount - 1)
                }
            })
        }
    }

    // GeminiAssistant.Tools

    override fun setCursorEnabled(enabled: Boolean): String {
        CursorSettings(requireContext()).cursorEnabled = enabled
        return getString(R.string.gemini_tool_done)
    }

    override fun setCursorSpeed(px: Int): String {
        val v = px.coerceIn(8, 64)
        CursorSettings(requireContext()).speedPx = v
        return getString(R.string.gemini_tool_done)
    }

    override fun setClickKey(key: String): String {
        if (key in listOf("space", "enter", "both")) {
            CursorSettings(requireContext()).clickKey = key
        }
        return getString(R.string.gemini_tool_done)
    }

    override fun setAutoPause(enabled: Boolean): String {
        CursorSettings(requireContext()).autoPause = enabled
        return getString(R.string.gemini_tool_done)
    }

    override fun openAccessibilitySettings(): String {
        Handler(Looper.getMainLooper()).post {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return getString(R.string.gemini_tool_done)
    }

    override fun getStatus(): String {
        val s = CursorSettings(requireContext())
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = ComponentName(requireContext(), ArrowCursorService::class.java)
            .flattenToString()
        val svcOn = enabledServices?.contains(serviceName) == true
        return "service=$svcOn, cursor=${s.cursorEnabled}, speed=${s.speedPx}px, clickKey=${s.clickKey}, autoPause=${s.autoPause}"
    }
}

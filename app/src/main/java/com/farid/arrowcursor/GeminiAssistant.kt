package com.farid.arrowcursor

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiAssistant(private val context: Context, private val tools: Tools) {

    interface Tools {
        fun setCursorEnabled(enabled: Boolean): String
        fun setCursorSpeed(px: Int): String
        fun setClickKey(key: String): String
        fun setAutoPause(enabled: Boolean): String
        fun openAccessibilitySettings(): String
        fun getStatus(): String
    }

    interface Callback {
        fun onResult(text: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendMessage(userText: String, history: List<Pair<String, String>>, callback: Callback) {
        Thread {
            try {
                val apiKey = KeyStore.getApiKey(context)
                if (apiKey.isNullOrBlank()) {
                    mainHandler.post {
                        callback.onError(context.getString(R.string.gemini_error_no_key))
                    }
                    return@Thread
                }

                val contents = ArrayList<Map<String, Any?>>()
                for ((userTurn, modelTurn) in history) {
                    contents.add(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(mapOf("text" to userTurn))
                        )
                    )
                    contents.add(
                        mapOf(
                            "role" to "model",
                            "parts" to listOf(mapOf("text" to modelTurn))
                        )
                    )
                }
                contents.add(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to userText))
                    )
                )

                val requestBody = buildRequestBody(contents)
                val urlString =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" +
                        URLEncoder.encode(apiKey, "UTF-8")
                val (code, responseText) = httpPost(urlString, requestBody)
                if (code != 200) {
                    mainHandler.post {
                        callback.onError(
                            context.getString(R.string.gemini_error_network) + " (HTTP " + code + ")"
                        )
                    }
                    return@Thread
                }

                val parsed = parseResponse(responseText)
                val firstText = parsed.first
                val functionCalls = parsed.second

                if (functionCalls.isEmpty()) {
                    val resultText = firstText
                    mainHandler.post { callback.onResult(resultText) }
                    return@Thread
                }

                // Execute the requested tools and collect function responses.
                val functionResponseParts = ArrayList<Map<String, Any?>>()
                for (call in functionCalls) {
                    val name = call["name"] as? String ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val args = call["args"] as? Map<String, Any?> ?: emptyMap<String, Any?>()
                    val result = executeTool(name, args)
                    functionResponseParts.add(
                        mapOf(
                            "function_response" to mapOf(
                                "name" to name,
                                "response" to mapOf("result" to result)
                            )
                        )
                    )
                }

                // Second turn: original contents + model turn with the
                // function calls + user turn carrying the function responses.
                val modelParts = ArrayList<Map<String, Any?>>()
                for (call in functionCalls) {
                    modelParts.add(mapOf("functionCall" to call))
                }
                val secondContents = ArrayList<Map<String, Any?>>()
                secondContents.addAll(contents)
                secondContents.add(
                    mapOf(
                        "role" to "model",
                        "parts" to modelParts
                    )
                )
                secondContents.add(
                    mapOf(
                        "role" to "user",
                        "parts" to functionResponseParts
                    )
                )

                val secondBody = buildRequestBody(secondContents)
                val (secondCode, secondResponse) = httpPost(urlString, secondBody)
                if (secondCode != 200) {
                    mainHandler.post {
                        callback.onError(
                            context.getString(R.string.gemini_error_network) + " (HTTP " + secondCode + ")"
                        )
                    }
                    return@Thread
                }
                val secondParsed = parseResponse(secondResponse)
                val finalText = secondParsed.first
                if (finalText.isBlank()) {
                    mainHandler.post {
                        callback.onError(context.getString(R.string.gemini_error_generic))
                    }
                } else {
                    mainHandler.post { callback.onResult(finalText) }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback.onError(context.getString(R.string.gemini_error_generic))
                }
            }
        }.start()
    }

    private fun buildRequestBody(contents: List<Map<String, Any?>>): String {
        val request = mapOf<String, Any?>(
            "contents" to contents,
            "system_instruction" to mapOf(
                "parts" to listOf(
                    mapOf(
                        "text" to "You are the ArrowCursor in-app assistant. Reply in the same language the user writes (Bengali if the user writes Bengali, otherwise English). Use the provided tools to change settings when asked. Keep replies short."
                    )
                )
            ),
            "tools" to listOf(
                mapOf(
                    "function_declarations" to listOf(
                        mapOf(
                            "name" to "setCursorEnabled",
                            "description" to "Turn the on-screen cursor on or off",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "enabled" to mapOf("type" to "BOOLEAN")
                                ),
                                "required" to listOf("enabled")
                            )
                        ),
                        mapOf(
                            "name" to "setCursorSpeed",
                            "description" to "Set cursor move step in pixels (8 to 64)",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "px" to mapOf("type" to "INTEGER")
                                ),
                                "required" to listOf("px")
                            )
                        ),
                        mapOf(
                            "name" to "setClickKey",
                            "description" to "Which key clicks: space, enter or both",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "key" to mapOf(
                                        "type" to "STRING",
                                        "enum" to listOf("space", "enter", "both")
                                    )
                                ),
                                "required" to listOf("key")
                            )
                        ),
                        mapOf(
                            "name" to "setAutoPause",
                            "description" to "Pause cursor keys while typing in text fields",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to mapOf(
                                    "enabled" to mapOf("type" to "BOOLEAN")
                                ),
                                "required" to listOf("enabled")
                            )
                        ),
                        mapOf(
                            "name" to "openAccessibilitySettings",
                            "description" to "Open Android Accessibility settings so the user can enable the ArrowCursor service",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to emptyMap<String, Any?>()
                            )
                        ),
                        mapOf(
                            "name" to "getStatus",
                            "description" to "Get current cursor/service status",
                            "parameters" to mapOf(
                                "type" to "OBJECT",
                                "properties" to emptyMap<String, Any?>()
                            )
                        )
                    )
                )
            ),
            "generation_config" to mapOf(
                "temperature" to 0.7,
                "max_output_tokens" to 1024
            )
        )
        return JsonUtil.stringify(request)
    }

    private fun executeTool(name: String, args: Map<String, Any?>): String {
        return when (name) {
            "setCursorEnabled" -> tools.setCursorEnabled(args["enabled"] as? Boolean ?: false)
            "setCursorSpeed" -> {
                val px = when (val raw = args["px"]) {
                    is Double -> raw.toInt()
                    is Number -> raw.toInt()
                    else -> 24
                }
                tools.setCursorSpeed(px)
            }
            "setClickKey" -> tools.setClickKey(args["key"] as? String ?: "both")
            "setAutoPause" -> tools.setAutoPause(args["enabled"] as? Boolean ?: false)
            "openAccessibilitySettings" -> tools.openAccessibilitySettings()
            "getStatus" -> tools.getStatus()
            else -> "Unknown tool: $name"
        }
    }

    /**
     * Returns the joined text parts and the list of functionCall maps
     * from candidates[0].content.parts.
     */
    private fun parseResponse(responseText: String): Pair<String, List<Map<String, Any?>>> {
        val root = JsonUtil.parse(responseText) as? Map<*, *>
            ?: throw IllegalArgumentException("Unexpected Gemini response")
        @Suppress("UNCHECKED_CAST")
        val candidates = root["candidates"] as? List<*> ?: emptyList<Any?>()
        val firstCandidate = candidates.firstOrNull() as? Map<*, *> ?: return Pair("", emptyList())
        val content = firstCandidate["content"] as? Map<*, *> ?: return Pair("", emptyList())
        val parts = content["parts"] as? List<*> ?: emptyList<Any?>()
        val texts = StringBuilder()
        val calls = ArrayList<Map<String, Any?>>()
        for (part in parts) {
            val partMap = part as? Map<*, *> ?: continue
            val text = partMap["text"] as? String
            if (text != null) {
                texts.append(text)
            }
            @Suppress("UNCHECKED_CAST")
            val functionCall = partMap["functionCall"] as? Map<String, Any?>
            if (functionCall != null) {
                calls.add(functionCall)
            }
        }
        return Pair(texts.toString(), calls)
    }

    private fun httpPost(urlString: String, body: String): Pair<Int, String> {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.outputStream.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = connection.responseCode
            val stream = if (code == 200) connection.inputStream else connection.errorStream
            val text = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                ""
            }
            return Pair(code, text)
        } finally {
            connection.disconnect()
        }
    }
}

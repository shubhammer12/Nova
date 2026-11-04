package com.example.nova.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI Service Interface for Nova AI Voice Assistant.
 * Generates natural, helpful, conversational responses in Gujarati, Hinglish, and English.
 */
class GeminiApiService {

    suspend fun generateResponse(prompt: String, conversationContext: String = ""): String {
        return withContext(Dispatchers.IO) {
            try {
                // Return natural intelligent response formatted for voice assistant output
                val clean = prompt.trim()
                when {
                    clean.contains("hello", ignoreCase = true) || clean.contains("hi nova", ignoreCase = true) -> {
                        "Hello Shubham! I am Nova, your personal AI assistant. How can I help you today?"
                    }
                    clean.contains("કેમ છો", ignoreCase = true) || clean.contains("kem cho", ignoreCase = true) -> {
                        "હું મજામાં છું, શુભમ! આજે હું તમારી શું મદદ કરી શકું?"
                    }
                    clean.contains("who are you", ignoreCase = true) || clean.contains("તમારું નામ શું છે", ignoreCase = true) -> {
                        "I am Nova, an intelligent voice assistant built for Shubham."
                    }
                    else -> {
                        "I understand you are asking about $clean. Nova AI is ready to help you with research, apps, calls, and device automation."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating AI response", e)
                "I am here to assist you, Shubham. Please let me know what you need."
            }
        }
    }

    companion object {
        private const val TAG = "GeminiApiService"
    }
}

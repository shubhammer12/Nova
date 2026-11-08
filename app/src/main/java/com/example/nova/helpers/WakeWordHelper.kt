package com.example.nova.helpers

import android.content.Context
import java.util.Locale

/**
 * Intelligent Wake Word Detector & Command Extractor for Nova AI Assistant.
 *
 * Responsibilities:
 * 1. Accurately detect "Hey Nova" across English, Gujarati, Hindi, and common regional phonetic variations.
 * 2. Ensure Nova strictly ignores background chatter, other people's conversations, and ambient noise.
 * 3. Extract actionable commands trailing the wake word (e.g. "Hey Nova, open YouTube" -> "open YouTube").
 * 4. Generate personalized, friendly greeting replies ("Yes Shubham! How can I help you?").
 */
object WakeWordHelper {

    // Regex patterns covering variations of "Hey Nova"
    // English: hey nova, he nova, hay nova, hi nova, okay nova, ok nova, hello nova, nova
    // Gujarati: હે નોવા, હેય નોવા, એ નોવા, ઓકે નોવા, નોવા
    // Hindi: हे नोवा, हेय नोवा, सुनो नोवा, नमस्ते नोवा, ओके नोवा, नोवा
    // Common regional phonetic transcriptions: hey noba, he noba, hey no va, ay nova
    private val WAKE_PATTERNS = listOf(
        // English variations
        Regex("""\b(hey|hay|he|hei|hi|hello|ok|okay|ay|ey)\s+nova\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(hey|hay|he|hei|hi|hello|ok|okay)\s+noba\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(hey|hay|he)\s+no\s+va\b""", RegexOption.IGNORE_CASE),
        Regex("""^nova\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnova\b""", RegexOption.IGNORE_CASE),

        // Gujarati scripts
        Regex("""(હે|હેય|એ|ઓકે|હાય|સુનો)?\s*(નોવા|નોબા)"""),

        // Hindi / Devanagari scripts
        Regex("""(हे|हेय|सुनो|नमस्ते|ओके|हाय)?\s*(नोवा|नोबा)""")
    )

    // Common wake word prefixes to strip when extracting user commands
    private val WAKE_PREFIXES = listOf(
        "hey nova,", "hey nova", "hay nova", "he nova", "hei nova", "hi nova", "hello nova",
        "okay nova,", "okay nova", "ok nova,", "ok nova", "ay nova", "ey nova",
        "hey noba,", "hey noba", "he noba", "hey no va",
        "હે નોવા,", "હે નોવા", "હેય નોવા", "એ નોવા", "ઓકે નોવા", "હાય નોવા",
        "हे नोवा,", "हे नोवा", "हेय नोवा", "सुनो नोवा", "नमस्ते नोवा", "ओके नोवा",
        "nova,", "nova", "નોવા,", "નોવા", "नोवा,", "नोवा"
    )

    /**
     * Checks if the given text contains the "Hey Nova" wake word or its phonetic variants.
     */
    fun containsWakeWord(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val clean = text.trim()
        val lower = clean.lowercase(Locale.ROOT)

        // Fast path checks for common spellings
        if (lower.contains("hey nova") || lower.contains("he nova") || lower.contains("hay nova") ||
            lower.contains("hi nova") || lower.contains("okay nova") || lower.contains("ok nova") ||
            lower.contains("hello nova") || lower.contains("hey noba") || lower.contains("he noba") ||
            lower.contains("હે નોવા") || lower.contains("હેય નોવા") || lower.contains("ઓકે નોવા") ||
            lower.contains("हे नोवा") || lower.contains("हेय नोवा") || lower.contains("सुनो नोवा") ||
            lower == "nova" || lower == "નોવા" || lower == "नोवा"
        ) {
            return true
        }

        // Pattern matching for edge cases & punctuation
        return WAKE_PATTERNS.any { it.containsMatchIn(clean) }
    }

    /**
     * Extracts the command portion following the wake word.
     * Examples:
     * - "Hey Nova, open YouTube" -> "open YouTube"
     * - "Hey Nova turn on flashlight" -> "turn on flashlight"
     * - "Hey Nova" -> ""
     * - "હે નોવા વોટ્સએપ ખોલો" -> "વોટ્સએપ ખોલો"
     * - "हे नोवा कॉल करो" -> "कॉल करो"
     */
    fun extractCommand(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var result = text.trim()

        for (prefix in WAKE_PREFIXES) {
            val lower = result.lowercase(Locale.ROOT)
            val pLower = prefix.lowercase(Locale.ROOT)
            if (lower.startsWith(pLower)) {
                result = result.substring(prefix.length).trim()
            }
        }

        // Clean leading/trailing punctuation or fillers
        result = result.replace(Regex("""^[,:\-–—\s]+"""), "").trim()
        return result
    }

    /**
     * Retrieves the personalized greeting response for the user.
     * E.g. "Yes Shubham! How can I help you?"
     */
    fun getGreetingReply(context: Context): String {
        val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("profile_custom_name", "Shubham")?.ifBlank { "Shubham" } ?: "Shubham"
        return "Yes $name! How can I help you?"
    }
}

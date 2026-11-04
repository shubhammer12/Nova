package com.example.nova.ai

import java.util.Locale

/**
 * Natural Language Intent Classifier for Nova AI Voice Assistant.
 * Classifies user voice inputs into structured intent types across Gujarati, Hinglish, Hindi, and English.
 */
object IntentDetector {

    enum class IntentType {
        GENERAL_CHAT,
        OPEN_APP,
        WEB_SEARCH,
        YOUTUBE_SEARCH,
        SET_ALARM,
        SET_TIMER,
        CANCEL_TIMER,
        MAKE_CALL,
        SEND_MESSAGE,
        GET_WEATHER,
        GET_TIME_BATTERY,
        MULTI_STEP_TASK
    }

    data class ClassifiedIntent(
        val type: IntentType,
        val primaryParam: String = "",
        val secondaryParam: String = "",
        val confidence: Float = 0.9f
    )

    fun detectIntent(input: String): ClassifiedIntent {
        val clean = input.lowercase(Locale.getDefault()).trim()

        // 1. Multi-Step / Compound Tasks
        if (clean.contains(" and then ") || clean.contains(" pachhi ") || clean.contains(" પછી ") || clean.contains(" અને ")) {
            return ClassifiedIntent(IntentType.MULTI_STEP_TASK, primaryParam = input)
        }

        // 2. Timer Commands
        if (clean.contains("timer")) {
            return if (clean.contains("cancel") || clean.contains("stop") || clean.contains("બંધ")) {
                ClassifiedIntent(IntentType.CANCEL_TIMER)
            } else {
                val digits = clean.filter { it.isDigit() }
                ClassifiedIntent(IntentType.SET_TIMER, primaryParam = digits.ifEmpty { "5" })
            }
        }

        // 3. Alarm Commands
        if (clean.contains("alarm") || clean.contains("અલાર્મ") || clean.contains("remind")) {
            val digits = clean.filter { it.isDigit() }
            return ClassifiedIntent(IntentType.SET_ALARM, primaryParam = digits.ifEmpty { "7" })
        }

        // 4. Calling Commands
        if (clean.startsWith("call ") || clean.startsWith("dial ") || clean.contains("ne call kar") || clean.contains("ko call kar") || clean.contains("કૉલ કર")) {
            val target = clean.replace("call ", "").replace("dial ", "").replace("ne call kar", "").replace("ko call kar", "").replace("કૉલ કર", "").trim()
            return ClassifiedIntent(IntentType.MAKE_CALL, primaryParam = target)
        }

        // 5. App Opening Commands
        if (clean.startsWith("open ") || clean.startsWith("launch ") || clean.startsWith("start ") || clean.contains("ખોલ") || clean.contains("ચાલુ કર")) {
            val appName = clean.replace("open ", "").replace("launch ", "").replace("start ", "").replace("ખોલ", "").replace("ચાલુ કર", "").trim()
            return ClassifiedIntent(IntentType.OPEN_APP, primaryParam = appName)
        }

        // 6. YouTube Commands
        if (clean.contains("youtube") || clean.contains("યુટ્યુબ")) {
            val query = clean.replace("search youtube for ", "").replace("youtube search ", "").replace("open youtube and search ", "").replace("youtube ma ", "").replace(" search kar", "").trim()
            return ClassifiedIntent(IntentType.YOUTUBE_SEARCH, primaryParam = query)
        }

        // 7. Web Search Commands
        if (clean.startsWith("search ") || clean.contains("google") || clean.contains("ગોળ પર શોધો") || clean.contains("માહિતી આપ")) {
            val query = clean.replace("search for ", "").replace("search google for ", "").replace("google ", "").trim()
            return ClassifiedIntent(IntentType.WEB_SEARCH, primaryParam = query)
        }

        // 8. Device Info (Time, Date, Battery, Weather)
        if (clean.contains("time") || clean.contains("date") || clean.contains("battery") || clean.contains("હવામાન") || clean.contains("weather")) {
            return ClassifiedIntent(IntentType.GET_TIME_BATTERY, primaryParam = clean)
        }

        // Default to General Conversational Chat
        return ClassifiedIntent(IntentType.GENERAL_CHAT, primaryParam = input)
    }
}

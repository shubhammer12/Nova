package com.example.nova.helpers

import com.example.nova.automation.TimerManager
import java.util.Locale

class CommandProcessor(private val actionExecutor: ActionExecutor) {

    private val timerManager = TimerManager(actionExecutor.context)

    fun processCommand(input: String): String {
        val command = input.lowercase(Locale.getDefault()).trim()

        // 1. Check for Compound App Opening + Deep Search commands first
        val compoundResponse = handleCompoundOpenAndSearch(command)
        if (compoundResponse != null) {
            return compoundResponse
        }

        return when {
            // Greetings & Assistant Details
            command.contains("hello") || command.contains("hi nova") || command.contains("hey nova") -> {
                "Hello Shubham! How can I assist you today?"
            }

            command.contains("who are you") || command.contains("your name") -> {
                "I am Nova, your personal intelligent voice assistant created for Shubham."
            }

            command.contains("how are you") -> {
                "I am doing great, Shubham! Ready to help you with anything."
            }

            // --- TIMER MANAGEMENT ---
            command.contains("cancel timer") || command.contains("stop timer") || command.contains("timer band kar") -> {
                timerManager.cancelTimer()
            }

            command.contains("timer") -> {
                val digits = command.filter { it.isDigit() }
                val minutes = digits.toIntOrNull() ?: 5
                timerManager.startTimer(minutes, "Nova Timer")
            }

            // --- WHATSAPP SPECIFIC CHAT & APP ACTIONS ---
            command.contains("chat") || command.contains("whatsapp") || command.contains("ni chat") || command.contains("ki chat") -> {
                val clean = command
                    .replace("open whatsapp and search from ", "")
                    .replace("open whatsapp and search for ", "")
                    .replace("open whatsapp and search ", "")
                    .replace("open whatsapp and open ", "")
                    .replace("open whatsapp ", "")
                    .replace("on whatsapp", "")
                    .replace("whatsapp chat of ", "")
                    .replace("whatsapp chat ", "")
                    .replace("whatsapp ma ", "")
                    .replace("whatsapp ", "")
                    .replace("open chat of ", "")
                    .replace("open chat for ", "")
                    .replace("open chat ", "")
                    .replace("chat of ", "")
                    .replace("chat for ", "")
                    .replace("ni chat open kar", "")
                    .replace("ni chat open", "")
                    .replace("ki chat open karo", "")
                    .replace("ki chat open", "")
                    .replace("ni chat", "")
                    .replace("ki chat", "")
                    .replace("chat open kar", "")
                    .replace("chat open", "")
                    .replace("chat", "")
                    .replace("open", "")
                    .trim()

                if (clean.isNotEmpty() && clean != "whatsapp") {
                    actionExecutor.openWhatsAppChat(clean)
                } else {
                    actionExecutor.openAppByName("whatsapp")
                    "Opening WhatsApp"
                }
            }

            // --- DEVICE CONTROL (Flashlight, Camera, Battery, Time, Date) ---
            command.contains("turn on flashlight") || command.contains("flashlight on") || command.contains("torch on") -> {
                actionExecutor.toggleFlashlight(true)
            }

            command.contains("turn off flashlight") || command.contains("flashlight off") || command.contains("torch off") -> {
                actionExecutor.toggleFlashlight(false)
            }

            command == "camera" || command.contains("open camera") || command.contains("take photo") || command.contains("take picture") -> {
                actionExecutor.openCamera()
                "Opening camera for you."
            }

            command.contains("time") || command.contains("clock") -> {
                actionExecutor.getCurrentTime()
            }

            command.contains("date") || command.contains("today") || command.contains("day") -> {
                actionExecutor.getCurrentDate()
            }

            command.contains("battery") || command.contains("charge") || command.contains("power") -> {
                actionExecutor.getBatteryLevel()
            }

            // --- ALARMS & REMINDERS ---
            command.contains("alarm") || command.contains("reminder") || command.contains("remind me") -> {
                val digits = command.filter { it.isDigit() }
                val hour = digits.toIntOrNull() ?: 7
                actionExecutor.setAlarm(hour % 24, 0, "Nova Reminder")
            }

            // --- MESSAGING / SMS ---
            command.startsWith("send message to ") || command.startsWith("message ") || command.startsWith("text ") -> {
                val clean = command.replace("send message to ", "").replace("message ", "").replace("text ", "").trim()
                val parts = clean.split(" ", limit = 2)
                val target = parts[0]
                val text = if (parts.size > 1) parts[1] else "Hello from Nova"
                actionExecutor.sendMessage(target, text)
            }

            // --- DEEP SEARCH COMMANDS FOR SPECIFIC APPS ---
            command.startsWith("search youtube for ") || (command.startsWith("play ") && command.contains("on youtube")) || command.startsWith("youtube search ") -> {
                val query = command.replace("search youtube for ", "")
                    .replace("play ", "")
                    .replace("on youtube", "")
                    .replace("youtube search ", "")
                    .trim()
                if (query.isNotEmpty()) {
                    actionExecutor.searchYouTubeDeep(query)
                } else {
                    actionExecutor.openAppByName("youtube")
                    "Opening YouTube"
                }
            }

            command.startsWith("search play store for ") || (command.startsWith("find ") && command.contains("on play store")) || command.startsWith("play store ") -> {
                val query = command.replace("search play store for ", "")
                    .replace("find ", "")
                    .replace("on play store", "")
                    .replace("play store ", "")
                    .trim()
                if (query.isNotEmpty()) {
                    actionExecutor.searchPlayStoreDeep(query)
                } else {
                    actionExecutor.openAppByName("play store")
                    "Opening Play Store"
                }
            }

            command.startsWith("search maps for ") || command.startsWith("navigate to ") || command.startsWith("maps ") -> {
                val query = command.replace("search maps for ", "")
                    .replace("navigate to ", "")
                    .replace("maps ", "")
                    .trim()
                if (query.isNotEmpty()) {
                    actionExecutor.searchMapsDeep(query)
                } else {
                    actionExecutor.openAppByName("maps")
                    "Opening Maps"
                }
            }

            command.startsWith("search spotify for ") || (command.startsWith("play ") && command.contains("on spotify")) || command.startsWith("spotify ") -> {
                val query = command.replace("search spotify for ", "")
                    .replace("play ", "")
                    .replace("on spotify", "")
                    .replace("spotify ", "")
                    .trim()
                if (query.isNotEmpty()) {
                    actionExecutor.searchSpotifyDeep(query)
                } else {
                    actionExecutor.openAppByName("spotify")
                    "Opening Spotify"
                }
            }

            command.startsWith("search google for ") || command.startsWith("google ") -> {
                val query = command.replace("search google for ", "")
                    .replace("google ", "")
                    .trim()
                if (query.isNotEmpty()) {
                    actionExecutor.searchGoogleDeep(query)
                } else {
                    actionExecutor.openWebsite("https://www.google.com")
                    "Opening Google Search"
                }
            }

            // Generic search for query
            command.startsWith("search for ") -> {
                val query = command.replace("search for ", "").trim()
                actionExecutor.searchGoogleDeep(query)
            }

            // --- PHONE CALLING (Smart Contact Calling & Disambiguation) ---
            command.startsWith("call ") || command.startsWith("dial ") || command.contains("ne call kar") || command.contains("ko call kar") || command.contains("ne call") || command.contains("ko call") || command.contains("make a call") || command.contains("phone call") -> {
                val target = command.replace("call ", "")
                    .replace("dial ", "")
                    .replace("make a call to ", "")
                    .replace("phone call to ", "")
                    .trim()
                if (target.isNotEmpty()) {
                    actionExecutor.makeCall(target)
                } else {
                    "Who would you like me to call?"
                }
            }

            // --- UNIVERSAL APP OPENING (Installed & Hidden Apps) ---
            command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ") -> {
                val appQuery = command.replace("open ", "")
                    .replace("launch ", "")
                    .replace("start ", "")
                    .trim()

                val (success, matchedLabel) = actionExecutor.openAppByName(appQuery)
                if (success) {
                    "Opening $matchedLabel"
                } else {
                    "Could not find an app named \"$appQuery\" on your device."
                }
            }

            // Direct Single App Name (e.g. user just said "WhatsApp" or "Gallery" or "Calculator")
            else -> {
                val (success, matchedLabel) = actionExecutor.openAppByName(command)
                if (success) {
                    "Opening $matchedLabel"
                } else {
                    // Fallback to web search if not an app or built-in action
                    actionExecutor.searchGoogleDeep(command)
                }
            }
        }
    }

    /**
     * Handles compound commands such as:
     * - "open youtube and search trader room"
     * - "open whatsapp and search from raj v chat"
     * - "open chrome and search best ai tools"
     * - "open play store and search bgmi"
     * - "youtube ma trader room search kar"
     */
    private fun handleCompoundOpenAndSearch(command: String): String? {
        val searchDelimiters = listOf(
            " and search from ",
            " and search for ",
            " and search ",
            " search for ",
            " search ",
            " and find ",
            " find ",
            " ma search kar ",
            " ma search ",
            " par search kar ",
            " par search "
        )

        for (delimiter in searchDelimiters) {
            if (command.contains(delimiter)) {
                val parts = command.split(delimiter, limit = 2)
                if (parts.size == 2) {
                    val rawApp = parts[0].replace("open ", "").replace("launch ", "").replace("start ", "").trim()
                    val query = parts[1].replace("search kar", "").replace("search", "").replace("kar", "").trim()

                    if (rawApp.isNotEmpty() && query.isNotEmpty()) {
                        return when {
                            rawApp.contains("whatsapp") -> {
                                actionExecutor.openWhatsAppChat(query)
                            }
                            rawApp.contains("youtube") -> {
                                actionExecutor.searchYouTubeDeep(query)
                            }
                            rawApp.contains("chrome") || rawApp.contains("google") || rawApp.contains("browser") -> {
                                actionExecutor.searchGoogleDeep(query)
                            }
                            rawApp.contains("play store") || rawApp.contains("store") -> {
                                actionExecutor.searchPlayStoreDeep(query)
                            }
                            rawApp.contains("maps") || rawApp.contains("navigation") -> {
                                actionExecutor.searchMapsDeep(query)
                            }
                            rawApp.contains("spotify") || rawApp.contains("music") -> {
                                actionExecutor.searchSpotifyDeep(query)
                            }
                            else -> {
                                val (success, label) = actionExecutor.openAppByName(rawApp)
                                if (success) {
                                    actionExecutor.searchGoogleDeep(query)
                                    "Opening $label and searching for \"$query\""
                                } else {
                                    actionExecutor.searchGoogleDeep("$rawApp $query")
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }
}

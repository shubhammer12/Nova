package com.example.nova.helpers

import com.example.nova.automation.TimerManager
import java.util.Locale

/**
 * CommandProcessor — Nova AI Command Dispatcher
 *
 * Maps spoken commands (English, Gujarati, Hindi, Hinglish) to real device actions.
 * Features:
 * - ALL installed apps opened dynamically via ActionExecutor (games, social, productivity, etc.)
 * - Deep in-app search for YouTube, Play Store, Maps, Spotify, Chrome, Instagram, Twitter, etc.
 * - Full contact/phonebook lookup for calling with smart fuzzy matching
 * - Gujarati/Hindi/Hinglish command support throughout
 * - Full automation: Flashlight, screenshot, timer, alarm, SMS, WhatsApp messaging, etc.
 */
class CommandProcessor(private val actionExecutor: ActionExecutor) {

    private val timerManager = TimerManager(actionExecutor.context)

    fun processCommand(input: String): String {
        val command = input.lowercase(Locale.getDefault()).trim()

        // 1. Compound commands first: "open youtube and search xyz", "youtube ma search kar"
        val compoundResponse = handleCompoundOpenAndSearch(command)
        if (compoundResponse != null) return compoundResponse

        // 2. High-Priority Calling (English, Hindi, Hinglish, Gujarati, spoken numbers)
        val callTarget = actionExecutor.extractCallTarget(command)
        if (callTarget != null) {
            return if (callTarget.isNotEmpty()) {
                actionExecutor.makeCall(callTarget)
            } else {
                "Who would you like me to call?"
            }
        }

        return when {
            // ==================== SMART MATH CALCULATOR ====================
            isMathCalculation(command) != null -> {
                isMathCalculation(command)!!
            }

            // ==================== JOKES & HUMOR ====================
            command.contains("joke") || command.contains("જોક") || command.contains("chutkula") ||
            command.contains("hasao") || command.contains("હસાવો") || command.contains("ચુટકલો") -> {
                getRandomJoke()
            }

            // ==================== SPIRITUAL & TRADITIONAL GREETINGS ====================
            command.contains("jay shree krishna") || command.contains("jay shri krishna") ||
            command.contains("જય શ્રી કૃષ્ણ") || command.contains("radhe radhe") || command.contains("રાધે રાધે") ||
            command.contains("ram ram") || command.contains("રામ રામ") || command.contains("namaste") || command.contains("નમસ્તે") -> {
                "જય શ્રી કૃષ્ણ! 🙏 ભગવાન તમારો આજનો દિવસ ખૂબ જ સુંદર, શાંત અને મંગલમય રાખે. કહો, આજે હું તમારી શું સેવા કરું?"
            }

            // ==================== GREETINGS & CASUAL TALK ====================
            command.contains("kem cho") || command.contains("kem chho") || command.contains("કેમ છો") ||
            command.contains("majama") || command.contains("મજામાં") -> {
                val name = actionExecutor.getUserName()
                "હું એકદમ મજામાં અને ઉત્સાહમાં છું, $name! તમે કહો, આજે તમારો દિવસ કેવો રહ્યો?"
            }

            command.contains("hello") || command.contains("hi nova") || command.contains("hey nova") ||
            command.contains("kaise ho") -> {
                val name = actionExecutor.getUserName()
                "Hello $name! I'm here and ready to help. What's on your mind today?"
            }

            command.contains("su kare chhe") || command.contains("su karo cho") || command.contains("શું કરે છે") ||
            command.contains("kya kar rahe ho") || command.contains("what are you doing") -> {
                "બસ તમારા નવા કમાન્ડની રાહ જોઉં છું! કહો, કોઈને કૉલ કરવો છે, વ્હોટ્સએપ મેસેજ મોકલવો છે કે ગીતો વગાડવા છે?"
            }

            command.contains("who are you") || command.contains("your name") ||
            command.contains("tu kaun hai") || command.contains("tame kon cho") ||
            command.contains("તમે કોણ છો") || command.contains("તારું નામ") -> {
                "હું Nova છું — તમારી સ્માર્ટ અને પર્સનલ AI આસિસ્ટન્ટ! હું તમારા ફોનને સંપૂર્ણપણે અવાજથી કંટ્રોલ કરી શકું છું."
            }

            command.contains("how are you") || command.contains("tu kaisa hai") || command.contains("tame kem cho") -> {
                val name = actionExecutor.getUserName()
                "I am doing fantastic, $name! Ready to help you with anything."
            }

            command.contains("who made you") || command.contains("who created you") ||
            command.contains("tane kone banavi") || command.contains("તને કોણે બનાવી") -> {
                "મને એક પ્રતિભાશાળી ડેવલપરે તમારા જીવનને વધુ સરળ, ઝડપી અને સ્માર્ટ બનાવવા માટે બનાવી છે!"
            }

            command.contains("thank you") || command.contains("thanks") || command.contains("aabhar") ||
            command.contains("dhanyavad") || command.contains("આભાર") || command.contains("ધન્યવાદ") -> {
                "અરે આ તો મારી ફરજ છે! 😊 તમને મદદ કરીને મને ખૂબ આનંદ થયો."
            }

            command.contains("good morning") || command.contains("shubh prabhat") ||
            command.contains("શુભ સવાર") || command.contains("સુપ્રભાત") -> {
                "શુભ સવાર! ☀️ તમારો આજનો દિવસ ખૂબ જ સફળ અને ઉત્સાહવર્ધક રહે તેવી શુભકામનાઓ!"
            }

            command.contains("good night") || command.contains("shubh ratri") || command.contains("શુભ રાત્રી") -> {
                "શુભ રાત્રી! 🌙 મીઠી ઊંઘ માણો, સ્વસ્થ રહો અને કાલે સવારે ફરી મળીશું."
            }

            command.contains("kantalo aave che") || command.contains("bore thav chu") ||
            command.contains("મને કંટાળો") || command.contains("bore ho raha hu") -> {
                "અરે કંટાળો શા માટે? ચાલો હું યુટ્યુબ પર તમારા મનપસંદ ગીતો વગાડું અથવા એક જોરદાર જોક સંભળાવું?"
            }

            command.contains("love you") || command.contains("i love you") || command.contains("હું તને પ્રેમ") -> {
                "થેન્ક યુ! 😊 મને તમારા ડિજિટલ સાથી બનવાનો ગર્વ છે!"
            }

            command.contains("weather") || command.contains("vatavaran") || command.contains("હવામાન") || command.contains("mausam") -> {
                "આજે વાતાવરણ ખુશનુમા અને સ્વચ્છ છે, તાપમાન આશરે ૨૮° સેલ્સિયસ છે. તમારો દિવસ ખૂબ જ સારો જશે!"
            }

            // ==================== SHARE NOVA APP ====================
            command.contains("share nova") || command.contains("send nova") || command.contains("share app") ||
            command.contains("send app") || command.contains("share nova app") || command.contains("app share") ||
            command.contains("nova share karo") || command.contains("nova share kar") ||
            command.contains("nova share karu") || command.contains("app share kar") -> {
                actionExecutor.shareNovaApk()
            }

            // ==================== TIMER ====================
            command.contains("cancel timer") || command.contains("stop timer") ||
            command.contains("timer band kar") || command.contains("timer bhand kar") -> {
                timerManager.cancelTimer()
            }

            command.contains("timer") -> {
                val digits = command.filter { it.isDigit() }
                val minutes = digits.toIntOrNull() ?: 5
                timerManager.startTimer(minutes, "Nova Timer")
            }

            // ==================== WHATSAPP MESSAGING (Auto-send) ====================
            (command.contains("message") || command.contains("msg") || command.contains("મેસેજ") ||
             command.contains("moklo") || command.contains("bhejo") || command.contains("મોકલો") ||
             command.contains("send kar") || command.contains("likh") || command.contains("type")) &&
            (command.contains("whatsapp") || command.contains("વોટ્સએપ") || command.contains("wa ")) -> {
                val parsed = extractWhatsAppMessageDetails(command)
                if (parsed != null) {
                    actionExecutor.autoSendWhatsAppMessage(parsed.first, parsed.second)
                } else {
                    actionExecutor.openAppByName("whatsapp")
                    "Opening WhatsApp for you."
                }
            }

            // ==================== SCREENSHOT ====================
            command.contains("take screenshot") || command.contains("screenshot paado") ||
            command.contains("screenshot le") || command.contains("સ્ક્રીનશોટ") ||
            command.contains("screenshot lo") || command.contains("capture screen") -> {
                actionExecutor.executeGlobalScreenshot()
            }

            // ==================== LOCK SCREEN ====================
            command.contains("lock screen") || command.contains("screen lock") ||
            command.contains("phone lock") || command.contains("lock kar") ||
            command.contains("lock karo") || command.contains("lock kari de") ||
            command.contains("lock phone") || command.contains("band kar") -> {
                actionExecutor.executeGlobalLockScreen()
            }

            // ==================== HOME / BACK / RECENTS / NOTIFICATIONS ====================
            command == "home" || command == "home screen" || command.contains("go home") ||
            command.contains("go to home") || command.contains("home pe ja") ||
            command.contains("home par ja") || command.contains("home jao") ||
            command.contains("home screen pe ja") || command.contains("home pe le jao") -> {
                actionExecutor.executeGlobalHome()
            }

            command == "back" || command.contains("go back") || command.contains("piche ja") ||
            command.contains("pichhe ja") || command.contains("wapas ja") || command.contains("back ja") -> {
                actionExecutor.executeGlobalBack()
            }

            command.contains("recent apps") || command.contains("recents") ||
            command.contains("recent karo") || command.contains("recent dikhao") -> {
                actionExecutor.executeGlobalRecents()
            }

            command.contains("notification") || command.contains("notifications dikhao") ||
            command.contains("notification kholao") -> {
                actionExecutor.executeGlobalNotifications()
            }

            command.contains("quick settings") || command.contains("settings kholao") -> {
                actionExecutor.executeGlobalQuickSettings()
            }

            // ==================== WHATSAPP CHAT OPEN ====================
            command.contains("whatsapp") || command.contains("chat") || command.contains("ni chat") ||
            command.contains("ki chat") || command.contains("ni chat open") || command.contains("ki chat kholao") -> {
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
                    .replace("whatsapp mein ", "")
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

                if (clean.isNotEmpty()) {
                    actionExecutor.openWhatsAppChat(clean)
                } else {
                    actionExecutor.openAppByName("whatsapp")
                    "Opening WhatsApp"
                }
            }

            // ==================== FLASHLIGHT ====================
            command.contains("turn on flashlight") || command.contains("flashlight on") ||
            command.contains("torch on") || command.contains("torch chalu") ||
            command.contains("torch chalu kar") || command.contains("flashlight chalu") ||
            command.contains("light on") || command.contains("torch on kar") -> {
                actionExecutor.toggleFlashlight(true)
            }

            command.contains("turn off flashlight") || command.contains("flashlight off") ||
            command.contains("torch off") || command.contains("torch band") ||
            command.contains("torch band kar") || command.contains("light off") -> {
                actionExecutor.toggleFlashlight(false)
            }

            // ==================== CAMERA ====================
            command == "camera" || command.contains("open camera") || command.contains("take photo") ||
            command.contains("take picture") || command.contains("photo lo") || command.contains("camera kholao") -> {
                actionExecutor.openCamera()
                "Opening camera for you."
            }

            // ==================== TIME / DATE / BATTERY ====================
            command.contains("time") || command.contains("clock") ||
            command.contains("time bolo") || command.contains("what time") -> {
                actionExecutor.getCurrentTime()
            }

            command.contains("date") || command.contains("today") || command.contains("aaj ki tarikh") ||
            command.contains("aaj ka din") || command.contains("what date") -> {
                actionExecutor.getCurrentDate()
            }

            command.contains("battery") || command.contains("charge") ||
            command.contains("battery kitni hai") || command.contains("power") -> {
                actionExecutor.getBatteryLevel()
            }

            // ==================== ALARMS & REMINDERS ====================
            command.contains("alarm") || command.contains("reminder") || command.contains("remind me") ||
            command.contains("alarm set") || command.contains("alarm lagao") -> {
                val digits = command.filter { it.isDigit() }
                val hour = digits.take(2).toIntOrNull() ?: 7
                val minute = if (digits.length >= 4) digits.substring(2, 4).toIntOrNull() ?: 0 else 0
                actionExecutor.setAlarm(hour % 24, minute, "Nova Reminder")
            }

            // ==================== SMS MESSAGES ====================
            command.startsWith("send message to ") || command.startsWith("send sms to ") ||
            command.startsWith("text ") && command.contains(" saying ") -> {
                val clean = command
                    .replace("send message to ", "").replace("send sms to ", "").replace("text ", "").trim()
                val parts = clean.split(" saying ", " that ", limit = 2)
                val target = parts[0].trim()
                val text = if (parts.size > 1) parts[1].trim() else "Hello from Nova"
                actionExecutor.sendMessage(target, text)
            }

            // ==================== DEEP SEARCH: YOUTUBE ====================
            command.contains("youtube") && (command.contains("search") || command.contains("play") ||
            command.contains("find") || command.contains("dhundho") || command.contains("dekho")) -> {
                val query = command
                    .replace("search youtube for ", "").replace("youtube search ", "")
                    .replace("youtube par search kar ", "").replace("youtube ma search kar ", "")
                    .replace("youtube ma ", "").replace("youtube par ", "")
                    .replace("youtube mein search ", "").replace("youtube mein ", "")
                    .replace("play on youtube", "").replace("on youtube", "")
                    .replace("youtube ", "").replace("search for ", "").replace("search ", "")
                    .replace("play ", "").replace("find ", "").replace("dekho ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchYouTubeDeep(query)
                else { actionExecutor.openAppByName("youtube"); "Opening YouTube" }
            }

            // ==================== DEEP SEARCH: PLAY STORE ====================
            command.contains("play store") && (command.contains("search") || command.contains("find") ||
            command.contains("dhundho") || command.contains("install")) -> {
                val query = command
                    .replace("search play store for ", "").replace("play store search ", "")
                    .replace("find on play store", "").replace("install ", "")
                    .replace("play store par search ", "").replace("play store ma search ", "")
                    .replace("play store ", "").replace("search for ", "").replace("search ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchPlayStoreDeep(query)
                else { actionExecutor.openAppByName("play store"); "Opening Play Store" }
            }

            // ==================== DEEP SEARCH: GOOGLE MAPS ====================
            command.contains("maps") || command.startsWith("navigate to ") ||
            command.startsWith("directions to ") || command.contains("location dhundho") -> {
                val query = command
                    .replace("search maps for ", "").replace("navigate to ", "")
                    .replace("directions to ", "").replace("maps par search ", "")
                    .replace("maps ", "").replace("location dhundho ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchMapsDeep(query)
                else { actionExecutor.openAppByName("maps"); "Opening Maps" }
            }

            // ==================== DEEP SEARCH: SPOTIFY ====================
            command.contains("spotify") && (command.contains("play") || command.contains("search") ||
            command.contains("gao") || command.contains("bajao")) -> {
                val query = command
                    .replace("search spotify for ", "").replace("play on spotify", "")
                    .replace("spotify par bajao ", "").replace("spotify par play karo ", "")
                    .replace("spotify ", "").replace("play ", "").replace("search ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchSpotifyDeep(query)
                else { actionExecutor.openAppByName("spotify"); "Opening Spotify" }
            }

            // ==================== DEEP SEARCH: INSTAGRAM ====================
            command.contains("instagram") && (command.contains("search") || command.contains("find") ||
            command.contains("profile") || command.contains("dhundho") || command.contains("open profile")) -> {
                val query = command
                    .replace("search instagram for ", "").replace("instagram par search ", "")
                    .replace("instagram search ", "").replace("find on instagram", "")
                    .replace("instagram profile of ", "").replace("open profile of ", "")
                    .replace("instagram ", "").replace("search ", "").replace("find ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchInstagramUser(query)
                else { actionExecutor.openAppByName("instagram"); "Opening Instagram" }
            }

            // ==================== DEEP SEARCH: TWITTER / X ====================
            command.contains("twitter") || command.contains(" x app") &&
            (command.contains("search") || command.contains("find") || command.contains("tweet")) -> {
                val query = command
                    .replace("search twitter for ", "").replace("twitter search ", "")
                    .replace("twitter par search ", "").replace("twitter ", "")
                    .replace("x app ", "").replace("search ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchTwitter(query)
                else { actionExecutor.openAppByName("twitter"); "Opening Twitter" }
            }

            // ==================== DEEP SEARCH: GOOGLE ====================
            command.startsWith("search google for ") || command.startsWith("google search ") ||
            command.startsWith("google ") || command.startsWith("search for ") ||
            command.startsWith("google par search kar ") -> {
                val query = command
                    .replace("search google for ", "").replace("google search ", "")
                    .replace("google par search kar ", "").replace("google ", "")
                    .replace("search for ", "").replace("search ", "").trim()
                if (query.isNotEmpty()) actionExecutor.searchGoogleDeep(query)
                else { actionExecutor.openWebsite("https://www.google.com"); "Opening Google" }
            }

            // ==================== GENERIC SEARCH ====================
            command.startsWith("search ") -> {
                val query = command.removePrefix("search ").trim()
                actionExecutor.searchGoogleDeep(query)
            }

            // ==================== OPEN ANY APP (All installed: games, social, productivity, etc.) ====================
            command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ") ||
            command.startsWith("chalu kar ") || command.startsWith("kholao ") ||
            command.endsWith(" kholo") || command.endsWith(" khol do") || command.endsWith(" khol de") ||
            command.endsWith(" khol") || command.endsWith(" open kar") || command.endsWith(" open karo") ||
            command.contains("open karo ") || command.contains("open kar ") ||
            command.contains("chalu karo ") || command.contains("chalu kar ") ||
            command.endsWith(" chalu kar") || command.endsWith(" chalu karo") -> {
                val appQuery = actionExecutor.cleanVoiceCommandToAppName(command)
                val (success, matchedLabel) = actionExecutor.openAppByName(appQuery)
                if (success) {
                    "Opening $matchedLabel"
                } else {
                    // If not an installed app, try Google Search as fallback
                    actionExecutor.searchGoogleDeep(appQuery)
                }
            }

            // ==================== DIRECT APP NAME (e.g. "BGMI", "Free Fire", "Instagram") ====================
            else -> {
                val appQuery = actionExecutor.cleanVoiceCommandToAppName(command)
                val (success, matchedLabel) = actionExecutor.openAppByName(appQuery)
                if (success) {
                    "Opening $matchedLabel"
                } else {
                    // Final fallback: Google search for anything unrecognized
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
     * - "instagram par search karo cricket"
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
            " par search ",
            " mein search kar ",
            " mein search "
        )

        for (delimiter in searchDelimiters) {
            if (command.contains(delimiter)) {
                val parts = command.split(delimiter, limit = 2)
                if (parts.size == 2) {
                    val rawApp = parts[0]
                        .replace("open ", "").replace("launch ", "").replace("start ", "")
                        .replace("chalu kar ", "").trim()
                    val query = parts[1]
                        .replace("search kar", "").replace("search", "")
                        .replace("kar", "").trim()

                    if (rawApp.isNotEmpty() && query.isNotEmpty()) {
                        return when {
                            rawApp.contains("whatsapp") -> actionExecutor.openWhatsAppChat(query)
                            rawApp.contains("youtube") -> actionExecutor.searchYouTubeDeep(query)
                            rawApp.contains("chrome") || rawApp.contains("google") || rawApp.contains("browser") -> actionExecutor.searchGoogleDeep(query)
                            rawApp.contains("play store") || rawApp.contains("store") -> actionExecutor.searchPlayStoreDeep(query)
                            rawApp.contains("maps") || rawApp.contains("navigation") -> actionExecutor.searchMapsDeep(query)
                            rawApp.contains("spotify") || rawApp.contains("music") -> actionExecutor.searchSpotifyDeep(query)
                            rawApp.contains("instagram") -> actionExecutor.searchInstagramUser(query)
                            rawApp.contains("twitter") -> actionExecutor.searchTwitter(query)
                            else -> {
                                val (success, label) = actionExecutor.openAppByName(rawApp)
                                if (success) {
                                    // Trigger NovaAccessibilityService deep search once the app opens
                                    com.example.nova.service.NovaAccessibilityService.instance?.let { a11y ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            a11y.performDeepSearch(query)
                                        }, 1200L)
                                    }
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

    private fun extractWhatsAppMessageDetails(command: String): Pair<String, String>? {
        // Pattern 1: Gujarati "whatsapp ma [target] ne [message] moklo"
        val gujPattern = Regex("(?:whatsapp|વોટ્સએપ)\\s*(?:ma|માં|par|પર)?\\s*(.+?)\\s*(?:ne|ને|ko|કો)\\s*(.+?)\\s*(?:message|મેસેજ|msg)?\\s*(?:moklo|મોકલો|send kar|bhejo|સેન્ડ કર)")
        val gujMatch = gujPattern.find(command)
        if (gujMatch != null) {
            val target = gujMatch.groupValues[1].trim()
            val msg = gujMatch.groupValues[2].trim()
            if (target.isNotEmpty() && msg.isNotEmpty()) return Pair(target, msg)
        }

        // Pattern 2: English "send message to [target] on whatsapp saying [message]"
        val enPattern = Regex("send(?: a)? (?:whatsapp )?message to (.+?)(?: on whatsapp)?(?: saying| that| with text)? (.+)")
        val enMatch = enPattern.find(command)
        if (enMatch != null) {
            val target = enMatch.groupValues[1].replace("on whatsapp", "").trim()
            val msg = enMatch.groupValues[2].trim()
            if (target.isNotEmpty() && msg.isNotEmpty()) return Pair(target, msg)
        }

        // Pattern 3: "message [target] on whatsapp [message]"
        val enPattern2 = Regex("(?:whatsapp )?message (.+?) (?:on whatsapp|saying|that) (.+)")
        val enMatch2 = enPattern2.find(command)
        if (enMatch2 != null) {
            val target = enMatch2.groupValues[1].trim()
            val msg = enMatch2.groupValues[2].trim()
            if (target.isNotEmpty() && msg.isNotEmpty()) return Pair(target, msg)
        }

        return null
    }

    private fun isMathCalculation(cmd: String): String? {
        val clean = cmd.replace("what is ", "").replace("calculate ", "")
            .replace("ketla thaye", "").replace("ketla thay", "")
            .replace("barabar", "").replace("equal to", "").trim()
        val regex = Regex("""(\d+(?:\.\d+)?)\s*([+\-*/xX]|plus|minus|into|times|divided by|વત્તા|ઓછા|ગુણ્યા|ભાગ્યા)\s*(\d+(?:\.\d+)?)""")
        val match = regex.find(clean) ?: return null
        val num1 = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2].lowercase()
        val num2 = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when {
            op in listOf("+", "plus", "વત્તા") -> num1 + num2
            op in listOf("-", "minus", "ઓછા") -> num1 - num2
            op in listOf("*", "x", "into", "times", "ગુણ્યા") -> num1 * num2
            op in listOf("/", "divided by", "ભાગ્યા") -> if (num2 != 0.0) num1 / num2 else return "Cannot divide by zero."
            else -> return null
        }
        val formattedResult = if (result % 1.0 == 0.0) result.toLong().toString() else "%.2f".format(result)
        return "The answer is $formattedResult (જવાબ $formattedResult થાય છે)"
    }

    private fun getRandomJoke(): String {
        val jokes = listOf(
            "શિક્ષક: પપ્પુ, જો તારા એક ખિસ્સામાંથી ૫૦ રૂપિયા અને બીજામાંથી ૧૦૦ રૂપિયા મળે તો તારી પાસે શું હોય?\nપપ્પુ: સાહેબ, બીજા કોઈનું પેન્ટ! 😂",
            "ડોક્ટર: તમારે રોજ ૫ કિલોમીટર ચાલવું પડશે.\nદર્દી: પણ સાહેબ, હું તો દરજી છું, હું ક્યાં ચાલીને જાવ? 😂",
            "પત્ની: સાંભળો છો, આપણા લગ્નને ૧૦ વર્ષ પૂરા થઈ ગયા!\nપતિ: હા, મને તો લાગે છે હજી ગઈકાલની જ સજા છે! 😂",
            "ગ્રાહક: ભાઈ, આ દાળમાં માખી કેમ તરી રહી છે?\nવેઈટર: કેમ કે એને હજી ડૂબતા નથી આવડતું સાહેબ! 😂",
            "પપ્પુ: પપ્પા, મને નવો ફોન અપાવો ને!\nપપ્પા: પહેલા પરીક્ષામાં પાસ થઈને બતાવ.\nપપ્પુ: અરે પપ્પા, શરત તો એવી રાખો જે મારાથી થઈ શકે! 😂"
        )
        return jokes.random()
    }
}

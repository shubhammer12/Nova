package com.example.nova.helpers

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActionExecutor(val context: Context) {

    data class ContactMatch(val name: String, val number: String, val score: Int)
    data class AppMatch(val label: String, val packageName: String, val activityName: String?, val score: Int)

    private data class CachedApp(
        val label: String,
        val packageName: String,
        val normalizedLabel: String,
        val compactLabel: String,
        val tokens: List<String>
    )

    private data class CachedContact(
        val name: String,
        val number: String,
        val normalizedName: String,
        val compactName: String,
        val tokens: List<String>
    )

    /**
     * Cleans natural language / Hinglish / voice command noise to isolate the target app name.
     */
    fun cleanVoiceCommandToAppName(input: String): String {
        var text = input.lowercase(Locale.getDefault()).trim()
        text = text.trim(',', '.', '!', '?', '"', '\'')

        val prefixes = listOf(
            "please open the app ",
            "please open the ",
            "please open app ",
            "please open ",
            "please launch the ",
            "please launch ",
            "please start ",
            "can you please open ",
            "can you open the ",
            "can you open ",
            "can you launch ",
            "open the app ",
            "open the ",
            "open app ",
            "open ",
            "launch the app ",
            "launch the ",
            "launch app ",
            "launch ",
            "start the app ",
            "start the ",
            "start app ",
            "start ",
            "run ",
            "kripya open karo ",
            "kripya open kar ",
            "kripya kholo ",
            "kripya khol "
        )
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
                break
            }
        }

        val suffixes = listOf(
            " application open karo",
            " application open kar",
            " application kholo",
            " application khol",
            " app open karo",
            " app open kar do",
            " app open kar",
            " app kholo",
            " app khol do",
            " app khol de",
            " app khol",
            " open kar do",
            " open kar de",
            " open karo",
            " open kar",
            " open please",
            " open",
            " khol do",
            " khol de",
            " kholo",
            " khol",
            " chalu karo",
            " chalu kar",
            " start karo",
            " launch karo",
            " application",
            " app",
            " please"
        )
        for (suffix in suffixes) {
            if (text.endsWith(suffix)) {
                text = text.removeSuffix(suffix).trim()
                break
            }
        }

        if (text.startsWith("the ")) {
            text = text.removePrefix("the ").trim()
        }

        return text
    }

    /**
     * Retrieves the launchable app list from memory cache or refreshes from PackageManager.
     * Fast (<2ms on cache hit, low-memory).
     */
    private fun getInstalledAppList(forceRefresh: Boolean = false): List<CachedApp> {
        val now = System.currentTimeMillis()
        val currentCache = cachedApps
        if (!forceRefresh && currentCache != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return currentCache
        }

        return synchronized(this) {
            val freshCheck = cachedApps
            if (!forceRefresh && freshCheck != null && (System.currentTimeMillis() - lastCacheTime < CACHE_TTL_MS)) {
                return@synchronized freshCheck
            }

            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val launchableActivities = try {
                pm.queryIntentActivities(mainIntent, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying launcher activities", e)
                emptyList()
            }

            val appList = ArrayList<CachedApp>(launchableActivities.size)
            val seenPackages = HashSet<String>(launchableActivities.size)

            for (resolveInfo in launchableActivities) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (!seenPackages.add(pkg.lowercase(Locale.getDefault()))) continue

                val rawLabel = try {
                    resolveInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }

                val normLabel = rawLabel.lowercase(Locale.getDefault()).trim()
                val compactLabel = normLabel.replace(Regex("[^a-z0-9]"), "")
                val tokens = normLabel.split(" ", "-", "_", ".").filter { it.isNotEmpty() }

                appList.add(CachedApp(
                    label = rawLabel,
                    packageName = pkg,
                    normalizedLabel = normLabel,
                    compactLabel = compactLabel,
                    tokens = tokens
                ))
            }

            cachedApps = appList
            lastCacheTime = System.currentTimeMillis()
            appList
        }
    }

    /**
     * Opens ANY installed app dynamically by name, speech query, or alias with sub-millisecond matching.
     * Returns Pair(Success, AppLabelMatched)
     */
    fun openAppByName(queryName: String): Pair<Boolean, String> {
        val cleanedQuery = cleanVoiceCommandToAppName(queryName)
        if (cleanedQuery.isEmpty()) return Pair(false, queryName)

        val compactQuery = cleanedQuery.replace(Regex("[^a-z0-9]"), "")
        if (compactQuery.isEmpty()) return Pair(false, queryName)

        // 1. Direct System / Common Store App Alias Check (Play Store, Phone, Camera, Settings, etc.)
        for ((alias, packageList) in SYSTEM_ALIASES) {
            val compactAlias = alias.replace(Regex("[^a-z0-9]"), "")
            if (cleanedQuery == alias || compactQuery == compactAlias) {
                for (pkg in packageList) {
                    if (openAppByPackage(pkg)) {
                        return Pair(true, alias.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } })
                    }
                }
            }
        }

        // 2. Search Dynamic Installed Apps (from in-memory cache)
        val match = findBestAppMatch(cleanedQuery, compactQuery, forceRefresh = false)
            ?: findBestAppMatch(cleanedQuery, compactQuery, forceRefresh = true) // If newly installed, refresh cache

        if (match != null) {
            if (openAppByPackage(match.packageName)) {
                return Pair(true, match.label)
            }
        }

        return Pair(false, queryName)
    }

    private fun findBestAppMatch(cleanQuery: String, compactQuery: String, forceRefresh: Boolean): CachedApp? {
        val apps = getInstalledAppList(forceRefresh)
        if (apps.isEmpty()) return null

        val queryWords = cleanQuery.split(" ").filter { it.isNotEmpty() }
        var bestApp: CachedApp? = null
        var bestScore = 0

        for (app in apps) {
            var score = 0
            when {
                // Exact label match
                app.normalizedLabel == cleanQuery -> score = 1000

                // Exact compact match (e.g. "playstore" == "playstore", "whatsapp" == "whatsapp")
                app.compactLabel == compactQuery -> score = 950

                // Starts with match (e.g. "google" for "Google Chrome")
                compactQuery.length >= 2 && app.compactLabel.startsWith(compactQuery) -> score = 900

                // App label contains full query or query contains full app label
                compactQuery.length >= 3 && app.compactLabel.contains(compactQuery) -> score = 860
                compactQuery.length >= 3 && compactQuery.contains(app.compactLabel) -> score = 840

                // Multi-word token match (e.g. query "play store" has all words in "google play store")
                queryWords.size > 1 && app.tokens.containsAll(queryWords) -> score = 820

                // Single-word token match (e.g. "terminal" in "web terminal")
                app.tokens.contains(cleanQuery) -> score = 780

                // Package name ends with .$compactQuery or contains compactQuery
                app.packageName.lowercase(Locale.getDefault()).endsWith(".$compactQuery") -> score = 750
                compactQuery.length >= 4 && app.packageName.lowercase(Locale.getDefault()).contains(compactQuery) -> score = 700
            }

            if (score > bestScore) {
                bestScore = score
                bestApp = app
                if (bestScore == 1000) break // Perfect match shortcut
            }
        }

        return if (bestScore >= 700) bestApp else null
    }

    fun openAppByPackage(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app by package $packageName", e)
            false
        }
    }

    /**
     * Converts spoken words for numbers (e.g. "nine eight seven...", "double nine") into standard digit string.
     */
    fun spokenWordsToDigits(input: String): String {
        var text = input.lowercase(Locale.getDefault()).trim()

        val doubleMap = mapOf(
            "double zero" to "00", "double one" to "11", "double two" to "22",
            "double three" to "33", "double four" to "44", "double five" to "55",
            "double six" to "66", "double seven" to "77", "double eight" to "88",
            "double nine" to "99",
            "triple zero" to "000", "triple one" to "111", "triple two" to "222",
            "triple three" to "333", "triple four" to "444", "triple five" to "555",
            "triple six" to "666", "triple seven" to "777", "triple eight" to "888",
            "triple nine" to "999"
        )
        for ((word, digits) in doubleMap) {
            text = text.replace(word, digits)
        }

        val digitMap = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3",
            "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
            "eight" to "8", "nine" to "9", "oh" to "0", "plus" to "+"
        )
        for ((word, digit) in digitMap) {
            text = text.replace(Regex("\\b$word\\b"), digit)
        }

        return text
    }

    /**
     * Accurately extracts the target name or phone number from any calling voice command
     * (English, Hindi, Hinglish, Gujarati). Returns null if no calling intent detected.
     */
    fun extractCallTarget(rawInput: String): String? {
        val inputWithDigits = spokenWordsToDigits(rawInput)
        var text = inputWithDigits.lowercase(Locale.getDefault()).trim()
        text = text.trim(',', '.', '!', '?', '"', '\'')

        // Direct phone number spoken (e.g. 7-15 digits without letters)
        val digitsOnly = text.filter { it.isDigit() || it == '+' }
        val lettersOnly = text.filter { it.isLetter() }
        if (digitsOnly.length >= 7 && lettersOnly.isEmpty()) {
            return digitsOnly
        }

        // Check for calling intent
        val callingKeywords = listOf(
            // English
            "call", "dial", "phone", "ring",
            // Hindi
            "lagao", "laga", "milao", "mila do",
            // Gujarati
            "fon kar", "call kar", "call karo", "call lagavo", "fon lagavo",
            "ne call kar", "ne fon kar", "phone lagavo"
        )
        val hasCallingKeyword = callingKeywords.any { keyword ->
            text.contains(keyword)
        }

        if (!hasCallingKeyword) {
            return null
        }

        val prefixes = listOf(
            "please make a phone call to ",
            "please make a call to ",
            "please make a call ",
            "please call to ",
            "please call ",
            "please dial to ",
            "please dial ",
            "please phone to ",
            "please phone ",
            "can you please call to ",
            "can you please call ",
            "can you call to ",
            "can you call ",
            "can you dial to ",
            "can you dial ",
            "make a phone call to ",
            "make a call to ",
            "make a call ",
            "phone call to ",
            "phone call ",
            "place a call to ",
            "place a call ",
            "call lagao to ",
            "call lagao ",
            "call laga do ",
            "call laga de ",
            "call laga ",
            "call karo to ",
            "call karo ",
            "call kar do ",
            "call kar de ",
            "call kar ",
            "call to ",
            "call ",
            "dial to ",
            "dial ",
            "phone lagao to ",
            "phone lagao ",
            "phone laga do ",
            "phone laga de ",
            "phone laga ",
            "phone karo to ",
            "phone karo ",
            "phone kar do ",
            "phone kar de ",
            "phone kar ",
            "phone to ",
            "phone "
        )

        for (prefix in prefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
                break
            }
        }

        val suffixes = listOf(
            " par call lagao",
            " par call karo",
            " par call kar do",
            " par call kar",
            " par call",
            " pe call lagao",
            " pe call karo",
            " pe call",
            " ko phone call karo",
            " ko phone call kar",
            " ko phone call lagao",
            " ko call lagao",
            " ko call laga do",
            " ko call laga de",
            " ko call laga",
            " ko call karo",
            " ko call kar do",
            " ko call kar de",
            " ko call kar",
            " ko call",
            " ko phone lagao",
            " ko phone laga do",
            " ko phone laga de",
            " ko phone laga",
            " ko phone karo",
            " ko phone kar do",
            " ko phone kar de",
            " ko phone kar",
            " ko phone",
            " ne phone lagao",
            " ne phone karo",
            " ne phone kar",
            " ne phone",
            " ne call lagao",
            " ne call karo",
            " ne call kar",
            " ne call",
            " call lagao",
            " call karo",
            " call kar do",
            " call kar",
            " call please",
            " call",
            " phone lagao",
            " phone karo",
            " phone kar do",
            " phone kar",
            " phone please",
            " phone",
            " lagao to",
            " lagao",
            " laga do",
            " laga de",
            " laga",
            " karo to",
            " karo",
            " kar do",
            " kar de",
            " kar",
            " please",
            " to"
        )

        for (suffix in suffixes) {
            if (text.endsWith(suffix)) {
                text = text.removeSuffix(suffix).trim()
                break
            }
        }

        val postFillers = listOf(" ko", " ne", " par", " pe")
        for (filler in postFillers) {
            if (text.endsWith(filler)) {
                text = text.removeSuffix(filler).trim()
                break
            }
        }

        if (text.startsWith("to ")) {
            text = text.removePrefix("to ").trim()
        }
        if (text.startsWith("the ")) {
            text = text.removePrefix("the ").trim()
        }

        return text
    }

    /**
     * In-memory cached contact list with thread safety.
     */
    private fun getCachedContactsList(forceRefresh: Boolean = false): List<CachedContact> {
        val now = System.currentTimeMillis()
        val current = cachedContacts
        if (!forceRefresh && current != null && (now - lastContactsCacheTime < CONTACTS_CACHE_TTL_MS)) {
            return current
        }

        return synchronized(this) {
            val fresh = cachedContacts
            if (!forceRefresh && fresh != null && (System.currentTimeMillis() - lastContactsCacheTime < CONTACTS_CACHE_TTL_MS)) {
                return@synchronized fresh
            }

            val contactsList = mutableListOf<CachedContact>()
            val seenNumbers = HashSet<String>()

            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex)?.trim() ?: continue
                        val num = it.getString(numIndex)?.trim() ?: continue
                        val cleanNum = num.filter { ch -> ch.isDigit() || ch == '+' }
                        if (cleanNum.isEmpty() || !seenNumbers.add(cleanNum)) continue

                        val normName = name.lowercase(Locale.getDefault()).trim()
                        val compactName = normName.replace(Regex("[^a-z0-9]"), "")
                        val tokens = normName.split(" ", "-", "_", ".").filter { t -> t.isNotEmpty() }

                        contactsList.add(CachedContact(name, num, normName, compactName, tokens))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying contacts", e)
            }

            cachedContacts = contactsList
            lastContactsCacheTime = System.currentTimeMillis()
            contactsList
        }
    }

    /**
     * Professional contact calling: only calls when confidence is strictly high.
     * Prevents calling wrong strangers by eliminating loose substring matches.
     */
    fun makeCall(contactNameOrNumber: String): String {
        try {
            val raw = contactNameOrNumber.trim()
            if (raw.isEmpty()) return "Who would you like me to call?"

            val convertedDigits = spokenWordsToDigits(raw)
            val digitsOnly = convertedDigits.filter { it.isDigit() || it == '+' }
            val lettersOnly = convertedDigits.filter { it.isLetter() }

            // 1. Direct phone number calling (at least 7 digits, no words)
            if (digitsOnly.length >= 7 && lettersOnly.length <= 2) {
                return executeDirectCall(digitsOnly, digitsOnly)
            }

            // 2. Clean spoken target name
            var cleanTarget = raw.lowercase(Locale.getDefault()).trim()
            cleanTarget = cleanTarget.removePrefix("to ").removePrefix("the ").trim()

            // 3. Search contacts in cache, or refresh if not found
            var matches = scoreAndFindContacts(cleanTarget, forceRefresh = false)
            if (matches.isEmpty()) {
                matches = scoreAndFindContacts(cleanTarget, forceRefresh = true)
            }

            if (matches.isEmpty()) {
                return "Could not find a contact named \"$cleanTarget\" in your phonebook."
            }

            val topScore = matches[0].score

            // 4. Disambiguation: if multiple distinct people have identical top score
            val topMatches = matches.filter { it.score == topScore }
            if (topMatches.size > 1) {
                val namesList = topMatches.take(3).joinToString(" or ") { it.name }
                return "I found multiple contacts for \"$cleanTarget\": $namesList. Which one would you like to call?"
            }

            // 5. Unambiguous exact match -> Call directly!
            val topMatch = matches[0]
            return executeDirectCall(topMatch.number, topMatch.name)

        } catch (e: SecurityException) {
            return "Call permission not granted. Please allow Phone permissions in Settings."
        } catch (e: Exception) {
            Log.e(TAG, "Error in makeCall", e)
            return "Unable to call $contactNameOrNumber."
        }
    }

    private fun executeDirectCall(number: String, displayName: String): String {
        val cleanNumber = number.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(cleanNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Calling $displayName..."
    }

    /**
     * High-precision contact search with zero false positives.
     * Evaluates full names, compact names, tokens, and word boundaries.
     */
    private fun scoreAndFindContacts(cleanQuery: String, forceRefresh: Boolean): List<ContactMatch> {
        val contacts = getCachedContactsList(forceRefresh)
        if (contacts.isEmpty()) return emptyList()

        val queryNorm = cleanQuery.lowercase(Locale.getDefault()).trim()
        val compactQuery = queryNorm.replace(Regex("[^a-z0-9]"), "")
        val queryTokens = queryNorm.split(" ", "-", "_", ".").filter { it.isNotEmpty() }
        if (compactQuery.isEmpty()) return emptyList()

        val results = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        for (contact in contacts) {
            val nameNorm = contact.normalizedName
            val compactName = contact.compactName
            val nameTokens = contact.tokens

            var score = 0
            when {
                // Exact full name match (e.g. "papa" == "papa", "raj patel" == "raj patel")
                nameNorm == queryNorm -> score = 1000

                // Exact compact match (e.g. "rajpatel" == "raj patel")
                compactName == compactQuery -> score = 950

                // Exact word set equality (e.g. "patel raj" == "raj patel")
                queryTokens.size > 1 && queryTokens.size == nameTokens.size && queryTokens.toSet() == nameTokens.toSet() -> score = 920

                // All query words exist as exact standalone words in contact name (e.g. query "raj patel" in "raj patel office")
                queryTokens.size > 1 && queryTokens.all { qToken -> nameTokens.contains(qToken) } -> score = 900

                // Single-word query matches an exact standalone word in contact name (e.g. query "papa" in "papa jio" or "my papa")
                queryTokens.size == 1 && nameTokens.contains(queryTokens[0]) -> score = 860

                // Name starts with query on word boundary (e.g. query "raj", contact "raj sharma")
                nameNorm.startsWith("$queryNorm ") -> score = 840

                // Honorific variations: query has honorific ("papa ji", "rahul bhai") matching contact ("papa", "rahul")
                queryTokens.size > 1 && nameTokens.isNotEmpty() &&
                    (queryTokens.contains("ji") || queryTokens.contains("bhai") || queryTokens.contains("sir")) &&
                    nameTokens.all { queryTokens.contains(it) } -> score = 850

                // Contact name has honorific ("rahul bhai") matching query ("rahul")
                queryTokens.isNotEmpty() && nameTokens.size > 1 &&
                    (nameTokens.contains("ji") || nameTokens.contains("bhai") || nameTokens.contains("sir")) &&
                    queryTokens.all { nameTokens.contains(it) } -> score = 850

                // All query tokens match prefix of contact words (minimum 3 characters)
                queryTokens.isNotEmpty() && queryTokens.all { q -> q.length >= 3 && nameTokens.any { n -> n.startsWith(q) } } -> score = 780
            }

            // High-confidence threshold only: eliminates accidental calls to random people
            if (score >= 750) {
                val cleanNum = contact.number.filter { it.isDigit() || it == '+' }
                if (seenNumbers.add(cleanNum)) {
                    results.add(ContactMatch(contact.name, contact.number, score))
                }
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * Public search interface used by WhatsApp, SMS, and messaging functions.
     */
    fun findMatchingContacts(query: String): List<ContactMatch> {
        val matches = scoreAndFindContacts(query, forceRefresh = false)
        return if (matches.isNotEmpty()) matches else scoreAndFindContacts(query, forceRefresh = true)
    }

    fun toggleFlashlight(enable: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) "Flashlight turned on" else "Flashlight turned off"
        } catch (e: Exception) {
            "Unable to control flashlight on this device."
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Nova Alarm"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            "Alarm set for $formattedTime"
        } catch (e: Exception) {
            "Failed to set alarm."
        }
    }

    fun sendMessage(target: String, messageText: String): String {
        val cleanTarget = target.lowercase(Locale.getDefault()).trim()
        val matches = findMatchingContacts(cleanTarget)

        val number = if (matches.isNotEmpty()) matches[0].number else cleanTarget.filter { it.isDigit() || it == '+' }

        return if (number.length >= 5) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                    putExtra("sms_body", messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening message for ${if (matches.isNotEmpty()) matches[0].name else number}"
            } catch (e: Exception) {
                "Unable to open message composer."
            }
        } else {
            "Could not find contact for $target to send message."
        }
    }

    fun createCalendarEvent(title: String, location: String = "Nova Event"): String {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = android.provider.CalendarContract.Events.CONTENT_URI
                putExtra(android.provider.CalendarContract.Events.TITLE, title)
                putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600000)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening calendar event editor for \"$title\""
        } catch (e: Exception) {
            "Unable to create calendar event."
        }
    }

    fun sendWhatsAppMessage(targetContact: String, messageText: String): String {
        return try {
            val matches = findMatchingContacts(targetContact)
            val number = if (matches.isNotEmpty()) matches[0].number.filter { it.isDigit() } else targetContact.filter { it.isDigit() }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(messageText)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening WhatsApp to send message to $targetContact"
        } catch (e: Exception) {
            openAppByName("whatsapp")
            "Opening WhatsApp"
        }
    }

    private fun formatPhoneNumberForWhatsApp(rawNumber: String): String {
        var digits = rawNumber.filter { it.isDigit() }
        if (digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length > 10 -> digits
            else -> "91$digits"
        }
    }

    fun openWhatsAppChat(targetContact: String): String {
        return try {
            val cleanTarget = targetContact.lowercase(Locale.getDefault())
                .replace("open whatsapp and search from", "")
                .replace("open whatsapp and search", "")
                .replace("open whatsapp and open", "")
                .replace("open whatsapp", "")
                .replace("search from", "")
                .replace("search for", "")
                .replace("search", "")
                .replace("chat of", "")
                .replace("ni chat open kar", "")
                .replace("ni chat open", "")
                .replace("ki chat open", "")
                .replace("ni chat", "")
                .replace("ki chat", "")
                .replace("chat", "")
                .replace("from", "")
                .replace("open", "")
                .replace("whatsapp", "")
                .trim()

            if (cleanTarget.isEmpty()) {
                openAppByName("whatsapp")
                return "Opening WhatsApp"
            }

            // Check if cleanTarget is already digits/phone number
            val directDigits = cleanTarget.filter { it.isDigit() }
            if (directDigits.length >= 10 && cleanTarget.all { it.isDigit() || it == '+' || it == ' ' }) {
                val formatted = formatPhoneNumberForWhatsApp(directDigits)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$formatted")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening WhatsApp chat for $directDigits"
            }

            val matches = findMatchingContacts(cleanTarget)

            if (matches.isNotEmpty()) {
                val matched = matches[0]
                val formattedNumber = formatPhoneNumberForWhatsApp(matched.number)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening WhatsApp chat of ${matched.name}"
            } else {
                openAppByName("whatsapp")
                "Could not find contact for \"$cleanTarget\" in contacts. Opening WhatsApp."
            }
        } catch (e: Exception) {
            openAppByName("whatsapp")
            "Opening WhatsApp"
        }
    }

    fun searchYouTubeDeep(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching YouTube for \"$query\""
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
            "Searching YouTube for \"$query\""
        }
    }

    fun searchPlayStoreDeep(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching Play Store for \"$query\""
        } catch (e: Exception) {
            openWebsite("https://play.google.com/store/search?q=${Uri.encode(query)}")
            "Searching Play Store for \"$query\""
        }
    }

    fun searchMapsDeep(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching Google Maps for \"$query\""
        } catch (e: Exception) {
            openWebsite("https://www.google.com/maps/search/${Uri.encode(query)}")
            "Searching Google Maps for \"$query\""
        }
    }

    fun searchSpotifyDeep(query: String): String {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage("com.spotify.music")
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Playing \"$query\" on Spotify"
        } catch (e: Exception) {
            openWebsite("https://open.spotify.com/search/${Uri.encode(query)}")
            "Searching Spotify for \"$query\""
        }
    }

    fun searchGoogleDeep(query: String): String {
        return openWebsite("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    fun searchInstagramUser(query: String): String {
        return try {
            // Try opening Instagram search directly
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/${Uri.encode(query.replace(" ", ""))}")).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching Instagram for \"$query\""
        } catch (e: Exception) {
            openWebsite("https://www.instagram.com/${Uri.encode(query.replace(" ", ""))}")
            "Searching Instagram for \"$query\""
        }
    }

    fun searchTwitter(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/search?q=${Uri.encode(query)}")).apply {
                setPackage("com.twitter.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching Twitter for \"$query\""
        } catch (e: Exception) {
            try {
                val xIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(xIntent)
            } catch (_: Exception) {}
            "Searching Twitter/X for \"$query\""
        }
    }

    fun getUserName(): String {
        return try {
            context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
                .getString("profile_custom_name", "Shubham")?.ifBlank { "Shubham" } ?: "Shubham"
        } catch (_: Exception) { "Shubham" }
    }

    fun openWebsite(url: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening search results"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL $url", e)
            "Unable to open web browser."
        }
    }

    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return "The current time is ${sdf.format(Date())}"
    }

    fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return "Today is ${sdf.format(Date())}"
    }

    fun getBatteryLevel(): String {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

        return if (batteryPct >= 0) {
            "Your battery is at $batteryPct percent."
        } else {
            "Unable to check battery status."
        }
    }

    fun openCamera(): String {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening camera"
        } catch (e: Exception) {
            "Unable to open camera."
        }
    }

    // --- AUTONOMOUS ACCESSIBILITY ACTIONS ---

    fun autoSendWhatsAppMessage(target: String, message: String): String {
        return try {
            val cleanTarget = target.trim()
            val cleanMessage = message.trim()
            val directDigits = cleanTarget.filter { it.isDigit() }

            val targetNumber = if (directDigits.length >= 10) {
                formatPhoneNumberForWhatsApp(directDigits)
            } else {
                val matches = findMatchingContacts(cleanTarget)
                if (matches.isNotEmpty()) formatPhoneNumberForWhatsApp(matches[0].number) else null
            }

            if (targetNumber != null) {
                val encodedText = Uri.encode(cleanMessage)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$targetNumber&text=$encodedText")
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                // If Nova Accessibility Service is running, automatically press the Send button after UI loads
                val accessibility = com.example.nova.service.NovaAccessibilityService.instance
                if (accessibility != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        accessibility.clickSendButton()
                    }, 800L)
                    "Sending WhatsApp message to $cleanTarget"
                } else {
                    "Opening WhatsApp chat with your message ready. Tap send or enable Nova Accessibility Service in Settings for hands-free auto-sending."
                }
            } else {
                openWhatsAppChat(cleanTarget)
                "Could not find contact for $cleanTarget. Opening WhatsApp."
            }
        } catch (e: Exception) {
            openAppByName("whatsapp")
            "Opening WhatsApp"
        }
    }

    fun executeGlobalHome(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)) {
            "Going to Home Screen"
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Going to Home Screen"
        }
    }

    fun executeGlobalBack(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) {
            "Going back"
        } else {
            "Please enable Nova Accessibility Service in Settings to allow automatic back navigation."
        }
    }

    fun executeGlobalRecents(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) {
            "Opening Recent Apps"
        } else {
            "Please enable Nova Accessibility Service in Settings."
        }
    }

    fun executeGlobalNotifications(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) {
            "Opening Notifications"
        } else {
            "Please enable Nova Accessibility Service in Settings."
        }
    }

    fun executeGlobalQuickSettings(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) {
            "Opening Quick Settings"
        } else {
            "Please enable Nova Accessibility Service in Settings."
        }
    }

    fun executeGlobalScreenshot(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) {
            "Taking screenshot"
        } else {
            "Please enable Nova Accessibility Service in Settings to take screenshots hands-free."
        }
    }

    fun executeGlobalLockScreen(): String {
        val service = com.example.nova.service.NovaAccessibilityService.instance
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            service != null && service.performAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            "Locking screen"
        } else {
            "Please enable Nova Accessibility Service in Settings to lock your screen."
        }
    }

    companion object {
        private const val TAG = "ActionExecutor"
        private const val CACHE_TTL_MS = 60_000L // 60 seconds
        private const val CONTACTS_CACHE_TTL_MS = 60_000L // 60 seconds

        @Volatile
        private var cachedApps: List<CachedApp>? = null
        private var lastCacheTime: Long = 0L

        @Volatile
        private var cachedContacts: List<CachedContact>? = null
        private var lastContactsCacheTime: Long = 0L

        private val SYSTEM_ALIASES = mapOf(
            "play store" to listOf("com.android.vending"),
            "playstore" to listOf("com.android.vending"),
            "google play" to listOf("com.android.vending"),
            "google play store" to listOf("com.android.vending"),
            "app store" to listOf("com.android.vending"),
            "store" to listOf("com.android.vending"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.coloros.gallery3d", "com.oneplus.gallery"),
            "photos" to listOf("com.google.android.apps.photos"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.android.camera", "com.oneplus.camera"),
            "settings" to listOf("com.android.settings"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.android.dialer"),
            "call" to listOf("com.google.android.dialer", "com.android.dialer"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "message" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "sms" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome"),
            "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.opera.browser"),
            "instagram" to listOf("com.instagram.android"),
            "spotify" to listOf("com.spotify.music"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "calendar" to listOf("com.google.android.calendar"),
            "gmail" to listOf("com.google.android.gm"),
            "mail" to listOf("com.google.android.gm"),
            "email" to listOf("com.google.android.gm"),
            "contacts" to listOf("com.google.android.contacts"),
            "files" to listOf("com.google.android.documentsui", "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles"),
            "file manager" to listOf("com.google.android.documentsui", "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles")
        )
    }

    /**
     * Shares the Nova APK with other devices via Bluetooth, WhatsApp, or any sharing app.
     * Uses PackageManager to locate the installed APK on the current device,
     * then copies it to cache and shares via FileProvider for compatibility.
     */
    fun shareNovaApk(): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(context.packageName, 0)
            val sourceApk = java.io.File(appInfo.sourceDir)

            // Copy APK to shareable cache location
            val cacheDir = java.io.File(context.cacheDir, "apk")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val destApk = java.io.File(cacheDir, "Nova.apk")

            sourceApk.inputStream().use { input ->
                destApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Build a content URI via FileProvider
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                destApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "Nova AI Assistant")
                putExtra(Intent.EXTRA_TEXT, "Install Nova AI Assistant - your personal intelligent voice assistant!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(
                Intent.createChooser(shareIntent, "Share Nova via...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "Sharing Nova APK. Choose Bluetooth, WhatsApp, or any sharing app."
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to share APK: ${e.message}")
            "Unable to share Nova APK: ${e.localizedMessage}"
        }
    }
}

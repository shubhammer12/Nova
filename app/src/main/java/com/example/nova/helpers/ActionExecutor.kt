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
     * Smart contact calling with fuzzy matching, full name priority & multi-match disambiguation.
     */
    fun makeCall(contactNameOrNumber: String): String {
        try {
            // Clean Gujarati / Hinglish / English calling command prefixes & suffixes
            val cleanTarget = contactNameOrNumber.lowercase(Locale.getDefault())
                .replace("open whatsapp and search from", "")
                .replace("open whatsapp and search", "")
                .replace("open whatsapp", "")
                .replace("phone lagao", "")
                .replace("phone laga", "")
                .replace("phone kar", "")
                .replace("phone karo", "")
                .replace("call karo", "")
                .replace("call kar", "")
                .replace("call lagao", "")
                .replace("call laga", "")
                .replace("ne phone kar", "")
                .replace("ne phone karo", "")
                .replace("ne phone laga", "")
                .replace("ne call kar", "")
                .replace("ne call karo", "")
                .replace("ko call karo", "")
                .replace("ko call kar", "")
                .replace("ko call", "")
                .replace("ne call", "")
                .replace("ne phone", "")
                .replace("ko phone", "")
                .replace("bhai ne", "")
                .replace("bhai ko", "")
                .replace("bhai", "")
                .replace("sir ne", "")
                .replace("sir ko", "")
                .replace("sir", "")
                .replace("call to", "")
                .replace("dial to", "")
                .replace("call", "")
                .replace("dial", "")
                .replace("phone", "")
                .trim()

            val digitsOnly = cleanTarget.filter { it.isDigit() || it == '+' }

            // If direct phone digits spoken
            if (digitsOnly.length >= 5) {
                return executeDirectCall(digitsOnly, cleanTarget)
            }

            // Perform Fuzzy & Disambiguation Contact Search
            val matches = findMatchingContacts(cleanTarget)

            if (matches.isEmpty()) {
                return "Could not find a contact named \"$cleanTarget\" in your phonebook."
            }

            val topScore = matches[0].score

            // If single top match or top match score is strictly greater than 2nd match score -> Call directly!
            if (matches.size == 1 || matches[1].score < topScore) {
                val topMatch = matches[0]
                return executeDirectCall(topMatch.number, topMatch.name)
            }

            // Multiple close matches with identical top score (e.g. "Raj V" vs "Raj Jadeja" when query is just "Raj")
            val topMatches = matches.filter { it.score == topScore }
            val namesList = topMatches.take(3).joinToString(" or ") { it.name }
            return "I found multiple contacts for $cleanTarget: $namesList. Which one would you like to call?"
        } catch (e: SecurityException) {
            return "Call permission not granted. Please allow Phone permissions in Settings."
        } catch (e: Exception) {
            return "Unable to call $contactNameOrNumber."
        }
    }

    private fun executeDirectCall(number: String, displayName: String): String {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Calling $displayName..."
    }

    fun findMatchingContacts(query: String): List<ContactMatch> {
        val cleanQuery = query.lowercase(Locale.getDefault())
            .replace("ne call kar", "")
            .replace("ko call kar", "")
            .replace("ne call", "")
            .replace("ko call", "")
            .replace("call kar", "")
            .replace("call karo", "")
            .replace("phone kar", "")
            .replace("phone karo", "")
            .replace("bhai", "")
            .replace("sir", "")
            .replace("call", "")
            .replace("dial", "")
            .trim()

        if (cleanQuery.isEmpty()) return emptyList()

        val results = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: continue
                val num = it.getString(numIndex) ?: continue
                val lowerName = name.lowercase(Locale.getDefault())

                var score = 0
                val cleanQueryNorm = cleanQuery.lowercase(Locale.getDefault()).trim()
                val lowerNameNorm = lowerName.trim()

                val queryTokens = cleanQueryNorm.split(" ").filter { t -> t.isNotEmpty() }
                val nameTokens = lowerNameNorm.split(" ").filter { t -> t.isNotEmpty() }

                when {
                    // 1. Exact Full Name Match (e.g. "raj v" == "raj v") -> Highest Priority (100)
                    lowerNameNorm == cleanQueryNorm -> score = 100

                    // 2. Exact Token Count & Token Equality (e.g. ["raj", "v"] == ["raj", "v"]) -> Score 95
                    queryTokens.size == nameTokens.size && queryTokens.zip(nameTokens).all { (q, n) -> q == n } -> score = 95

                    // 3. All query tokens match exact tokens in full name (e.g. query "raj v" in "raj v mer") -> Score 90
                    queryTokens.size > 1 && queryTokens.all { qToken -> nameTokens.contains(qToken) } -> score = 90

                    // 4. Exact First Name + Suffix/Surname Prefix match (e.g. "raj v" matches "raj vishwakarma") -> Score 85
                    queryTokens.size > 1 && queryTokens.mapIndexed { idx, qToken ->
                        if (idx < nameTokens.size) nameTokens[idx].startsWith(qToken) else false
                    }.all { it } -> score = 85

                    // 5. Query is exact prefix of full name -> Score 80
                    lowerNameNorm.startsWith(cleanQueryNorm) -> score = 80

                    // 6. All query tokens match prefix of name tokens
                    queryTokens.isNotEmpty() && queryTokens.all { qToken -> nameTokens.any { nToken -> nToken.startsWith(qToken) } } -> score = 70

                    // 7. Single token exact match (e.g. "raj" == "raj")
                    queryTokens.size == 1 && nameTokens.contains(queryTokens[0]) -> score = 60

                    // 8. Substring match
                    lowerNameNorm.contains(cleanQueryNorm) -> score = 50
                }

                if (score > 0 && seenNumbers.add(num)) {
                    results.add(ContactMatch(name, num, score))
                }
            }
        }

        // Sort by highest match score
        return results.sortedByDescending { it.score }
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

    companion object {
        private const val TAG = "ActionExecutor"
        private const val CACHE_TTL_MS = 60_000L // 60 seconds

        @Volatile
        private var cachedApps: List<CachedApp>? = null
        private var lastCacheTime: Long = 0L

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
}

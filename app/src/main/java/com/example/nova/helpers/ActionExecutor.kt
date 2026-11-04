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

    /**
     * Opens an app by searching all installed, system, web app shortcuts, PWAs, and single-letter apps on the phone.
     * Returns Pair(Success, AppLabelMatched)
     */
    fun openAppByName(queryName: String): Pair<Boolean, String> {
        val cleanQuery = queryName.lowercase(Locale.getDefault()).trim()
        if (cleanQuery.isEmpty()) return Pair(false, queryName)

        val pm = context.packageManager
        val isSingleLetterQuery = cleanQuery.length == 1

        // Handle common system app aliases ONLY when exact alias or exact word match
        val aliasPackageMap = mapOf(
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery", "com.coloros.gallery3d", "com.oneplus.gallery"),
            "photos" to listOf("com.google.android.apps.photos"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.android.camera", "com.oneplus.camera"),
            "settings" to listOf("com.android.settings"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.android.dialer"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "chrome" to listOf("com.android.chrome"),
            "instagram" to listOf("com.instagram.android"),
            "spotify" to listOf("com.spotify.music"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
            "calendar" to listOf("com.google.android.calendar"),
            "gmail" to listOf("com.google.android.gm"),
            "contacts" to listOf("com.google.android.contacts"),
            "files" to listOf("com.google.android.documentsui", "com.google.android.apps.nbu.files")
        )

        for ((alias, packageList) in aliasPackageMap) {
            if (cleanQuery == alias || cleanQuery.split(" ").contains(alias)) {
                for (pkg in packageList) {
                    if (openAppByPackage(pkg)) {
                        return Pair(true, alias.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        val appMatches = mutableListOf<AppMatch>()
        val seenPackages = mutableSetOf<String>()

        // 1. Search all launcher activities (includes PWA shortcuts & installed web apps)
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchableApps = pm.queryIntentActivities(mainIntent, 0)

            for (resolveInfo in launchableApps) {
                val rawLabel = resolveInfo.loadLabel(pm).toString()
                val labelNorm = rawLabel.lowercase(Locale.getDefault()).trim()
                val pkgNorm = resolveInfo.activityInfo.packageName.lowercase(Locale.getDefault())
                val activityName = resolveInfo.activityInfo.name

                val tokens = labelNorm.split(" ", "-", "_", ".").filter { it.isNotEmpty() }

                var score = 0
                when {
                    // Exact label match (e.g. query "x" == label "x" or query "web terminal" == label "web terminal")
                    labelNorm == cleanQuery -> score = 100

                    // Exact word token match (e.g. query "terminal" matches word "terminal" in "web terminal")
                    tokens.contains(cleanQuery) -> score = 90

                    // Package suffix match
                    pkgNorm.endsWith(".$cleanQuery") -> score = 85

                    // Starts-with match (Only for multi-letter queries)
                    !isSingleLetterQuery && labelNorm.startsWith(cleanQuery) -> score = 80

                    // Ends-with match (Only for multi-letter queries)
                    !isSingleLetterQuery && labelNorm.endsWith(cleanQuery) -> score = 70

                    // Substring match (Only for multi-letter queries)
                    !isSingleLetterQuery && labelNorm.contains(cleanQuery) -> score = 50
                }

                if (score > 0) {
                    seenPackages.add(pkgNorm)
                    appMatches.add(AppMatch(rawLabel, resolveInfo.activityInfo.packageName, activityName, score))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launchable apps", e)
        }

        // 2. Search all installed application packages (for web app shortcuts or hidden system apps)
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkgNorm = appInfo.packageName.lowercase(Locale.getDefault())
                if (seenPackages.contains(pkgNorm)) continue

                val rawLabel = appInfo.loadLabel(pm).toString()
                val labelNorm = rawLabel.lowercase(Locale.getDefault()).trim()
                val tokens = labelNorm.split(" ", "-", "_", ".").filter { it.isNotEmpty() }

                var score = 0
                when {
                    labelNorm == cleanQuery -> score = 100
                    tokens.contains(cleanQuery) -> score = 90
                    pkgNorm.endsWith(".$cleanQuery") -> score = 85
                    !isSingleLetterQuery && labelNorm.startsWith(cleanQuery) -> score = 80
                    !isSingleLetterQuery && labelNorm.contains(cleanQuery) -> score = 50
                }

                if (score > 0) {
                    seenPackages.add(pkgNorm)
                    appMatches.add(AppMatch(rawLabel, appInfo.packageName, null, score))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed applications", e)
        }

        // Sort by highest match score
        appMatches.sortByDescending { it.score }

        if (appMatches.isNotEmpty()) {
            val topMatch = appMatches[0]
            if (launchAppMatch(topMatch)) {
                return Pair(true, topMatch.label)
            }
        }

        return Pair(false, queryName)
    }

    private fun launchAppMatch(appMatch: AppMatch): Boolean {
        // Try ComponentName launch first (Crucial for Web Apps, PWAs and Web Terminals)
        if (appMatch.activityName != null) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(appMatch.packageName, appMatch.activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Fallback to launch intent
            }
        }
        return openAppByPackage(appMatch.packageName)
    }

    private fun openAppByPackage(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else false
        } catch (e: Exception) {
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
    }
}

package com.example.nova.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Nova AI Accessibility Service
 * Empowers Nova to autonomously navigate apps, type messages, click buttons,
 * and complete multi-step tasks dynamically when voice commands are received.
 */
class NovaAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Nova Accessibility Service Connected & Ready for autonomous task execution.")

        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event handling for accessibility automation
    }

    override fun onInterrupt() {
        Log.w(TAG, "Nova Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "Nova Accessibility Service destroyed.")
    }

    /**
     * Executes system global actions (Home, Back, Recent Apps, Notifications, Screenshot, Lock Screen).
     */
    fun performAction(actionId: Int): Boolean {
        return try {
            performGlobalAction(actionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing global action: $actionId", e)
            false
        }
    }

    /**
     * Clicks a button or element matching text or content description.
     */
    fun clickByText(query: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanQuery = query.trim().lowercase()

        val foundNodes = mutableListOf<AccessibilityNodeInfo>()
        collectMatchingNodes(root, cleanQuery, exact, foundNodes)

        for (node in foundNodes) {
            if (performClickOnNodeOrParent(node)) {
                return true
            }
        }
        return false
    }

    /**
     * Types text into the active/first editable text field and optionally clicks the "Send" button.
     */
    fun typeText(textToType: String, autoClickSend: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        val root = rootInActiveWindow
        if (root == null) {
            onComplete?.invoke(false)
            return
        }

        val editableNode = findEditableNode(root)
        if (editableNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val typed = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (autoClickSend) {
                // Wait briefly for UI to update send button icon, then click Send
                mainHandler.postDelayed({
                    val clickedSend = clickSendButton()
                    onComplete?.invoke(typed && clickedSend)
                }, 350L)
            } else {
                onComplete?.invoke(typed)
            }
        } else {
            Log.w(TAG, "No editable field found on screen")
            onComplete?.invoke(false)
        }
    }

    /**
     * Automatically finds and clicks standard "Send" buttons across WhatsApp, Telegram, SMS, etc.
     */
    fun clickSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false

        // 1. Try common send view IDs
        val sendIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "org.telegram.messenger:id/send_button"
        )
        for (id in sendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                if (performClickOnNodeOrParent(nodes[0])) return true
            }
        }

        // 2. Try by Content Description or Text
        val sendKeywords = listOf("send", "મોકલો", "भेजें", "send message", "send sms")
        for (keyword in sendKeywords) {
            if (clickByText(keyword)) return true
        }

        return false
    }

    /**
     * Recursively searches for editable input field.
     */
    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Recursively traverses accessibility tree to find nodes matching query.
     */
    private fun collectMatchingNodes(
        node: AccessibilityNodeInfo,
        query: String,
        exact: Boolean,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val matches = if (exact) {
            text == query || desc == query
        } else {
            text.contains(query) || desc.contains(query)
        }

        if (matches) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMatchingNodes(child, query, exact, results)
        }
    }

    /**
     * Autonomously locates search bars/buttons in the active third-party app (YouTube, Play Store, Settings, Chrome, etc.),
     * types the search query, and triggers search.
     */
    fun performDeepSearch(searchQuery: String, onComplete: ((Boolean) -> Unit)? = null) {
        val root = rootInActiveWindow
        if (root == null) {
            onComplete?.invoke(false)
            return
        }

        // 1. First check if an editable search field is already visible and active
        val editable = findEditableNode(root)
        if (editable != null) {
            typeIntoSearchField(editable, searchQuery, onComplete)
            return
        }

        // 2. Look for search buttons / icons across common apps
        val searchKeywords = listOf(
            "search", "શોધો", "सर्च", "खोजें", "find",
            "search query", "search youtube", "search google",
            "search apps", "search bar", "search or type url"
        )
        val searchIds = listOf(
            "com.google.android.youtube:id/menu_item_0",
            "com.google.android.youtube:id/search_button",
            "com.android.vending:id/search_box_idle_text",
            "com.android.vending:id/search_bar",
            "com.google.android.googlequicksearchbox:id/googleapp_search_box",
            "org.chromium.chrome:id/search_box_text",
            "org.chromium.chrome:id/url_bar",
            "com.spotify.music:id/find_search_field",
            "com.whatsapp:id/menuitem_search",
            "com.whatsapp.w4b:id/menuitem_search",
            "android:id/search_button",
            "android:id/search_src_text"
        )

        // Try search view IDs first
        for (id in searchIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                if (performClickOnNodeOrParent(nodes[0])) {
                    mainHandler.postDelayed({
                        val newRoot = rootInActiveWindow
                        val newEditable = newRoot?.let { findEditableNode(it) }
                        if (newEditable != null) {
                            typeIntoSearchField(newEditable, searchQuery, onComplete)
                        } else {
                            onComplete?.invoke(false)
                        }
                    }, 400L)
                    return
                }
            }
        }

        // Try text / content descriptions
        for (kw in searchKeywords) {
            val matchingNodes = mutableListOf<AccessibilityNodeInfo>()
            collectMatchingNodes(root, kw, exact = false, matchingNodes)
            for (node in matchingNodes) {
                if (performClickOnNodeOrParent(node)) {
                    mainHandler.postDelayed({
                        val newRoot = rootInActiveWindow
                        val newEditable = newRoot?.let { findEditableNode(it) }
                        if (newEditable != null) {
                            typeIntoSearchField(newEditable, searchQuery, onComplete)
                        } else {
                            onComplete?.invoke(false)
                        }
                    }, 400L)
                    return
                }
            }
        }

        Log.w(TAG, "No search button or field found on screen")
        onComplete?.invoke(false)
    }

    private fun typeIntoSearchField(
        fieldNode: AccessibilityNodeInfo,
        query: String,
        onComplete: ((Boolean) -> Unit)?
    ) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }
        fieldNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val typed = fieldNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        mainHandler.postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fieldNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
            clickByText("search")
            onComplete?.invoke(typed)
        }, 350L)
    }

    /**
     * Dispatches an exact coordinate click gesture on screen.
     */
    fun performGestureClick(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return false
        }
        val path = android.graphics.Path().apply {
            moveTo(x, y)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Performs a scroll gesture forward or backward on the active window.
     */
    fun performScroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollable.performAction(action)
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Clicks the node or its closest clickable parent.
     */
    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        private const val TAG = "NovaAccessibility"
        var instance: NovaAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}

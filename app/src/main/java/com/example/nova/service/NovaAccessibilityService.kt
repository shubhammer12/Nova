package com.example.nova.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
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
        // Event processing hook for active window changes
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

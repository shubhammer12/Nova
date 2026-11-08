package com.example.nova.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nova.service.NovaBackgroundVoiceService
import com.example.nova.utils.PermissionUtils

/**
 * Automatically resumes Nova Background Voice Assistant on phone startup if enabled by user.
 */
class NovaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
            val isVoiceServiceEnabled = prefs.getBoolean("background_voice_enabled", false)

            if (isVoiceServiceEnabled && PermissionUtils.hasAllPermissions(context)) {
                NovaBackgroundVoiceService.start(context)
            }
        }
    }
}

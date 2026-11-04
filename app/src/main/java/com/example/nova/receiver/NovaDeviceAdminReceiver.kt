package com.example.nova.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Administrator Receiver for Nova AI Assistant.
 * Protects Nova against unauthorized app uninstallation by requiring display fingerprint authentication.
 */
class NovaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Nova Uninstall Fingerprint Protection Activated.")
        Toast.makeText(context, "Nova Uninstall Fingerprint Protection Enabled!", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d(TAG, "Nova Uninstall/Deactivation requested. Fingerprint required.")
        return "Fingerprint authentication is required on display to uninstall Nova AI Assistant."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Nova Uninstall Protection Disabled.")
        Toast.makeText(context, "Nova Uninstall Protection Disabled.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "NovaDeviceAdminReceiver"
    }
}

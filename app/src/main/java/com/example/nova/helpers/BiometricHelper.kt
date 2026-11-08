package com.example.nova.helpers

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Biometric Fingerprint Authentication Helper for Nova AI Assistant.
 * Uses Android Hardware BiometricPrompt to scan display fingerprint before uninstallation.
 */
class BiometricHelper(private val activity: Activity) {

    fun isBiometricAvailable(): Boolean {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    fun authenticateForUninstall(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cancellationSignal = CancellationSignal()
            val executor = activity.mainExecutor

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString?.toString() ?: "Authentication Canceled")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Fingerprint not recognized. Try again.")
                }
            }

            try {
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle("Confirm Fingerprint to Uninstall Nova")
                    .setSubtitle("Scan your display fingerprint to authorize Nova uninstallation")
                    .setNegativeButton("Cancel", executor) { _, _ ->
                        onError("Uninstallation Canceled")
                    }
                    .build()

                prompt.authenticate(cancellationSignal, executor, callback)
            } catch (_: Exception) {
                // Fallback for devices without biometric hardware or lock screen
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }
}

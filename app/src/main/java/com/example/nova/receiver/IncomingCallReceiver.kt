package com.example.nova.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.nova.helpers.CallAnnouncerManager

/**
 * BroadcastReceiver listening for TelephonyManager.ACTION_PHONE_STATE_CHANGED.
 * When an incoming call arrives (RINGING), it triggers CallAnnouncerManager to speak the caller's name.
 * When the call is answered (OFFHOOK) or ended/rejected (IDLE), it stops speaking immediately.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IncomingCallReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state changed: $state (lastState: $lastState)")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d(TAG, "Incoming ringing call detected from: $incomingNumber")
                CallAnnouncerManager.onCallRinging(context, incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call picked up / answered (OFFHOOK). Halting announcement.")
                CallAnnouncerManager.onCallEndedOrAnswered()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call ended or idle (IDLE). Halting announcement.")
                CallAnnouncerManager.onCallEndedOrAnswered()
            }
        }

        lastState = state
    }
}

package com.example.nova.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.CountDownTimer
import androidx.core.app.NotificationCompat
import com.example.nova.R
import java.util.Locale

/**
 * Timer Manager component for Nova AI Voice Assistant.
 * Handles countdown timer creation, cancellation, status checking, and completion alerts.
 */
class TimerManager(private val context: Context) {

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingMillis: Long = 0L
    private var totalDurationMillis: Long = 0L

    fun startTimer(durationMinutes: Int, label: String = "Nova Timer", onTick: (remainingSeconds: Long) -> Unit = {}, onFinish: () -> Unit = {}): String {
        cancelTimer()

        totalDurationMillis = durationMinutes * 60 * 1000L
        remainingMillis = totalDurationMillis
        isTimerRunning = true

        countDownTimer = object : CountDownTimer(totalDurationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                onTick(millisUntilFinished / 1000L)
            }

            override fun onFinish() {
                isTimerRunning = false
                remainingMillis = 0L
                showTimerCompletedNotification(label)
                onFinish()
            }
        }.start()

        return "Timer set for $durationMinutes ${if (durationMinutes == 1) "minute" else "minutes"}"
    }

    fun startTimerSeconds(durationSeconds: Int, label: String = "Nova Timer"): String {
        cancelTimer()

        totalDurationMillis = durationSeconds * 1000L
        remainingMillis = totalDurationMillis
        isTimerRunning = true

        countDownTimer = object : CountDownTimer(totalDurationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
            }

            override fun onFinish() {
                isTimerRunning = false
                remainingMillis = 0L
                showTimerCompletedNotification(label)
            }
        }.start()

        return "Timer set for $durationSeconds seconds"
    }

    fun cancelTimer(): String {
        return if (isTimerRunning || countDownTimer != null) {
            countDownTimer?.cancel()
            countDownTimer = null
            isTimerRunning = false
            remainingMillis = 0L
            "Timer cancelled"
        } else {
            "No active timer to cancel"
        }
    }

    fun getRemainingTimeStatus(): String {
        return if (isTimerRunning && remainingMillis > 0) {
            val seconds = (remainingMillis / 1000) % 60
            val minutes = (remainingMillis / (1000 * 60)) % 60
            val hours = remainingMillis / (1000 * 60 * 60)

            when {
                hours > 0 -> String.format(Locale.getDefault(), "Timer has %d hours, %d minutes remaining", hours, minutes)
                minutes > 0 -> String.format(Locale.getDefault(), "Timer has %d minutes, %d seconds remaining", minutes, seconds)
                else -> String.format(Locale.getDefault(), "Timer has %d seconds remaining", seconds)
            }
        } else {
            "There is no active timer currently running."
        }
    }

    private fun showTimerCompletedNotification(label: String) {
        val channelId = "nova_timer_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nova Timer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Nova timer finishes"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Nova Timer Done!")
            .setContentText("$label finished!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(3001, builder.build())
    }
}

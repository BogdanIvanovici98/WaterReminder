package com.bogdan.waterreminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.*
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import android.media.AudioAttributes
import androidx.core.net.toUri
import java.util.Locale

class WaterReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "water_reminder_channel_v2"
        const val NOTIFICATION_ID = 1
        const val TAG = "WaterReminderReceiver"

        fun deleteOldNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val existingChannels = notificationManager.notificationChannels
                for (channel in existingChannels) {
                    if (channel.id != CHANNEL_ID) {
                        notificationManager.deleteNotificationChannel(channel.id)
                    }
                }
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                val soundUri =
                    "android.resource://${context.packageName}/raw/notification_custom".toUri()
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                channel.setSound(soundUri, attributes)

                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        // --- LOGICA UNITARĂ ---
        private fun calculateFirstNotificationMillis(
            context: Context
        ): Long {
            val prefs = context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
            val intervalMinutes = prefs.getInt("interval", 60)
            val startTime = prefs.getString("startTime", "08:00") ?: "08:00"
            val endTime = prefs.getString("endTime", "22:00") ?: "22:00"

            val calNow = Calendar.getInstance()
            val nowMinutes = calNow.get(Calendar.HOUR_OF_DAY) * 60 + calNow.get(Calendar.MINUTE)

            val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
            val (endHour, endMinute) = endTime.split(":").map { it.toInt() }
            val startTotal = startHour * 60 + startMinute
            val endTotal = endHour * 60 + endMinute

            val inInterval = if (endTotal > startTotal) {
                nowMinutes in startTotal until endTotal
            } else {
                nowMinutes >= startTotal || nowMinutes < endTotal
            }

            return if (inInterval) {
                System.currentTimeMillis() + intervalMinutes * 60 * 1000L
            } else {
                val calNext = Calendar.getInstance()
                calNext.set(Calendar.HOUR_OF_DAY, startHour)
                calNext.set(Calendar.MINUTE, startMinute)
                calNext.set(Calendar.SECOND, 0)
                calNext.set(Calendar.MILLISECOND, 0)
                if (calNext.timeInMillis <= System.currentTimeMillis()) {
                    calNext.add(Calendar.DAY_OF_MONTH, 1)
                }
                calNext.timeInMillis
            }
        }

        fun scheduleReminderAlarm(context: Context) {
            val prefs = context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("enabled", false)
            if (!isEnabled) return

            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmMgr.canScheduleExactAlarms()) {
                    Log.e(TAG, "Aplicația NU are permisiunea SCHEDULE_EXACT_ALARM!")
                    return
                }
            }

            val triggerAtMillis = calculateFirstNotificationMillis(context)

            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        alarmMgr.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                        alarmMgr.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }

                    else -> {
                        alarmMgr.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                }
                Log.d(TAG, "Alarmă programată la $triggerAtMillis (${Date(triggerAtMillis)})")
            } catch (e: SecurityException) {
                Log.e(TAG, "Nu s-a putut seta alarma exactă: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive() triggered! Intent: $intent, time: ${System.currentTimeMillis()}")

        val prefs = context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        val isNotificationEnabled = prefs.getBoolean("enabled", false)
        val startTime = prefs.getString("startTime", "08:00") ?: "08:00"
        val endTime = prefs.getString("endTime", "22:00") ?: "22:00"
        val interval = prefs.getInt("interval", 60) // minute

        Log.d(
            TAG,
            "Prefs: enabled=$isNotificationEnabled, startTime=$startTime, endTime=$endTime, interval=$interval"
        )

        if (!isNotificationEnabled) {
            Log.d(TAG, "Notificările sunt dezactivate, receiver nu face nimic.")
            return
        }

        val now = Calendar.getInstance()
        val currentHourMinute = String.format(
            Locale.US,
            "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )
        Log.d(TAG, "AlarmManager triggered at $currentHourMinute (${now.timeInMillis})")

        if (isTimeInInterval(currentHourMinute, startTime, endTime)) {
            Log.d(TAG, "În intervalul $startTime - $endTime. Trimitem notificare.")
            showNotification(
                context,
                context.getString(R.string.notification_title),
                context.getString(R.string.notification_text)
            )
        } else {
            Log.d(TAG, "În afara intervalului $startTime - $endTime. Nu trimitem notificare.")
        }

        // reprogramează alarma pentru următorul interval (periodicitate)
        Log.d(TAG, "Reprogramăm alarma pentru următorul interval de $interval minute.")
        reprogramAlarm(context, interval)
    }

    private fun isTimeInInterval(current: String, start: String, end: String): Boolean {
        fun toMinutes(time: String): Int {
            val parts = time.split(":")
            return parts[0].toInt() * 60 + parts[1].toInt()
        }

        val currentMin = toMinutes(current)
        val startMin = toMinutes(start)
        val endMin = toMinutes(end)
        val inInterval = if (endMin > startMin) {
            currentMin in startMin until endMin
        } else {
            currentMin >= startMin || currentMin < endMin
        }
        Log.d(
            TAG,
            "isTimeInInterval: current=$current ($currentMin), start=$start ($startMin), end=$end ($endMin), result=$inInterval"
        )
        return inInterval
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(
            TAG,
            "Construim notificarea cu smallIcon=R.drawable.ic_notification, title=$title, message=$message, time=${System.currentTimeMillis()}"
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        Log.d(
            TAG,
            "Trimitem notificarea: NOTIFICATION_ID=$NOTIFICATION_ID, time=${System.currentTimeMillis()}"
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun reprogramAlarm(context: Context, intervalMinutes: Int) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WaterReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + intervalMinutes * 60 * 1000L
        Log.d(
            TAG,
            "Pregătim să reprogramăm alarma: triggerAtMillis=$triggerAtMillis (${
                Date(triggerAtMillis)
            })"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmMgr.canScheduleExactAlarms()) {
            Log.e(TAG, "Aplicația NU are permisiunea SCHEDULE_EXACT_ALARM!")
            return
        }

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(
                        TAG,
                        "Alarmă setată cu setExactAndAllowWhileIdle la $triggerAtMillis (${
                            Date(triggerAtMillis)
                        })"
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                    alarmMgr.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(
                        TAG,
                        "Alarmă setată cu setExact la $triggerAtMillis (${Date(triggerAtMillis)})"
                    )
                }

                else -> {
                    alarmMgr.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(
                        TAG,
                        "Alarmă setată cu set la $triggerAtMillis (${Date(triggerAtMillis)})"
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Nu s-a putut seta alarma exactă: ${e.message}")
        }
    }
}
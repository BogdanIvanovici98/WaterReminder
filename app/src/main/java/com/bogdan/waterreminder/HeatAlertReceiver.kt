package com.bogdan.waterreminder

import android.app.*
import android.content.*
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.media.AudioAttributes
import java.util.*

class HeatAlertReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "water_reminder_channel_v2"
        const val NOTIFICATION_ID = 2025
        const val TAG = "HeatAlertReceiver"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                val soundUri = Uri.parse("android.resource://${context.packageName}/raw/notification_custom")
                channel.setSound(soundUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun scheduleHeatAlertAlarm(context: Context) {
            val prefs = context.getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("heat_alert_enabled", false)
            if (!isEnabled) return

            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeatAlertReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 12345, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Programează la următoarea oră fixă (ex: 13:00, 14:00 etc)
            val now = Calendar.getInstance()
            now.add(Calendar.HOUR_OF_DAY, 1)
            now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            val triggerAtMillis = now.timeInMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmMgr.canScheduleExactAlarms()) {
                    Log.e(TAG, "Aplicația NU are permisiunea SCHEDULE_EXACT_ALARM!")
                    return
                }
            }

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
                Log.d(TAG, "Heat alert alarm scheduled for: ${Date(triggerAtMillis)} [ms=$triggerAtMillis]")
            } catch (e: SecurityException) {
                Log.e(TAG, "Could not set exact alarm: ${e.message}")
            }
        }

        fun cancelHeatAlertAlarm(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HeatAlertReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 12345, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmMgr.cancel(pendingIntent)
            Log.d(TAG, "Heat alert alarm cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "HeatAlertReceiver onReceive triggered! intent=$intent")

        val prefs = context.getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("heat_alert_enabled", false)
        val tempThreshold = prefs.getInt("heat_alert_temp_threshold", 35)
        val tempUnit = prefs.getString("heat_alert_temp_unit", "C") ?: "C"

        // Orele de vârf: 12:00 - 18:00
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        Log.d(TAG, "onReceive: isEnabled=$isEnabled, hour=$hour, tempThreshold=$tempThreshold, tempUnit=$tempUnit")

        if (!isEnabled || hour < 12 || hour > 18) {
            Log.d(TAG, "Heat alert disabled or outside 12-18.")
            scheduleHeatAlertAlarm(context)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Cerem lastLocation...")
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d(TAG, "Am primit location=$location")
                if (location != null) {
                    Log.d(TAG, "Apelăm WeatherUtil.getCurrentTemperature...")
                    WeatherUtil.getCurrentTemperature(context, location, tempUnit) { temp ->
                        Log.d(TAG, "Am primit temperatura=$temp")
                        if (temp != null && temp >= tempThreshold) {
                            showNotification(
                                context,
                                context.getString(R.string.heat_notification_title),
                                context.getString(R.string.heat_notification_text, temp, if (tempUnit == "C") "°C" else "°F")
                            )
                        }
                        Log.d(TAG, "Reprogramez alarma pentru ora următoare.")
                        scheduleHeatAlertAlarm(context)
                    }
                } else {
                    Log.d(TAG, "Nu am putut obține locația.")
                    scheduleHeatAlertAlarm(context)
                }
            }
        } else {
            Log.d(TAG, "Nu există permisiune de locație!")
            scheduleHeatAlertAlarm(context)
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        Log.d(TAG, "showNotification() called!")
        createNotificationChannel(context)

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
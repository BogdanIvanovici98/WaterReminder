package com.bogdan.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.*
import androidx.core.content.edit

class MidnightResetReceiver : BroadcastReceiver() {
    companion object {
        private const val NEVER_KEY = ""

        fun scheduleMidnightResetAlarm(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, MidnightResetReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                        alarmMgr.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT -> {
                        alarmMgr.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                    else -> {
                        alarmMgr.set(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                }
                Log.d("MidnightResetReceiver", "Alarmă de reset reprogramată pentru ziua următoare (scheduleMidnightResetAlarm).")
            } catch (e: SecurityException) {
                Log.e("MidnightResetReceiver", "Nu s-a putut seta alarma de reset: ${e.message}")
            }
        }

        /**
         * Verifică dacă este o nouă zi și, dacă da, resetează valorile.
         * Aceasta funcție poate fi apelată din onResume/onStart din fragment/activitate.
         * Returnează true dacă s-a făcut resetarea.
         */
        fun resetIfNewDay(context: Context): Boolean {
            val prefs = context.getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
            val lastSavedDay = prefs.getString("lastSavedDay", null)
            val today = getToday()
            return if (lastSavedDay != today) {
                prefs.edit {
                    putInt("todayAmount", 0)
                        .putString("lastTime", NEVER_KEY)
                        .putString("lastSavedDay", today)
                        .putBoolean("goalCongratulatedToday", false)
                }
                Log.d("MidnightResetReceiver", "Counter resetat dinamic la deschidere ecran/fragment (resetIfNewDay)!")
                true
            } else {
                false
            }
        }

        private fun getToday(): String {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            return dateFormat.format(Date())
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
        val today = getToday()
        prefs.edit {
            putInt("todayAmount", 0)
                .putString("lastTime", NEVER_KEY)
                .putString("lastSavedDay", today)
                .putBoolean("goalCongratulatedToday", false)
        }
        Log.d("MidnightResetReceiver", "Counter resetat la miezul nopții!")

        // Reprogramează alarma pentru ziua următoare
        scheduleMidnightResetAlarm(context)
    }
}
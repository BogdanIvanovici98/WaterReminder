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
        // Folosește markerul gol, exact ca în HomeFragment!
        private const val NEVER_KEY = ""
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("WaterPrefs", Context.MODE_PRIVATE)
        val today = getToday()
        prefs.edit {
            putInt("todayAmount", 0)
                .putString("lastTime", NEVER_KEY) // Markerul gol, nu string tradus!
                .putString("lastSavedDay", today)
                .putBoolean("goalCongratulatedToday", false)
        }
        Log.d("MidnightResetReceiver", "Counter resetat la miezul nopții!")

        // Reprogramează alarma pentru ziua următoare
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
            Log.d("MidnightResetReceiver", "Alarmă de reset reprogramată pentru ziua următoare.")
        } catch (e: SecurityException) {
            Log.e("MidnightResetReceiver", "Nu s-a putut seta alarma de reset: ${e.message}")
        }
    }

    private fun getToday(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}
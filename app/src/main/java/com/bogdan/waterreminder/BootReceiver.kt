package com.bogdan.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reprogramează alarma dacă notificările sunt încă activate
            WaterReminderReceiver.scheduleReminderAlarm(context)
            HeatAlertReceiver.scheduleHeatAlertAlarm(context)
        }
    }
}
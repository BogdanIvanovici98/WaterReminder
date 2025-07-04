package com.bogdan.waterreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import java.util.*
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationsFragment : Fragment() {

    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var intervalPicker: NumberPicker
    private lateinit var saveButton: Button
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var prefs: SharedPreferences

    // Opțiuni: 2 min, 15 min + din oră în oră până la 12 ore
    private val intervalOptions =
        arrayOf(2, 15, 30, 45, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720)
    private lateinit var intervalLabels: Array<String>

    // Pentru notificări
    private val notificationPrefsName = "NotificationPrefs"
    private lateinit var requestNotificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var permissionCheckTriggeredFromConsistency = false

    private var pendingEnableAfterNotificationPermission =
        false // ADDED: flag pentru battery optimization

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        prefs = requireContext().getSharedPreferences(notificationPrefsName, Context.MODE_PRIVATE)

        startTimeButton = view.findViewById(R.id.startTimeButton)
        endTimeButton = view.findViewById(R.id.endTimeButton)
        intervalPicker = view.findViewById(R.id.intervalPicker)
        saveButton = view.findViewById(R.id.saveButton)
        notificationSwitch = view.findViewById(R.id.notificationSwitch)

        // Internaționalizează etichetele pentru intervale
        intervalLabels = arrayOf(
            getString(R.string.notifications_interval_2min),
            getString(R.string.notifications_interval_15min),
            getString(R.string.notifications_interval_30min),
            getString(R.string.notifications_interval_45min),
            getString(R.string.notifications_interval_1h),
            getString(R.string.notifications_interval_2h),
            getString(R.string.notifications_interval_3h),
            getString(R.string.notifications_interval_4h),
            getString(R.string.notifications_interval_5h),
            getString(R.string.notifications_interval_6h),
            getString(R.string.notifications_interval_7h),
            getString(R.string.notifications_interval_8h),
            getString(R.string.notifications_interval_9h),
            getString(R.string.notifications_interval_10h),
            getString(R.string.notifications_interval_11h),
            getString(R.string.notifications_interval_12h)
        )

        intervalPicker.minValue = 0
        intervalPicker.maxValue = intervalOptions.size - 1
        intervalPicker.displayedValues = intervalLabels

        val savedInterval = prefs.getInt("interval", 60)
        val savedIndex = intervalOptions.indexOf(savedInterval).takeIf { it >= 0 } ?: 2
        intervalPicker.value = savedIndex

        // Afișează ora inițială pe buton în formatul utilizatorului
        val startPref =
            prefs.getString("startTime", getString(R.string.notifications_start_time_default))
                ?: getString(R.string.notifications_start_time_default)
        val (h1, m1) = startPref.split(":").map { it.toInt() }
        startTimeButton.text = formatTimeForDisplay(h1, m1)

        val endPref = prefs.getString("endTime", getString(R.string.notifications_end_time_default))
            ?: getString(R.string.notifications_end_time_default)
        val (h2, m2) = endPref.split(":").map { it.toInt() }
        endTimeButton.text = formatTimeForDisplay(h2, m2)

        startTimeButton.setOnClickListener {
            showMaterialTimePicker(h1, m1) { hour, minute ->
                startTimeButton.text = formatTimeForDisplay(hour, minute)
            }
        }

        endTimeButton.setOnClickListener {
            showMaterialTimePicker(h2, m2) { hour, minute ->
                endTimeButton.text = formatTimeForDisplay(hour, minute)
            }
        }

        saveButton.setOnClickListener {
            val intervalMinutes = intervalOptions[intervalPicker.value]
            // Salvează în prefs ora în format HH:mm (logic, nu de display)
            val startText = startTimeButton.text.toString()
            val endText = endTimeButton.text.toString()
            val startRaw = formatTimeForPrefs(startText)
            val endRaw = formatTimeForPrefs(endText)

            prefs.edit {
                putString("startTime", startRaw)
                putString("endTime", endRaw)
                putInt("interval", intervalMinutes)
            }

            Log.d(
                "NotificationsFragment",
                "Setări salvate: startTime=$startRaw, endTime=$endRaw, interval=${intervalLabels[intervalPicker.value]} ($intervalMinutes min)"
            )

            Toast.makeText(
                requireContext(), getString(R.string.notifications_save_toast), Toast.LENGTH_SHORT
            ).show()

            // Dacă notificările sunt activate, reprogramează alarma cu noua logică
            if (prefs.getBoolean("enabled", false)) {
                scheduleReminderAlarm()
            }
        }

        // ---- NOTIFICĂRI ----

        requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                setNotificationsEnabled(true)
                enableNotifications()
                notificationSwitch.isChecked = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_permission_granted),
                    Toast.LENGTH_SHORT
                ).show()
                // ADDED: după ce userul acordă permisiunea, arată battery optimization dacă era cazul
                if (pendingEnableAfterNotificationPermission) {
                    pendingEnableAfterNotificationPermission = false
                    if (!isIgnoringBatteryOptimizations()) {
                        showBatteryOptimizationDialog()
                    }
                }
            } else {
                setNotificationsEnabled(false)
                notificationSwitch.isChecked = false
                if (!permissionCheckTriggeredFromConsistency) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.notifications_permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pendingEnableAfterNotificationPermission = false // reset flagul
            }
            permissionCheckTriggeredFromConsistency = false
        }

        loadSwitchState()
        checkNotificationPermissionConsistency()
        setupNotificationSwitch()

        return view
    }

    private fun loadSwitchState() {
        notificationSwitch.isChecked = getNotificationsEnabled()
    }

    private fun checkNotificationPermissionConsistency() {
        if (getNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                setNotificationsEnabled(false)
                notificationSwitch.isChecked = false
                disableNotifications()
                permissionCheckTriggeredFromConsistency = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notifications_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else {
            true
        }
    }

    private fun showNotificationSettingsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_notification_permission, null)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            val window = dialog.window
            if (window != null) {
                val decorView = window.decorView
                decorView.alpha = 0f
                decorView.animate().alpha(1f).setDuration(350).start()
            }
        }

        dialogView.findViewById<Button>(R.id.notificationDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.notificationDialogSettings).setOnClickListener {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            dialog.dismiss()
        }

        dialog.show()
        notificationSwitch.isChecked = false
    }

    private fun showBatteryOptimizationDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_battery_optimization, null)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            val window = dialog.window
            if (window != null) {
                val decorView = window.decorView
                decorView.alpha = 0f
                decorView.animate().alpha(1f).setDuration(350).start()
            }
        }

        dialogView.findViewById<Button>(R.id.batteryDialogDeny).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.batteryDialogAllow).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = ("package:" + requireContext().packageName).toUri()
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupNotificationSwitch() {
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                setNotificationsEnabled(false)
                disableNotifications()
                return@setOnCheckedChangeListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    setNotificationsEnabled(true)
                    enableNotifications()
                    if (!isIgnoringBatteryOptimizations()) {
                        showBatteryOptimizationDialog()
                    }
                } else {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        showNotificationSettingsDialog()
                        pendingEnableAfterNotificationPermission = false
                    } else {
                        pendingEnableAfterNotificationPermission = true
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            } else {
                setNotificationsEnabled(true)
                enableNotifications()
                if (!isIgnoringBatteryOptimizations()) {
                    showBatteryOptimizationDialog()
                }
            }
        }
    }

    private fun getNotificationsEnabled(): Boolean {
        val prefs =
            requireContext().getSharedPreferences(notificationPrefsName, Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled", false)
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        val prefs =
            requireContext().getSharedPreferences(notificationPrefsName, Context.MODE_PRIVATE)
        prefs.edit { putBoolean("enabled", enabled) }
    }

    private fun disableNotifications() {
        (activity as? MainActivity)?.cancelReminderAlarm()
        val prefs =
            requireContext().getSharedPreferences(notificationPrefsName, Context.MODE_PRIVATE)
        prefs.edit { putBoolean("enabled", false) }
    }

    private fun enableNotifications() {
        val prefs =
            requireContext().getSharedPreferences(notificationPrefsName, Context.MODE_PRIVATE)
        prefs.edit { putBoolean("enabled", true) }
        (activity as? MainActivity)?.scheduleReminderAlarm()
    }

    // --- MODIFICAT: Acum doar delegă către MainActivity care va apela WaterReminderReceiver cu logica unitară
    private fun scheduleReminderAlarm() {
        (activity as? MainActivity)?.scheduleReminderAlarm()
    }

    // ---------------------- RESTUL CODULUI (nemodificat) -----------------------
    private fun showMaterialTimePicker(
        initialHour: Int, initialMinute: Int, onTimeSelected: (Int, Int) -> Unit
    ) {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(requireContext())
        val tpd = TimePickerDialog.newInstance(
            { _, hourOfDay, minute, _ ->
                onTimeSelected(hourOfDay, minute)
            }, initialHour, initialMinute, is24Hour
        )
        tpd.version = TimePickerDialog.Version.VERSION_1 // layout 1
        tpd.show(parentFragmentManager, "MaterialTimePicker")
    }

    private fun formatTimeForDisplay(hour: Int, minute: Int): String {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val format = if (is24Hour) "HH:mm" else "hh:mm a"
        return java.text.SimpleDateFormat(format, Locale.getDefault()).format(calendar.time)
    }

    private fun formatTimeForPrefs(displayTime: String): String {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val tryFormats = if (is24Hour) arrayOf("HH:mm")
        else arrayOf("hh:mm a", "HH:mm")
        for (fmt in tryFormats) {
            try {
                val sdf = java.text.SimpleDateFormat(fmt, Locale.getDefault())
                val date = sdf.parse(displayTime)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    return String.format(
                        Locale.US,
                        "%02d:%02d",
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE)
                    )
                }
            } catch (_: Exception) {
            }
        }
        return displayTime.take(5)
    }
}
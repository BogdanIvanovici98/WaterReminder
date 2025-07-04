package com.bogdan.waterreminder

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.bogdan.waterreminder.databinding.FragmentSettingsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.provider.Settings
import android.content.Intent
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.edit

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val PREFS_NAME = "settings"
    private val THEME_KEY = "theme_mode"
    private val HEAT_ALERT_KEY = "heat_alert_enabled"
    private val HEAT_ALERT_TEMP_KEY = "heat_alert_temp_threshold"
    private val HEAT_ALERT_TEMP_UNIT_KEY = "heat_alert_temp_unit" // "C" sau "F"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: SharedPreferences

    private var justRequestedBgLocation = false
    private var isTogglingSwitchProgrammatically = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mode = prefs.getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.theme_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.themeSpinner.adapter = adapter
        }

        binding.themeSpinner.setSelection(
            when (mode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }
        )

        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val themeMode = when (position) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (AppCompatDelegate.getDefaultNightMode() != themeMode) {
                    AppCompatDelegate.setDefaultNightMode(themeMode)
                    prefs.edit { putInt(THEME_KEY, themeMode) }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        updateGoalValue()
        binding.goalValue.setOnClickListener { showSetGoalDialog() }

        val tempThreshold = prefs.getInt(HEAT_ALERT_TEMP_KEY, 35)
        val tempUnit = prefs.getString(HEAT_ALERT_TEMP_UNIT_KEY, "C") ?: "C"
        updateTempThresholdLabel(tempThreshold, tempUnit)
        binding.textTempThreshold.setOnClickListener { showTemperaturePickerDialog() }

        // Inițializare radio buttons pentru unitate
        val radioGroup = binding.radioGroupTempUnit
        binding.radioCelsius.isChecked = tempUnit == "C"
        binding.radioFahrenheit.isChecked = tempUnit == "F"
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newUnit = if (checkedId == R.id.radioCelsius) "C" else "F"
            if (newUnit != prefs.getString(HEAT_ALERT_TEMP_UNIT_KEY, "C")) {
                // Conversie valoare existentă
                val oldValue = prefs.getInt(HEAT_ALERT_TEMP_KEY, 35)
                val newValue =
                    if (newUnit == "F") celsiusToFahrenheit(oldValue) else fahrenheitToCelsius(
                        oldValue
                    )
                prefs.edit {
                    putString(
                        HEAT_ALERT_TEMP_UNIT_KEY,
                        newUnit
                    ).putInt(HEAT_ALERT_TEMP_KEY, newValue)
                }
                updateTempThresholdLabel(newValue, newUnit)
                if (binding.switchHeatAlert.isChecked) {
                    val heatPrefs = requireContext().getSharedPreferences(
                        "HeatAlertPrefs",
                        Context.MODE_PRIVATE
                    )
                    heatPrefs.edit {
                        putString(HEAT_ALERT_TEMP_UNIT_KEY, newUnit).putInt(
                            HEAT_ALERT_TEMP_KEY,
                            newValue
                        )
                    }
                    HeatAlertReceiver.scheduleHeatAlertAlarm(requireContext())
                }
            }
        }

        val enabled = prefs.getBoolean(HEAT_ALERT_KEY, false)
        val permissionsOk = hasLocationPermissions()
        showTemperatureLayout(enabled && permissionsOk)
        showTempUnitLayout(enabled && permissionsOk)
        binding.switchHeatAlert.isChecked = enabled && permissionsOk

        binding.switchHeatAlert.setOnCheckedChangeListener(safeSwitchListener)

        binding.switchHeatAlert.setOnLongClickListener {
            testWeatherRequest()
            true
        }

        // --- Custom layout info dialog for Heatwave Alert label ---
        binding.labelHeatAlert.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val textView = v as TextView
                val drawableRight = 2 // drawableEnd
                val drawable = textView.compoundDrawables[drawableRight]
                if (drawable != null) {
                    val drawableWidth = drawable.bounds.width()
                    if (event.x >= textView.width - textView.paddingEnd - drawableWidth) {
                        textView.performClick()
                        showHeatAlertInfoDialog()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
        binding.labelHeatAlert.setOnClickListener { /* accesibilitate/lint only */ }
    }

    private fun showHeatAlertInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_heat_alert_info, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.dialogHeatAlertOk).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        binding.switchHeatAlert.setOnCheckedChangeListener(null)
        val enabled = prefs.getBoolean(HEAT_ALERT_KEY, false)
        val permissionsOk = hasLocationPermissions()
        binding.switchHeatAlert.isChecked = enabled && permissionsOk

        showTemperatureLayout(enabled && permissionsOk)
        showTempUnitLayout(enabled && permissionsOk)

        if (enabled && !permissionsOk) {
            prefs.edit { putBoolean(HEAT_ALERT_KEY, false) }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_background_location_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.switchHeatAlert.setOnCheckedChangeListener(safeSwitchListener)
    }

    private fun showTemperatureLayout(show: Boolean) {
        binding.layoutSelectTemperature.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showTempUnitLayout(show: Boolean) {
        binding.layoutSelectTempUnit.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateTempThresholdLabel(value: Int, unit: String) {
        binding.textTempThreshold.text = "$value°${if (unit == "C") "C" else "F"}"
    }

    private fun hasLocationPermissions(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return fineGranted && backgroundGranted
    }

    private val safeSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isTogglingSwitchProgrammatically) return@OnCheckedChangeListener
        switchListener.onCheckedChanged(binding.switchHeatAlert, isChecked)
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                justRequestedBgLocation = true
                requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startHeatAlertEnableProcess()
            }
        } else {
            setSwitchStateSafe(false)
            if (!shouldShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showSettingsDialog()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_location_permission_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startHeatAlertEnableProcess()
        } else {
            setSwitchStateSafe(false)
            if (!shouldShowRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showSettingsDialog()
            } else {
                if (justRequestedBgLocation) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_background_location_required),
                        Toast.LENGTH_LONG
                    ).show()
                    justRequestedBgLocation = false
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_location_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                setSwitchStateSafe(false)
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return@OnCheckedChangeListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                setSwitchStateSafe(false)
                justRequestedBgLocation = true
                requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return@OnCheckedChangeListener
            }
            startHeatAlertEnableProcess()
        } else {
            disableHeatAlert()
        }
    }

    private fun setSwitchStateSafe(state: Boolean) {
        isTogglingSwitchProgrammatically = true
        binding.switchHeatAlert.isChecked = state
        isTogglingSwitchProgrammatically = false
    }

    private fun startHeatAlertEnableProcess() {
        showTemperatureLayout(false)
        showTempUnitLayout(false)

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setSwitchStateSafe(false)
            Toast.makeText(
                requireContext(),
                getString(R.string.toast_location_permission_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                WeatherUtil.getCurrentTemperature(requireContext(), location) { temp ->
                    requireActivity().runOnUiThread {
                        if (temp == null) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_temp_not_found),
                                Toast.LENGTH_LONG
                            ).show()
                            setSwitchStateSafe(false)
                        } else {
                            enableHeatAlert()
                        }
                    }
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_location_not_found),
                    Toast.LENGTH_LONG
                ).show()
                setSwitchStateSafe(false)
            }
        }
    }

    private fun enableHeatAlert() {
        prefs.edit { putBoolean(HEAT_ALERT_KEY, true) }
        val unit = prefs.getString(HEAT_ALERT_TEMP_UNIT_KEY, "C") ?: "C"
        val tempValue = prefs.getInt(HEAT_ALERT_TEMP_KEY, 35)
        val heatPrefs =
            requireContext().getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        heatPrefs.edit {
            putBoolean(HEAT_ALERT_KEY, true)
                .putInt(HEAT_ALERT_TEMP_KEY, tempValue)
                .putString(HEAT_ALERT_TEMP_UNIT_KEY, unit)
        }

        Toast.makeText(
            requireContext(),
            getString(R.string.toast_heat_alert_enabled),
            Toast.LENGTH_SHORT
        ).show()
        HeatAlertReceiver.scheduleHeatAlertAlarm(requireContext())
        setSwitchStateSafe(true)
        showTemperatureLayout(true)
        showTempUnitLayout(true)
    }

    private fun disableHeatAlert() {
        prefs.edit { putBoolean(HEAT_ALERT_KEY, false) }
        val heatPrefs =
            requireContext().getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        heatPrefs.edit { putBoolean(HEAT_ALERT_KEY, false) }
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_heat_alert_disabled),
            Toast.LENGTH_SHORT
        ).show()
        HeatAlertReceiver.cancelHeatAlertAlarm(requireContext())
        setSwitchStateSafe(false)
        showTemperatureLayout(false)
        showTempUnitLayout(false)
    }

    private fun updateGoalValue() {
        val goalValue = GoalPrefs.getGoal(requireContext())
        binding.goalValue.text =
            if (goalValue > 0) "$goalValue ml" else getString(R.string.set_goal_not_set)
    }

    private fun showSetGoalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_goal, null)
        val editText = dialogView.findViewById<EditText>(R.id.goalInput)
        val btnRecommended = dialogView.findViewById<Button>(R.id.btnRecommended)
        val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

        val currentGoal = GoalPrefs.getGoal(requireContext())
        editText.setText(currentGoal.takeIf { it > 0 }?.toString() ?: "")

        btnRecommended.setOnClickListener {
            editText.setText("2000")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            val value = editText.text.toString().toIntOrNull()
            if (value != null && value > 0) {
                GoalPrefs.setGoal(requireContext(), value)
                binding.goalValue.text = getString(R.string.goal_value_ml, value)
                dialog.dismiss()
            } else {
                editText.error = getString(R.string.set_goal_error)
            }
        }

        dialog.show()
    }

    private fun showTemperaturePickerDialog() {
        val tempUnit = prefs.getString(HEAT_ALERT_TEMP_UNIT_KEY, "C") ?: "C"
        val currentValue = prefs.getInt(HEAT_ALERT_TEMP_KEY, if (tempUnit == "C") 35 else 95)
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_temperature_picker, null)
        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.dialogNumberPicker)

        if (tempUnit == "C") {
            numberPicker.minValue = 10
            numberPicker.maxValue = 45
        } else {
            numberPicker.minValue = 50
            numberPicker.maxValue = 113
        }
        numberPicker.value = currentValue
        numberPicker.wrapSelectorWheel = false

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnOkTemp).setOnClickListener {
            val selectedValue = numberPicker.value
            prefs.edit { putInt(HEAT_ALERT_TEMP_KEY, selectedValue) }
            updateTempThresholdLabel(selectedValue, tempUnit)
            if (binding.switchHeatAlert.isChecked) {
                val heatPrefs =
                    requireContext().getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
                heatPrefs.edit { putInt(HEAT_ALERT_TEMP_KEY, selectedValue) }
                HeatAlertReceiver.scheduleHeatAlertAlarm(requireContext())
            }
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancelTemp).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun celsiusToFahrenheit(celsius: Int): Int = ((celsius * 9 / 5.0) + 32).toInt()
    private fun fahrenheitToCelsius(fahrenheit: Int): Int = (((fahrenheit - 32) * 5 / 9.0)).toInt()

    private fun testWeatherRequest() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    WeatherUtil.getCurrentTemperature(requireContext(), location) { temp ->
                        requireActivity().runOnUiThread {
                            if (temp == null) {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.toast_temp_not_found),
                                    Toast.LENGTH_LONG
                                ).show()
                                setSwitchStateSafe(false)
                            }
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_location_not_found),
                        Toast.LENGTH_LONG
                    ).show()
                    setSwitchStateSafe(false)
                }
            }
        }
    }

    private fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)
    }

    private fun showSettingsDialog() {
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_permission_denied_forever),
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = "package:${requireContext().packageName}".toUri()
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
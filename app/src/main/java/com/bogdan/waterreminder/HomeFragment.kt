package com.bogdan.waterreminder

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AlertDialog

// AdMob imports
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import androidx.core.content.edit

class HomeFragment : Fragment() {

    private lateinit var lastDrinkText: TextView
    private lateinit var waterCounterText: TextView
    private lateinit var drinkButton: Button
    private lateinit var resetButton: Button
    private lateinit var amountSlider: Slider
    private lateinit var sliderValueText: TextView

    // Compose WaterBottle container
    private lateinit var glassComposeContainer: FrameLayout

    private val prefsName = "WaterPrefs"
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    private var selectedAmount = 100 // default la 100 ml
    private var totalWaterDrank: Int = 0

    private var waterAmountState = mutableStateOf(0)

    private var drankWaterTrigger by mutableStateOf(0)

    private lateinit var waterBottleViewModel: WaterBottleViewModel

    private val NEVER_KEY = "" // string gol, marker pentru "niciodată"

    private val GOAL_CONGRATS_KEY = "goalCongratulatedToday"

    // Persistent reset counter for showing ad, similar to HistoryFragment
    private val RESET_COUNTER_PREFS = "ResetCounterPrefs"
    private val RESET_COUNTER_KEY = "resetCounter"

    // AdMob
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        lastDrinkText = view.findViewById(R.id.lastDrinkText)
        waterCounterText = view.findViewById(R.id.waterCounterText)
        drinkButton = view.findViewById(R.id.drinkButton)
        resetButton = view.findViewById(R.id.resetButton)
        amountSlider = view.findViewById(R.id.amountSlider)
        sliderValueText = view.findViewById(R.id.sliderValueText)
        glassComposeContainer = view.findViewById(R.id.glassComposeContainer)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAmountSlider()
        loadSavedData()
        setupDrinkButton()
        setupResetButton()

        // Inițializează ViewModel-ul O SINGURĂ DATĂ pe Activity (persistă între taburi!)
        waterBottleViewModel =
            ViewModelProvider(requireActivity())[WaterBottleViewModel::class.java]

        // Initializează ComposeView O SINGURĂ DATĂ!
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            WatterBottle(
                totalWaterAmount = 2000, // capacitate fixă
                usedWaterAmount = waterAmountState.value,
                drankWaterTrigger = drankWaterTrigger, // <- trigger pentru bule
                viewModel = waterBottleViewModel // <- ViewModel pentru bule persistente!
            )
        }
        glassComposeContainer.removeAllViews()
        glassComposeContainer.addView(composeView)

        // Încarcă Interstitial Ad la inițializare
        loadInterstitialAd()
    }

    private fun setupAmountSlider() {
        amountSlider.valueFrom = 0f
        amountSlider.valueTo = 1000f
        amountSlider.stepSize = 50f

        val startColor = ContextCompat.getColor(requireContext(), R.color.water_light)
        val endColor = ContextCompat.getColor(requireContext(), R.color.water_dark)

        amountSlider.value = 100f
        selectedAmount = 100
        sliderValueText.text = getString(R.string.home_slider_value, 100)

        val options = (0..1000 step 50).toList()

        amountSlider.addOnChangeListener { _, value, _ ->
            val amount = value.toInt()

            if (amount == 0) {
                sliderValueText.text = ""
                selectedAmount = 0
                return@addOnChangeListener
            }

            selectedAmount = amount
            sliderValueText.text = getString(R.string.home_slider_value, selectedAmount)

            val index = options.indexOf(amount)
            val maxIndex = options.size - 2
            val fraction = if (maxIndex > 0) index.toFloat() / maxIndex.toFloat() else 0f

            val color =
                android.animation.ArgbEvaluator().evaluate(fraction, startColor, endColor) as Int

            amountSlider.thumbTintList = ColorStateList.valueOf(color)
            amountSlider.trackActiveTintList = ColorStateList.valueOf(color)
            amountSlider.tickActiveTintList = ColorStateList.valueOf(color)
        }

        val initialColor = ContextCompat.getColor(requireContext(), R.color.water_light)
        amountSlider.thumbTintList = ColorStateList.valueOf(initialColor)
        amountSlider.trackActiveTintList = ColorStateList.valueOf(initialColor)
        amountSlider.tickActiveTintList = ColorStateList.valueOf(initialColor)
    }

    private fun updateWaterBottleView(amount: Int) {
        waterAmountState.value = amount
    }

    private fun loadSavedData() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val lastTime = prefs.getString("lastTime", NEVER_KEY)
        val todayAmount = prefs.getInt("todayAmount", 0)

        lastDrinkText.text = if (lastTime.isNullOrBlank() || lastTime == NEVER_KEY) {
            getString(R.string.home_never_drink)
        } else {
            lastTime
        }
        waterCounterText.text = getString(R.string.home_total_today, todayAmount)
        totalWaterDrank = todayAmount
        updateWaterBottleView(totalWaterDrank)
    }

    private fun setupDrinkButton() {
        drinkButton.setOnClickListener {
            val amount = selectedAmount
            if (amount == 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.home_select_amount_toast),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val now = Calendar.getInstance()
            val hour = timeFormat.format(now.time)
            val today = dateFormat.format(now.time)

            val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val lastSavedDay = prefs.getString("lastSavedDay", today)
            val currentAmount = if (today != lastSavedDay) 0 else prefs.getInt("todayAmount", 0)
            val newAmount = currentAmount + amount

            // *** Aici verificăm și afișăm dialogul de felicitare ***
            val goal = GoalPrefs.getGoal(requireContext())
            val congratulatedToday = prefs.getBoolean(GOAL_CONGRATS_KEY, false)
            if (!congratulatedToday && goal > 0 && currentAmount < goal && newAmount >= goal) {
                showCongratsDialog()
                prefs.edit { putBoolean(GOAL_CONGRATS_KEY, true) }
            }

            prefs.edit {
                putString("lastTime", hour)
                    .putString("lastSavedDay", today)
                    .putInt("todayAmount", newAmount)
            }

            WaterHistoryStorage.addWaterForToday(requireContext(), amount)
            lastDrinkText.text = hour
            waterCounterText.text = getString(R.string.home_total_today, newAmount)
            totalWaterDrank = newAmount
            updateWaterBottleView(totalWaterDrank.coerceAtMost(2000))

            drankWaterTrigger++
        }
    }

    private fun showCongratsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_congrats, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Setează fundalul dialogului transparent
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            val window = dialog.window
            if (window != null) {
                val decorView = window.decorView
                decorView.alpha = 0f
                decorView.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .start()
            }
        }

        // Poți personaliza comportamentul butonului OK dacă ai un buton cu id-ul btnCongratsOk
        dialogView.findViewById<View>(R.id.btnCongratsOk)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupResetButton() {
        resetButton.setOnClickListener {
            // Interstitial ad counter persistent logic
            val counterPrefs =
                requireContext().getSharedPreferences(RESET_COUNTER_PREFS, Context.MODE_PRIVATE)
            val resetCounter = counterPrefs.getInt(RESET_COUNTER_KEY, 0) + 1

            // Afișează reclama la fiecare 3 resetări (similar cu HistoryFragment)
            if (resetCounter % 3 == 0 && mInterstitialAd != null) {
                mInterstitialAd?.show(requireActivity())
                loadInterstitialAd() // Reîncarcă pentru data viitoare
            }

            // Salvează counter-ul actualizat
            counterPrefs.edit { putInt(RESET_COUNTER_KEY, resetCounter) }

            val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val today = dateFormat.format(Calendar.getInstance().time)
            prefs.edit {
                putInt("todayAmount", 0)
                    .putString("lastTime", NEVER_KEY)
                    .putString("lastSavedDay", today)
                    .putBoolean(GOAL_CONGRATS_KEY, false) // Resetăm și dialogul de congratulare!
            }
            lastDrinkText.text = getString(R.string.home_never_drink)
            waterCounterText.text = getString(R.string.home_total_today, 0)
            totalWaterDrank = 0
            updateWaterBottleView(totalWaterDrank)
            Toast.makeText(
                requireContext(),
                getString(R.string.home_reset_counter_toast),
                Toast.LENGTH_SHORT
            ).show()
            WaterHistoryStorage.resetToday(requireContext())
        }
    }

    // --- Funcție pentru încărcare interstitial ---
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            requireContext(),
            "ca-app-pub-9845021796840312/5195009948", // ID DE TEST AdMob!
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    mInterstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                }
            }
        )
    }
}
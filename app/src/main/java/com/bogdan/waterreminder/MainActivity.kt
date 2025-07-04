package com.bogdan.waterreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import android.graphics.Color
import android.view.View
import android.content.res.Configuration
import android.view.WindowInsetsController
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.android.billingclient.api.*
import com.google.android.play.core.review.ReviewManagerFactory
import androidx.core.content.edit

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {

    private var currentTabIndex = 2 // Home la mijloc
    private val tabOrder = listOf(
        R.id.notifications, // 0
        R.id.history,       // 1
        R.id.home,          // 2
        R.id.noads,         // 3
        R.id.settings       // 4
    )

    private lateinit var billingManager: BillingManager
    private val SUBSCRIPTION_ID = "premium_monthly" // Schimbă cu ID-ul real din Play Console

    // --- Review Dialog State ---
    private val REVIEW_PREFS = "in_app_review"
    private val KEY_FIRST_LAUNCH = "first_launch_time"
    private val KEY_LAST_REVIEW = "last_review_request"
    private val KEY_REVIEW_SHOWN_ONCE = "review_shown_once"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WaterReminderReceiver.deleteOldNotificationChannels(this)
        WaterReminderReceiver.createNotificationChannel(this)
        updateStatusBarAppearance()
        MobileAds.initialize(this) {}

        // === AdMob Banner sus ===
        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        // =========================

        // --- BillingManager setup ---
        billingManager = BillingManager(this, this)
        billingManager.startBillingConnection {
            // Poți chema aici queryActiveSubscriptions dacă vrei să verifici statusul la pornire
        }
        // ----------------------------

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Determină fragmentul activ după recreare (ex: schimbare temă)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, HomeFragment())
                .commit()
            currentTabIndex = tabOrder.indexOf(R.id.home)
            bottomNav.selectedItemId = R.id.home
        } else {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            currentTabIndex = when (currentFragment) {
                is HomeFragment -> tabOrder.indexOf(R.id.home)
                is NotificationsFragment -> tabOrder.indexOf(R.id.notifications)
                is HistoryFragment -> tabOrder.indexOf(R.id.history)
                is PremiumFragment -> tabOrder.indexOf(R.id.noads)
                is SettingsFragment -> tabOrder.indexOf(R.id.settings)
                else -> tabOrder.indexOf(R.id.home)
            }
            val tabId = tabOrder[currentTabIndex]
            bottomNav.selectedItemId = tabId
        }

        bottomNav.setOnItemSelectedListener { item ->
            val newTabIndex = tabOrder.indexOf(item.itemId)
            val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            val shouldSkip = (newTabIndex == currentTabIndex) &&
                    when (item.itemId) {
                        R.id.home -> currentFragment is HomeFragment
                        R.id.notifications -> currentFragment is NotificationsFragment
                        R.id.history -> currentFragment is HistoryFragment
                        R.id.noads -> currentFragment is PremiumFragment
                        R.id.settings -> currentFragment is SettingsFragment
                        else -> false
                    }
            if (shouldSkip) return@setOnItemSelectedListener true

            val fragment: Fragment = when (item.itemId) {
                R.id.home -> HomeFragment()
                R.id.notifications -> NotificationsFragment()
                R.id.history -> HistoryFragment()
                R.id.noads -> PremiumFragment()
                R.id.settings -> SettingsFragment()
                else -> HomeFragment()
            }

            val (enterAnim, exitAnim) = when {
                newTabIndex > currentTabIndex -> R.anim.slide_in_right to R.anim.slide_out_left
                newTabIndex < currentTabIndex -> R.anim.slide_in_left to R.anim.slide_out_right
                else -> R.anim.slide_in_right to R.anim.slide_out_left
            }

            supportFragmentManager.beginTransaction()
                .setCustomAnimations(enterAnim, exitAnim)
                .replace(R.id.nav_host_fragment, fragment)
                .commit()

            currentTabIndex = newTabIndex
            true
        }

        // Permisiuni pentru notificări (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Permisiune pentru alarmă exactă (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        scheduleMidnightResetAlarm()
        WaterReminderReceiver.createNotificationChannel(this)

        // --- Dialog pentru setarea goal la prima pornire (după permisiuni) ---
        if (GoalPrefs.getGoal(this) == 0) {
            window.decorView.post {
                showSetGoalDialog()
            }
        }

        // --- In-app review: setează prima lansare dacă nu există deja ---
        val prefs = getSharedPreferences(REVIEW_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_FIRST_LAUNCH)) {
            prefs.edit { putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis()) }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusBarAppearance()
        showReviewIfNeeded()
    }

    // === IN-APP REVIEW LOGIC ===
    private fun showReviewIfNeeded() {
        val prefs = getSharedPreferences(REVIEW_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, now)
        val lastReview = prefs.getLong(KEY_LAST_REVIEW, 0L)
        val reviewShownOnce = prefs.getBoolean(KEY_REVIEW_SHOWN_ONCE, false)
        val twentyFourHours = 24 * 60 * 60 * 1000L
        val sevenDays = 5 * 24 * 60 * 60 * 1000L

        val eligible = if (!reviewShownOnce) {
            now - firstLaunch > twentyFourHours
        } else {
            now - lastReview > sevenDays
        }
        Log.d(
            "InAppReview",
            "firstLaunch=$firstLaunch, lastReview=$lastReview, reviewShownOnce=$reviewShownOnce, now=$now, eligible=$eligible"
        )
        if (eligible) {
            Log.d("InAppReview", "Cerere de rating declanșată (eligible = true)")
            requestInAppReview(
                onShown = {
                    prefs.edit {
                        putLong(KEY_LAST_REVIEW, now)
                            .putBoolean(KEY_REVIEW_SHOWN_ONCE, true)
                    }
                }
            )
        } else {
            Log.d("InAppReview", "Nu s-a declanșat cererea de rating (eligible = false)")
        }
    }

    private fun requestInAppReview(
        onShown: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("InAppReview", "requestReviewFlow: SUCCES - se lansează dialogul nativ")
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    Log.d(
                        "InAppReview",
                        "launchReviewFlow: dialog închis (user a închis sau a dat review)"
                    )
                    onShown?.invoke()
                }
            } else {
                Log.d("InAppReview", "requestReviewFlow: EȘEC - nu se poate deschide dialogul")
                onFailed?.invoke()
            }
        }
    }

    private fun showSetGoalDialog(onGoalSet: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_goal, null)
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.goalInput)
        val btnRecommended = dialogView.findViewById<android.widget.Button>(R.id.btnRecommended)
        val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)

        btnRecommended.setOnClickListener {
            editText.setText("2000")
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            val value = editText.text.toString().toIntOrNull()
            if (value != null && value > 0) {
                GoalPrefs.setGoal(this, value)
                dialog.dismiss()
                onGoalSet?.invoke()
            } else {
                editText.error = getString(R.string.set_goal_error)
            }
        }

        dialog.show()
    }

    // --- Billing: Pornește flow-ul de achiziție pentru abonament ---
    fun startSubscriptionPurchase() {
        billingManager.launchPurchaseFlow(this, SUBSCRIPTION_ID)
    }

    // --- Billing: Primește callback la finalizare achiziție ---
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    billingManager.acknowledgePurchase(purchase) { success ->
                        if (success) {
                            Log.d("Billing", "Abonament confirmat și activat.")
                        }
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("Billing", "Achiziție anulată de utilizator.")
        } else {
            Log.e("Billing", "Eroare la achiziție: ${billingResult.debugMessage}")
        }
    }

    fun checkSubscriptionStatus(onResult: (Boolean) -> Unit) {
        billingManager.queryActiveSubscriptions { purchases ->
            val hasActiveSub =
                purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged }
            onResult(hasActiveSub)
        }
    }

    fun scheduleReminderAlarm() {
        WaterReminderReceiver.scheduleReminderAlarm(this)
    }

    fun cancelReminderAlarm() {
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WaterReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pendingIntent)
        Log.d("AlarmManager", "Alarmă OPRITĂ.")
    }

    private fun scheduleMidnightResetAlarm() {
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MidnightResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
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
            Log.d("AlarmManager", "Alarmă de reset la miezul nopții programată.")
        } catch (e: SecurityException) {
            Log.e("AlarmManager", "Nu s-a putut seta alarma de reset: ${e.message}")
        }
    }

    private fun updateStatusBarAppearance() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val window = this.window

        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            window.statusBarColor = Color.BLACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController
                controller?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = 0
            }
        } else {
            window.statusBarColor = Color.WHITE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController
                controller?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }
    }
}
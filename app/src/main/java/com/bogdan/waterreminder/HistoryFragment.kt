package com.bogdan.waterreminder

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.fragment.app.Fragment
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import android.content.Context
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.view.inputmethod.EditorInfo

// AdMob imports
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

class HistoryFragment : Fragment() {

    private var editMode = false
    private lateinit var calendarView: CalendarView
    private lateinit var editButton: Button
    private lateinit var editButtonContainer: LinearLayout

    // AdMob InterstitialAd support
    private var mInterstitialAd: InterstitialAd? = null

    // private var editCounter = 0 // vechi
    private val PREFS_NAME = "HistoryPrefs"
    private val COUNTER_KEY = "editCounter"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        editButton = view.findViewById(R.id.editHistoryButton)
        editButtonContainer = view.findViewById(R.id.editButtonContainer)

        val currentMonth = YearMonth.now()
        val startMonth = YearMonth.of(2020, 1)
        val endMonth = currentMonth.plusMonths(1)

        calendarView.setup(startMonth, endMonth, DayOfWeek.MONDAY)
        calendarView.scrollToMonth(currentMonth)

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                if (day.position != DayPosition.MonthDate) {
                    container.view.visibility = View.INVISIBLE
                    return
                } else {
                    container.view.visibility = View.VISIBLE
                }
                val context = container.view.context
                val dateString = day.date.toString()
                val amount = WaterHistoryStorage.getWaterForDate(context, dateString)
                container.dayNumberView.text = day.date.dayOfMonth.toString()

                val isDarkTheme =
                    (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

                // --- MODIFICARE: În funcție de editMode, badge-ul e editabil sau doar arată valoarea ---
                if (editMode) {
                    // Arată iconiță de edit, badge-ul e apăsabil
                    container.badgeView.text = if (amount > 0) "✏️" else "＋"
                    container.badgeView.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.calendar_day_text
                        )
                    )
                    container.badgeView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    container.badgeView.setOnClickListener {
                        showEditDialogForDay(context, dateString, amount)
                    }
                    // Decor vizual pentru edit mode (de ex. border diferit)
                    val background = container.badgeView.background as? GradientDrawable
                    background?.setColor(getBadgeColor(context, amount))
                    background?.setStroke(3, ContextCompat.getColor(context, R.color.blue))
                } else {
                    // Mod normal: badge arată valoarea ml (nu e apăsabil)
                    if (amount > 0) {
                        container.badgeView.text =
                            context.getString(R.string.history_ml_badge, amount)
                        container.badgeView.setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.calendar_day_text
                            )
                        )
                        if (isDarkTheme) {
                            container.badgeView.setShadowLayer(3f, 1f, 1f, "#FF000000".toColorInt())
                        } else {
                            container.badgeView.setShadowLayer(1f, 1f, 1f, "#FF000000".toColorInt())
                        }
                    } else {
                        container.badgeView.text = ""
                        container.badgeView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    }
                    container.badgeView.setOnClickListener(null)
                    val background = container.badgeView.background as? GradientDrawable
                    background?.setColor(getBadgeColor(context, amount))
                    background?.setStroke(0, Color.TRANSPARENT)
                }
            }
        }

        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                // Folosește limba sistemului pentru titlul lunii
                val locale =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        resources.configuration.locales[0]
                    } else {
                        @Suppress("DEPRECATION")
                        resources.configuration.locale
                    }
                val title = month.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                container.textView.text =
                    title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

                // Folosește resurse pentru abrevieri zile
                val daysOfWeek = listOf(
                    getString(R.string.calendar_day_l),
                    getString(R.string.calendar_day_ma),
                    getString(R.string.calendar_day_mi),
                    getString(R.string.calendar_day_j),
                    getString(R.string.calendar_day_v),
                    getString(R.string.calendar_day_s),
                    getString(R.string.calendar_day_d)
                )
                for (i in daysOfWeek.indices) {
                    val dayView = container.weekDaysContainer.findViewById<TextView>(
                        container.weekDaysContainer.resources.getIdentifier(
                            "day$i", "id", container.weekDaysContainer.context.packageName
                        )
                    )
                    dayView.text = daysOfWeek[i]
                }
            }
        }

        // --- Încarcă Interstitial Ad la inițializare ---
        loadInterstitialAd()

        // --- Butonul de editare/cancel ---
        editButton.setOnClickListener {
            editMode = !editMode
            editButton.text =
                if (editMode) getString(R.string.history_cancel) else getString(R.string.history_edit)
            calendarView.notifyCalendarChanged()
            // --- Interstitial Ad la fiecare 3 editări (când ieși din edit mode) ---
            if (!editMode) {
                val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editCounter = prefs.getInt(COUNTER_KEY, 0) + 1
                if (editCounter % 3 == 0 && mInterstitialAd != null) {
                    mInterstitialAd?.show(requireActivity())
                    loadInterstitialAd() // Reîncarcă pentru data viitoare
                }
                // Salvează counter-ul actualizat
                prefs.edit { putInt(COUNTER_KEY, editCounter) }
            }
        }

        return view
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

    // --- Dialog de editare pentru o zi PERSONALIZAT cu layout și fundal transparent ---
    private fun showEditDialogForDay(context: Context, date: String, oldAmount: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_day, null)

        val titleText: TextView = dialogView.findViewById(R.id.dialogTitle)
        val messageText: TextView = dialogView.findViewById(R.id.dialogMessage)
        val input: EditText = dialogView.findViewById(R.id.dialogInput)
        val btnOk: Button = dialogView.findViewById(R.id.dialogOk)
        val btnCancel: Button = dialogView.findViewById(R.id.dialogCancel)

        titleText.text = getString(R.string.history_edit_day_title, date)
        messageText.text = getString(R.string.history_edit_day_message)
        input.inputType = EditorInfo.TYPE_CLASS_NUMBER
        input.setText(if (oldAmount > 0) oldAmount.toString() else "")
        input.setSelection(input.text.length)

        val alertDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Transparent background & dialog_bg.xml for rounded corners
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnOk.setOnClickListener {
            val valueStr = input.text.toString()
            val value = valueStr.toIntOrNull() ?: 0
            WaterHistoryStorage.setWaterForDate(context, date, value)
            calendarView.notifyCalendarChanged()
            alertDialog.dismiss()
        }
        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val dayNumberView: TextView = view.findViewById(R.id.dayNumber)
        val badgeView: TextView = view.findViewById(R.id.calendarDayBadge)
    }

    class MonthViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.monthTitle)
        val weekDaysContainer: LinearLayout = view.findViewById(R.id.weekDaysContainer)
    }

    private fun getBadgeColor(context: Context, amount: Int?): Int {
        return when {
            amount == null || amount == 0 -> ContextCompat.getColor(
                context,
                R.color.calendar_null_color
            )

            amount <= 999 -> ContextCompat.getColor(context, R.color.calendar_badge_1000)
            amount <= 1999 -> ContextCompat.getColor(context, R.color.calendar_badge_2000)
            else -> ContextCompat.getColor(context, R.color.calendar_badge_above_2000)
        }
    }
}
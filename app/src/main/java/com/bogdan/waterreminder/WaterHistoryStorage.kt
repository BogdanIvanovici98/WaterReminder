package com.bogdan.waterreminder

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object WaterHistoryStorage {
    private const val FILE_NAME = "water_history.json"

    private fun load(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        return if (!file.exists()) JSONObject() else JSONObject(file.readText())
    }

    private fun save(context: Context, history: JSONObject) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(history.toString())
    }

    fun addWaterForToday(context: Context, amount: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val history = load(context)
        val current = history.optInt(today, 0)
        history.put(today, current + amount)
        save(context, history)
    }

    fun resetToday(context: Context) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val history = load(context)
        history.put(today, 0)
        save(context, history)
    }

    fun getWaterForDate(context: Context, date: String): Int {
        val history = load(context)
        return history.optInt(date, 0)
    }

    /**
     * Set amount of water for a specific date, replacing the value.
     */
    fun setWaterForDate(context: Context, date: String, amount: Int) {
        val history = load(context)
        history.put(date, amount)
        save(context, history)
    }
}
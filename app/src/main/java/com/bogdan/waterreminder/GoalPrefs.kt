package com.bogdan.waterreminder

import android.content.Context
import androidx.core.content.edit

object GoalPrefs {
    private const val PREFS_NAME = "settings"
    private const val GOAL_KEY = "water_goal_ml"

    fun getGoal(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(GOAL_KEY, 0)
    }

    fun setGoal(context: Context, goal: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(GOAL_KEY, goal) }
    }
}
package com.screenassist.app

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenAssistPrefs", Context.MODE_PRIVATE)

    fun savePrompts(prompts: String) { prefs.edit().putString("PROMPTS", prompts).apply() }
    fun getPrompts(): String = prefs.getString("PROMPTS", "হ্যালো!\nকেমন আছেন?\nধন্যবাদ।") ?: "হ্যালো!\nকেমন আছেন?\nধন্যবাদ।"

    fun saveBubbleSize(size: Float) { prefs.edit().putFloat("BUBBLE_SIZE", size).apply() }
    fun getBubbleSize(): Float = prefs.getFloat("BUBBLE_SIZE", 35f)

    fun saveBubbleEdge(edge: String) { prefs.edit().putString("BUBBLE_EDGE", edge).apply() }
    fun getBubbleEdge(): String = prefs.getString("BUBBLE_EDGE", "Right") ?: "Right"
}

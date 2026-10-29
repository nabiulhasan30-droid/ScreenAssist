package com.screenassist.app

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenAssistPrefs", Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) { prefs.edit().putString("API_KEY", apiKey).apply() }
    fun getApiKey(): String = prefs.getString("API_KEY", "") ?: ""

    fun savePrompt(prompt: String) { prefs.edit().putString("PROMPT", prompt).apply() }
    fun getPrompt(): String = prefs.getString("PROMPT", "Translate this to Bengali:") ?: "Translate this to Bengali:"

    fun saveBubbleSize(size: Float) { prefs.edit().putFloat("BUBBLE_SIZE", size).apply() }
    fun getBubbleSize(): Float = prefs.getFloat("BUBBLE_SIZE", 35f)

    fun saveBubbleEdge(edge: String) { prefs.edit().putString("BUBBLE_EDGE", edge).apply() }
    fun getBubbleEdge(): String = prefs.getString("BUBBLE_EDGE", "Right") ?: "Right"
}

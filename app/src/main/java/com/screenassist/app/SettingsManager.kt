package com.screenassist.app

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenAssistPrefs", Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("API_KEY", apiKey).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("API_KEY", "") ?: ""
    }

    fun savePrompt(prompt: String) {
        prefs.edit().putString("PROMPT", prompt).apply()
    }

    fun getPrompt(): String {
        return prefs.getString("PROMPT", "Translate this to Bengali:") ?: "Translate this to Bengali:"
    }
}

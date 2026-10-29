package com.screenassist.app

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import android.content.Intent
import android.provider.Settings

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_api_config -> showApiConfigDialog()
                R.id.nav_prompt_manager -> showPromptManagerDialog()
                R.id.nav_start_bubble -> {
                    Toast.makeText(this, "Please TURN ON ScreenAssist from Accessibility Settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showApiConfigDialog() {
        val editText = EditText(this)
        editText.setText(settingsManager.getApiKey())
        AlertDialog.Builder(this)
            .setTitle("API Configuration")
            .setMessage("Enter your Gemini API Key:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.saveApiKey(editText.text.toString())
                Toast.makeText(this, "API Key Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPromptManagerDialog() {
        val editText = EditText(this)
        editText.setText(settingsManager.getPrompt())
        AlertDialog.Builder(this)
            .setTitle("Prompt Manager")
            .setMessage("Enter your instruction for AI:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.savePrompt(editText.text.toString())
                Toast.makeText(this, "Prompt Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

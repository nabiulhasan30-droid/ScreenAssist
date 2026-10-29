package com.screenassist.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle

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
                R.id.nav_bubble_manager -> showBubbleManagerDialog()
                R.id.nav_start_bubble -> {
                    Toast.makeText(this, "Turn ON ScreenAssist from Settings", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showApiConfigDialog() {
        val editText = EditText(this).apply { setText(settingsManager.getApiKey()) }
        AlertDialog.Builder(this).setTitle("API Configuration").setView(editText)
            .setPositiveButton("Save") { _, _ -> settingsManager.saveApiKey(editText.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showPromptManagerDialog() {
        val editText = EditText(this).apply { setText(settingsManager.getPrompt()) }
        AlertDialog.Builder(this).setTitle("Prompt Manager").setView(editText)
            .setPositiveButton("Save") { _, _ -> settingsManager.savePrompt(editText.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showBubbleManagerDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        
        val sizeLabel = TextView(this).apply { text = "Bubble Size (Default 35):" }
        val sizeInput = EditText(this).apply { 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsManager.getBubbleSize().toInt().toString()) 
        }
        
        val edgeLabel = TextView(this).apply { text = "Screen Edge Position:"; setPadding(0, 30, 0, 10) }
        val edgeSpinner = Spinner(this)
        edgeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Left", "Right"))
        edgeSpinner.setSelection(if (settingsManager.getBubbleEdge() == "Right") 1 else 0)

        layout.addView(sizeLabel)
        layout.addView(sizeInput)
        layout.addView(edgeLabel)
        layout.addView(edgeSpinner)

        AlertDialog.Builder(this).setTitle("Bubble Management").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val size = sizeInput.text.toString().toFloatOrNull() ?: 35f
                settingsManager.saveBubbleSize(size)
                settingsManager.saveBubbleEdge(edgeSpinner.selectedItem.toString())
                Toast.makeText(this, "Saved! Turn off and on the bubble to apply.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null).show()
    }
}

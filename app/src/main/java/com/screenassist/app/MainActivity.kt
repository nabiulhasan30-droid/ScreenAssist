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
import android.text.InputType

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

    private fun showPromptManagerDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val hintText = TextView(this).apply { text = "Enter prompts (Write each on a new line):"; setPadding(0, 0, 0, 20) }
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(5)
            gravity = android.view.Gravity.TOP
            setText(settingsManager.getPrompts())
        }
        layout.addView(hintText); layout.addView(editText)

        AlertDialog.Builder(this).setTitle("Prompt Manager").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.savePrompts(editText.text.toString())
                Toast.makeText(this, "Prompts Saved!", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showBubbleManagerDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        val sizeLabel = TextView(this).apply { text = "Bubble Size (Default 35):" }
        val sizeInput = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(settingsManager.getBubbleSize().toInt().toString()) }
        val edgeLabel = TextView(this).apply { text = "Screen Edge Position:"; setPadding(0, 30, 0, 10) }
        val edgeSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Left", "Right")) }
        edgeSpinner.setSelection(if (settingsManager.getBubbleEdge() == "Right") 1 else 0)

        layout.addView(sizeLabel); layout.addView(sizeInput); layout.addView(edgeLabel); layout.addView(edgeSpinner)
        AlertDialog.Builder(this).setTitle("Bubble Management").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.saveBubbleSize(sizeInput.text.toString().toFloatOrNull() ?: 35f)
                settingsManager.saveBubbleEdge(edgeSpinner.selectedItem.toString())
                Toast.makeText(this, "Saved! Turn Bubble off/on to apply.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }
}

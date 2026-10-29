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
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Color

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var settingsManager: SettingsManager
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }
        
        val apiKeyInput = EditText(this).apply { hint = "Enter Gemini API Key"; setText(settingsManager.getApiKey()) }
        val fetchBtn = Button(this).apply { text = "Fetch Available Models"; setBackgroundColor(Color.parseColor("#6200EE")); setTextColor(Color.WHITE) }
        
        val modelLabel = TextView(this).apply { text = "Select Model:"; setPadding(0, 20, 0, 10) }
        val modelSpinner = Spinner(this)
        
        var currentModels = settingsManager.getModelsList().toList()
        var adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currentModels)
        modelSpinner.adapter = adapter
        
        val savedModel = settingsManager.getSelectedModel()
        if (currentModels.contains(savedModel)) modelSpinner.setSelection(currentModels.indexOf(savedModel))

        fetchBtn.setOnClickListener {
            val key = apiKeyInput.text.toString()
            if (key.isBlank()) { Toast.makeText(this, "Enter API key first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            fetchBtn.text = "Fetching Models..."
            fetchBtn.isEnabled = false
            
            activityScope.launch {
                val fetchedModels = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        val response = connection.inputStream.bufferedReader().readText()
                        val regex = """"name":\s*"models/(gemini[^"]+)"""".toRegex()
                        regex.findAll(response).map { it.groupValues[1] }.distinct().toList()
                    } catch (e: Exception) { emptyList<String>() }
                }
                
                fetchBtn.text = "Fetch Available Models"
                fetchBtn.isEnabled = true
                
                if (fetchedModels.isNotEmpty()) {
                    settingsManager.saveModelsList(fetchedModels.toSet())
                    currentModels = fetchedModels
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentModels)
                    modelSpinner.adapter = adapter
                    modelSpinner.setSelection(0)
                    Toast.makeText(this@MainActivity, "Models Fetched Successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed! Check API Key or Internet.", Toast.LENGTH_LONG).show()
                }
            }
        }

        layout.addView(apiKeyInput)
        layout.addView(fetchBtn)
        layout.addView(modelLabel)
        layout.addView(modelSpinner)

        AlertDialog.Builder(this).setTitle("API & Model Configuration").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.saveApiKey(apiKeyInput.text.toString())
                if (currentModels.isNotEmpty()) settingsManager.saveSelectedModel(modelSpinner.selectedItem.toString())
                Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
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
        val sizeInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(settingsManager.getBubbleSize().toInt().toString()) }
        val edgeLabel = TextView(this).apply { text = "Screen Edge Position:"; setPadding(0, 30, 0, 10) }
        val edgeSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Left", "Right")) }
        edgeSpinner.setSelection(if (settingsManager.getBubbleEdge() == "Right") 1 else 0)

        layout.addView(sizeLabel); layout.addView(sizeInput); layout.addView(edgeLabel); layout.addView(edgeSpinner)
        AlertDialog.Builder(this).setTitle("Bubble Management").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                settingsManager.saveBubbleSize(sizeInput.text.toString().toFloatOrNull() ?: 35f)
                settingsManager.saveBubbleEdge(edgeSpinner.selectedItem.toString())
            }.setNegativeButton("Cancel", null).show()
    }
}

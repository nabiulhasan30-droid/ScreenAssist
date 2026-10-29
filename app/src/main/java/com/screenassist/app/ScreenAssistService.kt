package com.screenassist.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.graphics.Color
import kotlinx.coroutines.*
import com.google.ai.client.generativeai.GenerativeModel
import kotlin.math.abs

class ScreenAssistService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var settingsManager: SettingsManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var resultView: View? = null
    private var hideJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::bubbleView.isInitialized) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> bubbleView.visibility = View.GONE
                    Intent.ACTION_USER_PRESENT -> bubbleView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onServiceConnected() {
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        registerReceiver(screenReceiver, filter)
        
        createFloatingBubble()
        startAutoHideTimer()
    }

    private fun createFloatingBubble() {
        bubbleView = TextView(this).apply {
            textSize = settingsManager.getBubbleSize()
            setPadding(15, 15, 15, 15)
            setBackgroundColor(Color.parseColor("#44000000"))
            setTextColor(Color.WHITE)
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }

        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hideJob?.cancel()
                    bubbleView.text = "🤖"; bubbleView.alpha = 1.0f
                    initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - initialTouchX) < 15 && abs(event.rawY - initialTouchY) < 15) processScreenText()
                    startAutoHideTimer()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(bubbleView, params)
    }

    private fun startAutoHideTimer() {
        hideJob?.cancel()
        hideJob = serviceScope.launch {
            delay(2000)
            val edge = settingsManager.getBubbleEdge()
            params.x = if (edge == "Right") resources.displayMetrics.widthPixels else 0
            bubbleView.text = if (edge == "Right") "◀" else "▶"
            bubbleView.alpha = 0.6f
            if (::bubbleView.isInitialized) windowManager.updateViewLayout(bubbleView, params)
        }
    }

    private fun processScreenText() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) return
        
        val screenText = extractText(rootNode)
        val apiKey = settingsManager.getApiKey()
        val prompt = settingsManager.getPrompt()
        
        if (apiKey.isBlank()) { Toast.makeText(this, "Add API Key first!", Toast.LENGTH_SHORT).show(); return }

        Toast.makeText(this, "Reading screen...", Toast.LENGTH_SHORT).show()
        
        serviceScope.launch {
            val allModels = settingsManager.getModelsList().toList()
            var currentModel = settingsManager.getSelectedModel()
            var success = false
            var errorMessage = ""
            
            if (!allModels.contains(currentModel) && allModels.isNotEmpty()) currentModel = allModels[0]
            
            var startIndex = allModels.indexOf(currentModel)
            if (startIndex == -1) startIndex = 0
            
            for (i in startIndex until allModels.size) {
                val modelToTry = allModels[i]
                try {
                    if (i > startIndex) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ScreenAssistService, "Limit reached! Switching to: $modelToTry", Toast.LENGTH_LONG).show()
                        }
                        settingsManager.saveSelectedModel(modelToTry)
                    }
                    
                    val generativeModel = GenerativeModel(modelName = modelToTry, apiKey = apiKey)
                    val response = generativeModel.generateContent("$prompt\n\nScreen Text: $screenText")
                    
                    showResultOverlay(response.text ?: "No response")
                    success = true
                    break 
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unknown error"
                }
            }
            
            if (!success) showResultOverlay("Error after trying all models: $errorMessage")
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = java.lang.StringBuilder()
        if (node.text != null) sb.append(node.text).append("\n")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) { sb.append(extractText(child)); child.recycle() }
        }
        return sb.toString()
    }

    private fun showResultOverlay(result: String) {
        if (resultView != null) { windowManager.removeView(resultView); resultView = null }
        
        val context = this
        resultView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#F5FFFFFF")); setPadding(40, 40, 40, 40)
            val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val closeBtn = Button(context).apply {
                text = "Close"; setBackgroundColor(Color.parseColor("#6200EE")); setTextColor(Color.WHITE)
                setOnClickListener { windowManager.removeView(this@apply.parent.parent as View); resultView = null }
            }
            val textView = TextView(context).apply { text = result; textSize = 16f; setTextColor(Color.BLACK); setPadding(0, 20, 0, 0) }
            layout.addView(closeBtn); layout.addView(textView); addView(layout)
        }
        val rp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(resultView, rp)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
        if (resultView != null) windowManager.removeView(resultView)
        serviceScope.cancel()
    }
}

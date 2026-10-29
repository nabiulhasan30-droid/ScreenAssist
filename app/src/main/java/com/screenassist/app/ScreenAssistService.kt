package com.screenassist.app

import android.accessibilityservice.AccessibilityService
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
    private lateinit var settingsManager: SettingsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var resultView: View? = null

    override fun onServiceConnected() {
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingBubble()
    }

    private fun createFloatingBubble() {
        bubbleView = TextView(this).apply {
            text = "🤖"
            textSize = 35f
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0.5f // স্ক্রিনের পাশে লুকিয়ে থাকার সময় হালকা হয়ে থাকবে
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleView.alpha = 1.0f // ক্লিক করলে উজ্জ্বল হবে
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val xDiff = (event.rawX - initialTouchX).toInt()
                    val yDiff = (event.rawY - initialTouchY).toInt()
                    if (abs(xDiff) < 10 && abs(yDiff) < 10) {
                        processScreenText() // ক্লিক করলে কাজ শুরু করবে
                    }
                    // কাজ শেষে আবার স্ক্রিনের পাশে গিয়ে লুকিয়ে পড়বে
                    val screenWidth = resources.displayMetrics.widthPixels
                    params.x = if (params.x > screenWidth / 2) screenWidth else 0
                    bubbleView.alpha = 0.5f
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(bubbleView, params)
    }

    private fun processScreenText() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Toast.makeText(this, "Can't read screen right now", Toast.LENGTH_SHORT).show()
            return
        }
        val screenText = extractText(rootNode)
        if (screenText.isBlank()) {
            Toast.makeText(this, "No text found on screen", Toast.LENGTH_SHORT).show()
            return
        }
        
        val apiKey = settingsManager.getApiKey()
        val prompt = settingsManager.getPrompt()
        
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Please add API Key in the app settings!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Gemini AI is processing...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
                val response = generativeModel.generateContent("$prompt\n\nScreen Text: $screenText")
                showResultOverlay(response.text ?: "No response")
            } catch (e: Exception) {
                showResultOverlay("Error: ${e.message}")
            }
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = java.lang.StringBuilder()
        if (node.text != null) {
            sb.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(extractText(child))
                child.recycle()
            }
        }
        return sb.toString()
    }

    private fun showResultOverlay(result: String) {
        if (resultView != null) {
            windowManager.removeView(resultView)
            resultView = null
        }
        
        val context = this
        resultView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#EEFFFFFF"))
            setPadding(40, 40, 40, 40)
            
            val linearLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            val closeBtn = Button(context).apply {
                text = "Close AI Result"
                setBackgroundColor(Color.parseColor("#6200EE"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    windowManager.removeView(this@apply.parent.parent as View)
                    resultView = null
                }
            }
            
            val textView = TextView(context).apply {
                text = result
                textSize = 18f
                setTextColor(Color.BLACK)
                setPadding(0, 20, 0, 0)
            }
            
            linearLayout.addView(closeBtn)
            linearLayout.addView(textView)
            addView(linearLayout)
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(resultView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
        if (resultView != null) {
            windowManager.removeView(resultView)
        }
        serviceScope.cancel()
    }
}

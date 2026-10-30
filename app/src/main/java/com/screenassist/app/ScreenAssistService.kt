package com.screenassist.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
                    bubbleView.text = "📋"; bubbleView.alpha = 1.0f
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
                    if (abs(event.rawX - initialTouchX) < 15 && abs(event.rawY - initialTouchY) < 15) showPromptsMenu()
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

    private fun showPromptsMenu() {
        if (resultView != null) { windowManager.removeView(resultView); resultView = null }
        
        val promptsList = settingsManager.getPrompts().split("\n").filter { it.isNotBlank() }
        if (promptsList.isEmpty()) { Toast.makeText(this, "No prompts saved!", Toast.LENGTH_SHORT).show(); return }

        val context = this
        resultView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#F5FFFFFF")); setPadding(40, 40, 40, 40)
            val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            
            val closeBtn = Button(context).apply {
                text = "Close Menu"; setBackgroundColor(Color.parseColor("#D32F2F")); setTextColor(Color.WHITE)
                setOnClickListener { windowManager.removeView(this@apply.parent.parent as View); resultView = null }
            }
            layout.addView(closeBtn)

            for (promptText in promptsList) {
                val btn = Button(context).apply {
                    text = promptText; isAllCaps = false
                    setBackgroundColor(Color.parseColor("#6200EE")); setTextColor(Color.WHITE)
                    val marginParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    marginParams.setMargins(0, 15, 0, 0)
                    layoutParams = marginParams

                    setOnClickListener {
                        pasteTextToFocusedField(promptText)
                        windowManager.removeView(this@apply.parent.parent.parent as View)
                        resultView = null
                    }
                }
                layout.addView(btn)
            }
            addView(layout)
        }
        
        val rp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(resultView, rp)
    }

    private fun pasteTextToFocusedField(textToPaste: String) {
        val rootNode = rootInActiveWindow
        val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToPaste) }
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            if (!success) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ScreenAssist", textToPaste)
                clipboard.setPrimaryClip(clip)
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
            Toast.makeText(this, "Pasted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tap on a text field first!", Toast.LENGTH_LONG).show()
        }
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

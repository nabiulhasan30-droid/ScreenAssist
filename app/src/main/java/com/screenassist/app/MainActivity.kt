package com.screenassist.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "Welcome to ScreenAssist!"
        textView.textSize = 24f
        textView.setTextColor(Color.BLACK)
        textView.gravity = Gravity.CENTER
        
        setContentView(textView)
    }
}

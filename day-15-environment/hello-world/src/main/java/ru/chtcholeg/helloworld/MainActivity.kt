package ru.chtcholeg.helloworld

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "Hello, World!"
            textSize = 32f
            gravity = android.view.Gravity.CENTER
        }

        setContentView(textView)
    }
}

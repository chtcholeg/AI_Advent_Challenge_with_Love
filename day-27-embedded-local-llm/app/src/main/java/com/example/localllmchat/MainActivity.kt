package com.example.localllmchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.localllmchat.ui.ChatScreen
import com.example.localllmchat.ui.theme.LocalLlmChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalLlmChatTheme {
                ChatScreen()
            }
        }
    }
}

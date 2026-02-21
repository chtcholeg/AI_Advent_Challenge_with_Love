package com.example.localllmchat.data

import java.util.UUID

data class Message(
    val text: String,
    val isUser: Boolean,
    val id: String = UUID.randomUUID().toString()
)

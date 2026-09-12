package com.example.tuanyingshi.domain.model

data class Comment(
    val id: String,
    val username: String,
    val avatarLabel: String,
    val content: String,
    val likes: Int,
    val timeText: String,
)

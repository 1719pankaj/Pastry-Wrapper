package com.example.cpplearner.roomDB

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey(autoGenerate = true)
    val chatId: Long = 0,
    val summary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
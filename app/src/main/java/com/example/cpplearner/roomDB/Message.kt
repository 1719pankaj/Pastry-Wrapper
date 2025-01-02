package com.example.cpplearner.roomDB

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chatId: Long,
    val text: String,
    val isUser: Boolean,
    val modelName: String? = null, // Null for user messages, model name for assistant messages
    val timestamp: Long = System.currentTimeMillis()
)
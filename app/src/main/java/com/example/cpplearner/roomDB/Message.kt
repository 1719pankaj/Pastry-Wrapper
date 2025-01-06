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
    val thought: String? = null,  // New field for thought content
    val isUser: Boolean,
    val hasImage: Boolean = false,
    val imagePath: String? = null, // Store local file path
    val imageType: String? = null,  // Store mime type
    val hasAttachment: Boolean = false,
    val attachmentFileName: String? = null,  // Store local file path
    val modelName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
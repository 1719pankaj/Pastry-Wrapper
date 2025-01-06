package com.example.cpplearner.roomDB

import androidx.room.*

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChat(chatId: Long): List<Message>

    @Update
    suspend fun update(message: Message)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 4")
    suspend fun getLastMessages(chatId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastUserMessage(chatId: Long): Message

    @Query("SELECT imagePath FROM messages WHERE chatId = :chatId AND hasImage = 1")
    suspend fun getChatImages(chatId: Long): List<String>
    //Get attachmentFileName
    @Query("SELECT attachmentFileName FROM messages WHERE chatId = :chatId AND hasAttachment = 1")
    suspend fun getChatAttachments(chatId: Long): List<String>
}
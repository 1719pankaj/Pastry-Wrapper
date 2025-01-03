package com.example.cpplearner.roomDB

import androidx.room.*

@Dao
interface ChatDao {
    @Insert
    suspend fun insertChat(chat: Chat): Long

    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    suspend fun getAllChats(): List<Chat>

    @Transaction
    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatWithMessages(chatId: Long): ChatWithMessages?

    @Query("UPDATE chats SET summary = :summary WHERE chatId = :chatId")
    suspend fun updateChatSummary(chatId: Long, summary: String)

    @Query("SELECT * FROM chats WHERE chatId IN (SELECT DISTINCT chatId FROM messages)")
    fun getChatsWithMessages(): List<Chat>

    @Query("SELECT * FROM chats WHERE summary IS NULL OR summary = ''")
    suspend fun getChatsWithoutSummary(): List<Chat>

    @Query("DELETE FROM chats WHERE chatId NOT IN (SELECT DISTINCT chatId FROM messages)")
    suspend fun deleteEmptyChats()

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: Long)

}
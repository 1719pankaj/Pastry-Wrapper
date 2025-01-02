package com.example.cpplearner.roomDB

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Message::class, Chat::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
}
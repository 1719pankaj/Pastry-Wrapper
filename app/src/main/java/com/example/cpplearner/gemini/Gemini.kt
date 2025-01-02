package com.example.cpplearner.gemini

import android.util.Log
import com.example.cpplearner.provider.ModelProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

class Gemini(val apiKey: String, val modelName: String) {
    lateinit var model: GenerativeModel

    private var GEMINI_API_KEY: String = apiKey

    val status = initModel(modelName)

    val chatHistory = mutableListOf(
        content("user") {
            text("Hii, I am trying to make a wrapper for Gemini API and this session is just dev and debug so don't bother wasting too may tokens unless I mention banana its your signal to blast out a massive wall of text. And please don't mind me saying hii a billion times.")
        },
        content("model") {
            text("Okay, hii! Sounds good. I understand this is a dev/debug session, so I'll keep my responses concise and not go overboard on tokens unless you say \"banana\". And no worries about the \"hiis,\" I'm here for you. Let's get this Gemini API wrapper rolling!\n\nSo, what are we working on today? What's the first step you're thinking of taking?\n")
        },
    )

    suspend fun sendMessage(message: String): String? {
        val chat = model.startChat()
        val response = chat.sendMessage(message)
        return response.candidates.first().content.parts.first().asTextOrNull()
    }

    fun chatHistorytoText(): List<String> {
        return chatHistory.map { it.parts.first().asTextOrNull() ?: "" }
    }

    suspend fun sendMessageStream(message: String): Flow<String> {
    val chat = model.startChat(chatHistory)
    updateChatHistory(message, true)
    var fullResponse = ""
    return chat.sendMessageStream(message)
        .map { response ->
            val text = response.text ?: ""
            fullResponse += text
            text
        }.onCompletion {
            updateChatHistory(fullResponse, false)
        }
}

    fun updateChatHistory(message: String, user: Boolean) {
        chatHistory.add(content(if (user) "user" else "model") {
            text(message)
        })
    }

    fun initModel(modelName: String): Int {
        return try {
            model = ModelProvider.getModel(modelName, GEMINI_API_KEY)
            1
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }


}
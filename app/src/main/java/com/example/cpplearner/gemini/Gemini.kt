package com.example.cpplearner.gemini

import android.graphics.Bitmap
import android.util.Log
import com.example.cpplearner.provider.ModelConfig
import com.example.cpplearner.provider.ModelProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

class Gemini(val apiKey: String, val modelName: String) {
    lateinit var model: GenerativeModel

    private var GEMINI_API_KEY: String = apiKey

    val status = initModel(modelName)

    val chatHistory = mutableListOf(
        content("user") {
            text("Hii, I am trying to make a wrapper for Gemini API and this session is just dev and debug so don't bother wasting too many tokens unless I mention banana its your signal to blast out a massive wall of text. And please don't mind me saying hii a billion times.")
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

    suspend fun sendMessageStream(message: String): Flow<Pair<String, String>> {
        val chat = model.startChat(chatHistory)
        updateChatHistory(message, "",true)
        var fulltext = ""
        var thot = ""
        var text = ""
        return chat.sendMessageStream(message)
            .map { response ->
                thot = response.candidates.first().content.parts.first().asTextOrNull() ?: ""
                fulltext = response.text ?: ""
                text = fulltext.replace(thot, "")
                Pair(text, thot)
            }.onCompletion {
                updateChatHistory(text, thot,  false)
            }
    }

    suspend fun sendMessageWithImageStream(message: String, bitmap: Bitmap): Flow<Pair<String, String>> {
        val chat = model.startChat()
        updateChatHistory(message, "", true)

        return flow {
            try {
                val response = model.generateContentStream(
                    content {
                        image(bitmap)
                        text(message)
                    }
                )

                var fulltext = ""
                var thot = ""
                var text = ""

                response.collect { chunk ->
                    thot = chunk.candidates.first().content.parts.first().asTextOrNull() ?: ""
                    fulltext = chunk.text ?: ""
                    text = fulltext.replace(thot, "")
                    emit(Pair(text, thot))
                }

                updateChatHistory(text, thot, false)
            } catch (e: Exception) {
                Log.e("Gemini", "Error generating image response", e)
                emit(Pair("Error analyzing image", e.localizedMessage ?: "Unknown error"))
            }
        }
    }

    fun updateChatHistory(message: String, thought: String, isUser: Boolean) {
        if (isUser) {
            chatHistory.add(content("user") {
                text(message)
            })
        } else {
            if (message.isBlank()) {
                chatHistory.add(content("model") {
                    text(thought) // Message
                })
            } else {
                chatHistory.add(content("model") {
                    text(message)
                    text(thought) // Thought
                })
            }
        }
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
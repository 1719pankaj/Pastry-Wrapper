package com.example.cpplearner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.cpplearner.databinding.FragmentDebugBinding
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DebugFragment : Fragment() {

    lateinit var binding: FragmentDebugBinding
    lateinit var model: GenerativeModel
    lateinit var GEMINI_API_KEY: String
    lateinit var chat: Chat

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDebugBinding.inflate(inflater, container, false)


        GEMINI_API_KEY = "AIzaSyDyGLtUDcwOR_wnz3jP3_H9OLQfPPf5o_4"
        val chatHistory = listOf(
            content("user") {
                text("Hii")
            },
            content("model") {
                text("The user greeted me with \"Hii\". A simple and friendly greeting. A natural response would be to reciprocate the greeting and offer assistance.")
                text("Hello! How can I help you today?\n")
            },
        )
        model = GenerativeModel(
            "gemini-2.0-flash-thinking-exp-1219",
//            "gemini-2.0-flash-exp",
            GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 1f
                topK = 35
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "text/plain"
            },
        )

        chat = model.startChat(chatHistory)

        binding.runTestBT.setOnClickListener {
            lifecycleScope.launch {
                collectResponse()
            }
        }

        return binding.root
    }

    var fulltext = ""
    fun runGeminiTest(message: String): Flow<Pair<String, String>> {
        return chat.sendMessageStream(message)
            .map { response ->
                val thot = response.candidates.first().content.parts.first().asTextOrNull() ?: ""
                fulltext = response.text ?: ""
                val text = fulltext.replace(thot, "")
                Pair(text, thot)
            }
    }

    private suspend fun collectResponse() {
        var message = ""
        var thought = ""

        runGeminiTest("Hello").collect { (text, thot) ->
            message += text
            thought += thot
            binding.messageTv.text = if (message.isNotBlank()) message.trimStart() else thought
            binding.thoughtTv.text = if (message.isNotBlank()) thought.trimStart() else ""
        }
    }

}
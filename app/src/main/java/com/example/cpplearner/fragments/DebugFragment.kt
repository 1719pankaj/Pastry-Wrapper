package com.example.cpplearner.fragments

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
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
import kotlin.math.log

class DebugFragment : Fragment() {

    lateinit var binding: FragmentDebugBinding
    lateinit var model: GenerativeModel
    lateinit var DEBUG_API_KEY: String
    lateinit var chat: Chat

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDebugBinding.inflate(inflater, container, false)

        binding.thoughtTv.movementMethod = ScrollingMovementMethod()
        binding.messageTv.movementMethod = ScrollingMovementMethod()


        DEBUG_API_KEY = "AIzaSyCrMe41shwixfO9Oo8o8ciwkceSr9apOXs"
        val chatHistory = listOf(
//            content("user") {
//                text("Hii")
//            },
//            content("model") {
//                text("The user greeted me with \"Hii\". A simple and friendly greeting. A natural response would be to reciprocate the greeting and offer assistance.")
//                text("Hello! How can I help you today?\n")
//            },
            content("user") {
                text("Hii, I am trying to make a wrapper for Gemini API and this session is just dev and debug so don't bother wasting too many tokens unless I mention banana its your signal to blast out a massive wall of text. And please don't mind me saying hii a billion times.")
            },
            content("model") {
                text("Okay, hii! Sounds good. I understand this is a dev/debug session, so I'll keep my responses concise and not go overboard on tokens unless you say \"banana\". And no worries about the \"hiis,\" I'm here for you. Let's get this Gemini API wrapper rolling!\n\nSo, what are we working on today? What's the first step you're thinking of taking?\n")
            },
        )
        model = GenerativeModel(
            "gemini-2.0-flash-thinking-exp-1219",
//            "gemini-2.0-flash-exp",
            DEBUG_API_KEY,
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
            val message = binding.debugMessageET.text.toString()
            binding.debugMessageET.text.clear()
            lifecycleScope.launch {
                collectResponse(message)
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

    private suspend fun collectResponse(debugMessage: String) {
        var message = ""
        var thought = ""

        runGeminiTest(debugMessage).collect { (text, thot) ->
            message += text
            thought += thot
            binding.messageTv.text = if (message.isNotBlank()) message.trimStart() else thought
            binding.thoughtTv.text = if (message.isNotBlank()) thought.trimStart() else ""
        }
    }

}
package com.example.cpplearner.provider

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

object ModelProvider {
    fun getModel(modelName: String, apiKey: String): GenerativeModel {
        return GenerativeModel(
            modelName,
            apiKey,
            generationConfig = generationConfig {
                temperature = 1f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "text/plain"
            }
        )
    }
}
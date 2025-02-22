package com.example.cpplearner.provider

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

object ModelProvider {
    fun getModel(modelName: String, apiKey: String): GenerativeModel {
        val modelConfig = ModelConfigProvider.getModelByName(modelName) ?: ModelConfigProvider.getDefaultModel()

        return GenerativeModel(
            modelName,
            apiKey,
            generationConfig = generationConfig {
                temperature = modelConfig.temperature
                topK = modelConfig.topK
                topP = modelConfig.topP
                maxOutputTokens = modelConfig.maxOutputTokens
                responseMimeType = modelConfig.responseMimeType
            }
        )
    }
}
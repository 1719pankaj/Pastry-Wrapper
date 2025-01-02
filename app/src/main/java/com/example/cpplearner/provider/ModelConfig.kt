package com.example.cpplearner.provider

data class ModelConfig(
    val displayName: String,
    val modelName: String,
    val inputPricing: String,
    val outputPricing: String,
    val rateLimits: String,
    val knowledgeCutoff: String
)

object ModelConfigProvider {
    private val models = listOf(
        ModelConfig(
            displayName = "Gemini 1.5 Pro",
            modelName = "gemini-1.5-pro",
            inputPricing = "≤128K tokens: Input $0.075/1K tokens",
            outputPricing = "≤128K tokens: Output $0.30/1K tokens",
            rateLimits = "2000 RPM (Free)\n15 RPM, 1500 req/day",
            knowledgeCutoff = "Sep 2024"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Flash Experimental",
            modelName = "gemini-2.0-flash-exp",
            inputPricing = "Input Free",
            outputPricing = "Output Free",
            rateLimits = "1500 RPM (Free)\n10 RPM, 1000 req/day",
            knowledgeCutoff = "Sep 2024"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Flash Thinking Experimental",
            modelName = "gemini-2.0-flash-thinking-exp-1219",
            inputPricing = "Input Free",
            outputPricing = "Output Free",
            rateLimits = "1500 RPM (Free)\n10 RPM, 1000 req/day",
            knowledgeCutoff = "Sep 2024"
        ),
        ModelConfig(
            displayName = "Gemini Experimental 1206",
            modelName = "gemini-exp-1206",
            inputPricing = "Input Free",
            outputPricing = "Output Free",
            rateLimits = "1500 RPM (Free)\n10 RPM, 1000 req/day",
            knowledgeCutoff = "Sep 2024"
        ),
        ModelConfig(
            displayName = "LearnLM 1.5 Pro Experimental",
            modelName = "learnlm-1.5-pro-experimental",
            inputPricing = "Input Free",
            outputPricing = "Output Free",
            rateLimits = "1500 RPM (Free)\n10 RPM, 1000 req/day",
            knowledgeCutoff = "Sep 2024"
        )
    )

    fun getModels(): List<ModelConfig> = models

    fun getModelByName(modelName: String): ModelConfig? {
        return models.find { it.modelName == modelName }
    }

    fun getDefaultModel(): ModelConfig = models[0]
}
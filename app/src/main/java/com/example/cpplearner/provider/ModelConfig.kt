package com.example.cpplearner.provider

data class ModelConfig(
    val displayName: String,
    val modelName: String,
    val contextSizeLimit: Long,
    val inputPricing: String,
    val outputPricing: String,
    val rateLimits: String,
    val knowledgeCutoff: String,
    val specialFlags: List<String> = emptyList(),
    val temperature: Float = 1f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 8192,
    val responseMimeType: String = "text/plain"
)

object ModelConfigProvider {
    private val models = listOf(
        ModelConfig(
            displayName = "Gemini 2.0 Flash",
            modelName = "gemini-2.0-flash",
            contextSizeLimit = 1048576,
            inputPricing = "Input $0.10/1M tokens",
            outputPricing = "Output $0.40/1M tokens",
            rateLimits = "2000 RPM \n(Free) 15 RPM, 1500 req/day",
            knowledgeCutoff = "Aug 2024",
            specialFlags = listOf<String>("Prod-ready"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 40,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Flash Lite Preview 02-05",
            modelName = "gemini-2.0-flash-lite-preview-02-05",
            contextSizeLimit = 1048576,
            inputPricing = "Input $0.075/1M tokens",
            outputPricing = "Output $0.30/1M tokens",
            rateLimits = "4000 RPM \n(Free) 30 RPM, 1500 req/day",
            knowledgeCutoff = "Aug 2024",
            specialFlags = listOf<String>("Preview"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 64,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Pro Experimental 02-05",
            modelName = "gemini-2.0-pro-exp-02-05",
            contextSizeLimit = 2097152,
            inputPricing = "Input $0.0/1M tokens",
            outputPricing = "Output $0.0/1M tokens",
            rateLimits = "5 RPM \n(Free) 2 RPM, 50 req/day",
            knowledgeCutoff = "Aug 2024",
            specialFlags = listOf<String>("Experimental", "Free"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 64,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Flash Thinking Experimental 01-21",
            modelName = "gemini-2.0-flash-thinking-exp-01-21",
            contextSizeLimit = 1048576,
            inputPricing = "Input $0.0/1M tokens",
            outputPricing = "Output $0.0/1M tokens",
            rateLimits = "10 RPM \n(Free) 10 RPM, 1500 req/day",
            knowledgeCutoff = "Aug 2024",
            specialFlags = listOf<String>("Experimental", "Free"),
            // Optional: override default values if needed
            temperature = 0.7f,
            topK = 64,
            topP = 0.95f,
            maxOutputTokens = 65536,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 2.0 Flash Experimental",
            modelName = "gemini-2.0-flash-exp",
            contextSizeLimit = 1048576,
            inputPricing = "Input $0.0/1M tokens",
            outputPricing = "Output $0.0/1M tokens",
            rateLimits = "10 RPM \n(Free) 10 RPM, 1500 req/day",
            knowledgeCutoff = "Aug 2024",
            specialFlags = listOf<String>("Free", "Experimental"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 40,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "LearnLM 1.5 Pro Experimental",
            modelName = "learnlm-1.5-pro-experimental",
            contextSizeLimit = 32767,
            inputPricing = "NA",
            outputPricing = "NA",
            rateLimits = "NA",
            knowledgeCutoff = "Sep 2024",
            specialFlags = listOf<String>("LearnLM", "Alfa", "Experimental"),
            temperature = 1f,
            topK = 64,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 1.5 Pro",
            modelName = "gemini-1.5-pro",
            contextSizeLimit = 2000000,
            inputPricing = "≤128K tokens: Input $0.075/1K tokens",
            outputPricing = "≤128K tokens: Output $0.30/1K tokens",
            rateLimits = "1000 RPM \n(Free) 2 RPM, 50 req/day",
            knowledgeCutoff = "Sep 2024",
            specialFlags = listOf<String>("Prod-ready"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 40,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),

        ModelConfig(
            displayName = "Gemini 1.5 Flash",
            modelName = "gemini-1.5-flash",
            contextSizeLimit = 1000000,
            inputPricing = "≤128K tokens: Input $0.075/1K tokens",
            outputPricing = "≤128K tokens: Output $0.30/1K tokens",
            rateLimits = "4000 RPM \n(Free) 15 RPM, 1500 req/day",
            knowledgeCutoff = "Sep 2024",
            specialFlags = listOf<String>("Prod-ready"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 40,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini 1.5 Flash 8B",
            modelName = "gemini-1.5-flash-8b",
            contextSizeLimit = 1000000,
            inputPricing = "≤128K tokens: Input $0.075/1K tokens",
            outputPricing = "≤128K tokens: Output $0.30/1K tokens",
            rateLimits = "2000 RPM \n(Free) 15 RPM, 1500 req/day",
            knowledgeCutoff = "Sep 2024",
            specialFlags = listOf<String>("Prod-ready"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 40,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
        ModelConfig(
            displayName = "Gemini Experimental 1206",
            modelName = "gemini-exp-1206",
            contextSizeLimit = 1048576,
            inputPricing = "Input $0.0/1M tokens",
            outputPricing = "Output $0.0/1M tokens",
            rateLimits = "1500 RPM (Free)\n10 RPM, 1000 req/day",
            knowledgeCutoff = "Sep 2024",
            specialFlags = listOf<String>("Deprecated", "Free", "Experimental"),
            // Optional: override default values if needed
            temperature = 1f,
            topK = 64,
            topP = 0.95f,
            maxOutputTokens = 8192,
            responseMimeType = "text/plain"
        ),
    )

    fun getModels(): List<ModelConfig> = models

    fun getModelByName(modelName: String): ModelConfig? {
        return models.find { it.modelName == modelName }
    }

    fun getDefaultModel(): ModelConfig = models[1]
}
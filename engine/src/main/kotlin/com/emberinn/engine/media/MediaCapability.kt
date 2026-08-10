package com.emberinn.engine.media

/**
 * 媒体内联能力判定（对齐官方 openai.js isImageInliningSupported / isVideoInliningSupported /
 * isAudioInliningSupported：main_api=openai + media_inlining + 模型白名单 includes + source 分支）。
 * 白名单与 source 分支为官方逐字移植；模型列表元数据（openrouter/mistral/moonshot 等）由调用方
 * 通过 modalitySupports 提供，App 暂未拉取模型元数据时回退 false（边界登记）。
 */
object MediaCapability {

    val VISION_MODELS: List<String> = listOf(
        // OpenAI
        "chatgpt-4o-latest", "gpt-4-turbo", "gpt-4-vision", "gpt-4.1", "gpt-4.5-preview",
        "gpt-4o", "gpt-5", "o1", "o3", "o4-mini",
        // Claude
        "claude-3", "claude-opus-4", "claude-sonnet-4", "claude-haiku-4",
        // Cohere
        "c4ai-aya-vision", "command-a-vision",
        // Google AI Studio
        "gemini-2.0", "gemini-2.5", "gemini-3", "gemini-exp-1206", "learnlm",
        "gemini-robotics", "gemma-3-27b", "gemma-3-12b", "gemma-3-4b", "gemma-4",
        // MistralAI
        "mistral-small-2503", "mistral-small-2506", "mistral-small-latest",
        "mistral-medium-latest", "mistral-medium-2505", "mistral-medium-2508", "pixtral",
        // xAI (Grok)
        "grok-4", "grok-2-vision",
        // Moonshot
        "moonshot-v1-8k-vision-preview", "moonshot-v1-32k-vision-preview",
        "moonshot-v1-128k-vision-preview", "kimi-k2.5", "kimi-latest",
        // Z.AI (GLM)
        "glm-4.5v", "glm-4.6v", "glm-5v-turbo", "autoglm-phone",
        // SiliconFlow
        "Qwen/Qwen3-VL-32B-Instruct", "Qwen/Qwen3-VL-8B-Instruct",
        "Qwen/Qwen3-VL-235B-A22B-Instruct", "Qwen/Qwen3-VL-30B-A3B-Instruct",
        "zai-org/GLM-4.5V",
    )

    val VIDEO_MODELS: List<String> = listOf(
        "gemini-2.0", "gemini-2.5", "gemini-exp-1206", "gemini-3", "gemma-4",
        "glm-4.5v", "glm-4.6v", "glm-5v-turbo",
    )

    val AUDIO_MODELS: List<String> = listOf(
        "gemini-2.0", "gemini-2.5", "gemini-3", "gemini-exp-1206",
        "gpt-4o-audio", "gpt-4o-realtime", "gpt-4o-mini-audio", "gpt-4o-mini-realtime",
        "gpt-audio", "gpt-realtime",
    )

    /** 官方 source 枚举值（与 App ProviderSpec.source 映射）。 */
    object Source {
        const val OPENAI = "openai"
        const val AZURE_OPENAI = "azure_openai"
        const val MAKERSUITE = "makersuite"
        const val VERTEXAI = "vertexai"
        const val CLAUDE = "claude"
        const val OPENROUTER = "openrouter"
        const val CUSTOM = "custom"
        const val MISTRALAI = "mistralai"
        const val COHERE = "cohere"
        const val XAI = "xai"
        const val MOONSHOT = "moonshot"
        const val ZAI = "zai"
        const val SILICONFLOW = "siliconflow"
        const val WORKERS = "workers"
    }

    fun isImageInliningSupported(
        source: String,
        model: String,
        vision: Boolean = false,
    ): Boolean = when (source) {
        Source.OPENAI, Source.AZURE_OPENAI ->
            // 官方逐字：includes(视觉前缀) && some(x => !includes(x))（后者几乎恒真，原文如此）
            VISION_MODELS.any { model.contains(it) } &&
                listOf("gpt-4-turbo-preview", "o1-mini", "o3-mini").any { !model.contains(it) }
        Source.MAKERSUITE, Source.VERTEXAI, Source.CLAUDE, Source.COHERE, Source.XAI, Source.ZAI, Source.SILICONFLOW ->
            VISION_MODELS.any { model.contains(it) }
        Source.OPENROUTER, Source.MISTRALAI, Source.MOONSHOT, Source.WORKERS -> vision
        Source.CUSTOM -> true
        else -> false
    }

    fun isVideoInliningSupported(
        source: String,
        model: String,
        video: Boolean = false,
    ): Boolean = when (source) {
        Source.MAKERSUITE, Source.VERTEXAI, Source.ZAI -> VIDEO_MODELS.any { model.contains(it) }
        Source.OPENROUTER -> video
        else -> false
    }

    fun isAudioInliningSupported(
        source: String,
        model: String,
        audio: Boolean = false,
    ): Boolean = when (source) {
        Source.OPENAI, Source.MAKERSUITE, Source.VERTEXAI -> AUDIO_MODELS.any { model.contains(it) }
        Source.OPENROUTER -> audio
        Source.CUSTOM -> true
        else -> false
    }
}

package com.emberinn.engine.expression

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.prompt.ReasoningEngine
import com.emberinn.engine.prompt.ReasoningSettings
import com.emberinn.engine.worldinfo.VectorTextUtils
import kotlin.random.Random

/** 官方 EXPRESSION_API 数值（local/extras/webllm 需对应后端，App 端消费 llm/none）。 */
enum class ExpressionApi(val value: Int) {
    LOCAL(0),
    EXTRAS(1),
    LLM(2),
    WEBLLM(3),
    NONE(99),
}

/** 官方 PROMPT_TYPE。 */
enum class ExpressionPromptType(val value: String) {
    RAW("raw"),
    FULL("full"),
}

/**
 * 表情精灵引擎（对齐官方 extensions/expressions + endpoints/sprites.js 纯逻辑）：
 * 文件名→标签、图片元数据、按标签分组排序、按表达式选立绘、LLM 分类提示词构造与响应解析。
 * DOM 显示/动画属于 App/服务层。
 */
object ExpressionEngine {

    const val RESET_SPRITE_LABEL = "#reset"

    /** 官方 DEFAULT_LLM_PROMPT（{{labels}} 占位）。 */
    const val DEFAULT_LLM_PROMPT =
        "Ignore previous instructions. Classify the emotion of the last message. Output just one word, e.g. \"joy\" or \"anger\". Choose only one of the following labels: {{labels}}"

    /** 官方 DEFAULT_EXPRESSIONS：28 个 GoEmotions 标签（resolveExpressionsList 离线回退集）。 */
    val DEFAULT_EXPRESSIONS: List<String> = listOf(
        "admiration", "amusement", "anger", "annoyance", "approval", "caring", "confusion",
        "curiosity", "desire", "disappointment", "disapproval", "disgust", "embarrassment",
        "excitement", "fear", "gratitude", "grief", "joy", "love", "nervousness", "optimism",
        "pride", "realization", "relief", "remorse", "sadness", "surprise", "neutral",
    )

    private val labelRegex = Regex("""^(.+?)(?:[-\.].*?)?$""")
    private val extensionRegex = Regex("""\.[^/.]+$""")


    data class SpriteEntry(val label: String, val path: String)

    data class ExpressionImage(
        val expression: String,
        val fileName: String,
        val title: String,
        val imageSrc: String,
        val type: String = "success",
        val isCustom: Boolean? = null,
    )

    data class ExpressionGroup(
        val label: String,
        val files: MutableList<ExpressionImage>,
    )

    data class ExpressionSettings(
        val fallbackExpression: String? = null,
        val allowMultiple: Boolean = false,
        val rerollIfSame: Boolean = false,
        val customLabels: Set<String> = emptySet(),
    )

    /**
     * 对齐 expressions sampleClassifyText：
     * 去宏/引号/星号，短文本裁到句尾；长文本取首尾各 250 字符后拼接；LLM 模式只 trim。
     */
    fun sampleClassifyText(text: String, useLlm: Boolean = false): String? {
        if (text.isEmpty()) return text
        var result = MacroEngine.substitute(text, MacroEnv(user = "", char = "")).replace(Regex("""[*"]"""), "")
        if (useLlm) return result.trim()

        val threshold = 500
        val half = threshold / 2
        result = if (text.length < threshold) {
            VectorTextUtils.trimToEndSentence(result)
        } else {
            VectorTextUtils.trimToEndSentence(result.take(half)) + " " +
                VectorTextUtils.trimToStartSentence(result.takeLast(half))
        }
        return result.trim()
    }

    /** 对齐 sprites.js GET /get：filename 转小写后提取主标签（joy / joy-1 / joy.expressive → joy）。 */
    fun labelFromFilename(fileName: String): String {
        val lower = fileName.lowercase()
        return labelRegex.find(lower)?.groupValues?.get(1) ?: lower
    }

    /** 对齐 expressions getExpressionImageData。 */
    fun imageData(sprite: SpriteEntry, customLabels: Set<String>? = null): ExpressionImage {
        val fileName = sprite.path.substringAfterLast('/').substringBefore('?')
        val title = fileName.replace(extensionRegex, "")
        return ExpressionImage(
            expression = sprite.label,
            fileName = fileName,
            title = title,
            imageSrc = sprite.path,
            isCustom = customLabels?.contains(sprite.label),
        )
    }

    /** 对齐 getSpritesList 分组：同标签合并，主文件排最前，其余标 additional。 */
    fun groupSprites(
        sprites: List<SpriteEntry>,
        customLabels: Set<String>? = null,
    ): List<ExpressionGroup> {
        val groups = mutableListOf<ExpressionGroup>()
        for (sprite in sprites) {
            val image = imageData(sprite, customLabels)
            val existing = groups.firstOrNull { it.label == sprite.label }
            if (existing != null) {
                existing.files += image
            } else {
                groups += ExpressionGroup(sprite.label, mutableListOf(image))
            }
        }
        for (group in groups) {
            group.files.sortWith { a, b ->
                when {
                    a.title == group.label && b.title == group.label -> 0
                    a.title == group.label -> -1
                    b.title == group.label -> 1
                    else -> a.title.compareTo(b.title)
                }
            }
            for (i in 1 until group.files.size) {
                group.files[i] = group.files[i].copy(type = "additional")
            }
        }
        return groups
    }

    /**
     * 对齐 chooseSpriteForExpression：
     * fallback 表情、多立绘随机、rerollIfSame 排除上一张、overrideSpriteFile 指定文件。
     */
    fun chooseSprite(
        folderName: String,
        expression: String,
        spriteCache: Map<String, List<ExpressionGroup>>,
        settings: ExpressionSettings = ExpressionSettings(),
        prevSrc: String? = null,
        overrideFile: String? = null,
        random: () -> Double = { Random.nextDouble() },
    ): ExpressionImage? {
        val cache = spriteCache[folderName] ?: return null
        if (expression == RESET_SPRITE_LABEL) return null

        var group = cache.firstOrNull { it.label == expression }
        if (group?.files?.isNotEmpty() != true && settings.fallbackExpression != null) {
            group = cache.firstOrNull { it.label == settings.fallbackExpression }
        }
        if (group?.files?.isNotEmpty() != true) return null

        var spriteFile = group.files.first()
        if (overrideFile != null) {
            spriteFile = group.files.firstOrNull { it.fileName == overrideFile } ?: spriteFile
        } else if (settings.allowMultiple && group.files.size > 1) {
            var possible: List<ExpressionImage> = group.files
            if (settings.rerollIfSame) {
                possible = possible.filter { prevSrc == null || it.imageSrc != prevSrc }
            }
            if (possible.isEmpty()) return null
            spriteFile = possible[((random() * possible.size).toInt()).coerceIn(0, possible.size - 1)]
        }
        return spriteFile
    }

    /**
     * 对齐官方 getLlmPrompt：labels → `"a", "b"` 串后替换 {{labels}}；
     * customPrompt 为空时用 DEFAULT_LLM_PROMPT（官方 substituteParamsExtended(customPrompt) || 默认）。
     */
    fun llmPrompt(labels: List<String>, customPrompt: String? = null): String {
        val labelsString = labels.joinToString(", ") { "\"$it\"" }
        val template = customPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_LLM_PROMPT
        return template.replace("{{labels}}", labelsString)
    }

    /**
     * 对齐官方 parseLlmResponse：
     * 1) JSON.parse 取 emotion（trim + 小写，必须在 labels 内）；
     * 2) 失败 → removeReasoningFromString 清理后 Fuse 模糊搜索（App 等价：标签整词匹配 → includes 匹配）；
     * 3) 全部失败返回 null（调用方走 fallback_expression）。
     */
    fun parseLlmResponse(
        emotionResponse: String,
        labels: List<String>,
        reasoningSettings: ReasoningSettings = ReasoningSettings(),
    ): String? {
        // 1) JSON {emotion: "..."}
        runCatching {
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(emotionResponse)
            val emotion = (parsed as? kotlinx.serialization.json.JsonObject)
                ?.get("emotion") as? kotlinx.serialization.json.JsonPrimitive
            val response = emotion?.content?.trim()?.lowercase()
            if (!response.isNullOrBlank() && labels.contains(response)) return response
        }

        // 2) 清理推理内容后模糊匹配（官方 Fuse → 简化：整词 → includes）
        val cleaned = ReasoningEngine.removeReasoningFromString(emotionResponse, reasoningSettings).lowercase()
        val wordMatch = Regex("""[a-z]+""").findAll(cleaned).map { it.value }.toList()
        wordMatch.firstOrNull { it in labels }?.let { return it }
        for (label in labels) {
            if (cleaned.contains(label.lowercase())) return label
        }
        return null
    }
}

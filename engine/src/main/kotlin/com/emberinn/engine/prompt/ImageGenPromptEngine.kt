package com.emberinn.engine.prompt

import java.text.Normalizer

/**
 * 官方 stable-diffusion 扩展 prompt 相关纯函数（index.js 1:1）：
 * - generationMode 数值 / modeLabels / triggerWords / messageTrigger（activationRegex + specialCases）
 * - defaultPromptTemplates（13 个 generationMode 模板，官方文本逐字）
 * - stringFormat（utils.js L757-764 1:1）
 * - getGenerationType / getQuietPrompt（index.js L2860-2889 1:1）
 * - parseInteractiveTrigger（index.js processTriggers 纯逻辑部分 L375-434）
 * - processReply（index.js L2891-2928 1:1，minimal 模式与常规清洗）
 * 差分：scripts/diff/imagegen-prompt-official.mjs → engine ImageGenPromptDiffTest。
 */
object ImageGenPromptEngine {

    // ---------- generationMode（官方 index.js L113-128） ----------
    const val MODE_TOOL = -2
    const val MODE_MESSAGE = -1
    const val MODE_CHARACTER = 0
    const val MODE_USER = 1
    const val MODE_SCENARIO = 2
    const val MODE_RAW_LAST = 3
    const val MODE_NOW = 4
    const val MODE_FACE = 5
    const val MODE_FREE = 6
    const val MODE_BACKGROUND = 7
    const val MODE_CHARACTER_MULTIMODAL = 8
    const val MODE_USER_MULTIMODAL = 9
    const val MODE_FACE_MULTIMODAL = 10
    const val MODE_FREE_EXTENDED = 11

    /** 官方 generationMode 名称 → 数值（模板 map 键用数字字符串）。 */
    val MODE_NUMBERS: Map<String, Int> = mapOf(
        "TOOL" to -2, "MESSAGE" to -1, "CHARACTER" to 0, "USER" to 1, "SCENARIO" to 2,
        "RAW_LAST" to 3, "NOW" to 4, "FACE" to 5, "FREE" to 6, "BACKGROUND" to 7,
        "CHARACTER_MULTIMODAL" to 8, "USER_MULTIMODAL" to 9, "FACE_MULTIMODAL" to 10, "FREE_EXTENDED" to 11,
    )

    /** 官方 modeLabels（index.js L136-150，键为数字字符串）。 */
    val MODE_LABELS: Map<String, String> = mapOf(
        "-2" to "Function Tool Prompt Description",
        "-1" to "Chat Message Template",
        "0" to "Character (\"Yourself\")",
        "5" to "Portrait (\"Your Face\")",
        "1" to "User (\"Me\")",
        "2" to "Scenario (\"The Whole Story\")",
        "4" to "Last Message",
        "3" to "Raw Last Message",
        "7" to "Background",
        "8" to "Character (Multimodal Mode)",
        "10" to "Portrait (Multimodal Mode)",
        "9" to "User (Multimodal Mode)",
        "11" to "Free Mode (LLM-Extended)",
    )

    /** 官方 triggerWords（index.js L152-160，键为数字字符串）。 */
    val TRIGGER_WORDS: Map<String, List<String>> = mapOf(
        "0" to listOf("you"),
        "1" to listOf("me"),
        "2" to listOf("scene"),
        "3" to listOf("raw_last"),
        "4" to listOf("last"),
        "5" to listOf("face"),
        "7" to listOf("background"),
    )

    /** 官方 messageTrigger.specialCases（index.js L164-171）。 */
    val SPECIAL_CASES: Map<String, List<String>> = mapOf(
        "0" to listOf("you", "yourself"),
        "1" to listOf("me", "myself"),
        "2" to listOf("story", "scenario", "whole story"),
        "4" to listOf("last message"),
        "5" to listOf("face", "portrait", "selfie"),
        "7" to listOf("background", "scene background", "scene", "scenery", "surroundings", "environment"),
    )

    /** 官方 messageTrigger.activationRegex（index.js L163）。 */
    val ACTIVATION_REGEX = Regex(
        "\\b(send|mail|imagine|generate|make|create|draw|paint|render|show)\\b.{0,10}\\b(pic|picture|image|drawing|painting|photo|photograph)\\b(?:\\s+of)?(?:\\s+(?:a|an|the|this|that|those|your)?\\s+)?(.+)",
        RegexOption.IGNORE_CASE,
    )

    /** 官方 multimodalMap（index.js L130-134）：CHARACTER→CHARACTER_MULTIMODAL 等。 */
    val MULTIMODAL_MAP: Map<Int, Int> = mapOf(
        MODE_CHARACTER to MODE_CHARACTER_MULTIMODAL,
        MODE_USER to MODE_USER_MULTIMODAL,
        MODE_FACE to MODE_FACE_MULTIMODAL,
    )

    // ---------- defaultPromptTemplates（官方 index.js L174-218 逐字） ----------

    private val TEMPLATE_MESSAGE = "[{{char}} sends a picture that contains: {{prompt}}]."

    private val TEMPLATE_TOOL = "The text prompt used to generate the image." +
        " Must represent an exhaustive description of the desired image that will allow an artist or a photographer to perfectly recreate it."

    private val TEMPLATE_CHARACTER = "In the next response I want you to provide only a detailed comma-delimited list of keywords and phrases which describe {{char}}. The list must include all of the following items in this order: name, species and race, gender, age, clothing, occupation, physical features and appearances. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase 'full body portrait,'"

    private val TEMPLATE_FACE = "In the next response I want you to provide only a detailed comma-delimited list of keywords and phrases which describe {{char}}. The list must include all of the following items in this order: name, species and race, gender, age, facial features and expressions, occupation, hair and hair accessories (if any), what they are wearing on their upper body (if anything). Do not describe anything below their neck. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase 'close up facial portrait,'"

    private val TEMPLATE_USER = "Ignore previous instructions and provide a detailed description of {{user}}'s physical appearance from the perspective of {{char}} in the form of a comma-delimited list of keywords and phrases. The list must include all of the following items in this order: name, species and race, gender, age, clothing, occupation, physical features and appearances. Do not include descriptions of non-visual qualities such as personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase 'full body portrait,'. Ignore the rest of the story when crafting this description. Do not reply as {{char}} when writing this description, and do not attempt to continue the story."

    private val TEMPLATE_SCENARIO = "Ignore previous instructions and provide a detailed description for all of the following: a brief recap of recent events in the story, {{char}}'s appearance, and {{char}}'s surroundings. Do not reply as {{char}} while writing this description."

    private val TEMPLATE_NOW = "Ignore previous instructions. Your next response must be formatted as a single comma-delimited list of concise keywords.  The list will describe of the visual details included in the last chat message.\n\n" +
        "    Only mention characters by using pronouns ('he','his','she','her','it','its') or neutral nouns ('male', 'the man', 'female', 'the woman').\n\n" +
        "    Ignore non-visible things such as feelings, personality traits, thoughts, and spoken dialog.\n\n" +
        "    Add keywords in this precise order:\n" +
        "    a keyword to describe the location of the scene,\n" +
        "    a keyword to mention how many characters of each gender or type are present in the scene (minimum of two characters:\n" +
        "    {{user}} and {{char}}, example: '2 men ' or '1 man 1 woman ', '1 man 3 robots'),\n\n" +
        "    keywords to describe the relative physical positioning of the characters to each other (if a commonly known term for the positioning is known use it instead of describing the positioning in detail) + 'POV',\n\n" +
        "    a single keyword or phrase to describe the primary act taking place in the last chat message,\n\n" +
        "    keywords to describe {{char}}'s physical appearance and facial expression,\n" +
        "    keywords to describe {{char}}'s actions,\n" +
        "    keywords to describe {{user}}'s physical appearance and actions.\n\n" +
        "    If character actions involve direct physical interaction with another character, mention specifically which body parts interacting and how.\n\n" +
        "    A correctly formatted example response would be:\n" +
        "    '(location),(character list by gender),(primary action), (relative character position) POV, (character 1's description and actions), (character 2's description and actions)'"

    private val TEMPLATE_RAW_LAST = "Ignore previous instructions and provide ONLY the last chat message string back to me verbatim. Do not write anything after the string. Do not reply as {{char}} when writing this description, and do not attempt to continue the story."

    private val TEMPLATE_BACKGROUND = "Ignore previous instructions and provide a detailed description of {{char}}'s surroundings in the form of a comma-delimited list of keywords and phrases. The list must include all of the following items in this order: location, time of day, weather, lighting, and any other relevant details. Do not include descriptions of characters and non-visual qualities such as names, personality, movements, scents, mental traits, or anything which could not be seen in a still photograph. Do not write in full sentences. Prefix your description with the phrase 'background,'. Ignore the rest of the story when crafting this description. Do not reply as {{char}} when writing this description, and do not attempt to continue the story."

    private val TEMPLATE_FACE_MULTIMODAL = "Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with \"close-up portrait\"."

    private val TEMPLATE_CHARACTER_MULTIMODAL = "Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with \"full body portrait\"."

    private val TEMPLATE_USER_MULTIMODAL = "Provide an exhaustive comma-separated list of tags describing the appearance of the character on this image in great detail. Start with \"full body portrait\"."

    private val TEMPLATE_FREE_EXTENDED = "Ignore previous instructions and provide an exhaustive comma-separated list of tags describing the appearance of \"{0}\" in great detail. Start with {{charPrefix}} (sic) if the subject is associated with {{char}}."

    /** 官方 promptTemplates（键为数字字符串）。 */
    val DEFAULT_PROMPT_TEMPLATES: Map<String, String> = mapOf(
        "-1" to TEMPLATE_MESSAGE,
        "-2" to TEMPLATE_TOOL,
        "0" to TEMPLATE_CHARACTER,
        "5" to TEMPLATE_FACE,
        "1" to TEMPLATE_USER,
        "2" to TEMPLATE_SCENARIO,
        "4" to TEMPLATE_NOW,
        "3" to TEMPLATE_RAW_LAST,
        "7" to TEMPLATE_BACKGROUND,
        "10" to TEMPLATE_FACE_MULTIMODAL,
        "8" to TEMPLATE_CHARACTER_MULTIMODAL,
        "9" to TEMPLATE_USER_MULTIMODAL,
        "11" to TEMPLATE_FREE_EXTENDED,
    )

    // ---------- stringFormat（utils.js L757-764 1:1） ----------
    /** stringFormat('Hello, {0}!', 'world') → 'Hello, world!'；未传参的 {n} 原样保留。 */
    fun stringFormat(format: String, vararg args: String): String =
        Regex("\\{(\\d+)}").replace(format) { m ->
            val idx = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            if (idx < args.size) args[idx] else m.value
        }

    // ---------- getGenerationType / getQuietPrompt（index.js L2860-2889 1:1） ----------
    /**
     * 官方 getGenerationType：默认 FREE(6)；triggerWords 精确匹配（trim 小写相等）→ 对应 mode；
     * multimodal_captioning 且 multimodalMap 命中 → 切 multimodal；FREE 且 free_extend → FREE_EXTENDED(11)。
     */
    fun getGenerationType(prompt: String, multimodalCaptioning: Boolean, freeExtend: Boolean): Int {
        var mode = MODE_FREE
        outer@ for ((key, values) in TRIGGER_WORDS) {
            for (value in values) {
                if (value.lowercase() == prompt.lowercase().trim()) {
                    mode = key.toIntOrNull() ?: MODE_FREE
                    break@outer
                }
            }
        }
        if (multimodalCaptioning && MULTIMODAL_MAP[mode] != null) {
            mode = MULTIMODAL_MAP[mode] ?: mode
        }
        if (mode == MODE_FREE && freeExtend) {
            mode = MODE_FREE_EXTENDED
        }
        return mode
    }

    /** 官方 getQuietPrompt：FREE 模式原样返回 trigger，否则 stringFormat(模板, trigger)。 */
    fun getQuietPrompt(mode: Int, trigger: String, prompts: Map<String, String>): String {
        if (mode == MODE_FREE) return trigger
        val template = prompts[mode.toString()] ?: return trigger
        return stringFormat(template, trigger)
    }

    // ---------- parseInteractiveTrigger（index.js processTriggers 纯逻辑 L375-434） ----------
    data class InteractiveTrigger(val mode: Int, val subject: String)

    /**
     * 官方 processTriggers 纯逻辑：最后一条用户消息匹配 activationRegex →
     * 提取 subject（分组3，trim，空则无触发）；subject 命中 specialCases 时替换为该 mode 的 triggerWord[0]；
     * 最终模式由 getGenerationType(subject) 解析（与官方 generatePicture→getGenerationType 一致）。
     * @return 未触发返回 null。
     */
    fun parseInteractiveTrigger(message: String?): InteractiveTrigger? {
        if (message.isNullOrBlank()) return null
        val messageLower = message.lowercase()
        val match = ACTIVATION_REGEX.find(messageLower) ?: return null
        var subject = (match.groupValues.getOrNull(3) ?: "").trim()
        if (subject.isEmpty()) return null
        outer@ for ((modeKey, triggers) in SPECIAL_CASES) {
            for (trigger in triggers) {
                if (subject == trigger) {
                    subject = TRIGGER_WORDS[modeKey]?.firstOrNull() ?: subject
                    break@outer
                }
            }
        }
        return InteractiveTrigger(getGenerationType(subject, false, false), subject)
    }

    // ---------- processReply（index.js L2891-2928 1:1） ----------
    /**
     * Sanitizes generated prompt for image generation.
     * 官方 index.js L2896-2928 逐字移植。
     * @param minimalPromptProcessing 对应官方 extension_settings.sd.minimal_prompt_processing。
     */
    fun processReply(str: String?, minimalPromptProcessing: Boolean): String {
        if (str.isNullOrBlank()) return ""

        if (minimalPromptProcessing) {
            // Minimal prompt processing: JSON and similar should be preserved
            var s = Normalizer.normalize(str, Normalizer.Form.NFD)
            s = s.replace(Regex("\\s+"), " ")
            s = s.trim()
            return s
        }

        var s = str.replace("\"", "")
        s = s.replace("\u201c", "")
        s = s.replace("\n", ", ")
        s = Normalizer.normalize(s, Normalizer.Form.NFD)

        // Strip out non-alphanumeric characters barring model syntax exceptions
        s = s.replace(Regex("[^a-zA-Z0-9.,:_(){}<>\\[\\]/'\\-|#]+"), " ")

        s = s.replace(Regex("\\s+"), " ")
        s = s.trim()

        s = s.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        return s
    }
}

package com.emberinn.engine.worldinfo

/**
 * 对齐官方 world-info.js WorldInfoBuffer：
 * 深度缓冲 + 递归缓冲 + 注入缓冲 + 全局扫描文本；
 * matchKeys 含正则关键词、大小写、整词匹配。
 */
class WorldInfoBuffer(
    messages: List<String>,
    private val global: GlobalScanData,
    private val settings: WorldInfoSettings,
) {
    private val depthBuffer: List<String> = messages
        .take(WorldInfoConstants.MAX_SCAN_DEPTH)
        .map { it.trim() }

    private val recurseBuffer = mutableListOf<String>()
    private val injectBuffer = mutableListOf<String>()
    private var skew = 0

    fun addRecurse(message: String) { recurseBuffer.add(message) }
    fun addInject(message: String) { injectBuffer.add(message) }
    fun hasRecurse(): Boolean = recurseBuffer.isNotEmpty()
    fun advanceScan() { skew++ }
    fun getDepth(): Int = settings.depth + skew

    fun get(entry: WorldInfoEntry, scanState: Int): String {
        var depth = entry.scanDepth ?: getDepth()
        if (depth <= 0) return ""
        if (depth < 0) return ""
        if (depth > WorldInfoConstants.MAX_SCAN_DEPTH) depth = WorldInfoConstants.MAX_SCAN_DEPTH

        val matcher = "\u0001"
        val joiner = "\n" + matcher
        val startDepth = 0
        val result = StringBuilder(matcher)
        result.append(depthBuffer.subList(startDepth, depth.coerceAtMost(depthBuffer.size)).joinToString(joiner))

        if (entry.matchPersonaDescription && global.personaDescription.isNotEmpty()) result.append(joiner).append(global.personaDescription)
        if (entry.matchCharacterDescription && global.characterDescription.isNotEmpty()) result.append(joiner).append(global.characterDescription)
        if (entry.matchCharacterPersonality && global.characterPersonality.isNotEmpty()) result.append(joiner).append(global.characterPersonality)
        if (entry.matchCharacterDepthPrompt && global.characterDepthPrompt.isNotEmpty()) result.append(joiner).append(global.characterDepthPrompt)
        if (entry.matchScenario && global.scenario.isNotEmpty()) result.append(joiner).append(global.scenario)
        if (entry.matchCreatorNotes && global.creatorNotes.isNotEmpty()) result.append(joiner).append(global.creatorNotes)
        if (injectBuffer.isNotEmpty()) result.append(joiner).append(injectBuffer.joinToString(joiner))
        if (recurseBuffer.isNotEmpty() && scanState != WorldInfoConstants.STATE_MIN_ACTIVATIONS) {
            result.append(joiner).append(recurseBuffer.joinToString(joiner))
        }
        return result.toString()
    }

    fun matchKeys(haystack: String, needle: String, entry: WorldInfoEntry): Boolean {
        parseRegexFromString(needle)?.let { return it.containsMatchIn(haystack) }

        val caseSensitive = entry.caseSensitive ?: settings.caseSensitive
        val transformedHaystack = if (caseSensitive) haystack else haystack.lowercase()
        val transformedNeedle = if (caseSensitive) needle else needle.lowercase()
        val matchWholeWords = entry.matchWholeWords ?: settings.matchWholeWords

        if (matchWholeWords) {
            val keyWords = transformedNeedle.split(Regex("\\s+"))
            if (keyWords.size > 1) {
                return transformedHaystack.contains(transformedNeedle)
            }
            val regex = Regex("(?:^|\\W)(${escapeRegex(transformedNeedle)})(?:$|\\W)")
            if (regex.containsMatchIn(transformedHaystack)) return true
        } else {
            return transformedHaystack.contains(transformedNeedle)
        }
        return false
    }

    /** 对齐官方 getScore：主/次关键词计数（用于分组评分）。 */
    fun getScore(entry: WorldInfoEntry, scanState: Int): Int {
        val bufferState = get(entry, scanState)
        var primaryScore = 0
        var secondaryScore = 0
        for (key in entry.keys) {
            if (matchKeys(bufferState, key, entry)) primaryScore++
        }
        for (key in entry.keySecondary) {
            if (matchKeys(bufferState, key, entry)) secondaryScore++
        }
        if (entry.keys.isEmpty()) return 0
        if (entry.keySecondary.isNotEmpty()) {
            when (entry.selectiveLogic ?: WorldInfoConstants.AND_ANY) {
                WorldInfoConstants.AND_ANY -> return primaryScore + secondaryScore
                WorldInfoConstants.AND_ALL ->
                    return if (secondaryScore == entry.keySecondary.size) primaryScore + secondaryScore else primaryScore
            }
        }
        return primaryScore
    }

    private fun parseRegexFromString(text: String): Regex? {
        val m = Regex("^/(.*)/([a-z]*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text) ?: return null
        val pattern = m.groupValues[1]
        val flags = m.groupValues[2]
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        return runCatching { Regex(pattern, options) }.getOrNull()
    }

    private fun escapeRegex(s: String): String = buildString {
        for (c in s) {
            if (c in "\\^$.|?*+()[]{}") append('\\')
            append(c)
        }
    }
}

package com.emberinn.engine.worldinfo

import kotlin.math.roundToInt

/**
 * 世界书扫描器，对齐官方 checkWorldInfo：
 * 常量/关键词（主+次+逻辑）、概率、预算、递归、min activations、delay 层级、
 * 分组互斥/评分、时间效果（sticky/cooldown/delay）、角色/标签过滤、外部强制激活（RAG）、
 * 装饰器、正则内容处理、位置组装。
 * 条目内容在激活时按官方 substituteParams 替换一次，替换后文本进入递归缓冲/预算/最终输出
 * （官方 checkWorldInfo 的 entry.content = substituteParams(entry.content) 语义）。
 */
class WorldInfoScanner(
    private val tokenCounter: TokenCounter = TokenCounter { it.length },
    private val random: RandomProvider = RandomProvider { kotlin.random.Random.nextDouble() },
    private val substitute: MacroSubstituter = MacroSubstituter { it },
    private val messageTransformer: (String) -> String = { it },
    // 官方 world-info.js BUILDING PROMPT：getRegexedString(entry.content, WORLD_INFO,
    // { depth: regexDepth, isMarkdown: false, isPrompt: true })；regexDepth 仅 atDepth 条目非空。
    // 三个参数：内容、正则深度（atDepth 时 = entry.depth ?: DEFAULT_DEPTH，否则 null）、条目 position。
    private val contentTransformer: (String, Int?, Int) -> String = { content, _, _ -> content },
) {

    fun scan(
        chat: List<String>,
        maxContext: Int,
        entries: List<WorldInfoEntry>,
        settings: WorldInfoSettings = WorldInfoSettings(),
        global: GlobalScanData = GlobalScanData(),
        timedMetadata: TimedEffectsMetadata = TimedEffectsMetadata(),
        isDryRun: Boolean = false,
        // 外部强制激活（对齐官方 WorldInfoBuffer.externalActivations，RAG/向量检索喂到这里）
        externalActivations: Map<String, WorldInfoEntry> = emptyMap(),
        // 官方 checkWorldInfo：extensionPrompts 中 scan=true 的提示经 getExtensionPromptByName
        // （宏替换后）addInject 进扫描缓冲，匹配所有条目的扫描文本（不在聊天深度里）
        scanInjections: List<String> = emptyList(),
    ): WorldInfoResult {
        // 官方：聊天消息先过正则（getRegexedString），再进世界书扫描
        val buffer = WorldInfoBuffer(chat.map(messageTransformer), global, settings)
        scanInjections.forEach { buffer.addInject(it) }
        externalActivations.forEach { (_, entry) -> buffer.addExternalActivation(entry) }

        var scanState = WorldInfoConstants.STATE_INITIAL
        var tokenBudgetOverflowed = false
        var count = 0
        val allActivated = linkedMapOf<String, WorldInfoEntry>()
        val failedProbability = mutableSetOf<WorldInfoEntry>()
        var allActivatedText = ""

        var budget = (settings.budgetPercent * maxContext / 100.0).roundToInt().coerceAtLeast(1)
        if (settings.budgetCap > 0 && budget > settings.budgetCap) budget = settings.budgetCap

        // 对齐 getSortedEntries 的 sortFn：order 降序
        val sortedEntries = entries.sortedWith(compareByDescending { it.order })
        val timedEffects = WorldInfoTimedEffects(chat.size, sortedEntries, timedMetadata, isDryRun)
        timedEffects.checkTimedEffects()

        // 官方：availableRecursionDelayLevels.shift() ?? 0 —— 首个层级在扫描前移出列表
        val delayLevels = sortedEntries
            .filter { it.delayUntilRecursion > 0 }
            .map { it.delayUntilRecursion }
            .distinct()
            .sorted()
            .toMutableList()
        var currentDelayLevel = if (delayLevels.isNotEmpty()) delayLevels.removeAt(0) else 0

        while (scanState != WorldInfoConstants.STATE_NONE) {
            if (settings.maxRecursionSteps > 0 && settings.maxRecursionSteps <= count) break
            count++

            var nextScanState = WorldInfoConstants.STATE_NONE
            val activatedNow = linkedSetOf<WorldInfoEntry>()

            for (entry in sortedEntries) {
                if (failedProbability.contains(entry) || allActivated.containsKey("${entry.world}.${entry.uid}")) continue
                if (entry.disable) continue

                if (entry.triggers.isNotEmpty() && entry.triggers.none { it == global.trigger }) continue

                val filter = entry.characterFilter
                if (filter != null) {
                    // 对齐官方：names.includes(当前角色名)；exclude 取反
                    if (filter.names.isNotEmpty()) {
                        val nameIncluded = filter.names.any { it == global.characterName }
                        val filteredOut = if (filter.isExclude) nameIncluded else !nameIncluded
                        if (filteredOut) continue
                    }
                    // 对齐官方：tagMap 与排除表相交
                    if (filter.tags.isNotEmpty()) {
                        val includesTag = filter.tags.any { tag -> global.characterTags.contains(tag) }
                        val filteredOut = if (filter.isExclude) includesTag else !includesTag
                        if (filteredOut) continue
                    }
                }

                val isSticky = timedEffects.isEffectActive("sticky", entry)
                val isCooldown = timedEffects.isEffectActive("cooldown", entry)
                val isDelay = timedEffects.isEffectActive("delay", entry)

                if (isDelay) continue
                if (isCooldown && !isSticky) continue

                if (entry.delayUntilRecursion > 0) {
                    if (scanState != WorldInfoConstants.STATE_RECURSION && !isSticky) continue
                    if (scanState == WorldInfoConstants.STATE_RECURSION && entry.delayUntilRecursion > currentDelayLevel && !isSticky) continue
                }
                if (scanState == WorldInfoConstants.STATE_RECURSION && settings.recursive && entry.excludeRecursion && !isSticky) continue

                if ("@@activate" in entry.decorators) { activatedNow.add(entry); continue }
                if ("@@dont_activate" in entry.decorators) continue

                // 对齐官方：外部强制激活（RAG 等）在 constant 之前生效，跳过关键词/概率
                buffer.getExternallyActivated(entry)?.let {
                    activatedNow.add(it)
                    continue
                }

                if (entry.constant) { activatedNow.add(entry); continue }
                if (isSticky) { activatedNow.add(entry); continue }

                if (entry.keys.isEmpty()) continue

                val textToScan = buffer.get(entry, scanState)
                val primaryKeyMatch = entry.keys.firstOrNull { key ->
                    val substituted = substitute.substitute(key)
                    substituted.isNotEmpty() && buffer.matchKeys(textToScan, substituted.trim(), entry)
                }
                if (primaryKeyMatch == null) continue

                val hasSecondary = entry.selective && entry.keySecondary.isNotEmpty()
                if (!hasSecondary) { activatedNow.add(entry); continue }

                val selectiveLogic = entry.selectiveLogic ?: WorldInfoConstants.AND_ANY
                var hasAny = false
                var hasAll = true
                var matched = false
                for (keySecondary in entry.keySecondary) {
                    val sub = substitute.substitute(keySecondary)
                    val secondaryMatch = sub.isNotEmpty() && buffer.matchKeys(textToScan, sub.trim(), entry)
                    if (secondaryMatch) hasAny = true else hasAll = false
                    if (selectiveLogic == WorldInfoConstants.AND_ANY && secondaryMatch) { matched = true; break }
                    if (selectiveLogic == WorldInfoConstants.NOT_ALL && !secondaryMatch) { matched = true; break }
                }
                if (!matched) {
                    if (selectiveLogic == WorldInfoConstants.NOT_ANY && !hasAny) matched = true
                    if (selectiveLogic == WorldInfoConstants.AND_ALL && hasAll) matched = true
                }
                if (matched) activatedNow.add(entry)
            }

            val newEntries = activatedNow
                .sortedWith(
                    compareBy<WorldInfoEntry> { if (timedEffects.isEffectActive("sticky", it)) 0 else 1 }
                        .thenBy { sortedEntries.indexOf(it) },
                )
                .toMutableList()

            filterByInclusionGroups(newEntries, allActivated, buffer, scanState, timedEffects, settings, random)

            var newContent = ""
            val textToScanTokens = tokenCounter.count(allActivatedText)

            var ignoresBudget = newEntries.count { it.ignoreBudget }
            for (entry in newEntries) {
                if (entry.ignoreBudget) ignoresBudget--
                if (tokenBudgetOverflowed && !entry.ignoreBudget) {
                    if (ignoresBudget > 0) continue else break
                }

                val success = if (!entry.useProbability || entry.probability == 100) {
                    true
                } else if (timedEffects.isEffectActive("sticky", entry)) {
                    // 官方 verifyProbability：sticky 条目激活中无需重掷
                    true
                } else {
                    val roll = random.nextDouble() * 100
                    if (roll <= entry.probability) true else { failedProbability.add(entry); false }
                }
                if (!success) continue

            // 官方：entry.content = substituteParams(entry.content) 后用于预算/递归/最终输出
            val content = substitute.substitute(entry.content)
            newContent += "$content\n"

            if (!entry.ignoreBudget && (textToScanTokens + tokenCounter.count(newContent)) >= budget) {
                tokenBudgetOverflowed = true
                continue
            }
            allActivated["${entry.world}.${entry.uid}"] = entry.copy(content = content)
            }

            val successfulNew = newEntries.filter { !failedProbability.contains(it) }
            val recursionCandidates = successfulNew.filter { !it.preventRecursion }
            fun substitutedOf(entry: WorldInfoEntry): String =
                allActivated["${entry.world}.${entry.uid}"]?.content ?: entry.content

            if (settings.recursive && !tokenBudgetOverflowed && recursionCandidates.isNotEmpty()) {
                nextScanState = WorldInfoConstants.STATE_RECURSION
            }
            if (settings.recursive && !tokenBudgetOverflowed &&
                scanState == WorldInfoConstants.STATE_MIN_ACTIVATIONS && buffer.hasRecurse()
            ) {
                nextScanState = WorldInfoConstants.STATE_RECURSION
            }

            val minNotSatisfied = settings.minActivations > 0 && allActivated.size < settings.minActivations
            if (nextScanState == WorldInfoConstants.STATE_NONE && !tokenBudgetOverflowed && minNotSatisfied) {
                val overMax = (settings.minActivationsDepthMax > 0 && buffer.getDepth() > settings.minActivationsDepthMax) ||
                    (buffer.getDepth() > chat.size)
                if (!overMax) {
                    nextScanState = WorldInfoConstants.STATE_MIN_ACTIVATIONS
                    buffer.advanceScan()
                }
            }

            if (nextScanState == WorldInfoConstants.STATE_NONE && delayLevels.isNotEmpty()) {
                nextScanState = WorldInfoConstants.STATE_RECURSION
                currentDelayLevel = delayLevels.removeAt(0)
            }

            scanState = nextScanState
            if (scanState != WorldInfoConstants.STATE_NONE) {
                val text = recursionCandidates.joinToString("\n") { substitutedOf(it) }
                if (text.isNotEmpty()) {
                    buffer.addRecurse(text)
                    allActivatedText = text + "\n" + allActivatedText
                }
            }
        }

        timedEffects.setTimedEffects(allActivated.values.toList())
        timedEffects.cleanUp()
        buffer.resetExternalEffects()
        return assemble(allActivated.values.toList()).copy(timedMetadata = timedMetadata)
    }

    /** 对齐官方 filterByInclusionGroups + filterGroupsByTimedEffects + filterGroupsByScoring。 */
    private fun filterByInclusionGroups(
        newEntries: MutableList<WorldInfoEntry>,
        allActivated: Map<String, WorldInfoEntry>,
        buffer: WorldInfoBuffer,
        scanState: Int,
        timedEffects: WorldInfoTimedEffects,
        settings: WorldInfoSettings,
        random: RandomProvider,
    ) {
        val grouped = linkedMapOf<String, MutableList<WorldInfoEntry>>()
        newEntries.filter { !it.group.isNullOrBlank() }.forEach { item ->
            item.group!!.split(Regex(""",\s*""")).filter { it.isNotEmpty() }.forEach { group ->
                grouped.getOrPut(group) { mutableListOf() }.add(item)
            }
        }
        if (grouped.isEmpty()) return

        fun removeEntry(entry: WorldInfoEntry) { newEntries.removeAll { it === entry } }
        fun removeAllBut(group: List<WorldInfoEntry>, chosen: WorldInfoEntry?) {
            for (entry in group) {
                if (entry === chosen) continue
                removeEntry(entry)
            }
        }

        val hasStickyMap = linkedMapOf<String, Boolean>()
        for ((key, group) in grouped) {
            hasStickyMap[key] = false
            val stickyEntries = group.filter { timedEffects.isEffectActive("sticky", it) }
            if (stickyEntries.isNotEmpty()) {
                group.filterNot { stickyEntries.contains(it) }.forEach { removeEntry(it) }
                hasStickyMap[key] = true
            }
            group.filter { timedEffects.isEffectActive("cooldown", it) }.forEach { removeEntry(it) }
            group.filter { timedEffects.isEffectActive("delay", it) }.forEach { removeEntry(it) }
        }

        for ((key, group) in grouped) {
            if (!settings.useGroupScoring && group.none { it.useGroupScoring == true }) continue
            if (hasStickyMap[key] == true) continue
            val scores = group.map { buffer.getScore(it, scanState) }.toMutableList()
            val maxScore = scores.maxOrNull() ?: 0
            var i = 0
            while (i < group.size) {
                val isScored = group[i].useGroupScoring ?: settings.useGroupScoring
                if (isScored && scores[i] < maxScore) {
                    removeEntry(group[i])
                    group.removeAt(i)
                    scores.removeAt(i)
                    i--
                }
                i++
            }
        }

        for ((key, group) in grouped) {
            if (hasStickyMap[key] == true) continue
            if (allActivated.values.any { it.group == key }) {
                removeAllBut(group, null)
                continue
            }
            if (group.size <= 1) continue

            val prios = group.filter { it.groupOverride == true }.sortedWith(compareByDescending { it.order })
            if (prios.isNotEmpty()) {
                removeAllBut(group, prios.first())
                continue
            }

            val totalWeight = group.sumOf { it.groupWeight ?: 100 }
            val rollValue = random.nextDouble() * totalWeight
            var currentWeight = 0
            var winner: WorldInfoEntry? = null
            for (entry in group) {
                currentWeight += entry.groupWeight ?: 100
                if (rollValue <= currentWeight) { winner = entry; break }
            }
            if (winner == null) continue
            removeAllBut(group, winner)
        }
    }

    private fun assemble(activated: List<WorldInfoEntry>): WorldInfoResult {
        val before = mutableListOf<String>()
        val after = mutableListOf<String>()
        val em = mutableListOf<EmEntry>()
        val anTop = mutableListOf<String>()
        val anBottom = mutableListOf<String>()
        val depthGroups = mutableListOf<DepthEntry>()
        val outlet = linkedMapOf<String, MutableList<String>>()

        activated.sortedWith(compareByDescending { it.order }).forEach { entry ->
            // 对齐官方 BUILDING PROMPT：getRegexedString(entry.content, WORLD_INFO, ...)
            val regexDepth = WorldInfoConstants.regexDepthOf(entry.position, entry.depth)
            val content = contentTransformer(entry.content, regexDepth, entry.position)
            if (content.isEmpty()) return@forEach
            when (entry.position) {
                WorldInfoConstants.POSITION_BEFORE -> before.add(0, content)
                WorldInfoConstants.POSITION_AFTER -> after.add(0, content)
                WorldInfoConstants.POSITION_EM_TOP -> em.add(0, EmEntry(0, content))
                WorldInfoConstants.POSITION_EM_BOTTOM -> em.add(0, EmEntry(1, content))
                WorldInfoConstants.POSITION_AN_TOP -> anTop.add(0, content)
                WorldInfoConstants.POSITION_AN_BOTTOM -> anBottom.add(0, content)
                WorldInfoConstants.POSITION_AT_DEPTH -> {
                    val d = entry.depth ?: WorldInfoConstants.DEFAULT_DEPTH
                    val role = entry.role ?: "system"
                    val existing = depthGroups.firstOrNull { it.depth == d && it.role == role }
                    if (existing != null) {
                        val idx = depthGroups.indexOf(existing)
                        depthGroups[idx] = existing.copy(entries = listOf(content) + existing.entries)
                    } else {
                        depthGroups.add(DepthEntry(d, role, listOf(content)))
                    }
                }
                WorldInfoConstants.POSITION_OUTLET -> {
                    val name = entry.outletName ?: return@forEach
                    outlet.getOrPut(name) { mutableListOf() }.add(content)
                }
            }
        }

        return WorldInfoResult(
            worldInfoBefore = before.joinToString("\n"),
            worldInfoAfter = after.joinToString("\n"),
            emEntries = em,
            anBefore = anTop,
            anAfter = anBottom,
            depthEntries = depthGroups,
            outletEntries = outlet,
            activated = activated,
        )
    }
}

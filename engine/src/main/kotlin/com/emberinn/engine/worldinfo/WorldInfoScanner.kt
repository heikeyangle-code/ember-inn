package com.emberinn.engine.worldinfo

import kotlin.math.roundToInt

/**
 * 世界书扫描器，对齐官方 checkWorldInfo 核心（Stage 1）：
 * 常量/关键词（主+次+逻辑）、概率、预算、递归、min activations、delay 层、位置组装。
 * Stage 2 待接：sticky/cooldown/delay 时间效果、分组互斥、向量化、角色过滤、regex 内容处理、多世界合并策略。
 */
class WorldInfoScanner(
    private val tokenCounter: TokenCounter = TokenCounter { it.length },
    private val random: RandomProvider = RandomProvider { kotlin.random.Random.nextDouble() },
    private val substitute: MacroSubstituter = MacroSubstituter { it },
) {

    fun scan(
        chat: List<String>,
        maxContext: Int,
        entries: List<WorldInfoEntry>,
        settings: WorldInfoSettings = WorldInfoSettings(),
        global: GlobalScanData = GlobalScanData(),
    ): WorldInfoResult {
        val buffer = WorldInfoBuffer(chat, global, settings)

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

        val delayLevels = sortedEntries
            .filter { it.delayUntilRecursion == true }
            .map { 1 }
            .toMutableList()
        var currentDelayLevel = delayLevels.firstOrNull() ?: 0

        while (scanState != WorldInfoConstants.STATE_NONE) {
            if (settings.maxRecursionSteps > 0 && settings.maxRecursionSteps <= count) break
            count++

            var nextScanState = WorldInfoConstants.STATE_NONE
            val activatedNow = linkedSetOf<WorldInfoEntry>()

            for (entry in sortedEntries) {
                if (failedProbability.contains(entry) || allActivated.containsKey("${entry.world}.${entry.uid}")) continue
                if (!entry.enabled) continue

                if (entry.triggers.isNotEmpty() && entry.triggers.none { it == global.trigger }) continue

                if (entry.delayUntilRecursion == true) {
                    if (scanState != WorldInfoConstants.STATE_RECURSION) continue
                    if (scanState == WorldInfoConstants.STATE_RECURSION && currentDelayLevel < 1) continue
                }
                if (scanState == WorldInfoConstants.STATE_RECURSION && settings.recursive && entry.excludeRecursion) continue

                if ("@@activate" in entry.decorators) { activatedNow.add(entry); continue }
                if ("@@dont_activate" in entry.decorators) continue

                if (entry.constant) { activatedNow.add(entry); continue }

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

            val newEntries = activatedNow.sortedWith(compareBy { sortedEntries.indexOf(it) })

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
                } else {
                    val roll = random.nextDouble() * 100
                    if (roll <= entry.probability) true else { failedProbability.add(entry); false }
                }
                if (!success) continue

                val content = substitute.substitute(entry.content)
                newContent += "$content\n"

                if (!entry.ignoreBudget && (textToScanTokens + tokenCounter.count(newContent)) >= budget) {
                    tokenBudgetOverflowed = true
                    continue
                }
                allActivated["${entry.world}.${entry.uid}"] = entry
            }

            val successfulNew = newEntries.filter { !failedProbability.contains(it) }
            val recursionCandidates = successfulNew.filter { !it.preventRecursion }

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
                val text = recursionCandidates.joinToString("\n") { it.content }
                if (text.isNotEmpty()) {
                    buffer.addRecurse(text)
                    allActivatedText = text + "\n" + allActivatedText
                }
            }
        }

        return assemble(allActivated.values.toList())
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
            val content = entry.content
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

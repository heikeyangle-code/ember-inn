package com.emberinn.engine.prompt

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 官方 /preset 的预设名选择（preset-manager.js presetCommandCallback，release 8172dcd）：
 * 1) exact：allPresets.find(p => p.toLowerCase().trim() === name.toLowerCase().trim())
 * 2) fuzzy：new Fuse(allPresets)（fuse.js ^7.1.0 默认 options）→ 取 [0].item
 *
 * Fuse 部分为逐字移植（Bitap + fieldNorm + 排序），与 scripts/diff/preset-fuzzy-official.mjs
 * （真实 fuse.js@7.1.0）差分对拍，见 FusePresetDiffTest。
 */
object FusePresetSearch {

    // Fuse.js v7.1 默认配置（presetCommandCallback 未传 options）
    private const val MAX_BITS = 32
    private const val LOCATION = 0
    private const val DISTANCE = 100
    private const val THRESHOLD = 0.6
    private const val MIN_MATCH_CHAR_LENGTH = 1
    private const val IGNORE_LOCATION = false
    private const val FIND_ALL_MATCHES = false

    /** 官方 presetCommandCallback 的选择名部分；无参/空列表返回 null（调用方返回当前预设）。 */
    fun selectPresetName(names: List<String>, name: String): String? {
        if (name.isEmpty()) return null
        if (names.isEmpty()) return null
        val trimmed = name.trim()
        val exact = names.firstOrNull { it.trim().equals(trimmed, ignoreCase = true) }
        if (exact != null) return exact
        return fuzzyBest(names, name)
    }

    /** Fuse.js 字符串列表模糊搜索：返回最佳项（score 升序，同分按列表顺序）。 */
    fun fuzzyBest(names: List<String>, query: String): String? {
        if (query.isEmpty()) return null
        val pattern = query.lowercase()
        val chunks = chunkPattern(pattern)
        if (chunks.isEmpty()) return null

        val scored = ArrayList<Pair<Double, Int>>()
        names.forEachIndexed { idx, item ->
            if (item.isBlank()) return@forEachIndexed // 官方 FuseIndex 跳过空白项
            val text = item.lowercase()
            val bitapScore = scoreText(text, pattern, chunks) ?: return@forEachIndexed
            val total = bitapScore.pow(fieldNorm(item))
            scored.add(total to idx)
        }
        if (scored.isEmpty()) return null
        scored.sortWith(compareBy({ it.first }, { it.second }))
        return names[scored.first().second]
    }

    // ---- Fuse.js BitapSearch ----

    private data class Chunk(val pattern: String, val alphabet: Map<Char, Int>, val startIndex: Int)

    private fun chunkPattern(pattern: String): List<Chunk> {
        if (pattern.isEmpty()) return emptyList()
        val chunks = mutableListOf<Chunk>()
        if (pattern.length > MAX_BITS) {
            val len = pattern.length
            val remainder = len % MAX_BITS
            val end = len - remainder
            var i = 0
            while (i < end) {
                val p = pattern.substring(i, i + MAX_BITS)
                chunks += Chunk(p, alphabet(p), i)
                i += MAX_BITS
            }
            if (remainder > 0) {
                val startIndex = len - MAX_BITS
                val p = pattern.substring(startIndex)
                chunks += Chunk(p, alphabet(p), startIndex)
            }
        } else {
            chunks += Chunk(pattern, alphabet(pattern), 0)
        }
        return chunks
    }

    private fun alphabet(pattern: String): Map<Char, Int> {
        val mask = HashMap<Char, Int>()
        for (i in pattern.indices) {
            val char = pattern[i]
            mask[char] = (mask[char] ?: 0) or (1 shl (pattern.length - i - 1))
        }
        return mask
    }

    /** 官方 FuseIndex norm.get：token 数 = 非空格连续段数；norm = 1/sqrt(tokens)，保留 3 位小数。 */
    private fun fieldNorm(text: String): Double {
        var tokens = 0
        var inToken = false
        for (c in text) {
            if (c == ' ') {
                inToken = false
            } else if (!inToken) {
                inToken = true
                tokens++
            }
        }
        val norm = 1.0 / sqrt(tokens.toDouble())
        return round(norm * 1000.0) / 1000.0
    }

    private data class BitapResult(val isMatch: Boolean, val score: Double)

    private fun scoreText(text: String, pattern: String, chunks: List<Chunk>): Double? {
        // BitapSearch.searchIn 的整串精确分支（score=0）
        if (pattern == text) return 0.0
        var hasMatches = false
        var totalScore = 0.0
        for (chunk in chunks) {
            val result = bitapSearch(text, chunk.pattern, chunk.alphabet, LOCATION + chunk.startIndex)
            if (result.isMatch) hasMatches = true
            totalScore += result.score
        }
        return if (hasMatches) totalScore / chunks.size else null
    }

    private fun bitapSearch(
        text: String,
        pattern: String,
        alphabet: Map<Char, Int>,
        location: Int,
    ): BitapResult {
        val patternLen = pattern.length
        val textLen = text.length
        val expectedLocation = max(0, min(location, textLen))
        var currentThreshold = THRESHOLD
        var bestLocation = expectedLocation

        // 精确子串加速
        var index = text.indexOf(pattern, bestLocation)
        while (index >= 0) {
            val score = computeScore(patternLen, errors = 0, currentLocation = index, expectedLocation)
            if (score < currentThreshold) currentThreshold = score
            bestLocation = index + patternLen
            index = text.indexOf(pattern, bestLocation)
        }

        bestLocation = -1
        var lastBitArr = IntArray(0)
        var finalScore = 1.0
        var binMax = patternLen + textLen
        val mask = 1 shl (patternLen - 1)

        for (i in 0 until patternLen) {
            var binMin = 0
            var binMid = binMax
            while (binMin < binMid) {
                val score = computeScore(patternLen, errors = i, currentLocation = expectedLocation + binMid, expectedLocation)
                if (score <= currentThreshold) binMin = binMid else binMax = binMid
                binMid = (binMax - binMin) / 2 + binMin
            }
            binMax = binMid

            var start = max(1, expectedLocation - binMid + 1)
            val finish = if (FIND_ALL_MATCHES) textLen else min(expectedLocation + binMid, textLen) + patternLen
            val bitArr = IntArray(finish + 2)
            bitArr[finish + 1] = (1 shl i) - 1

            var j = finish
            while (j >= start) {
                val currentLocation = j - 1
                val charMatch = alphabet[charAt(text, currentLocation)] ?: 0

                bitArr[j] = ((bitArr[j + 1] shl 1) or 1) and charMatch
                if (i > 0) {
                    bitArr[j] = bitArr[j] or
                        ((((lastBitArr.getOrNull(j + 1) ?: 0) or (lastBitArr.getOrNull(j) ?: 0)) shl 1) or 1 or (lastBitArr.getOrNull(j + 1) ?: 0))
                }

                if ((bitArr[j] and mask) != 0) {
                    finalScore = computeScore(patternLen, errors = i, currentLocation = currentLocation, expectedLocation)
                    if (finalScore <= currentThreshold) {
                        currentThreshold = finalScore
                        bestLocation = currentLocation
                        if (bestLocation <= expectedLocation) break
                        start = max(1, 2 * expectedLocation - bestLocation)
                    }
                }
                j--
            }

            // 无望找到更优匹配，提前结束
            val score = computeScore(patternLen, errors = i + 1, currentLocation = expectedLocation, expectedLocation)
            if (score > currentThreshold) break
            lastBitArr = bitArr
        }

        return BitapResult(bestLocation >= 0, max(0.001, finalScore))
    }

    private fun charAt(text: String, index: Int): Char =
        if (index in text.indices) text[index] else '\u0000'

    private fun computeScore(
        patternLen: Int,
        errors: Int,
        currentLocation: Int,
        expectedLocation: Int,
    ): Double {
        val accuracy = errors.toDouble() / patternLen
        if (IGNORE_LOCATION) return accuracy
        val proximity = abs(expectedLocation - currentLocation)
        if (DISTANCE == 0) return if (proximity != 0) 1.0 else accuracy
        return accuracy + proximity.toDouble() / DISTANCE
    }
}

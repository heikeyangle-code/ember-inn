package com.emberinn.engine.tokenizer

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HuggingFace tokenizer.json（BPE）引擎——Claude / Llama3 官方模型文件逐字打包，
 * 与 JTokkit（tiktoken）同思路：模型文件进 App，引擎实现编码。
 * 官方 ST 用 @agnai/web-tokenizers；本环境无该库，无法生成官方差分 fixture，
 * 实现按 HF tokenizer.json v3 规范移植（登记边界，差分待有库环境）。
 */
class HfBpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val idToPiece: Map<Int, String>,
    private val merges: Map<Pair<String, String>, Int>,
    private val addedTokens: List<AddedToken>,
    private val normalizerType: String?,
    private val splitRegex: Regex?,
    private val addPrefixSpace: Boolean,
    private val decoderAddPrefixSpace: Boolean,
    private val byteEncoder: Map<Int, Char>,
    private val byteDecoder: Map<Char, Int>,
) {

    data class AddedToken(val id: Int, val content: String, val special: Boolean)

    fun count(text: String): Int = encode(text).size

    fun encode(text: String): List<Int> {
        var t = text
        normalizerType?.let {
            t = Normalizer.normalize(t, when (it) {
                "NFD" -> Normalizer.Form.NFD
                "NFKC" -> Normalizer.Form.NFKC
                else -> Normalizer.Form.NFC
            })
        }
        val ids = mutableListOf<Int>()
        val rawSegments = splitAddedTokens(t)
        // HF ByteLevel post_processor：add_prefix_space 仅当首个元素不是特殊 token 时补前缀
        val segments = if (addPrefixSpace && !t.startsWith(" ") && rawSegments.firstOrNull()?.special == false) {
            val head = rawSegments.first()
            listOf(Seg(false, 0, " " + head.text)) + rawSegments.drop(1)
        } else {
            rawSegments
        }
        for (seg in segments) {
            if (seg.special) {
                ids += seg.id
                continue
            }
            val preTokens = if (splitRegex != null) splitIsolated(splitRegex, seg.text) else listOf(seg.text)
            for (pt in preTokens) {
                val token = toByteChars(pt)
                for (piece in bpe(token)) {
                    val id = vocab[piece]
                    if (id != null) {
                        ids += id
                    } else if (piece.length == 1) {
                        // byte fallback：单字节字符不在 vocab 时按 <0xXX>（官方 byte_fallback 语义）
                        val b = byteDecoder[piece[0]]
                        val fb = b?.let { vocab["<0x%02X>".format(it)] }
                        if (fb != null) ids += fb
                    }
                }
            }
        }
        return ids
    }

    fun decode(ids: List<Int>): String {
        val bytes = mutableListOf<Int>()
        for (id in ids) {
            val piece = idToPiece[id] ?: continue
            if (piece.isNotEmpty() && piece.all { it in byteDecoder }) {
                piece.forEach { bytes += byteDecoder.getValue(it) }
            } else if (piece.startsWith("<0x") && piece.endsWith(">")) {
                piece.substring(3, 5).toIntOrNull(16)?.let { bytes += it }
            } else {
                addedTokens.firstOrNull { it.id == id }?.content
                    ?.toByteArray(Charsets.UTF_8)
                    ?.forEach { bytes += it.toInt() and 0xFF }
            }
        }
        var out = String(bytes.map { it.toByte() }.toByteArray(), Charsets.UTF_8)
        if (decoderAddPrefixSpace && out.startsWith(" ")) out = out.removePrefix(" ")
        return out
    }

    // ---------- HF BPE ----------

    private fun bpe(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        if (merges.isEmpty()) return token.map { it.toString() }
        var parts = token.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val r = merges[parts[i] to parts[i + 1]] ?: continue
                if (r < bestRank) {
                    bestRank = r
                    best = parts[i] to parts[i + 1]
                }
            }
            val pair = best ?: break
            val next = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i + 1 < parts.size && parts[i] == pair.first && parts[i + 1] == pair.second) {
                    next.add(pair.first + pair.second)
                    i += 2
                } else {
                    next.add(parts[i])
                    i++
                }
            }
            parts = next
        }
        return parts
    }

    // ---------- HF 组件 ----------

    private data class Seg(val special: Boolean, val id: Int, val text: String)

    private fun splitAddedTokens(text: String): List<Seg> {
        if (addedTokens.isEmpty()) return listOf(Seg(false, 0, text))
        val sorted = addedTokens.sortedByDescending { it.content.length }
        val out = mutableListOf<Seg>()
        var pos = 0
        var rawStart = 0
        while (pos < text.length) {
            val match = sorted.firstOrNull { it.content.isNotEmpty() && text.startsWith(it.content, pos) }
            if (match != null) {
                if (rawStart < pos) out += Seg(false, 0, text.substring(rawStart, pos))
                out += Seg(true, match.id, match.content)
                pos += match.content.length
                rawStart = pos
            } else {
                pos++
            }
        }
        if (rawStart < text.length) out += Seg(false, 0, text.substring(rawStart))
        return out
    }

    private fun toByteChars(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = byteEncoder[b.toInt() and 0xFF]
            append(c ?: b.toInt().toChar())
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val cache = ConcurrentHashMap<String, HfBpeTokenizer>()

        fun forModel(model: String): HfBpeTokenizer? = when {
            model.contains("claude") -> forResource("claude.json")
            model.contains("llama3") || model.contains("llama-3") -> forResource("llama3.json")
            else -> null
        }

        fun forResource(name: String): HfBpeTokenizer = cache.getOrPut(name) {
            val root = json.parseToJsonElement(
                checkNotNull(HfBpeTokenizer::class.java.getResource("/tokenizers/$name")).readText(),
            ).jsonObject

            val model = root["model"] as? JsonObject ?: JsonObject(emptyMap())
            val vocabObj = model["vocab"] as? JsonObject ?: JsonObject(emptyMap())
            val vocab = vocabObj.mapValues { (_, v) -> (v as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
            val mergesList = (model["merges"] as? JsonArray)?.mapNotNull { el ->
                val arr = el as? JsonArray ?: return@mapNotNull null
                val a = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val b = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                a to b
            } ?: emptyList()
            val merges = mergesList.mapIndexed { i, p -> p to i }.toMap()

            val added = (root["added_tokens"] as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val content = (o["content"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                AddedToken(id, content, (o["special"] as? JsonPrimitive)?.content?.toBoolean() ?: false)
            } ?: emptyList()

            fun str(el: kotlinx.serialization.json.JsonElement?): String? =
                (el as? JsonPrimitive)?.contentOrNull
            fun bool(el: kotlinx.serialization.json.JsonElement?, def: Boolean = false): Boolean =
                (el as? JsonPrimitive)?.content?.toBoolean() ?: def

            val normalizerType = str((root["normalizer"] as? JsonObject)?.get("type"))
            val pre = root["pre_tokenizer"] as? JsonObject
            var splitRegex: Regex? = null
            var preAddPrefix = false
            when (str(pre?.get("type"))) {
                "ByteLevel" -> preAddPrefix = bool(pre?.get("add_prefix_space"))
                "Sequence" -> {
                    val items = pre?.get("pretokenizers") as? JsonArray ?: JsonArray(emptyList())
                    for (item in items) {
                        val o = item as? JsonObject ?: continue
                        when (str(o["type"])) {
                            "Split" -> {
                                val regex = str((o["pattern"] as? JsonObject)?.get("Regex"))
                                if (regex != null) splitRegex = runCatching { Regex(regex) }.getOrNull()
                            }
                            "ByteLevel" -> preAddPrefix = bool(o["add_prefix_space"])
                        }
                    }
                }
            }
            val post = root["post_processor"] as? JsonObject
            val postAddPrefix = post?.let { str(it["type"]) == "ByteLevel" && bool(it["add_prefix_space"]) } ?: false
            val decoder = root["decoder"] as? JsonObject
            val decoderAddPrefix = decoder?.let { str(it["type"]) == "ByteLevel" && bool(it["add_prefix_space"]) } ?: false

            val byteEncoder = bytesToUnicode()
            HfBpeTokenizer(
                vocab = vocab,
                idToPiece = vocab.entries.associate { (k, v) -> v to k },
                merges = merges,
                addedTokens = added,
                normalizerType = normalizerType,
                splitRegex = splitRegex,
                addPrefixSpace = preAddPrefix || postAddPrefix,
                decoderAddPrefixSpace = decoderAddPrefix,
                byteEncoder = byteEncoder,
                byteDecoder = byteEncoder.entries.associate { (b, c) -> c to b },
            )
        }

        /** 官方 GPT-2/tiktoken bytes_to_unicode 映射（HF ByteLevel 同一张表）。 */
        private fun bytesToUnicode(): Map<Int, Char> {
            val bs = mutableListOf<Int>()
            for (b in '!'.code..'~'.code) bs += b
            for (b in 0xA1..0xAC) bs += b
            for (b in 0xAE..0xFF) bs += b
            val cs = bs.toMutableList()
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs += b
                    cs += 256 + n
                    n++
                }
            }
            return bs.zip(cs).associate { (b, c) -> b to c.toChar() }
        }

        private fun splitIsolated(regex: Regex, text: String): List<String> {
            val out = mutableListOf<String>()
            var pos = 0
            for (m in regex.findAll(text)) {
                if (m.range.first > pos) out += text.substring(pos, m.range.first)
                out += m.value
                pos = m.range.last + 1
            }
            if (pos < text.length) out += text.substring(pos)
            return out
        }
    }
}

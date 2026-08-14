package com.emberinn.engine.tokenizer

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * SentencePiece BPE 引擎——官方 src/tokenizers 模型文件逐字打包（Gemini/llama/mistral/yi/jamba/nerdstash）。
 * 模型结构（已核对 7 个文件）：model_type=BPE、precompiled_charsmap 为空（归一化为恒等）、
 * byte pieces 存在（<0xXX>）。官方 ST 用 @agnai/sentencepiece-js；本环境无该库，无法官方差分，
 * 实现按 sentencepiece BPE 规范移植（登记边界，差分待有库环境）。
 */
class SentencePieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val idToPiece: Map<Int, String>,
    private val scores: Map<String, Float>,
    private val bytePieceIds: Map<Int, Int>,
    private val addDummyPrefix: Boolean,
    private val escapeWhitespaces: Boolean,
    private val removeExtraWhitespaces: Boolean,
    private val unkId: Int,
) {

    fun count(text: String): Int = encode(text).size

    fun encode(text: String): List<Int> {
        var t = text
        if (removeExtraWhitespaces) {
            t = t.trim().replace(Regex(" +"), " ")
        }
        if (escapeWhitespaces) t = t.replace(' ', '\u2581')
        if (addDummyPrefix && !t.startsWith("\u2581")) t = "\u2581" + t

        var parts = codePoints(t).toMutableList()
        // 未登录单字符：byte fallback 拆成 <0xXX>；无 byte pieces 则保留原字符（最后落 unk）
        val expanded = mutableListOf<String>()
        for (p in parts) {
            if (p in vocab) {
                expanded += p
            } else if (bytePieceIds.isNotEmpty()) {
                for (b in p.toByteArray(Charsets.UTF_8)) {
                    val id = bytePieceIds[b.toInt() and 0xFF]
                    expanded += idToPiece[id] ?: p
                }
            } else {
                expanded += p
            }
        }
        parts = expanded

        // sentencepiece BPE：反复合并“合并后 piece 在 vocab 中且 score 最高”的相邻对（同分取先出现）
        while (parts.size > 1) {
            var bestPair: Pair<String, String>? = null
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in 0 until parts.size - 1) {
                val merged = parts[i] + parts[i + 1]
                val sc = scores[merged] ?: continue
                if (sc > bestScore) {
                    bestScore = sc
                    bestPair = parts[i] to parts[i + 1]
                }
            }
            val pair = bestPair ?: break
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

        return parts.map { p ->
            vocab[p] ?: unkId
        }
    }

    fun decode(ids: List<Int>): String {
        val out = ByteArrayOutputStream()
        for (id in ids) {
            val piece = idToPiece[id] ?: continue
            if (piece.startsWith("<0x") && piece.endsWith(">")) {
                piece.substring(3, 5).toIntOrNull(16)?.let { out.write(it) }
            } else {
                out.write(piece.toByteArray(Charsets.UTF_8))
            }
        }
        var text = String(out.toByteArray(), Charsets.UTF_8)
        if (escapeWhitespaces) text = text.replace('\u2581', ' ')
        if (addDummyPrefix && text.startsWith(" ")) text = text.removePrefix(" ")
        return text
    }

    private fun codePoints(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            out += String(Character.toChars(cp))
            i += Character.charCount(cp)
        }
        return out
    }

    companion object {
        private val cache = ConcurrentHashMap<String, SentencePieceTokenizer>()

        fun forModel(model: String): SentencePieceTokenizer? {
            val key = com.emberinn.engine.provider.TokenizerModel.map(model)
            val resource = when (key) {
                // 只保留 Google Gemini（gemma.model）；llama/mistral/yi/jamba/nerdstash 未打包（登记边界）
                "gemma" -> "gemma.model"
                else -> return null
            }
            return forResource(resource)
        }

        fun forResource(name: String): SentencePieceTokenizer = cache.getOrPut(name) {
            parse(readResource(name))
        }

        private fun readResource(name: String): ByteArray =
            checkNotNull(SentencePieceTokenizer::class.java.getResource("/tokenizers/$name")).readBytes()

        private fun parse(data: ByteArray): SentencePieceTokenizer {
            val root = Proto.fields(data)
            val vocab = linkedMapOf<String, Int>()
            val scores = HashMap<String, Float>()
            val types = HashMap<String, Int>()
            var unkId = 0
            for (entry in root[1].orEmpty()) {
                val raw = entry as ByteArray
                val pf = Proto.fields(raw)
                val piece = Proto.str(pf, 1) ?: continue
                val id = vocab.size
                vocab[piece] = id
                Proto.float(pf, 2)?.let { scores[piece] = it }
                val type = Proto.varint(pf, 3) ?: 1
                types[piece] = type
                if (type == 2) unkId = id
            }
            val byteIds = HashMap<Int, Int>()
            for ((piece, id) in vocab) {
                if (piece.startsWith("<0x") && piece.endsWith(">")) {
                    piece.substring(3, 5).toIntOrNull(16)?.let { byteIds[it] = id }
                }
            }
            val trainer = Proto.fields(root[2]?.firstOrNull() as? ByteArray ?: ByteArray(0))
            val modelType = Proto.varint(trainer, 3) ?: 1
            require(modelType == 2) { "sentencepiece: 仅支持 BPE（model_type=2），实际 $modelType" }
            val norm = Proto.fields(root[3]?.firstOrNull() as? ByteArray ?: ByteArray(0))
            val charsmap = Proto.bytes(norm, 2)
            require(charsmap == null || charsmap.isEmpty()) { "sentencepiece: precompiled_charsmap 非空（归一化边界未支持）" }
            return SentencePieceTokenizer(
                vocab = vocab,
                idToPiece = vocab.entries.associate { (k, v) -> v to k },
                scores = scores,
                bytePieceIds = byteIds,
                addDummyPrefix = Proto.bool(norm, 3),
                escapeWhitespaces = Proto.bool(norm, 5, default = true),
                removeExtraWhitespaces = Proto.bool(norm, 4),
                unkId = unkId,
            )
        }
    }

    /** 极简 protobuf wire 解析（sentencepiece_model.proto 子集）。 */
    private object Proto {
        fun fields(data: ByteArray, start: Int = 0, end: Int = data.size): Map<Int, List<Any>> {
            val out = HashMap<Int, MutableList<Any>>()
            var i = start
            while (i < end) {
                val (key, n1) = varint(data, i)
                i = n1
                val f = key ushr 3
                val wt = key and 7
                when (wt) {
                    0 -> {
                        val (v, n2) = varint(data, i)
                        i = n2
                        out.getOrPut(f) { mutableListOf() }.add(v)
                    }
                    2 -> {
                        val (len, n2) = varint(data, i)
                        i = n2
                        out.getOrPut(f) { mutableListOf() }.add(data.copyOfRange(i, i + len))
                        i += len
                    }
                    5 -> {
                        val v = ((data[i + 3].toInt() and 0xFF) shl 24) or ((data[i + 2].toInt() and 0xFF) shl 16) or
                            ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
                        out.getOrPut(f) { mutableListOf() }.add(v)
                        i += 4
                    }
                    else -> throw IllegalArgumentException("unsupported wire type $wt")
                }
            }
            return out
        }

        private fun varint(data: ByteArray, i: Int): Pair<Int, Int> {
            var r = 0
            var s = 0
            var p = i
            while (true) {
                val x = data[p].toInt() and 0xFF
                p++
                r = r or ((x and 0x7F) shl s)
                if (x and 0x80 == 0) return r to p
                s += 7
            }
        }

        fun str(f: Map<Int, List<Any>>, field: Int): String? =
            (f[field]?.firstOrNull() as? ByteArray)?.toString(Charsets.UTF_8)

        fun bytes(f: Map<Int, List<Any>>, field: Int): ByteArray? =
            f[field]?.firstOrNull() as? ByteArray

        fun float(f: Map<Int, List<Any>>, field: Int): Float? =
            (f[field]?.firstOrNull() as? Int)?.let { Float.fromBits(it) }

        fun varint(f: Map<Int, List<Any>>, field: Int): Int? =
            f[field]?.firstOrNull() as? Int

        fun bool(f: Map<Int, List<Any>>, field: Int, default: Boolean = false): Boolean =
            (f[field]?.firstOrNull() as? Int)?.let { it != 0 } ?: default
    }
}

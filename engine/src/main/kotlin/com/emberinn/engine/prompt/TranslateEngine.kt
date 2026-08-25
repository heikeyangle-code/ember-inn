package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.VectorTextUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray

/**
 * Translate 纯函数（对齐官方 translate/index.js translateIncomingMessage / translateIncomingMessageReasoning）。
 * 差分脚本 scripts/diff/translate-official.mjs → fixture 19 例，TranslateDiffTest 验证。
 *
 * 核心行为：
 * - name2Override(message.name) 优先于 charName：{{char}}/{{Char}} → name2Override（让"AI 说话者自己的显示名"被翻译，而非角色卡名）。
 * - 写 extra 键约定：message→extra.display_text；reasoning→extra.reasoning_display_text。
 * - 8 provider body/url 构造契约（libre/google/lingva/deepl/deeplx/onering 等），App 接线直接调用。
 */
object TranslateEngine {

    /** 官方 substituteParams(name2Override)：{{char}}/{{Char}} → name2Override（或 charName）。 */
    fun substituteParamsNameOverride(text: String, charName: String, nameOverride: String? = null): String {
        val override = nameOverride ?: charName
        return text.replace("{{char}}", override).replace("{{Char}}", override)
    }

    // -------- provider body/URL contracts --------

    fun libreBody(text: String, target: String, apiKey: String?): Map<String, String> = buildMap {
        put("q", text); put("source", "auto"); put("target", target); put("format", "text")
        if (!apiKey.isNullOrBlank()) put("api_key", apiKey)
    }

    /** 官方 src/endpoints/translate.js:183：{base}/auto/{target}/{text}，base 自带 /api/v1。 */
    fun lingvaUrl(baseUrl: String, text: String, target: String): String {
        val u = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val enc = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        return "${u}auto/$target/$enc"
    }

    data class DeepLBody(val auth_key: String, val text: List<String>, val target_lang: String)

    fun deeplBody(text: String, target: String, apiKey: String): DeepLBody =
        DeepLBody(auth_key = apiKey, text = listOf(text), target_lang = target)

    data class DeepLxBody(val text: String, val source_lang: String, val target_lang: String, val url: String)

    fun deeplxBody(text: String, target: String, baseUrl: String): DeepLxBody =
        DeepLxBody(text = text, source_lang = "auto", target_lang = target, url = baseUrl)

    data class OneRingBody(val text: String, val from_lang: String, val to_lang: String, val url: String)

    fun oneringBody(text: String, target: String, baseUrl: String, internalLang: String, targetLang: String): OneRingBody {
        val from = if (target == internalLang) targetLang else internalLang
        val url = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        return OneRingBody(text = text, from_lang = from, to_lang = target, url = url)
    }

    // -------- Google（google-translate-api-x 10.7.2，官方默认 forceBatch=true → batchexecute 路径）--------

    /**
     * 官方服务端 /google → new Translator({to, requestFunction}) → translate(text)：
     * 库默认 forceBatch:true，字符串输入也走 batchTranslate（translate.cjs:6-16）。
     */
    private const val GOOGLE_RPCID = "MkEWBc"

    /** JS encodeURIComponent 逐字语义：保留 A-Za-z0-9 - _ . ! ~ * ' ( )，其余按 UTF-8 百分号大写。 */
    fun jsEncodeURIComponent(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '!'.code || c == '~'.code ||
                c == '*'.code || c == '\''.code || c == '('.code || c == ')'.code
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append("%02X".format(c))
            }
        }
        return sb.toString()
    }

    /** 官方 batchTranslate 的 URL（URLSearchParams 序列化；URLEncoder 与之同集合、空格→+）。 */
    fun googleBatchUrl(reqId: Int): String {
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        return "https://translate.google.com/_/TranslateWebserverUi/data/batchexecute?" +
            "rpcids=$GOOGLE_RPCID" +
            "&source-path=${enc("/")}" +
            "&f.sid=" + "" +
            "&bl=" + "" +
            "&hl=${enc("en-US")}" +
            "&soc-app=1&soc-platform=1&soc-device=1" +
            "&_reqid=$reqId" +
            "&rt=c"
    }

    /**
     * 官方 freq 构造（batchTranslate.cjs:71-88）：
     * body = 'f.req=' + encodeURIComponent(JSON.stringify([[[rpcid, JSON.stringify([[text,"auto",to,false],[null]]), null, i.toString(36)]]])) + '&'
     * fromIso='auto'（getCode('auto')）、autoCorrect=false（DEFAULT_OPTIONS）、单输入 i=0。
     */
    fun googleBody(text: String, target: String, reqId: Int): String {
        val inner = buildJsonArray {
            add(buildJsonArray {
                add(JsonPrimitive(text))
                add(JsonPrimitive("auto"))
                add(JsonPrimitive(target))
                add(JsonPrimitive(false))
            })
            add(buildJsonArray { add(JsonNull) })
        }
        val freqPart = buildJsonArray {
            add(JsonPrimitive(GOOGLE_RPCID))
            add(JsonPrimitive(inner.toString()))
            add(JsonNull)
            add(JsonPrimitive(reqId.toString(36)))
        }
        val payload = buildJsonArray { add(freqPart) }.toString()
        return "f.req=" + jsEncodeURIComponent(payload) + "&"
    }

    /**
     * 官方响应解析（batchTranslate.cjs:89-141）：slice(6) → 按 '\n' 切 →
     * 取 '[' 开头且 chunk[3]!=='e' 的行 → JSON.parse → wrb.fr 条目 → JSON.parse(entry[2]) →
     * inner[1][0][0][5] 为空时取 [0]，否则 map(o=>o[0]).filter(Boolean).join(' ')。
     */
    fun googleParse(responseBody: String): String? {
        var found: String? = null
        for (chunk in responseBody.substring(6).split('\n')) {
            if (chunk.isEmpty() || chunk[0] != '[' || chunk.getOrNull(3) == 'e') continue
            val outer = runCatching { Json.parseToJsonElement(chunk).jsonArray }.getOrNull() ?: continue
            for (entry in outer) {
                val arr = entry as? kotlinx.serialization.json.JsonArray ?: continue
                val kind = (arr.getOrNull(0) as? JsonPrimitive)?.content ?: continue
                if (kind != "wrb.fr") continue
                // entry[2]===null：官方 Partial fail → throw；调用方视为整体失败
                val payloadStr = (arr.getOrNull(2) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                    ?: return null
                val inner = runCatching { Json.parseToJsonElement(payloadStr).jsonArray }.getOrNull() ?: return null
                val row = ((inner.getOrNull(1)?.jsonArray)?.getOrNull(0)?.jsonArray)?.getOrNull(0)?.jsonArray
                    ?: return null
                val alt = row.getOrNull(5)
                found = if (alt == null || alt is JsonNull) {
                    (row.getOrNull(0) as? JsonPrimitive)?.content
                } else {
                    alt.jsonArray
                        .mapNotNull { o -> (o as? kotlinx.serialization.json.JsonArray)?.getOrNull(0) as? JsonPrimitive }
                        .map { it.content }
                        .filter { it.isNotEmpty() } // 官方 filter(Boolean)
                        .joinToString(" ")
                }
            }
        }
        return found
    }

    // -------- 分块与图片链接保护（官方 translate()/translateInner/chunkedTranslate，index.js:431-517）--------

    /** 内嵌 markdown 图片链接段：isLink=true 时 text 为链接原文（不送翻译）。 */
    data class ImageLinkSegment(val isLink: Boolean, val text: String)

    /**
     * 官方 translate() 的切分：
     *   const chunks = text.split(/!\[.*?]\([^)]*\)/);
     *   const links = [...text.matchAll(/!\[.*?]\([^)]*\)/g)];
     * 文本段与链接段交错；末尾链接后会有空文本段。
     */
    private val IMAGE_LINK_RE = Regex("!\\[.*?]\\([^)]*\\)")

    fun imageLinkSegments(text: String): List<ImageLinkSegment> {
        val out = mutableListOf<ImageLinkSegment>()
        var last = 0
        for (m in IMAGE_LINK_RE.findAll(text)) {
            out += ImageLinkSegment(isLink = false, text = text.substring(last, m.range.first))
            out += ImageLinkSegment(isLink = true, text = m.value)
            last = m.range.last + 1
        }
        out += ImageLinkSegment(isLink = false, text = text.substring(last))
        return out
    }

    /** 官方 translateInner 的分块上限：google/lingva 5000、deeplx 1500、bing 1000；其余 provider 不分块。 */
    fun chunkSize(provider: String): Int? = when (provider) {
        "google", "lingva" -> 5000
        "deeplx" -> 1500
        "bing" -> 1000
        else -> null
    }

    /**
     * 官方 chunkedTranslate 的网络调用序列：长度不超限=整段一次；
     * 超限按 utils.js splitRecursive（默认分隔符 '\n\n','\n',' ',''）切块后逐块调用。
     * size==null（不分块 provider）= 整段一次。
     */
    fun chunked(text: String, provider: String): List<String> {
        val size = chunkSize(provider) ?: return listOf(text)
        if (text.length <= size) return listOf(text)
        return VectorTextUtils.splitRecursive(text, size)
    }
}

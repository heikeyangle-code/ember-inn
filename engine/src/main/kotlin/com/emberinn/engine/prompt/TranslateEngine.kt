package com.emberinn.engine.prompt

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

    fun googleEndpoint(target: String): Pair<String, String> {
        val lang = if (target == "pt-BR") "pt" else target
        return "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t" to "q"
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
}

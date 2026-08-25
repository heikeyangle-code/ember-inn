package com.emberinn.app.data

import com.emberinn.engine.worldinfo.VectorTextUtils

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import com.emberinn.engine.prompt.TranslateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 翻译执行层：对齐官方 src/endpoints/translate.js 的全部 8 家提供商协议
 * （Libre/Google/Yandex/Lingva/DeepL/OneRing/DeepLX/Bing；Bing 按官方依赖 bing-translate-api
 *  的 token 流程原样移植：GET /translator 取 IG/IID/key/token → POST /ttranslatev3）。
 * 简化登记：Bing token 每请求重新获取（官方带缓存/过期，行为等价）；Google 走官方库
 *  google-translate-api-x 默认的 batchexecute 路径（forceBatch:true）。
 */
class TranslateClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val bingUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/151.0.4129.59"

    /**
     * 官方 translateIncomingMessage / translateOutgoingMessage：**翻译前先 substituteParams(name2Override)**，
     * 让 {{char}} / {{Char}} 先被 speaker 自己的显示名替换（角色卡/说话人名）。
     *
     * @param charName 角色卡 char_name（nameOverride 为空时 fallback）
     * @param nameOverride 说话者显示名（AI 消息是 message.name；用户消息是 user_name）
     */
    suspend fun translate(
        context: Context,
        text: String,
        charName: String = "",
        nameOverride: String? = null,
        targetLang: String? = null,
        providerOverride: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        // 官方出站路径不做 substituteParams（translateOutgoingMessage 无名字上下文）：
        // 双名皆空时保持原文，避免把 {{char}} 替换成空串
        val prepared = if (charName.isNotBlank() || nameOverride != null) {
            TranslateEngine.substituteParamsNameOverride(text, charName, nameOverride)
        } else {
            text
        }
        // 官方 /translate：provider= 未传时用扩展设置（index.js:801）
        val provider = providerOverride ?: ServicesPrefs.translateProvider(context)
        val target = targetLang ?: ServicesPrefs.translateTargetLanguage(context)
        val apiKey = ServicesPrefs.translateApiKey(context)
        val url = ServicesPrefs.translateUrl(context)
        // 官方 translate()（index.js:449-485）：先按内嵌图片链接切段，文本段逐段翻译、链接原样回插；
        // 任一段失败则整体失败（官方 throw → toastr.error）
        runCatching {
            buildString {
                for (segment in TranslateEngine.imageLinkSegments(prepared)) {
                    if (segment.isLink) {
                        append(segment.text)
                    } else if (segment.text.isEmpty()) {
                        // 官方 translateInner('') → ''，不发网络请求
                    } else {
                        for (chunk in TranslateEngine.chunked(segment.text, provider)) {
                            append(dispatch(context, chunk, target, provider, apiKey, url) ?: throw IllegalStateException("translate failed"))
                        }
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * 官方 translateIncomingMessageReasoning：reasoning 也 substituteParams(name2Override)，
     * 结果写入 message.extra.reasoning_display_text。targetLang 由调用方显式传。
     */
    suspend fun translateReasoning(
        context: Context,
        text: String,
        targetLang: String,
        charName: String = "",
        nameOverride: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || targetLang.isBlank()) return@withContext null
        val prepared = TranslateEngine.substituteParamsNameOverride(text, charName, nameOverride)
        val provider = ServicesPrefs.translateProvider(context)
        val apiKey = ServicesPrefs.translateApiKey(context)
        val url = ServicesPrefs.translateUrl(context)
        // 官方同走 translate()：图片链接切段 + 分块（见上）
        runCatching {
            buildString {
                for (segment in TranslateEngine.imageLinkSegments(prepared)) {
                    if (segment.isLink) {
                        append(segment.text)
                    } else if (segment.text.isEmpty()) {
                        // 官方 translateInner('') → ''，不发网络请求
                    } else {
                        for (chunk in TranslateEngine.chunked(segment.text, provider)) {
                            append(dispatch(context, chunk, targetLang, provider, apiKey, url) ?: throw IllegalStateException("translate failed"))
                        }
                    }
                }
            }
        }.getOrNull()
    }

    /** 8 家 provider 的统一分发（translate / translateReasoning 共用）。 */
    private fun dispatch(
        context: Context,
        text: String,
        target: String,
        provider: String,
        apiKey: String,
        url: String,
    ): String? = when (provider) {
        "libre" -> libre(text, target, apiKey, url)
        "google" -> google(text, target)
        "lingva" -> lingva(text, target, url)
        "deepl" -> deepl(context, text, target, apiKey)
        "deeplx" -> deeplx(text, target, url)
        "oneringtranslator" -> onering(context, text, target, url)
        "bing" -> bing(text, target)
        "yandex" -> yandex(text, target)
        // 官方 translateInner default：未知 provider 原样返回（index.js:514-516）
        else -> text
    }

    // ---- Libre（官方：JSON {q, source:'auto', target, format:'text', api_key} → translatedText）----
    private fun libre(text: String, target: String, apiKey: String, url: String): String? {
        val endpoint = url.ifBlank { "https://libretranslate.com/translate" }
        val payload = JSONObject().apply {
            TranslateEngine.libreBody(text, libreLang(target), apiKey.ifBlank { null }).forEach { (k, v) -> put(k, v) }
        }.toString()
        val request = Request.Builder().url(endpoint).post(payload.toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("translatedText").ifBlank { null }
        }
    }

    // ---- Google（官方服务端 /google → google-translate-api-x（默认 forceBatch）→ batchexecute；
    //      服务端映射 pt-BR→pt（src/endpoints/translate.js:78-80）；_reqid=Math.floor(1000+random*9000)）----
    private fun google(text: String, target: String): String? {
        val lang = if (target == "pt-BR") "pt" else target
        val reqId = Random.nextInt(1000, 10000)
        val body = TranslateEngine.googleBody(text, lang, reqId)
            .toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url(TranslateEngine.googleBatchUrl(reqId))
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return TranslateEngine.googleParse(resp.body?.string().orEmpty())?.ifBlank { null }
        }
    }

    // ---- Yandex（官方：客户端 5000 分块（translateProviderYandex）→ form 重复 text 字段 + lang；
    //      服务端映射 pt-PT→pt、zh-CN|zh-TW→zh（src/endpoints/translate.js:105-111）；响应 json.text 用逗号连接）----
    private fun yandex(text: String, target: String): String? {
        val lang = when (target) {
            "pt-PT" -> "pt"
            "zh-CN", "zh-TW" -> "zh"
            else -> target
        }
        // 官方 translateProviderYandex：>5000 时 utils.js splitRecursive(text, 5000)
        val chunks = if (text.length <= 5000) listOf(text) else VectorTextUtils.splitRecursive(text, 5000)
        val ucid = UUID.randomUUID().toString().replace("-", "")
        val endpoint = "https://translate.yandex.net/api/v1/tr.json/translate?ucid=$ucid&srv=android&format=text"
        val body = FormBody.Builder()
            .apply { for (chunk in chunks) add("text", chunk) }
            .add("lang", lang)
            .build()
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("text") ?: return null
            // 官方服务端 json.text.join()：默认逗号分隔
            val parts = List(arr.length()) { arr.optString(it) }
            return parts.joinToString(",").ifBlank { null }
        }
    }

    // ---- Lingva（官方 lingva 扩展：/api/v1/{text}/{target}/auto → translation）----
    private fun lingva(text: String, target: String, url: String): String? {
        val base = url.ifBlank { "https://lingva.ml/api/v1" } // 官方 LINGVA_DEFAULT
        val lang = when (target) {
            "zh-CN", "zh-TW" -> "zh"
            "pt-BR", "pt-PT" -> "pt"
            else -> target
        }
        val endpoint = TranslateEngine.lingvaUrl(base, text, lang)
        val request = Request.Builder().url(endpoint).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("translation").ifBlank { null }
        }
    }

    // ---- DeepL（官方：form text/target_lang(+formality)，DeepL-Auth-Key。
    //      官方无自定义 URL（LOCAL_URL 不含 deepl）：deepl_endpoint||'free' 选 api-free/api 主机
    //      （src/endpoints/translate.js:234-236）；zh 仅 zh-CN/zh-TW→ZH；formality 集合见 :228）----
    private fun deepl(context: Context, text: String, target: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val endpoint = if (ServicesPrefs.translateDeeplEndpoint(context) == "pro") {
            "https://api.deepl.com/v2/translate"
        } else {
            "https://api-free.deepl.com/v2/translate"
        }
        val lang = if (target == "zh-CN" || target == "zh-TW") "ZH" else target
        val body = FormBody.Builder()
            .add("text", text)
            .add("target_lang", lang)
            .apply {
                if (lang in setOf("de", "fr", "it", "es", "nl", "ja", "ru", "pt-BR", "pt-PT")) {
                    add("formality", "default")
                }
            }
            .build()
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("translations") ?: return null
            return arr.optJSONObject(0)?.optString("text")?.ifBlank { null }
        }
    }

    // ---- OneRing（官方：GET {url}?text&from_lang&to_lang → JSON.result）----
    private fun onering(context: Context, text: String, target: String, url: String): String? {
        val base = url.ifBlank { "http://127.0.0.1:4990/translate" }
        // 官方 translateProviderOneRing（extensions/translate/index.js）：
        // from_lang = lang == internal_language ? target_language : internal_language；to_lang = lang 原样
        // （服务端的 pt-BR→pt 重写对官方请求体不生效——客户端只发 from_lang/to_lang）
        val ob = TranslateEngine.oneringBody(
            text, target, base,
            ServicesPrefs.translateInternalLanguage(context),
            ServicesPrefs.translateTargetLanguage(context),
        )
        val endpoint = ob.url + "?text=${URLEncoder.encode(ob.text, "UTF-8")}&from_lang=${URLEncoder.encode(ob.from_lang, "UTF-8")}&to_lang=${URLEncoder.encode(ob.to_lang, "UTF-8")}"
        val request = Request.Builder().url(endpoint).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("result").ifBlank { null }
        }
    }

    // ---- DeepLX（官方：JSON {text, source_lang:'auto', target_lang} → data；zh-CN/zh-TW→ZH）----
    private fun deeplx(text: String, target: String, url: String): String? {
        // 官方 DEEPLX_URL_DEFAULT（src/endpoints/translate.js:10）：本地默认端点
        val effectiveUrl = url.ifBlank { "http://127.0.0.1:1188/translate" }
        val lang = if (target == "zh-CN" || target == "zh-TW") "ZH" else target
        val payload = JSONObject().apply {
            val b = TranslateEngine.deeplxBody(text, lang, effectiveUrl)
            put("text", b.text); put("source_lang", b.source_lang); put("target_lang", b.target_lang)
        }.toString()
        val request = Request.Builder().url(effectiveUrl).post(payload.toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("data").ifBlank { null }
        }
    }

    // ---- Bing（官方依赖 bing-translate-api 4.2.1：GET /translator 取 IG/IID/key/token →
    //      POST /ttranslatev3 form {fromLang,text,token,key,to} → [0].translations[0].text）----
    private fun bing(text: String, target: String): String? {
        // 官方服务端 /bing 映射（src/endpoints/translate.js:383-392）：仅此三项
        val lang = when (target) {
            "zh-CN" -> "zh-Hans"
            "zh-TW" -> "zh-Hant"
            "pt-BR" -> "pt"
            else -> target
        }
        // 1) 取全局配置（官方 fetchGlobalConfig）
        var subdomain = ""
        val configPage = client.newCall(
            Request.Builder().url("https://www.bing.com/translator").header("User-Agent", bingUserAgent).build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) return null
            // 官方 got 跟随重定向后取 redirectUrl 的子域
            resp.request.url.host.removeSuffix(".bing.com").takeIf { it != "www" && it.isNotBlank() }?.let { subdomain = it }
            resp.body?.string().orEmpty()
        }
        val ig = Regex("""IG:"([^"]+)"""").find(configPage)?.groupValues?.get(1) ?: return null
        val iid = Regex("""data-iid="([^"]+)"""").find(configPage)?.groupValues?.get(1) ?: return null
        val helper = Regex("""params_AbusePreventionHelper\s?=\s?([^\]]+\])""").find(configPage)?.groupValues?.get(1) ?: return null
        val cfg = runCatching { JSONArray(helper) }.getOrNull() ?: return null
        val key = cfg.optString(0)
        val token = cfg.optString(1)
        if (key.isBlank() || token.isBlank()) return null

        // 2) 翻译请求（官方 makeRequestURL：/ttranslatev3?isVertical=1&&IG=..&IID=..）
        val host = if (subdomain.isNotEmpty()) "$subdomain.bing.com" else "www.bing.com"
        val endpoint = "https://$host/ttranslatev3?isVertical=1&&IG=$ig&IID=$iid"
        val body = FormBody.Builder()
            .add("fromLang", "auto-detect")
            .add("text", text)
            .add("token", token)
            .add("key", key)
            .add("to", lang)
            .add("tryFetchingGenderDebiasedTranslations", "true")
            .build()
        val request = Request.Builder().url(endpoint)
            .header("User-Agent", bingUserAgent)
            .header("Referer", "https://$host/translator")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string().orEmpty())
            return arr.optJSONObject(0)?.optJSONArray("translations")?.optJSONObject(0)?.optString("text")?.ifBlank { null }
        }
    }

    /** 官方 translate.js /libre：zh-CN→zh、zh-TW→zt、pt-BR/pt-PT→pt。 */
    private fun libreLang(target: String): String = when (target) {
        "zh-CN" -> "zh"
        "zh-TW" -> "zt"
        "pt-BR", "pt-PT" -> "pt"
        else -> target
    }
}

package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
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

/**
 * 翻译执行层：对齐官方 src/endpoints/translate.js 的全部 8 家提供商协议
 * （Libre/Google/Yandex/Lingva/DeepL/OneRing/DeepLX/Bing；Bing 按官方依赖 bing-translate-api 4.2.1
 *  的 token 流程原样移植：GET /translator 取 IG/IID/key/token → POST /ttranslatev3）。
 * 简化登记：Bing token 每请求重新获取（官方带缓存/过期，行为等价）；Google 走 google-translate-api-x
 *  的免费端点（client=gtx）。
 */
class TranslateClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val bingUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/151.0.4129.59"

    suspend fun translate(
        context: Context,
        text: String,
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val provider = ServicesPrefs.translateProvider(context)
        val target = ServicesPrefs.translateTargetLanguage(context)
        val apiKey = ServicesPrefs.translateApiKey(context)
        val url = ServicesPrefs.translateUrl(context)
        runCatching {
            when (provider) {
                "libre" -> libre(text, target, apiKey, url)
                "google" -> google(text, target)
                "lingva" -> lingva(text, target, url)
                "deepl" -> deepl(text, target, apiKey, url)
                "deeplx" -> deeplx(text, target, url)
                "oneringtranslator" -> onering(text, target, url)
                "bing" -> bing(text, target)
                "yandex" -> yandex(text, target)
                else -> null
            }
        }.getOrNull()
    }

    // ---- Libre（官方：JSON {q, source:'auto', target, format:'text', api_key} → translatedText）----
    private fun libre(text: String, target: String, apiKey: String, url: String): String? {
        val endpoint = url.ifBlank { "https://libretranslate.com/translate" }
        val payload = JSONObject()
            .put("q", text)
            .put("source", "auto")
            .put("target", libreLang(target))
            .put("format", "text")
            .apply { if (apiKey.isNotBlank()) put("api_key", apiKey) }
            .toString()
        val request = Request.Builder().url(endpoint).post(payload.toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("translatedText").ifBlank { null }
        }
    }

    // ---- Google（官方 google-translate-api-x：免费端点 client=gtx）----
    private fun google(text: String, target: String): String? {
        val lang = if (target == "pt-BR") "pt" else target
        val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t"
        val body = FormBody.Builder().add("q", text).build()
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string().orEmpty())
            val outer = arr.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until outer.length()) {
                val row = outer.optJSONArray(i) ?: continue
                val seg = row.optString(0)
                if (seg.isNotEmpty()) sb.append(seg)
            }
            return sb.toString().ifBlank { null }
        }
    }

    // ---- Yandex（官方：POST /api/v1/tr.json/translate?ucid=&srv=android&format=text，form text/lang → json.text join）----
    private fun yandex(text: String, target: String): String? {
        val lang = when (target) {
            "pt-PT" -> "pt"
            "zh-CN", "zh-TW" -> "zh"
            else -> target
        }
        val ucid = UUID.randomUUID().toString().replace("-", "")
        val endpoint = "https://translate.yandex.net/api/v1/tr.json/translate?ucid=$ucid&srv=android&format=text"
        val body = FormBody.Builder()
            .add("text", text)
            .add("lang", lang)
            .build()
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("text") ?: return null
            val sb = StringBuilder()
            for (i in 0 until arr.length()) sb.append(arr.optString(i))
            return sb.toString().ifBlank { null }
        }
    }

    // ---- Lingva（官方：GET {base}/auto/{lang}/{encodeURIComponent(text)} → translation）----
    private fun lingva(text: String, target: String, url: String): String? {
        val base = url.ifBlank { "https://lingva.ml/api/v1" }.trimEnd('/')
        val lang = when (target) {
            "zh-CN", "zh-TW" -> "zh"
            "pt-BR", "pt-PT" -> "pt"
            else -> target
        }
        val endpoint = "$base/auto/$lang/${URLEncoder.encode(text, "UTF-8")}"
        val request = Request.Builder().url(endpoint).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("translation").ifBlank { null }
        }
    }

    // ---- DeepL（官方：form text/target_lang(+formality)，free/pro 端点，DeepL-Auth-Key）----
    private fun deepl(text: String, target: String, apiKey: String, url: String): String? {
        if (apiKey.isBlank()) return null
        val endpoint = url.ifBlank { "https://api-free.deepl.com/v2/translate" }
        val lang = if (target == "zh" || target == "zh-CN" || target == "zh-TW") "ZH" else target
        val body = FormBody.Builder()
            .add("text", text)
            .add("target_lang", lang)
            .apply {
                if (lang in setOf("de", "fr", "it", "es", "nl", "ja", "ru", "pt", "pt-BR", "pt-PT")) {
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

    // ---- OneRing（官方：GET {url}?text&from_lang&to_lang → result）----
    private fun onering(text: String, target: String, url: String): String? {
        val base = url.ifBlank { "http://127.0.0.1:4990/translate" }
        val lang = if (target == "pt-BR" || target == "pt-PT") "pt" else target
        val endpoint = "$base?text=${URLEncoder.encode(text, "UTF-8")}&from_lang=auto&to_lang=${URLEncoder.encode(lang, "UTF-8")}"
        val request = Request.Builder().url(endpoint).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("result").ifBlank { null }
        }
    }

    // ---- DeepLX（官方：JSON {text, source_lang:'auto', target_lang} → data；zh→ZH）----
    private fun deeplx(text: String, target: String, url: String): String? {
        if (url.isBlank()) return null
        val lang = if (target == "zh" || target == "zh-CN" || target == "zh-TW") "ZH" else target
        val payload = JSONObject()
            .put("text", text)
            .put("source_lang", "auto")
            .put("target_lang", lang)
            .toString()
        val request = Request.Builder().url(url).post(payload.toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("data").ifBlank { null }
        }
    }

    // ---- Bing（官方依赖 bing-translate-api 4.2.1：GET /translator 取 IG/IID/key/token →
    //      POST /ttranslatev3 form {fromLang,text,token,key,to} → [0].translations[0].text）----
    private fun bing(text: String, target: String): String? {
        val lang = when (target) {
            "zh", "zh-CN" -> "zh-Hans"
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

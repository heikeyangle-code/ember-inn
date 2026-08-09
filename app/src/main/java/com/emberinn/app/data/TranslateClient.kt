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
import java.util.concurrent.TimeUnit

/**
 * 翻译执行层（P1-6）：对齐官方 translate 扩展的提供商协议。
 * 已实现：LibreTranslate / DeepL / DeepLX（POST + JSON/表单，api_key 头或字段）；
 * 未实现（登记）：Google/Lingva/Bing/OneRing/Yandex（公开端点变更多，待补）。
 */
class TranslateClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

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
                "deepl" -> deepl(text, target, apiKey, url)
                "deeplx" -> deeplx(text, target, apiKey, url)
                else -> null
            }
        }.getOrNull()
    }

    private fun libre(text: String, target: String, apiKey: String, url: String): String? {
        val endpoint = url.ifBlank { "https://libretranslate.com/translate" }
        val body = FormBody.Builder()
            .add("q", text)
            .add("source", "auto")
            .add("target", target)
            .apply { if (apiKey.isNotBlank()) add("api_key", apiKey) }
            .build()
        val request = Request.Builder().url(endpoint).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("translatedText").ifBlank { null }
        }
    }

    private fun deepl(text: String, target: String, apiKey: String, url: String): String? {
        val endpoint = url.ifBlank { "https://api-free.deepl.com/v2/translate" }
        val payload = JSONObject()
            .put("text", JSONArray().put(text))
            .put("target_lang", target.uppercase())
            .toString()
        val builder = Request.Builder().url(endpoint)
            .post(payload.toRequestBody(jsonMedia))
        if (apiKey.isNotBlank()) builder.header("Authorization", "DeepL-Auth-Key $apiKey")
        val request = builder.build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("translations") ?: return null
            return arr.optJSONObject(0)?.optString("text")?.ifBlank { null }
        }
    }

    private fun deeplx(text: String, target: String, apiKey: String, url: String): String? {
        if (url.isBlank()) return null
        val payload = JSONObject()
            .put("text", text)
            .put("source_lang", "auto")
            .put("target_lang", target.uppercase())
            .toString()
        val builder = Request.Builder().url(url).post(payload.toRequestBody(jsonMedia))
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        val request = builder.build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string().orEmpty()).optString("data").ifBlank { null }
        }
    }
}

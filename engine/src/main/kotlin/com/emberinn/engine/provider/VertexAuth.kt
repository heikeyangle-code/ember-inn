package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * 官方 src/endpoints/google.js getVertexAIAuth / generateJWTToken / getAccessToken /
 * getProjectIdFromServiceAccount / getGoogleApiConfig 的 App 移植（Vertex AI Full/Express 认证）。
 */
object VertexAuth {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient()

    @Volatile
    private var cachedToken: Pair<String, Long>? = null // access_token, expiresAtMillis

    fun serviceAccount(jsonText: String): JsonObject? =
        runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()

    /** 官方 onVertexAIValidateServiceAccount：必填字段 + type=service_account。 */
    fun validate(jsonText: String): String? {
        val sa = serviceAccount(jsonText) ?: return "Invalid JSON format"
        val required = listOf("type", "project_id", "private_key", "client_email", "client_id")
        val missing = required.filter { sa[it]?.jsonPrimitive?.contentOrNull.isNullOrBlank() }
        if (missing.isNotEmpty()) return "Missing required fields: ${missing.joinToString(", ")}"
        if (sa["type"]?.jsonPrimitive?.content != "service_account") return "Invalid service account type. Expected \"service_account\""
        return null
    }

    /** 官方 generateJWTToken：RS256，scope cloud-platform，aud oauth2 token，1 小时过期。 */
    fun jwt(sa: JsonObject): String = jwt(sa, System.currentTimeMillis() / 1000)

    /** 差分用：注入固定时间戳（官方 Date.now() 冻结后逐字对拍）。 */
    fun jwt(sa: JsonObject, nowEpochSec: Long): String {
        val now = nowEpochSec
        val header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}")
        val payload = b64(
            "{\"iss\":\"${sa["client_email"]?.jsonPrimitive?.content.orEmpty()}\"," +
                "\"scope\":\"https://www.googleapis.com/auth/cloud-platform\"," +
                "\"aud\":\"https://oauth2.googleapis.com/token\"," +
                "\"iat\":$now,\"exp\":${now + 3600}}",
        )
        val input = "$header.$payload"
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey(sa))
            update(input.toByteArray(Charsets.UTF_8))
        }
        return "$input.${b64(sig.sign())}"
    }

    /** 官方 getAccessToken：jwt-bearer 换 access_token（1 小时缓存，到期前 1 分钟刷新）。 */
    fun accessToken(sa: JsonObject): String {
        val cached = cachedToken
        if (cached != null && cached.second > System.currentTimeMillis() + 60_000) return cached.first
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt(sa))
            .build()
        val request = Request.Builder().url("https://oauth2.googleapis.com/token").post(form).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Failed to get access token: HTTP ${resp.code}")
            val body = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val token = body["access_token"]?.jsonPrimitive?.content ?: error("access_token missing")
            cachedToken = token to (System.currentTimeMillis() + 3600_000)
            return token
        }
    }

    /** 官方 getProjectIdFromServiceAccount。 */
    fun projectId(sa: JsonObject): String =
        sa["project_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Project ID not found in service account JSON")

    /** 官方 getGoogleApiConfig / chat-completions.js Vertex URL（global 与 region 前缀分支一致）。 */
    fun url(region: String, model: String, projectId: String?, endpoint: String): String {
        val base = if (region == "global") {
            "https://aiplatform.googleapis.com/v1"
        } else {
            "https://$region-aiplatform.googleapis.com/v1"
        }
        return if (projectId.isNullOrBlank()) {
            "$base/publishers/google/models/${model}:$endpoint"
        } else {
            "$base/projects/$projectId/locations/$region/publishers/google/models/${model}:$endpoint"
        }
    }

    /** 官方 getVertexAIAuth + getGoogleApiConfig / chat-completions.js 的 URL+请求头（差分对拍用）。 */
    data class VertexRequest(val url: String, val headers: Map<String, String>)

    fun requestUrlAndHeaders(
        authMode: String,
        region: String,
        model: String,
        projectId: String?,
        reverseProxy: String,
        proxyPassword: String,
        apiKey: String,
        endpoint: String,
        accessToken: String? = null,
    ): VertexRequest {
        val encoded = model // 调用方已 URLEncoder.encode；这里保持原样便于对拍
        val proxy = reverseProxy.trim()
        return if (proxy.isNotEmpty()) {
            val url = proxy.trimEnd('/') + "/v1/publishers/google/models/" + encoded + ":" + endpoint
            VertexRequest(url, mapOf("Authorization" to "Bearer $proxyPassword"))
        } else if (authMode == "full") {
            val url = url(region, encoded, projectId, endpoint)
            val headers = if (accessToken != null) mapOf("Authorization" to "Bearer $accessToken") else emptyMap()
            VertexRequest(url, headers)
        } else {
            val url = url(region, encoded, projectId, endpoint)
            val headers = if (apiKey.isNotBlank()) mapOf("x-goog-api-key" to apiKey) else emptyMap()
            VertexRequest(url, headers)
        }
    }

    private fun privateKey(sa: JsonObject): PrivateKey {
        val pem = sa["private_key"]?.jsonPrimitive?.content.orEmpty()
        val body = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(body)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun b64(s: String): String = b64(s.toByteArray(Charsets.UTF_8))
}

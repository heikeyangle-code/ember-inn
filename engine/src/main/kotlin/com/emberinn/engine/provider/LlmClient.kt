package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 连接档案（提供商 + Key + 模型 + 采样参数 + 区域/账户扩展）。 */
@Serializable
data class ConnectionProfile(
    val id: String = "",
    val name: String = "",
    val providerId: String,
    val apiKey: String = "",
    val baseUrlOverride: String = "",
    val model: String = "",
    val sampler: SamplerParams = SamplerParams(),
    val region: String = "",
    val accountId: String = "",
    val apiVersionOverride: String = "",
)

/** 提供商连接存储（JSON 文件，多档案：profiles.json，旧单档案 connection.json 自动迁移）。 */
class ProviderStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(dir, "profiles.json")
    private val legacyFile = File(dir, "connection.json")

    @Serializable
    data class ProfileList(
        val activeId: String = "",
        val profiles: List<ConnectionProfile> = emptyList(),
    )

    fun load(): ConnectionProfile? {
        val list = loadList()
        return list.profiles.firstOrNull { it.id == list.activeId }
            ?: list.profiles.firstOrNull()
    }

    fun profiles(): List<ConnectionProfile> = loadList().profiles

    fun save(profile: ConnectionProfile) = save(profile, active = true)

    fun save(profile: ConnectionProfile, active: Boolean) {
        dir.mkdirs()
        val list = loadList()
        val normalized = if (profile.id.isBlank()) {
            profile.copy(id = "p-" + System.nanoTime().toString(36))
        } else profile
        val others = list.profiles.filterNot { it.id == normalized.id }
        val updated = others + normalized
        val activeId = if (active) normalized.id else (list.activeId.ifBlank { updated.firstOrNull()?.id ?: "" })
        file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = activeId, profiles = updated)))
    }

    fun setActive(id: String) {
        val list = loadList()
        if (list.profiles.any { it.id == id }) {
            file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = id, profiles = list.profiles)))
        }
    }

    fun delete(id: String) {
        val list = loadList()
        val updated = list.profiles.filterNot { it.id == id }
        file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = if (list.activeId == id) updated.firstOrNull()?.id ?: "" else list.activeId, profiles = updated)))
    }

    private fun loadList(): ProfileList {
        if (!file.exists() && legacyFile.exists()) {
            runCatching {
                val legacy = json.decodeFromString<ConnectionProfile>(legacyFile.readText())
                val withId = if (legacy.id.isBlank()) legacy.copy(id = legacy.providerId) else legacy
                file.writeText(json.encodeToString(ProfileList.serializer(), ProfileList(activeId = withId.id, profiles = listOf(withId))))
            }
        }
        return if (file.exists()) runCatching {
            json.decodeFromString(ProfileList.serializer(), file.readText())
        }.getOrNull() ?: ProfileList() else ProfileList()
    }
}

/** LLM 客户端：OpenAI 兼容 / Anthropic / Gemini 三个协议，非流式 + SSE 流式。 */
class LlmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 非流式对话：返回纯文本回复（按协议解析官方响应体）。 */
    fun chatCompletions(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
    ): String {
        val request = buildRequest(provider, profile, messages, stream = false)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val text = response.body?.string() ?: return ""
            return parseChatResponse(provider.protocol, text)
        }
    }

    /** 拉取模型列表（对齐官方 status/models 逻辑；解析失败返回空，由 UI 兜底 default_models）。 */
    fun models(
        provider: ProviderSpec,
        profile: ConnectionProfile,
    ): List<String> {
        val url = modelsUrl(provider, profile) ?: return emptyList()
        val builder = Request.Builder().url(url).get()
        applyAuth(builder, provider, profile, anthropicVersion = true)
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val text = response.body?.string() ?: return emptyList()
            return parseModels(provider, text)
        }
    }

    /** SSE 流式：按协议解析增量；流结束（含服务端直接关闭）保证回调 onDone。 */
    fun streamChatCompletions(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
    ) {
        val request = buildRequest(provider, profile, messages, stream = true)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val source = response.body?.source() ?: return
            val sb = StringBuilder()
            var finished = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                sb.append(line).append('\n')
                if (line.isEmpty()) {
                    for (chunk in SseParser.parse(sb.toString(), provider.protocol)) {
                        if (chunk.done) {
                            finished = true
                            onDone()
                        } else if (chunk.content.isNotEmpty()) {
                            onDelta(chunk.content)
                        }
                    }
                    sb.setLength(0)
                }
            }
            if (!finished) onDone()
        }
    }

    private fun buildRequest(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        stream: Boolean,
    ): Request {
        val base = resolveBase(provider, profile)
        val builder = Request.Builder()
        when (provider.protocol) {
            "anthropic" -> {
                val url = base.trimEnd('/') + "/messages"
                val request = AnthropicRequestBuilder.build(
                    model = profile.model,
                    messages = messages,
                    maxTokens = profile.sampler.maxTokens,
                    temperature = profile.sampler.temperature,
                    stream = stream,
                    topP = profile.sampler.topP,
                )
                builder.url(url).post(request.body.toRequestBody("application/json".toMediaType()))
                builder.header("x-api-key", profile.apiKey)
                builder.header("anthropic-version", "2023-06-01")
                if (request.betaHeaders.isNotEmpty()) {
                    builder.header("anthropic-beta", request.betaHeaders.joinToString(","))
                }
            }
            "google" -> {
                val apiVersion = provider.apiVersion.ifBlank { "v1beta" }
                val model = URLEncoder.encode(profile.model, "UTF-8")
                val params = mutableListOf<String>()
                if (provider.authType == "google-key" && profile.apiKey.isNotEmpty()) {
                    params += "key=" + URLEncoder.encode(profile.apiKey, "UTF-8")
                }
                if (stream) params += "alt=sse"
                val url = base.trimEnd('/') + "/" + apiVersion + "/models/" + model + ":generateContent" +
                    (if (params.isNotEmpty()) "?" + params.joinToString("&") else "")
                val body = GoogleRequestBuilder.build(
                    model = profile.model,
                    messages = messages,
                    maxOutputTokens = profile.sampler.maxTokens,
                    temperature = profile.sampler.temperature,
                    topP = profile.sampler.topP,
                )
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
            }
            else -> {
                if (provider.id == "vertexai") {
                    error("Vertex AI 需要服务账号与项目配置，请使用 Gemini (AI Studio) 或自定义地址。")
                }
                val url = when (provider.id) {
                    "azure" -> {
                        val apiVersion = profile.apiVersionOverride.ifBlank { provider.apiVersion }.ifBlank { "2024-12-01" }
                        base.trimEnd('/') + "/openai/deployments/" +
                            URLEncoder.encode(profile.model, "UTF-8") +
                            "/chat/completions?api-version=" + apiVersion
                    }
                    "workers-ai" -> {
                        val account = profile.accountId.trim()
                        if (account.isEmpty()) {
                            error("Cloudflare Workers AI 需要账户 ID。")
                        }
                        base.trimEnd('/') + "/" + account + "/ai/v1/chat/completions"
                    }
                    else -> base.trimEnd('/') + "/chat/completions"
                }
                val body = ChatRequestBuilder.buildOpenAiCompatible(
                    model = profile.model,
                    messages = messages,
                    params = profile.sampler.copy(stream = stream),
                )
                builder.url(url).post(body.toRequestBody("application/json".toMediaType()))
                applyAuth(builder, provider, profile, anthropicVersion = false)
            }
        }
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    private fun resolveBase(provider: ProviderSpec, profile: ConnectionProfile): String {
        if (profile.baseUrlOverride.isNotBlank()) return profile.baseUrlOverride.trimEnd('/')
        if (profile.region.isNotBlank()) {
            provider.regionBases[profile.region]?.let { return it.trimEnd('/') }
        }
        return provider.baseUrl
    }

    private fun applyAuth(
        builder: Request.Builder,
        provider: ProviderSpec,
        profile: ConnectionProfile,
        anthropicVersion: Boolean,
    ) {
        val key = profile.apiKey.trim()
        if (key.isEmpty()) return
        when (provider.authType) {
            "x-api-key" -> {
                builder.header("x-api-key", key)
                if (anthropicVersion && provider.protocol == "anthropic") {
                    builder.header("anthropic-version", "2023-06-01")
                }
            }
            "api-key" -> builder.header("api-key", key)
            // google-key 走 URL 查询参数；none 无认证
            "google-key", "none" -> Unit
            else -> builder.header("Authorization", "Bearer $key")
        }
    }

    private fun modelsUrl(provider: ProviderSpec, profile: ConnectionProfile): String? {
        val base = resolveBase(provider, profile)
        if (base.isBlank()) return null
        return when (provider.id) {
            "azure" -> {
                val apiVersion = profile.apiVersionOverride.ifBlank { provider.apiVersion }.ifBlank { "2024-12-01" }
                base.trimEnd('/') + "/openai/models?api-version=" + apiVersion
            }
            "workers-ai" -> {
                val account = profile.accountId.trim()
                if (account.isEmpty()) return null
                base.trimEnd('/') + "/" + account + "/ai/models/search?task=Text+Generation&per_page=1000"
            }
            "google" -> {
                val apiVersion = provider.apiVersion.ifBlank { "v1beta" }
                val key = if (provider.authType == "google-key" && profile.apiKey.isNotEmpty()) {
                    "?key=" + URLEncoder.encode(profile.apiKey, "UTF-8")
                } else ""
                base.trimEnd('/') + "/" + apiVersion + "/models" + key
            }
            "vertexai" -> null
            else -> {
                val endpoint = provider.modelsEndpoint
                if (endpoint.isBlank()) return null
                val query = provider.modelsQuery.entries.joinToString("&") { (k, v) ->
                    k + "=" + URLEncoder.encode(v, "UTF-8")
                }
                base.trimEnd('/') + "/" + endpoint.trimStart('/') + (if (query.isNotEmpty()) "?" + query else "")
            }
        }
    }

    private fun parseChatResponse(protocol: String, text: String): String = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        when (protocol) {
            "anthropic" -> root["content"]?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.asText() }
                ?.joinToString("").orEmpty()
            "google" -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.asText() }
                ?.joinToString("").orEmpty()
            else -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.asText().orEmpty()
        }
    }.getOrDefault("")

    private fun parseModels(provider: ProviderSpec, text: String): List<String> = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        when (provider.modelsFormat) {
            "google" -> root["models"]?.jsonArray?.mapNotNull { m ->
                val obj = m.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val methods = obj["supportedGenerationMethods"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                if (methods != null && "generateContent" !in methods) null else name.removePrefix("models/")
            }.orEmpty()
            "workers" -> root["result"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }.orEmpty()
            "azure" -> {
                val value = root["value"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }.orEmpty()
                if (value.isNotEmpty()) {
                    value
                } else {
                    root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.asText() }.orEmpty()
                }
            }
            else -> root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.asText() }.orEmpty()
        }
    }.getOrDefault(emptyList())

    private fun JsonElement?.asText(): String? = when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> null
    }
}

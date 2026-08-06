package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 连接档案（提供商 + Key + 模型 + 采样参数）。 */
@Serializable
data class ConnectionProfile(
    val providerId: String,
    val apiKey: String = "",
    val baseUrlOverride: String = "",
    val model: String = "",
    val sampler: SamplerParams = SamplerParams(),
)

/** 提供商连接存储（JSON 文件，对齐 App 的连接档案概念）。 */
class ProviderStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(dir, "connection.json")

    fun save(profile: ConnectionProfile) {
        dir.mkdirs()
        file.writeText(json.encodeToString(ConnectionProfile.serializer(), profile))
    }

    fun load(): ConnectionProfile? =
        if (file.exists()) runCatching {
            json.decodeFromString(ConnectionProfile.serializer(), file.readText())
        }.getOrNull() else null
}

/** LLM 客户端：OpenAI 兼容协议（非流式 + SSE 流式）。 */
class LlmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

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
            return response.body?.string() ?: ""
        }
    }

    /** SSE 流式：onDelta 收增量，onDone 在 [DONE] 时回调。 */
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
            while (true) {
                val line = source.readUtf8Line() ?: break
                sb.append(line).append('\n')
                if (line.isEmpty()) {
                    for (chunk in SseParser.parse(sb.toString())) {
                        if (chunk.done) {
                            onDone()
                        } else if (chunk.content.isNotEmpty()) {
                            onDelta(chunk.content)
                        }
                    }
                    sb.setLength(0)
                }
            }
        }
    }

    private fun buildRequest(
        provider: ProviderSpec,
        profile: ConnectionProfile,
        messages: List<CompletionMessage>,
        stream: Boolean,
    ): Request {
        val base = profile.baseUrlOverride.ifBlank { provider.baseUrl }
        val url = base.trimEnd('/') + "/chat/completions"
        val body = ChatRequestBuilder.buildOpenAiCompatible(
            model = profile.model,
            messages = messages,
            params = profile.sampler.copy(stream = stream),
        )
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        if (profile.apiKey.isNotEmpty()) {
            if (provider.authType == "x-api-key") {
                builder.header("x-api-key", profile.apiKey)
            } else {
                builder.header("Authorization", "Bearer ${profile.apiKey}")
            }
        }
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }
}

package com.emberinn.engine.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** 服务商注册表条目（数据驱动，README 供应商表）。 */
@Serializable
data class ProviderSpec(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val description: String = "",
    val icon: String = "",
    val protocol: String,
    @SerialName("auth_type")
    val authType: String = "bearer",
    @SerialName("base_url")
    val baseUrl: String,
    @SerialName("region_variants")
    val regionVariants: List<String> = emptyList(),
    @SerialName("region_bases")
    val regionBases: Map<String, String> = emptyMap(),
    @SerialName("extra_headers")
    val extraHeaders: Map<String, String> = emptyMap(),
    @SerialName("api_version")
    val apiVersion: String = "",
    @SerialName("models_endpoint")
    val modelsEndpoint: String = "models",
    /** 模型列表响应格式：openai(data[].id) / google(models[].name) / workers(result[].name) / azure(value[].id)。 */
    @SerialName("models_format")
    val modelsFormat: String = "openai",
    @SerialName("models_query")
    val modelsQuery: Map<String, String> = emptyMap(),
    @SerialName("default_models")
    val defaultModels: List<String> = emptyList(),
    /** 建议的最大回复 tokens（推理模型思考会占额度，512 太小正文常被掐空）；用户可改。 */
    @SerialName("default_max_tokens")
    val defaultMaxTokens: Int? = null,
    /** 该服务商默认上下文窗口（tokens）；未知模型兜底，已知模型优先看 [modelContexts]。 */
    @SerialName("default_context_window")
    val defaultContextWindow: Int? = null,
    /** 已知模型 → 上下文窗口（tokens），用于“默认按模型”自动填胶囊分母。 */
    @SerialName("model_contexts")
    val modelContexts: Map<String, Int> = emptyMap(),
    @SerialName("requires_key")
    val requiresKey: Boolean = true,
    @SerialName("docs_url")
    val docsUrl: String = "",
)

object ProviderRegistry {

    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<ProviderSpec> {
        val resource = checkNotNull(ProviderRegistry::class.java.getResource("/providers/providers.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        return root.getValue("providers").jsonArray.map {
            json.decodeFromJsonElement(ProviderSpec.serializer(), it)
        }
    }

    fun get(id: String): ProviderSpec? = all().firstOrNull { it.id == id }
}

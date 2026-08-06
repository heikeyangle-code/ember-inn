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
    val protocol: String,
    @SerialName("auth_type")
    val authType: String = "bearer",
    @SerialName("base_url")
    val baseUrl: String,
    @SerialName("region_variants")
    val regionVariants: List<String> = emptyList(),
    @SerialName("extra_headers")
    val extraHeaders: Map<String, String> = emptyMap(),
    @SerialName("api_version")
    val apiVersion: String = "",
    @SerialName("models_endpoint")
    val modelsEndpoint: String = "v1/models",
    @SerialName("default_models")
    val defaultModels: List<String> = emptyList(),
    @SerialName("requires_key")
    val requiresKey: Boolean = true,
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

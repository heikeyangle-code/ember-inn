package com.emberinn.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 官方 openai.js proxies：命名代理预设（name/url/password），App 按连接档案侧存。 */
@Serializable
data class ProxyPreset(
    val name: String,
    val url: String = "",
    val password: String = "",
)

object ProxyPresetStore {

    private const val NAME = "ember_proxy_presets"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun list(context: Context, profileId: String): List<ProxyPreset> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("proxies_$profileId", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ProxyPreset.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, profileId: String, presets: List<ProxyPreset>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("proxies_$profileId", json.encodeToString(ListSerializer(ProxyPreset.serializer()), presets))
            .apply()
    }
}

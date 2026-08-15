package com.emberinn.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 官方 openai.js proxies：命名代理预设（name/url/password），全局一份（对齐官方 settings.proxies）。 */
@Serializable
data class ProxyPreset(
    val name: String,
    val url: String = "",
    val password: String = "",
)

object ProxyPresetStore {

    private const val NAME = "ember_proxy_presets"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** 官方 settings.proxies（全局）；旧版按档案侧存的数据首次读取时合并迁移。 */
    fun list(context: Context): List<ProxyPreset> {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("proxies", null)
        val global = raw?.let { runCatching {
            json.decodeFromString(ListSerializer(ProxyPreset.serializer()), it)
        }.getOrNull() } ?: emptyList()
        if (raw == null) {
            // 迁移：旧 per-profile 键 → 全局列表（按名字去重）
            val legacy = prefs.all.entries
                .filter { it.key.startsWith("proxies_") }
                .mapNotNull { (_, v) ->
                    (v as? String)?.let { runCatching {
                        json.decodeFromString(ListSerializer(ProxyPreset.serializer()), it)
                    }.getOrNull() }
                }
                .flatten()
            val merged = LinkedHashMap<String, ProxyPreset>()
            legacy.forEach { merged[it.name] = it }
            global.forEach { merged[it.name] = it }
            if (merged.isNotEmpty()) {
                save(context, merged.values.toList())
                legacy.forEach { prefs.edit().remove("proxies_${it.name}").apply() }
            }
            return merged.values.toList()
        }
        return global
    }

    fun save(context: Context, presets: List<ProxyPreset>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("proxies", json.encodeToString(ListSerializer(ProxyPreset.serializer()), presets))
            .apply()
    }
}

package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.CfgPromptEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * CFG Scale 配置存储（官方 extension_settings.cfg）：全局 + 按角色。
 * 会话级存 chat_metadata.cfg_*（ChatStore.metadata），角色级按角色 id 存 SharedPreferences
 * （官方按角色文件名存 extension_settings.cfg.chara，语义一致）。
 */
object CfgPrefs {

    private const val NAME = "ember_cfg"
    private const val KEY_GLOBAL = "global"
    private const val KEY_CHARA = "chara_map"

    private val json = Json { ignoreUnknownKeys = true }

    fun global(context: Context): CfgPromptEngine.CfgGlobal {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return CfgPromptEngine.CfgGlobal(
            guidanceScale = p.getFloat("global_guidance_scale", 1f).toDouble(),
            negativePrompt = p.getString("global_negative_prompt", "") ?: "",
            positivePrompt = p.getString("global_positive_prompt", "") ?: "",
        )
    }

    fun saveGlobal(context: Context, g: CfgPromptEngine.CfgGlobal) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("global_guidance_scale", g.guidanceScale.toFloat())
            .putString("global_negative_prompt", g.negativePrompt)
            .putString("global_positive_prompt", g.positivePrompt)
            .apply()
    }

    fun chara(context: Context, characterId: String?): CfgPromptEngine.CfgChara? {
        if (characterId.isNullOrBlank()) return null
        return charaMap(context)[characterId]
    }

    fun charaMap(context: Context): Map<String, CfgPromptEngine.CfgChara> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_CHARA, null) ?: return emptyMap()
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            root.mapNotNull { (id, v) ->
                val o = v.jsonObject
                id to CfgPromptEngine.CfgChara(
                    name = id,
                    guidanceScale = o["guidance_scale"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                    negativePrompt = o["negative_prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                    positivePrompt = o["positive_prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun saveChara(context: Context, c: CfgPromptEngine.CfgChara) {
        val next = charaMap(context).toMutableMap()
        if (c.guidanceScale == null && c.negativePrompt.isBlank() && c.positivePrompt.isBlank()) {
            next.remove(c.name)
        } else {
            next[c.name] = c
        }
        val obj = JsonObject(
            next.mapValues { (_, v) ->
                buildJsonObject {
                    v.guidanceScale?.let { put("guidance_scale", JsonPrimitive(it)) }
                    put("negative_prompt", JsonPrimitive(v.negativePrompt))
                    put("positive_prompt", JsonPrimitive(v.positivePrompt))
                }
            },
        )
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CHARA, json.encodeToString(JsonObject.serializer(), obj))
            .apply()
    }
}

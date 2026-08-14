package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptOrderEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Prompt Manager 存储：全局提示项 + 全局顺序（官方 serviceSettings.prompts / prompt_order 语义；
 * 每角色顺序为后续扩展，当前先存全局默认顺序，登记）。
 */
object PromptManagerPrefs {

    private const val NAME = "ember_prompt_manager"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun prompts(context: Context): List<PromptItem> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("prompts", null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(PromptItem.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun savePrompts(context: Context, items: List<PromptItem>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("prompts", json.encodeToString(ListSerializer(PromptItem.serializer()), items))
            .apply()
    }

    /** 官方 prompt_order 按角色 id 存储（"null" 键 = 全局）。 */
    fun orders(context: Context): Map<String, List<PromptOrderEntry>> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("orders", null)
            ?: return emptyMap()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.MapSerializer(String.serializer(), ListSerializer(PromptOrderEntry.serializer())),
                raw,
            )
        }.getOrDefault(emptyMap())
    }

    fun order(context: Context, characterId: String? = null): List<PromptOrderEntry> =
        orders(context)[characterId ?: "null"] ?: emptyList()

    fun saveOrder(context: Context, characterId: String?, order: List<PromptOrderEntry>) {
        val key = characterId ?: "null"
        val all = orders(context).toMutableMap()
        if (order.isEmpty()) all.remove(key) else all[key] = order
        saveOrders(context, all)
    }

    /** 官方 oai_settings.prompt_order = preset.prompt_order：整表替换（按角色 id，null=全局）。 */
    fun saveOrders(context: Context, all: Map<String, List<PromptOrderEntry>>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(
                "orders",
                json.encodeToString(
                    kotlinx.serialization.builtins.MapSerializer(String.serializer(), ListSerializer(PromptOrderEntry.serializer())),
                    all,
                ),
            )
            .apply()
    }
}

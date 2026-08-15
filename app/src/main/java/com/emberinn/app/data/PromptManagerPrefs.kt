package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptOrderEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Prompt Manager 存储：全局提示项 + 全局顺序（官方 serviceSettings.prompts / prompt_order 语义）。
 * 官方 1.18 PromptManager 是 global 策略：所有聊天共用 character_id=100000（dummyId）的那份顺序
 * （PromptManager.js configuration.promptOrder），因此 App 也统一存/读该键，不再按角色分叉。
 */
object PromptManagerPrefs {

    private const val NAME = "ember_prompt_manager"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** 官方 PromptManager dummyId=100000（global 策略唯一顺序键）。 */
    const val GLOBAL_CHARACTER_ID = "100000"

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

    /** 官方 prompt_order 按角色 id 存储；global 策略下 App 只使用 GLOBAL_CHARACTER_ID。 */
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

    /** 官方 getPromptOrderForCharacter：global 策略下总是取 dummyId=100000 那份；
     *  旧版本存的 "null" 键作为兼容回退。 */
    fun order(context: Context, characterId: String? = null): List<PromptOrderEntry> {
        val all = orders(context)
        return all[characterId ?: GLOBAL_CHARACTER_ID] ?: all["null"] ?: emptyList()
    }

    fun saveOrder(context: Context, characterId: String?, order: List<PromptOrderEntry>) {
        val key = characterId ?: GLOBAL_CHARACTER_ID
        val all = orders(context).toMutableMap()
        if (order.isEmpty()) all.remove(key) else all[key] = order
        saveOrders(context, all)
    }

    /** 官方 serviceSettings.prompt_order = 整表替换（{character_id, order} 列表）。 */
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

    /** 官方 handleCharacterReset：恢复默认顺序（移除后重建 promptManagerDefaultPromptOrder）。 */
    fun resetOrderToDefault(context: Context) {
        val all = orders(context).toMutableMap()
        all[GLOBAL_CHARACTER_ID] = com.emberinn.engine.prompt.PromptManagerCore.DEFAULT_ORDER_ENTRIES
        saveOrders(context, all)
    }
}

package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptOrderEntry
import kotlinx.serialization.builtins.ListSerializer
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

    fun order(context: Context): List<PromptOrderEntry> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("order", null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(PromptOrderEntry.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun saveOrder(context: Context, order: List<PromptOrderEntry>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("order", json.encodeToString(ListSerializer(PromptOrderEntry.serializer()), order))
            .apply()
    }
}

package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.ImageGenPromptEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 图像生成 prompt templates 存储（对齐官方 stable-diffusion 扩展 extension_settings.sd.prompts）。
 *
 * 官方对照（SillyTavern/public/scripts/extensions/stable-diffusion/index.js）：
 *   - defaultSettings.prompts = promptTemplates（13 个 generationMode 模板）
 *   - loadSettings：只插入缺失键（官方 `prompts[key] === undefined` 才补默认，用户已改的保留）
 *   - addPromptTemplates：按模式号排序渲染，编辑写回 prompts[name]，Restore default 重置为默认
 *
 * App 落地：sd_prompts 存 JSON 对象（键=模式数字字符串，值=模板文本）。
 * 读取时合并引擎默认 [ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES]，缺失键补默认（官方语义）。
 */
class PromptTemplateStore(context: Context) {

    private val prefs = context.getSharedPreferences("ember_services", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    /** 合并默认后的全部模板（键=模式数字字符串）。 */
    fun templates(): Map<String, String> {
        val merged = ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES.toMutableMap()
        val raw = prefs.getString("sd_prompts", null) ?: return merged
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            for ((k, v) in obj) {
                v.jsonPrimitive.contentOrNull?.let { merged[k] = it }
            }
        }
        return merged
    }

    fun get(key: String): String = templates()[key] ?: ""

    /** 保存指定模板（写回 sd_prompts；键值同官方 prompts[name]）。 */
    fun set(key: String, text: String) {
        val merged = templates().toMutableMap()
        merged[key] = text
        write(merged)
    }

    /** 重置指定模板为官方默认（官方 Restore default 按钮语义）。 */
    fun restore(key: String) {
        val merged = templates().toMutableMap()
        val def = ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES[key]
        if (def != null) merged[key] = def
        write(merged)
    }

    private fun write(map: Map<String, String>) {
        val obj = buildJsonObject {
            for ((k, v) in map) put(k, JsonPrimitive(v))
        }
        prefs.edit().putString("sd_prompts", obj.toString()).apply()
    }
}

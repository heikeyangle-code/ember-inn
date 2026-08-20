package com.emberinn.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 图像生成样式库（对齐官方 stable-diffusion 扩展 extension_settings.sd.styles）。
 *
 * 官方对照（SillyTavern/public/scripts/extensions/stable-diffusion/index.js）：
 *   - styles = [{ name, prefix, negative }]（onStyleSelect L675 / onSaveStyleClick L726 /
 *     onRenameStyleClick L764 / onDeleteStyleClick L690）
 *   - 选中样式 → 写 sd_prompt_prefix / sd_negative_prompt 字段并存 sd.style（App 端经
 *     ServicesPrefs.saveImageAdvanced 落盘）
 *   - 保存 = 用当前 prefix/negative 新建或覆盖同名样式；重命名 / 删除同官方语义
 *     （删除后切到首个剩余样式或清空）。
 *
 * App 落地：styles 存 ember_services.sd_styles（JSON 数组），活动样式名存 sd_style。
 */
class StyleStore(context: Context) {

    private val prefs = context.getSharedPreferences("ember_services", Context.MODE_PRIVATE)

    data class Style(val name: String, val prefix: String, val negative: String)

    fun styles(): List<Style> {
        val raw = prefs.getString("sd_styles", null) ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                Style(
                    name = name,
                    prefix = o["prefix"]?.jsonPrimitive?.contentOrNull ?: "",
                    negative = o["negative"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
        }.getOrDefault(emptyList())
    }

    fun active(): String = prefs.getString("sd_style", "") ?: ""

    fun setActive(name: String) {
        prefs.edit().putString("sd_style", name).apply()
    }

    /** 保存（新建或覆盖同名样式），并把活动样式设为 name。 */
    fun save(name: String, prefix: String, negative: String) {
        if (name.isBlank()) return
        val list = styles().toMutableList()
        val style = Style(name, prefix, negative)
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) list[idx] = style else list.add(style)
        write(list)
        setActive(name)
    }

    /** 重命名；新名空/重复/未变返回 false。 */
    fun rename(oldName: String, newName: String): Boolean {
        val name = newName.trim()
        if (name.isBlank() || name == oldName) return false
        val list = styles().toMutableList()
        val idx = list.indexOfFirst { it.name == oldName }
        if (idx < 0 || list.any { it.name == name }) return false
        list[idx] = list[idx].copy(name = name)
        write(list)
        if (active() == oldName) setActive(name)
        return true
    }

    /** 删除；删除活动样式时切到首个剩余或清空（官方 onDeleteStyleClick）。 */
    fun delete(name: String) {
        val list = styles().filterNot { it.name == name }
        write(list)
        if (active() == name) {
            setActive(list.firstOrNull()?.name ?: "")
        }
    }

    private fun write(list: List<Style>) {
        val arr = JsonArray(list.map { s ->
            buildJsonObject {
                put("name", JsonPrimitive(s.name))
                put("prefix", JsonPrimitive(s.prefix))
                put("negative", JsonPrimitive(s.negative))
            }
        })
        prefs.edit().putString("sd_styles", arr.toString()).apply()
    }
}

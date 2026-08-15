package com.emberinn.app.ui.settings

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * 用户预设存储（官方 preset 文件导入：data/default-user/content/presets/{type}/{name}.json）。
 * 类型目录：context / instruct / sampler / sysprompt / reasoning。
 */
object UserPresetStore {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(context: Context, type: String): File =
        File(context.filesDir, "presets/$type").apply { mkdirs() }

    fun list(context: Context, type: String): List<String> =
        dir(context, type).listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun load(context: Context, type: String, name: String): JsonObject? =
        runCatching {
            json.parseToJsonElement(File(dir(context, type), "$name.json").readText()).jsonObject
        }.getOrNull()

    fun save(context: Context, type: String, name: String, content: String): Boolean =
        runCatching {
            val parsed = json.parseToJsonElement(content).jsonObject
            val safeName = sanitizeFilename(name) ?: return false
            File(dir(context, type), "$safeName.json").writeText(json.encodeToString(JsonObject.serializer(), parsed))
            true
        }.getOrDefault(false)

    fun rename(context: Context, type: String, oldName: String, newName: String): Boolean {
        val safe = sanitizeFilename(newName) ?: return false
        if (safe == oldName) return true
        val from = File(dir(context, type), "$oldName.json")
        val to = File(dir(context, type), "$safe.json")
        if (!from.exists()) return false
        if (to.exists()) return false
        return runCatching { from.renameTo(to) }.getOrDefault(false)
    }

    /** 复制内置预设为同名用户预设（官方 rename 对默认预设等价于另存新名）。 */
    fun saveBuiltinAs(context: Context, type: String, content: JsonObject, name: String): Boolean =
        save(context, type, name, json.encodeToString(JsonObject.serializer(), content))

    fun delete(context: Context, type: String, name: String) {
        File(dir(context, type), "$name.json").delete()
    }

    /**
     * 对齐官方后端 sanitize-filename 默认行为（Unicode 保留）：
     * 剔除路径分隔符/保留字非法字符与控制字符，去首尾空格与尾部点号；空名返回 null。
     */
    fun sanitizeFilename(name: String): String? {
        val illegal = Regex("[<>:\"/\\|?*\u0000-\u001f]")
        var out = illegal.replace(name, "").trim()
        while (out.endsWith(".")) out = out.dropLast(1)
        out = out.trim()
        return out.ifBlank { null }
    }

    /**
     * 官方导入类型识别：先用 preset-manager.js performMasterImport 的 legacy 顺序
     * （instruct→context→sysprompt→preset(textgen)→reasoning，引擎差分锁定）；
     * 官方 openai 采样预设导入不校验字段（按文件名存），App 兜底 temperature+openai_max_tokens → sampler。
     * textgen（preset）暂按 sampler 存储，应用等 textgen 后端（HANDOFF 3.7 登记）。
     */
    fun detectType(obj: JsonObject): String? = when (com.emberinn.engine.prompt.PresetApplyEngine.detectLegacyImportType(obj)) {
        "instruct" -> "instruct"
        "context" -> "context"
        "sysprompt" -> "sysprompt"
        "preset" -> "sampler"
        "reasoning" -> "reasoning"
        else -> if (obj["temperature"] != null && obj["openai_max_tokens"] != null) "sampler" else null
    }
}

package com.emberinn.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.icons.PhosphorIcons
import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.PresetApplyEngine
import com.emberinn.engine.prompt.PresetLibrary
import com.emberinn.engine.prompt.ReasoningSettings
import com.emberinn.engine.prompt.SyspromptSettings
import com.emberinn.engine.provider.SamplerParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 预设管理器（官方 preset-manager.js + Advanced Formatting 抽屉全链）：
 * 五类预设选择即应用（引擎 PresetApplyEngine，官方差分锁定）+ 保存当前为预设 + 删除用户预设 +
 * 单预设文件导入（官方 legacy 识别顺序）+ 多区段主导入/导出（官方 masterSections）。
 * context/instruct/sysprompt 的运行时消费点是 textgen 后端（未实现，选择先保存，登记）；sampler 应用到提供商详情页；reasoning 进总装。
 */
@Composable
fun PresetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(PresetPrefsStore.load(context)) }
    var applied by remember { mutableStateOf(PresetSettingsStore.load(context)) }
    var userPresets by remember {
        mutableStateOf(
            mapOf(
                "context" to UserPresetStore.list(context, "context"),
                "instruct" to UserPresetStore.list(context, "instruct"),
                "sampler" to UserPresetStore.list(context, "sampler"),
                "sysprompt" to UserPresetStore.list(context, "sysprompt"),
                "reasoning" to UserPresetStore.list(context, "reasoning"),
            ),
        )
    }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var saveAsType by remember { mutableStateOf<String?>(null) }
    var saveAsName by remember { mutableStateOf("") }
    var masterImportSections by remember { mutableStateOf<JsonObject?>(null) }
    var masterImportChecked by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pendingSensitive by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    var pendingOverwrite by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    var expandedEditor by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        prefs = PresetPrefsStore.load(context)
        applied = PresetSettingsStore.load(context)
    }

    fun presetJson(type: String, name: String): JsonObject? =
        when (type) {
            "context" -> PresetLibrary.contextPresetsRaw().firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == name
            } ?: UserPresetStore.load(context, "context", name)
            "instruct" -> PresetLibrary.instructPresetsRaw().firstOrNull {
                it["name"]?.jsonPrimitive?.contentOrNull == name
            } ?: UserPresetStore.load(context, "instruct", name)
            "sampler" -> PresetLibrary.samplerPresets("openai").firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sampler", name)
            "sysprompt" -> PresetLibrary.systemPromptPresets().firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sysprompt", name)
            "reasoning" -> PresetLibrary.reasoningPresets().firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "reasoning", name)
            else -> null
        }

    fun apply(type: String, name: String) {
        val preset = presetJson(type, name) ?: return
        when (type) {
            "context" -> PresetSettingsStore.applyContext(context, preset)
            "instruct" -> PresetSettingsStore.applyInstruct(context, preset)
            "sysprompt" -> PresetSettingsStore.applySysprompt(context, preset)
            "reasoning" -> PresetSettingsStore.applyReasoning(context, preset)
            "sampler" -> {
                // 官方 onSettingsPresetChange：选中即应用到当前活动连接（bind_preset_to_connection 默认 true）。
                PresetSettingsStore.applySampler(context, name)
                prefs = prefs.copy(samplerPreset = name)
                PresetPrefsStore.save(context, prefs)
            }
        }
        refresh()
        importMessage = "已应用：$type / $name"
    }

    fun saveCurrent(type: String, name: String) {
        val json = Json { ignoreUnknownKeys = true }
        val state = PresetSettingsStore.load(context)
        val body: JsonObject? = when (type) {
            "context" -> {
                val settings = json.encodeToJsonElement(ContextSettings.serializer(), state.context).jsonObject.toMutableMap()
                settings["always_force_name2"] = JsonPrimitive(state.contextGlobals.alwaysForceName2)
                settings["trim_sentences"] = JsonPrimitive(state.contextGlobals.trimSentences)
                settings["single_line"] = JsonPrimitive(state.contextGlobals.singleLine)
                PresetApplyEngine.filterPresetSettings(
                    JsonObject(settings), "context", name, state.context.preset, true, 0, 0,
                )
            }
            "instruct" -> PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(InstructSettings.serializer(), state.instruct).jsonObject,
                "instruct", name, state.instruct.preset, true, 0, 0,
            )
            "sysprompt" -> PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(SyspromptSettings.serializer(), state.sysprompt).jsonObject,
                "sysprompt", name, state.sysprompt.name, true, 0, 0,
            )
            "reasoning" -> PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(ReasoningSettings.serializer(), state.reasoning).jsonObject,
                "reasoning", name, state.reasoning.name, true, 0, 0,
            )
            "sampler" -> {
                val profile = ChatRepository(context).profile() ?: run {
                    importMessage = "保存失败：未配置提供商"
                    return
                }
                PresetApplyEngine.getChatCompletionPresetBody(
                    PresetSettingsStore.samplerSettingsJson(profile.sampler, profile.contextWindow, profile.sampler.maxTokens),
                )
            }
            else -> null
        }
        if (body != null) {
            val ok = UserPresetStore.save(context, type, name, body.toString())
            importMessage = if (ok) "已保存预设：$type / $name" else "保存失败：文件名无效"
            userPresets = userPresets + (type to UserPresetStore.list(context, type))
        }
    }

    fun saveSamplerImport(name: String, content: JsonObject) {
        val ok = UserPresetStore.save(context, "sampler", name, content.toString())
        importMessage = if (ok) "已导入：sampler / $name" else "导入失败：文件名无效"
        userPresets = userPresets + ("sampler" to UserPresetStore.list(context, "sampler"))
    }

    fun proceedSamplerImport(name: String, content: JsonObject) {
        val exists = UserPresetStore.list(context, "sampler").contains(name) ||
            PresetLibrary.samplerPresets("openai").any { it.name == name }
        if (exists) {
            pendingOverwrite = name to content
        } else {
            saveSamplerImport(name, content)
        }
    }

    // 单预设文件导入（官方 performMasterImport legacy 顺序识别）
    val presetImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        val parsed = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject }.getOrNull()
        if (parsed == null) {
            importMessage = "导入失败：不是有效 JSON"
            return@rememberLauncherForActivityResult
        }
        val type = UserPresetStore.detectType(parsed)
        if (type == null) {
            importMessage = "导入失败：无法识别预设类型"
            return@rememberLauncherForActivityResult
        }
        val fileName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".json").orEmpty().ifBlank { "preset" }
        // 官方：openai 采样预设导入用文件名（onPresetImportFileChange）；
        // 通用 per-API 导入与 master legacy 用 data.name ?? 文件名。
        val name = if (type == "sampler") fileName
            else parsed["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fileName
        if (type == "sampler") {
            // 官方 onPresetImportFileChange：敏感字段确认剥离 → 同名覆盖确认
            val sensitive = PresetApplyEngine.detectSensitivePresetFields(parsed)
            if (sensitive.isNotEmpty()) {
                pendingSensitive = name to parsed
                return@rememberLauncherForActivityResult
            }
            proceedSamplerImport(name, parsed)
            return@rememberLauncherForActivityResult
        }
        val ok = UserPresetStore.save(context, type, name, text)
        importMessage = if (ok) "已导入：$type / $name" else "导入失败：文件名无效"
        userPresets = userPresets + (type to UserPresetStore.list(context, type))
    }


    // 多区段主导出（官方 af_master_export：instruct/context/sysprompt/reasoning/srw）
    val masterExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = masterExportBody(context)
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(sections.toString().toByteArray())
            }
            importMessage = "已导出多区段预设"
        }.onFailure { importMessage = "导出失败：${it.message}" }
    }

    // 多区段主导入
    val masterImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        val parsed = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject }.getOrNull()
        if (parsed == null) {
            importMessage = "导入失败：不是有效 JSON"
            return@rememberLauncherForActivityResult
        }
        val legacy = PresetApplyEngine.detectLegacyImportType(parsed)
        if (legacy != null) {
            val type = if (legacy == "preset") "sampler" else legacy
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".json").orEmpty().ifBlank { "preset" }
            val name = parsed["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fileName
            val ok = UserPresetStore.save(context, type, name, text)
            importMessage = if (ok) "已导入：$type / $name" else "导入失败：文件名无效"
            userPresets = userPresets + (type to UserPresetStore.list(context, type))
            return@rememberLauncherForActivityResult
        }
        val valid = PresetApplyEngine.masterSectionsValid(parsed)
        val validKeys = valid.filterValues { it is JsonPrimitive && it.content == "true" }.keys
        if (validKeys.isEmpty()) {
            importMessage = "导入失败：没有可识别的区段"
            return@rememberLauncherForActivityResult
        }
        masterImportSections = parsed
        masterImportChecked = validKeys.associateWith { true }
    }

    SettingsGlassPage { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsTopBar(title = "预设", onBack = onBack, sky = sky)
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(onClick = { presetImporter.launch(arrayOf("application/json")) }) { Text("导入预设") }
                TextButton(onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    masterExporter.launch("presets_$stamp.json")
                }) { Text("导出全部") }
                TextButton(onClick = { masterImporter.launch(arrayOf("application/json")) }) { Text("导入全部") }
                EmberSwitch(
                    checked = prefs.bindPresetToConnection,
                    onCheckedChange = {
                        prefs = prefs.copy(bindPresetToConnection = it)
                        PresetPrefsStore.save(context, prefs)
                    },
                )
                Text(
                    "绑定到连接",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                importMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            PresetSection(
                title = "上下文模板（context）",
                items = (PresetLibrary.contextPresets().map { it.preset } - userPresets["context"].orEmpty().toSet()).map { it to false } +
                    userPresets["context"].orEmpty().map { it to true },
                selected = prefs.contextPreset,
                onSelect = { apply("context", it) },
                onSaveCurrent = { saveAsType = "context"; saveAsName = applied.context.preset },
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "context", name)
                    userPresets = userPresets + ("context" to UserPresetStore.list(context, "context"))
                },
            )
            AppliedEditorToggle("context", expandedEditor) { expandedEditor = it }
            if (expandedEditor == "context") {
                AppliedPresetEditor("context", applied) { updated ->
                    PresetSettingsStore.update(context, updated)
                    applied = updated
                }
            }
            PresetSection(
                title = "指导模板（instruct）",
                items = (PresetLibrary.instructPresets().map { it.preset } - userPresets["instruct"].orEmpty().toSet()).map { it to false } +
                    userPresets["instruct"].orEmpty().map { it to true },
                selected = prefs.instructPreset,
                onSelect = { apply("instruct", it) },
                onSaveCurrent = { saveAsType = "instruct"; saveAsName = applied.instruct.preset },
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "instruct", name)
                    userPresets = userPresets + ("instruct" to UserPresetStore.list(context, "instruct"))
                },
            )
            AppliedEditorToggle("instruct", expandedEditor) { expandedEditor = it }
            if (expandedEditor == "instruct") {
                AppliedPresetEditor("instruct", applied) { updated ->
                    PresetSettingsStore.update(context, updated)
                    applied = updated
                }
            }
            PresetSection(
                title = "采样预设（OpenAI）",
                items = listOf("" to false) +
                    (PresetLibrary.samplerPresets("openai").map { it.name } - userPresets["sampler"].orEmpty().toSet()).map { it to false } +
                    userPresets["sampler"].orEmpty().map { it to true },
                selected = prefs.samplerPreset,
                onSelect = { apply("sampler", it) },
                onSaveCurrent = { saveAsType = "sampler"; saveAsName = "" },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "sampler", name)
                    userPresets = userPresets + ("sampler" to UserPresetStore.list(context, "sampler"))
                },
            )
            PresetSection(
                title = "系统提示预设（sysprompt）",
                items = listOf("" to false) +
                    (PresetLibrary.systemPromptPresets().map { it.name } - userPresets["sysprompt"].orEmpty().toSet()).map { it to false } +
                    userPresets["sysprompt"].orEmpty().map { it to true },
                selected = prefs.syspromptPreset,
                onSelect = { apply("sysprompt", it) },
                onSaveCurrent = { saveAsType = "sysprompt"; saveAsName = applied.sysprompt.name },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "sysprompt", name)
                    userPresets = userPresets + ("sysprompt" to UserPresetStore.list(context, "sysprompt"))
                },
            )
            AppliedEditorToggle("sysprompt", expandedEditor) { expandedEditor = it }
            if (expandedEditor == "sysprompt") {
                AppliedPresetEditor("sysprompt", applied) { updated ->
                    PresetSettingsStore.update(context, updated)
                    applied = updated
                }
            }
            PresetSection(
                title = "推理预设（reasoning）",
                items = listOf("" to false) +
                    (PresetLibrary.reasoningPresets().map { it.name } - userPresets["reasoning"].orEmpty().toSet()).map { it to false } +
                    userPresets["reasoning"].orEmpty().map { it to true },
                selected = prefs.reasoningPreset,
                onSelect = { apply("reasoning", it) },
                onSaveCurrent = { saveAsType = "reasoning"; saveAsName = applied.reasoning.name },
                emptyLabel = "默认（不应用）",
                onDeleteUser = { name ->
                    UserPresetStore.delete(context, "reasoning", name)
                    userPresets = userPresets + ("reasoning" to UserPresetStore.list(context, "reasoning"))
                },
            )
            AppliedEditorToggle("reasoning", expandedEditor) { expandedEditor = it }
            if (expandedEditor == "reasoning") {
                AppliedPresetEditor("reasoning", applied) { updated ->
                    PresetSettingsStore.update(context, updated)
                    applied = updated
                }
            }
            Text(
                "采样预设应用到“提供商与模型”详情页；reasoning 预设的 prefix/suffix/separator 进总装与显示；" +
                    "context/instruct/sysprompt 已按官方语义保存，运行时消费点等 textgen 协议后端接入（登记）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    // 保存当前为预设
    saveAsType?.let { type ->
        AlertDialog(
            onDismissRequest = { saveAsType = null },
            title = { Text("保存当前设置为预设") },
            text = {
                EmberTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text("预设名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = saveAsName.trim()
                    saveAsType = null
                    if (name.isNotBlank()) saveCurrent(type, name)
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { saveAsType = null }) { Text("取消") } },
        )
    }

    // 官方 onPresetImportFileChange：敏感字段确认（移除后导入 / 原样导入 / 取消）
    pendingSensitive?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { pendingSensitive = null },
            title = { Text("预设包含敏感字段") },
            text = {
                Text("检测到 proxy / 自定义端点等敏感字段：${PresetApplyEngine.detectSensitivePresetFields(content).joinToString(", ")}")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSensitive = null
                    val stripped = content.toMutableMap()
                    for (field in PresetApplyEngine.openaiSensitiveFields) stripped.remove(field)
                    proceedSamplerImport(name, JsonObject(stripped))
                }) { Text("移除后导入") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingSensitive = null
                        proceedSamplerImport(name, content)
                    }) { Text("原样导入") }
                    TextButton(onClick = { pendingSensitive = null }) { Text("取消") }
                }
            },
        )
    }

    // 官方同名覆盖确认
    pendingOverwrite?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = { Text("预设名已存在") },
            text = { Text("同名预设“$name”已存在，覆盖？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingOverwrite = null
                    saveSamplerImport(name, content)
                }) { Text("覆盖") }
            },
            dismissButton = { TextButton(onClick = { pendingOverwrite = null }) { Text("取消") } },
        )
    }

    // 多区段导入选择
    masterImportSections?.let { sections ->
        AlertDialog(
            onDismissRequest = { masterImportSections = null },
            title = { Text("选择要导入的区段") },
            text = {
                Column {
                    masterImportChecked.forEach { (key, checked) ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                masterImportChecked = masterImportChecked + (key to !checked)
                            }.padding(vertical = 4.dp),
                        ) {
                            Text(
                                sectionLabel(key),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (checked) "✓" else "", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val checked = masterImportChecked.filterValues { it }.keys
                    for (key in checked) {
                        val section = sections[key] as? JsonObject ?: continue
                        when (key) {
                            "instruct" -> PresetSettingsStore.applyInstruct(context, section)
                            "context" -> PresetSettingsStore.applyContext(context, section)
                            "sysprompt" -> PresetSettingsStore.applySysprompt(context, section)
                            "reasoning" -> PresetSettingsStore.applyReasoning(context, section)
                            "preset" -> {
                                val name = section["name"]?.jsonPrimitive?.contentOrNull ?: "preset"
                                UserPresetStore.save(context, "sampler", name, section.toString())
                            }
                            "srw" -> {
                                val behavior = BehaviorPrefs.load(context)
                                BehaviorPrefs.save(
                                    context,
                                    behavior.copy(
                                        userPromptBias = section["value"]?.jsonPrimitive?.contentOrNull ?: "",
                                        showUserPromptBias = section["show"]?.jsonPrimitive?.content == "true",
                                    ),
                                )
                            }
                        }
                    }
                    masterImportSections = null
                    refresh()
                    importMessage = "已导入 ${checked.size} 个区段"
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { masterImportSections = null }) { Text("取消") } },
        )
    }
}

private fun sectionLabel(key: String): String = when (key) {
    "instruct" -> "指导模板（instruct）"
    "context" -> "上下文模板（context）"
    "sysprompt" -> "系统提示（sysprompt）"
    "preset" -> "文本补全采样（textgen）"
    "reasoning" -> "推理模板（reasoning）"
    "srw" -> "开始回复前缀（srw）"
    else -> key
}

/** 官方 af_master_export 多区段体（不含 textgen preset；srw 从 BehaviorPrefs）。 */
private fun masterExportBody(context: android.content.Context): JsonObject {
    val json = Json { ignoreUnknownKeys = true }
    val state = PresetSettingsStore.load(context)
    val behavior = BehaviorPrefs.load(context)

    val contextSettings = json.encodeToJsonElement(ContextSettings.serializer(), state.context).jsonObject.toMutableMap()
    contextSettings["always_force_name2"] = JsonPrimitive(state.contextGlobals.alwaysForceName2)
    contextSettings["trim_sentences"] = JsonPrimitive(state.contextGlobals.trimSentences)
    contextSettings["single_line"] = JsonPrimitive(state.contextGlobals.singleLine)

    return buildJsonObject {
        put("instruct", PresetApplyEngine.filterPresetSettings(
            json.encodeToJsonElement(InstructSettings.serializer(), state.instruct).jsonObject,
            "instruct", state.instruct.preset, state.instruct.preset, true, 0, 0,
        ))
        put("context", PresetApplyEngine.filterPresetSettings(
            JsonObject(contextSettings), "context", state.context.preset, state.context.preset, true, 0, 0,
        ))
        put("sysprompt", PresetApplyEngine.filterPresetSettings(
            json.encodeToJsonElement(SyspromptSettings.serializer(), state.sysprompt).jsonObject,
            "sysprompt", state.sysprompt.name, state.sysprompt.name, true, 0, 0,
        ))
        put("reasoning", PresetApplyEngine.filterPresetSettings(
            json.encodeToJsonElement(ReasoningSettings.serializer(), state.reasoning).jsonObject,
            "reasoning", state.reasoning.name, state.reasoning.name, true, 0, 0,
        ))
        put("srw", buildJsonObject {
            put("value", JsonPrimitive(behavior.userPromptBias))
            put("show", JsonPrimitive(behavior.showUserPromptBias))
        })
    }
}

@Composable
private fun AppliedEditorToggle(type: String, expanded: String?, onToggle: (String?) -> Unit) {
    TextButton(
        onClick = { onToggle(if (expanded == type) null else type) },
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
    ) {
        Text(if (expanded == type) "收起生效设置" else "查看/编辑生效设置")
    }
}

@Composable
private fun PresetSection(
    title: String,
    items: List<Pair<String, Boolean>>,
    selected: String,
    onSelect: (String) -> Unit,
    onSaveCurrent: () -> Unit,
    onDeleteUser: (String) -> Unit,
    emptyLabel: String? = null,
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = 4.dp),
        )
        TextButton(onClick = onSaveCurrent, modifier = Modifier.padding(top = 8.dp)) {
            Text("保存当前为预设")
        }
    }
    items.forEach { (name, isUser) ->
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(name) }
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(
                if (name.isEmpty()) (emptyLabel ?: "默认") else name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected == name) {
                Text("✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (isUser) {
                IconButton(onClick = { onDeleteUser(name) }, modifier = Modifier.size(28.dp)) {
                    Icon(PhosphorIcons.Delete, contentDescription = "删除用户预设", modifier = Modifier.size(14.dp))
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 4.dp))
    }
}

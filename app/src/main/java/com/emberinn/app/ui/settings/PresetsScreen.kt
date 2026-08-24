package com.emberinn.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.engine.prompt.ContextSettings
import com.emberinn.engine.prompt.InstructSettings
import com.emberinn.engine.prompt.PresetApplyEngine
import com.emberinn.engine.prompt.PresetLibrary
import com.emberinn.engine.prompt.ReasoningSettings
import com.emberinn.engine.prompt.SyspromptSettings
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderStore
import java.io.File
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
    var pendingDeleteUser by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameName by remember { mutableStateOf("") }
    var exportConnectionData by remember { mutableStateOf(false) }
    var pendingSensitiveExport by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    var exportStep2 by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    var masterExportSections by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var masterExportKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingRestore by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    // 采样预设按当前活动连接协议选择（openai/textgen/novel/kobold）
    val samplerType = remember { activeSamplerPresetType(context) }

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
            "sampler" -> PresetLibrary.samplerPresets(samplerType).firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sampler", name)
            "sysprompt" -> PresetLibrary.systemPromptPresets().firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "sysprompt", name)
            "reasoning" -> PresetLibrary.reasoningPresets().firstOrNull { it.name == name }?.settings
                ?: UserPresetStore.load(context, "reasoning", name)
            else -> null
        }

    fun apply(type: String, name: String) {
        // 官方 kobold GUI 特殊预设：不读预设文件，直接保持当前 UI 设置
        if (type == "sampler" && name == "gui") {
            if (!PresetSettingsStore.applySampler(context, "gui")) {
                importMessage = "应用失败：未配置提供商或预设不存在"
                return
            }
            prefs = prefs.copy(samplerPreset = "gui")
            PresetPrefsStore.save(context, prefs)
            refresh()
            importMessage = "已应用：sampler / gui"
            return
        }
        val preset = presetJson(type, name) ?: return
        when (type) {
            "context" -> PresetSettingsStore.applyContext(context, preset)
            "instruct" -> PresetSettingsStore.applyInstruct(context, preset)
            "sysprompt" -> PresetSettingsStore.applySysprompt(context, preset)
            "reasoning" -> PresetSettingsStore.applyReasoning(context, preset)
            "sampler" -> {
                // 官方 onSettingsPresetChange：选中即应用到当前活动连接（bind_preset_to_connection 默认 true）。
                if (!PresetSettingsStore.applySampler(context, name)) {
                    importMessage = "应用失败：未配置提供商或预设不存在"
                    return
                }
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
                // 官方 getChatCompletionPreset：settingsToUpdate 全键（含连接字段）+ prompts/prompt_order
                val base = kotlinx.serialization.json.buildJsonObject {
                    PresetSettingsStore.samplerSettingsJson(profile.sampler, profile.contextWindow, profile.sampler.maxTokens).forEach { (k, v) -> put(k, v) }
                    PresetSettingsStore.connectionSettingsJson(profile).forEach { (k, v) -> put(k, v) }
                }
                val body = PresetApplyEngine.getChatCompletionPresetBody(base).toMutableMap()
                body["prompts"] = json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(com.emberinn.engine.prompt.PromptItem.serializer()),
                    com.emberinn.app.data.PromptManagerPrefs.prompts(context),
                )
                body["prompt_order"] = kotlinx.serialization.json.JsonArray(
                    com.emberinn.app.data.PromptManagerPrefs.orders(context).map { (cid, order) ->
                        kotlinx.serialization.json.buildJsonObject {
                            if (cid != "null" && cid != null) {
                                val cidNum = cid.toIntOrNull()
                                put(
                                    "character_id",
                                    if (cidNum != null) kotlinx.serialization.json.JsonPrimitive(cidNum)
                                    else kotlinx.serialization.json.JsonPrimitive(cid),
                                )
                            }
                            put(
                                "order",
                                json.encodeToJsonElement(
                                    kotlinx.serialization.builtins.ListSerializer(com.emberinn.engine.prompt.PromptOrderEntry.serializer()),
                                    order,
                                ),
                            )
                        }
                    },
                )
                kotlinx.serialization.json.JsonObject(body)
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
            PresetLibrary.samplerPresets(samplerType).any { it.name == name }
        if (exists) {
            pendingOverwrite = name to content
        } else {
            saveSamplerImport(name, content)
            // 官方 onPresetImportFileChange：导入成功后 trigger('change') 应用该预设
            apply("sampler", name)
        }
    }

    /** 官方 restore：内置默认预设 → 恢复默认设置；自定义预设 → 恢复到上次保存。 */
    fun requestRestore(type: String, name: String) {
        val builtin = when (type) {
            "context" -> PresetLibrary.contextPresets().any { it.preset == name }
            "instruct" -> PresetLibrary.instructPresets().any { it.preset == name }
            "sampler" -> PresetLibrary.samplerPresets(samplerType).any { it.name == name }
            "sysprompt" -> PresetLibrary.systemPromptPresets().any { it.name == name }
            else -> PresetLibrary.reasoningPresets().any { it.name == name }
        }
        pendingRestore = Triple(type, name, builtin)
    }

    /** 官方 deletePreset：删用户预设；删的是当前选中项时自动选第一个剩余并应用。 */
    fun deleteUserPreset(type: String, name: String) {
        UserPresetStore.delete(context, type, name)
        userPresets = userPresets + (type to UserPresetStore.list(context, type))
        val selected = when (type) {
            "context" -> prefs.contextPreset
            "instruct" -> prefs.instructPreset
            "sampler" -> prefs.samplerPreset
            "sysprompt" -> prefs.syspromptPreset
            else -> prefs.reasoningPreset
        }
        if (selected == name) {
            val remaining = when (type) {
                "context" -> PresetLibrary.contextPresets().map { it.preset } + UserPresetStore.list(context, type)
                "instruct" -> PresetLibrary.instructPresets().map { it.preset } + UserPresetStore.list(context, type)
                "sampler" -> PresetLibrary.samplerPresets(samplerType).map { it.name } + UserPresetStore.list(context, type)
                "sysprompt" -> PresetLibrary.systemPromptPresets().map { it.name } + UserPresetStore.list(context, type)
                else -> PresetLibrary.reasoningPresets().map { it.name } + UserPresetStore.list(context, type)
            }.distinct()
            if (remaining.isNotEmpty()) {
                apply(type, remaining.first())
            } else {
                refresh()
            }
        } else {
            refresh()
        }
        importMessage = "已删除预设：$type / $name"
    }

    /** 官方 renamePreset：用户预设改名；内置预设改名为另存新用户预设，选中项同步并应用。 */
    fun doRename(type: String, oldName: String, newName: String) {
        val safe = UserPresetStore.sanitizeFilename(newName) ?: run {
            importMessage = "重命名失败：名字无效"
            return
        }
        val renamed = if (UserPresetStore.list(context, type).contains(oldName)) {
            UserPresetStore.rename(context, type, oldName, safe)
        } else {
            val body = presetJson(type, oldName) ?: return
            UserPresetStore.save(context, type, safe, body.toString())
        }
        if (!renamed) {
            importMessage = "重命名失败：名字无效或已存在"
            return
        }
        userPresets = userPresets + (type to UserPresetStore.list(context, type))
        when (type) {
            "context" -> prefs = prefs.copy(contextPreset = safe)
            "instruct" -> prefs = prefs.copy(instructPreset = safe)
            "sampler" -> prefs = prefs.copy(samplerPreset = safe)
            "sysprompt" -> prefs = prefs.copy(syspromptPreset = safe)
            "reasoning" -> prefs = prefs.copy(reasoningPreset = safe)
        }
        PresetPrefsStore.save(context, prefs)
        apply(type, safe)
        importMessage = "已重命名：$oldName → $safe"
    }

    // 单预设导出（官方 data-preset-manager-export + openai onExportPresetClick：
    // 敏感字段确认（sampler）+ 是否导出连接数据（默认不导出））
    val singleExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, body) = exportStep2 ?: return@rememberLauncherForActivityResult
        val out = body.toMutableMap()
        if (!exportConnectionData) {
            for (k in connectionPresetKeys) out.remove(k)
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(kotlinx.serialization.json.JsonObject(out).toString().toByteArray())
            }
            importMessage = "已导出预设：$name"
        }.onFailure { importMessage = "导出失败：${it.message}" }
        exportStep2 = null
    }

    fun startSingleExport(type: String, name: String) {
        val body = presetJson(type, name) ?: return
        if (type == "sampler") {
            val sensitive = PresetApplyEngine.detectSensitivePresetFields(body)
            if (sensitive.isNotEmpty()) {
                pendingSensitiveExport = name to body
                return
            }
        }
        exportStep2 = name to body
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


    // 多区段主导出（官方 af_master_export：弹窗勾选区段；默认 instruct/context/sysprompt/reasoning 勾选，
    // textgen preset 与 srw 不勾；文件名 ST-formatting-{yyyy-MM-dd}.json）
    val masterExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = masterExportBody(context, masterExportKeys)
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
            // 官方 performMasterImport：textgen（preset）用文件名，其余用 data.name ?? 文件名
            val name = if (legacy == "preset") fileName
                else parsed["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: fileName
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
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = { presetImporter.launch(arrayOf("application/json")) }) { Text("导入预设") }
                    TextButton(onClick = {
                        masterExportSections = mapOf(
                            "instruct" to true, "context" to true, "sysprompt" to true,
                            "reasoning" to true, "preset" to false, "srw" to false,
                        )
                    }) { Text("导出全部") }
                    TextButton(onClick = { masterImporter.launch(arrayOf("application/json")) }) { Text("导入全部") }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    EmberSwitch(
                        checked = prefs.bindPresetToConnection,
                        onChange = {
                            prefs = prefs.copy(bindPresetToConnection = it)
                            PresetPrefsStore.save(context, prefs)
                        },
                    )
                    Text(
                        "绑定到连接",
                        fontSize = 11.sp,
                        color = EmberTheme.colors.lineStrong,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    importMessage?.let {
                        Text(
                            it,
                            fontSize = 11.sp,
                            color = EmberTheme.colors.lineStrong,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            color = EmberTheme.colors.ink, fontSize = 15.sp,
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                EmberSwitch(
                    checked = applied.contextDerived,
                    onChange = { applied = applied.copy(contextDerived = it); PresetSettingsStore.update(context, applied) },
                )
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                EmberSwitch(
                    checked = applied.instructDerived,
                    onChange = { applied = applied.copy(instructDerived = it); PresetSettingsStore.update(context, applied) },
                )
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                EmberSwitch(
                    checked = applied.bindModelTemplates,
                    onChange = { applied = applied.copy(bindModelTemplates = it); PresetSettingsStore.update(context, applied) },
                )
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                EmberSwitch(
                    checked = applied.contextSizeDerived,
                    onChange = { applied = applied.copy(contextSizeDerived = it); PresetSettingsStore.update(context, applied) },
                )
            }
            var afBehavior by remember { mutableStateOf(BehaviorPrefs.load(context)) }
            color = EmberTheme.colors.inkMute, fontSize = 11.sp,
            ShellInput(
                value = afBehavior.userPromptBias,
                onValueChange = {
                    afBehavior = afBehavior.copy(userPromptBias = it)
                    BehaviorPrefs.save(context, afBehavior)
                },
                label = "回复前缀（会拼在生成回复前）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                EmberSwitch(
                    checked = afBehavior.showUserPromptBias,
                    onChange = {
                        afBehavior = afBehavior.copy(showUserPromptBias = it)
                        BehaviorPrefs.save(context, afBehavior)
                    },
                )
            }
            PresetSection(
                title = "上下文模板（context）",
                items = (PresetLibrary.contextPresets().map { it.preset } - userPresets["context"].orEmpty().toSet()).map { it to false } +
                    userPresets["context"].orEmpty().map { it to true },
                selected = prefs.contextPreset,
                onSelect = { apply("context", it) },
                onSaveCurrent = { saveAsType = "context"; saveAsName = applied.context.preset },
                onUpdate = { if (prefs.contextPreset.isNotBlank()) saveCurrent("context", prefs.contextPreset) },
                onRename = { if (prefs.contextPreset.isNotBlank()) { renameTarget = "context" to prefs.contextPreset; renameName = prefs.contextPreset } },
                onExport = { if (prefs.contextPreset.isNotBlank()) startSingleExport("context", prefs.contextPreset) },
                onRestore = { if (prefs.contextPreset.isNotBlank()) requestRestore("context", prefs.contextPreset) },
                onDeleteUser = { pendingDeleteUser = "context" to prefs.contextPreset },
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
                onUpdate = { if (prefs.instructPreset.isNotBlank()) saveCurrent("instruct", prefs.instructPreset) },
                onRename = { if (prefs.instructPreset.isNotBlank()) { renameTarget = "instruct" to prefs.instructPreset; renameName = prefs.instructPreset } },
                onExport = { if (prefs.instructPreset.isNotBlank()) startSingleExport("instruct", prefs.instructPreset) },
                onRestore = { if (prefs.instructPreset.isNotBlank()) requestRestore("instruct", prefs.instructPreset) },
                onDeleteUser = { pendingDeleteUser = "instruct" to prefs.instructPreset },
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
                items = PresetSettingsStore.samplerPresetNames(context).map { name ->
                    name to (name in userPresets["sampler"].orEmpty().toSet())
                },
                selected = prefs.samplerPreset,
                onSelect = { apply("sampler", it) },
                onSaveCurrent = { saveAsType = "sampler"; saveAsName = prefs.samplerPreset },
                onUpdate = {
                    if (prefs.samplerPreset == "gui") importMessage = "GUI 预设不可更新（官方语义）"
                    else if (prefs.samplerPreset.isNotBlank()) saveCurrent("sampler", prefs.samplerPreset)
                },
                onRename = {
                    if (prefs.samplerPreset == "gui") importMessage = "GUI 预设不可重命名（官方语义）"
                    else if (prefs.samplerPreset.isNotBlank()) { renameTarget = "sampler" to prefs.samplerPreset; renameName = prefs.samplerPreset }
                },
                onExport = {
                    if (prefs.samplerPreset == "gui") importMessage = "GUI 预设不可导出（官方语义）"
                    else if (prefs.samplerPreset.isNotBlank()) startSingleExport("sampler", prefs.samplerPreset)
                },
                onRestore = {
                    if (prefs.samplerPreset == "gui") importMessage = "GUI 预设不可恢复（官方语义）"
                    else if (prefs.samplerPreset.isNotBlank()) requestRestore("sampler", prefs.samplerPreset)
                },
                onDeleteUser = { pendingDeleteUser = "sampler" to prefs.samplerPreset },
            )
            PresetSection(
                title = "系统提示预设（sysprompt）",
                items = (PresetLibrary.systemPromptPresets().map { it.name } - userPresets["sysprompt"].orEmpty().toSet()).map { it to false } +
                    userPresets["sysprompt"].orEmpty().map { it to true },
                selected = prefs.syspromptPreset,
                onSelect = { apply("sysprompt", it) },
                onSaveCurrent = { saveAsType = "sysprompt"; saveAsName = applied.sysprompt.name },
                onUpdate = { if (prefs.syspromptPreset.isNotBlank()) saveCurrent("sysprompt", prefs.syspromptPreset) },
                onRename = { if (prefs.syspromptPreset.isNotBlank()) { renameTarget = "sysprompt" to prefs.syspromptPreset; renameName = prefs.syspromptPreset } },
                onExport = { if (prefs.syspromptPreset.isNotBlank()) startSingleExport("sysprompt", prefs.syspromptPreset) },
                onRestore = { if (prefs.syspromptPreset.isNotBlank()) requestRestore("sysprompt", prefs.syspromptPreset) },
                onDeleteUser = { pendingDeleteUser = "sysprompt" to prefs.syspromptPreset },
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
                items = (PresetLibrary.reasoningPresets().map { it.name } - userPresets["reasoning"].orEmpty().toSet()).map { it to false } +
                    userPresets["reasoning"].orEmpty().map { it to true },
                selected = prefs.reasoningPreset,
                onSelect = { apply("reasoning", it) },
                onSaveCurrent = { saveAsType = "reasoning"; saveAsName = applied.reasoning.name },
                onUpdate = { if (prefs.reasoningPreset.isNotBlank()) saveCurrent("reasoning", prefs.reasoningPreset) },
                onRename = { if (prefs.reasoningPreset.isNotBlank()) { renameTarget = "reasoning" to prefs.reasoningPreset; renameName = prefs.reasoningPreset } },
                onExport = { if (prefs.reasoningPreset.isNotBlank()) startSingleExport("reasoning", prefs.reasoningPreset) },
                onRestore = { if (prefs.reasoningPreset.isNotBlank()) requestRestore("reasoning", prefs.reasoningPreset) },
                onDeleteUser = { pendingDeleteUser = "reasoning" to prefs.reasoningPreset },
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
                fontSize = 12.sp,
                color = EmberTheme.colors.lineStrong,
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
                ShellInput(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = "预设名",
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
                    apply("sampler", name)
                }) { Text("覆盖") }
            },
            dismissButton = { TextButton(onClick = { pendingOverwrite = null }) { Text("取消") } },
        )
    }

    // 官方删除确认（deletePreset：不可恢复，当前设置将被覆盖）
    pendingDeleteUser?.let { (type, name) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteUser = null },
            title = { Text("删除此预设？") },
            text = { Text("此操作不可恢复，当前设置将被覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteUser = null
                    deleteUserPreset(type, name)
                }) { Text("删除", color = EmberTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteUser = null }) { Text("取消") } },
        )
    }

    // 官方 renamePreset：输入新名字
    renameTarget?.let { (type, oldName) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名预设") },
            text = {
                ShellInput(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = "新名字",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameName.trim()
                    renameTarget = null
                    if (newName.isNotBlank() && newName != oldName) doRename(type, oldName, newName)
                }) { Text("重命名") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    // 官方 onExportPresetClick：敏感字段确认（sampler）
    pendingSensitiveExport?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { pendingSensitiveExport = null },
            title = { Text("预设包含敏感字段") },
            text = { Text("将导出 proxy / 自定义端点等敏感字段：${PresetApplyEngine.detectSensitivePresetFields(content).joinToString(", ")}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSensitiveExport = null
                    val stripped = content.toMutableMap()
                    for (field in PresetApplyEngine.openaiSensitiveFields) stripped.remove(field)
                    exportStep2 = name to kotlinx.serialization.json.JsonObject(stripped)
                }) { Text("移除后导出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingSensitiveExport = null
                        exportStep2 = name to content
                    }) { Text("原样导出") }
                    TextButton(onClick = { pendingSensitiveExport = null }) { Text("取消") }
                }
            },
        )
    }

    // 官方 exportPreset 弹窗：是否导出连接数据（默认不导出）
    exportStep2?.let { (name, _) ->
        AlertDialog(
            onDismissRequest = { exportStep2 = null },
            title = { Text("是否导出连接数据？") },
            text = {
                Column {
                    color = EmberTheme.colors.inkMute, fontSize = 12.sp,
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = exportConnectionData,
                            onClick = { exportConnectionData = true },
                            label = { Text("导出连接数据") },
                        )
                        FilterChip(
                            selected = !exportConnectionData,
                            onClick = { exportConnectionData = false },
                            label = { Text("不导出连接数据") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    singleExporter.launch("$name.json")
                }) { Text("导出") }
            },
            dismissButton = { TextButton(onClick = { exportStep2 = null }) { Text("取消") } },
        )
    }

    // 官方 restore：默认预设恢复默认设置 / 自定义预设恢复到上次保存
    pendingRestore?.let { (type, name, builtin) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复预设？") },
            text = {
                Text(if (builtin) "重置内置预设将恢复官方默认设置。" else "重置自定义预设将恢复到上次保存的设置。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    if (builtin) UserPresetStore.delete(context, type, name)
                    apply(type, name)
                    importMessage = "已恢复：$type / $name"
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } },
        )
    }

    // 官方 af_master_export：勾选区段后导出
    masterExportSections?.let { sections ->
        AlertDialog(
            onDismissRequest = { masterExportSections = null },
            title = { Text("选择要导出的区段") },
            text = {
                Column {
                    sections.forEach { (key, checked) ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                masterExportSections = sections + (key to !checked)
                            }.padding(vertical = 4.dp),
                        ) {
                            Text(
                                sectionLabel(key),
                                color = EmberTheme.colors.ink, fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (checked) "✓" else "", color = EmberTheme.colors.accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val checked = sections.filterValues { it }.keys
                    masterExportSections = null
                    if (checked.isEmpty()) {
                        importMessage = "未选择任何区段"
                        return@TextButton
                    }
                    masterExportKeys = checked
                    masterExporter.launch("ST-formatting-${java.time.LocalDate.now()}.json")
                }) { Text("导出") }
            },
            dismissButton = { TextButton(onClick = { masterExportSections = null }) { Text("取消") } },
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
                                color = EmberTheme.colors.ink, fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (checked) "✓" else "", color = EmberTheme.colors.accent)
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

/** 活动连接协议 → 采样预设目录（官方 sampler presets：openai/textgen/novel/kobold）。 */
private fun activeSamplerPresetType(context: android.content.Context): String =
    PresetSettingsStore.samplerPresetType(context)

/** 官方 settingsToUpdate 中 isConnection=true 的 preset 键（单预设导出“不导出连接数据”时剥离）。 */
private val connectionPresetKeys = setOf(
    "chat_completion_source", "group_models", "sort_models", "openai_model", "claude_model",
    "openrouter_model", "openrouter_use_fallback", "openrouter_providers", "openrouter_quantizations",
    "openrouter_allow_fallbacks", "openrouter_middleout", "ai21_model", "mistralai_model", "cohere_model",
    "perplexity_model", "groq_model", "chutes_model", "siliconflow_model", "siliconflow_endpoint",
    "minimax_model", "minimax_endpoint", "electronhub_model", "nanogpt_model", "nanogpt_provider",
    "nanogpt_payg_override", "deepseek_model", "aimlapi_model", "xai_model", "pollinations_model",
    "moonshot_model", "fireworks_model", "cometapi_model", "custom_model", "custom_url",
    "custom_include_body", "custom_exclude_body", "custom_include_headers", "custom_prompt_post_processing",
    "google_model", "vertexai_model", "zai_model", "zai_endpoint", "workers_ai_model", "workers_ai_account_id",
    "reverse_proxy", "show_external_models", "proxy_password", "vertexai_auth_mode", "vertexai_region",
    "vertexai_express_project_id", "azure_base_url", "azure_deployment_name", "azure_api_version",
    "azure_openai_model", "bypass_status_check",
)

private fun sectionLabel(key: String): String = when (key) {
    "instruct" -> "指导模板（instruct）"
    "context" -> "上下文模板（context）"
    "sysprompt" -> "系统提示（sysprompt）"
    "preset" -> "文本补全采样（textgen）"
    "reasoning" -> "推理模板（reasoning）"
    "srw" -> "开始回复前缀（srw）"
    else -> key
}

/** 官方 af_master_export 多区段体（按勾选区段导出；srw 从 BehaviorPrefs）。 */
private fun masterExportBody(context: android.content.Context, sections: Set<String>): JsonObject {
    val json = Json { ignoreUnknownKeys = true }
    val state = PresetSettingsStore.load(context)
    val behavior = BehaviorPrefs.load(context)

    val contextSettings = json.encodeToJsonElement(ContextSettings.serializer(), state.context).jsonObject.toMutableMap()
    contextSettings["always_force_name2"] = JsonPrimitive(state.contextGlobals.alwaysForceName2)
    contextSettings["trim_sentences"] = JsonPrimitive(state.contextGlobals.trimSentences)
    contextSettings["single_line"] = JsonPrimitive(state.contextGlobals.singleLine)

    return buildJsonObject {
        if ("instruct" in sections) {
            put("instruct", PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(InstructSettings.serializer(), state.instruct).jsonObject,
                "instruct", state.instruct.preset, state.instruct.preset, true, 0, 0,
            ))
        }
        if ("context" in sections) {
            put("context", PresetApplyEngine.filterPresetSettings(
                JsonObject(contextSettings), "context", state.context.preset, state.context.preset, true, 0, 0,
            ))
        }
        if ("sysprompt" in sections) {
            put("sysprompt", PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(SyspromptSettings.serializer(), state.sysprompt).jsonObject,
                "sysprompt", state.sysprompt.name, state.sysprompt.name, true, 0, 0,
            ))
        }
        if ("reasoning" in sections) {
            put("reasoning", PresetApplyEngine.filterPresetSettings(
                json.encodeToJsonElement(ReasoningSettings.serializer(), state.reasoning).jsonObject,
                "reasoning", state.reasoning.name, state.reasoning.name, true, 0, 0,
            ))
        }
        if ("srw" in sections) {
            put("srw", buildJsonObject {
                put("value", JsonPrimitive(behavior.userPromptBias))
                put("show", JsonPrimitive(behavior.showUserPromptBias))
            })
        }
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
    onDeleteUser: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
) {
    // 官方 preset manager：下拉选择 + 对选中项的一排操作按钮（update/new/rename/delete/export/restore）
    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(top = 14.dp, bottom = 2.dp),
    ) {
        Text(
            if (expanded) "▼" else "▶",
            fontSize = 12.sp,
            color = EmberTheme.colors.lineStrong,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            color = EmberTheme.colors.ink, fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "当前：${selected.ifBlank { "默认" }}",
            fontSize = 11.sp,
            color = EmberTheme.colors.accent,
        )
        Text(
            "（${items.size}）",
            fontSize = 11.sp,
            color = EmberTheme.colors.lineStrong,
        )
    }
    if (expanded) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text("${selected.ifBlank { "默认" }} ▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    items.forEach { (name, isUser) ->
                        DropdownMenuItem(
                            text = { Text(if (name.isEmpty()) "默认" else name + (if (isUser) "（我的）" else "")) },
                            onClick = {
                                menuOpen = false
                                onSelect(name)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
            TextButton(onClick = onSaveCurrent) { Text("另存为") }
            if (onUpdate != null) {
                TextButton(onClick = onUpdate) { Text("更新当前") }
            }
            if (onRename != null) {
                TextButton(onClick = onRename) { Text("重命名") }
            }
            if (onExport != null) {
                TextButton(onClick = onExport) { Text("导出") }
            }
            TextButton(onClick = onDeleteUser, enabled = selected.isNotBlank()) { Text("删除", color = EmberTheme.colors.danger) }
            if (onRestore != null) {
                TextButton(onClick = onRestore) { Text("恢复默认") }
            }
        }
    }
}

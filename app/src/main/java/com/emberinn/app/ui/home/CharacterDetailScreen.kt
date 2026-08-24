@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.home

import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.emberShadow
import com.emberinn.app.ui.components.emberGlass

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.emberinn.app.data.CharacterCardEdit
import com.emberinn.app.ui.settings.GlobalRegexPrefs
import com.emberinn.app.data.ImageGenClient
import com.emberinn.app.data.CharacterRecord
import com.emberinn.app.data.CharacterRegexScript
import com.emberinn.app.data.ModelOverride
import com.emberinn.app.data.ThemeRecipe
import com.emberinn.app.data.SessionRecord
import com.emberinn.app.data.WorldEntryDraft
import com.emberinn.app.data.WorldStore
import com.emberinn.app.ui.components.edgeSwipeBack
import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.icons.FaIcons
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberBottomSheet
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import com.emberinn.app.ui.components.EmberSlider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 角色详情编辑页（P1-4）：官方 v2 字段全集编辑 + 世界书条目管理 + 备用开场白。
 * 本地状态收集改动，底部"保存修改"一次写回（v2 归一，talkativeness 进 extensions）。
 */
@Composable
fun CharacterDetailScreen(
    record: CharacterRecord,
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenChat: (SessionRecord) -> Unit,
) {
    val context = LocalContext.current
    val seed = record.seedColor?.let { Color(it.toInt()) }

    var fields by remember(record.id) { mutableStateOf(vm.readCharacterFields(record)) }
    var entries by remember(record.id) { mutableStateOf(vm.readWorldEntries(record)) }
    val worldStore = remember { WorldStore(context) }
    var worldLink by remember(record.id) { mutableStateOf(CharacterCardEdit.readWorldLink(record.rawJson) ?: "") }
    var worldBookExpanded by remember { mutableStateOf(false) }
    var regexScripts by remember(record.id) { mutableStateOf(vm.readRegexScripts(record)) }
    // 官方 regex 扩展 character_allowed_regex：该卡正则是否允许在本角色上生效
    // 用户要求默认打开；显式关闭后从允许列表移除（GlobalRegexPrefs）
    var regexAllowed by remember(record.id) {
        mutableStateOf(true)
    }
    var variables by remember(record.id) { mutableStateOf(vm.readVariables(record)) }
    var modelOverride by remember(record.id) { mutableStateOf(vm.readModelOverride(record)) }
    var modelOverrideExpanded by remember { mutableStateOf(false) }
    var editingModelOverride by remember { mutableStateOf(false) }
    var themeRecipe by remember(record.id) { mutableStateOf(vm.readThemeRecipe(record)) }
    var themeRecipeExpanded by remember { mutableStateOf(false) }
    var editingThemeRecipe by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    var editingKey by remember { mutableStateOf<String?>(null) }
    var fieldDraft by remember { mutableStateOf("") }
    var editingEntryIdx by remember { mutableStateOf<Int?>(null) }
    var confirmDeleteEntry by remember { mutableStateOf(false) }
    var addingEntry by remember { mutableStateOf(false) }
    var editingGreetingIdx by remember { mutableStateOf<Int?>(null) }
    var greetingDraft by remember { mutableStateOf("") }
    var editingDepth by remember { mutableStateOf(false) }
    var editingRegexIdx by remember { mutableStateOf<Int?>(null) }
    var addingRegex by remember { mutableStateOf(false) }
    var editingVarKey by remember { mutableStateOf<String?>(null) }
    var varDraftKey by remember { mutableStateOf("") }
    var varDraftValue by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val themeRecipeImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { u ->
            runCatching {
                val text = context.contentResolver.openInputStream(u)?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                val imported = CharacterCardEdit.themeRecipeFromJson(text)
                themeRecipe = imported
                dirty = true
                Toast.makeText(context, "已导入主题配方", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败：配方文件格式不对", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { s -> s.write(vm.exportJson(record).toByteArray()) }
                Toast.makeText(context, "已导出：${record.name}.json", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setField(key: String, v: String) {
        fields = when (key) {
            "name" -> fields.copy(name = v)
            "description" -> fields.copy(description = v)
            "personality" -> fields.copy(personality = v)
            "scenario" -> fields.copy(scenario = v)
            "mes_example" -> fields.copy(mesExample = v)
            "system_prompt" -> fields.copy(systemPrompt = v)
            "post_history_instructions" -> fields.copy(postHistoryInstructions = v)
            "creator" -> fields.copy(creator = v)
            "character_version" -> fields.copy(characterVersion = v)
            "creator_notes" -> fields.copy(creatorNotes = v)
            "tags" -> fields.copy(tags = v)
            else -> fields
        }
        dirty = true
    }

    val save = {
        vm.saveCharacterFields(record, fields)
        vm.saveWorldEntries(record, entries)
        vm.saveRegexScripts(record, regexScripts)
        vm.saveVariables(record, variables)
        vm.saveModelOverride(record, modelOverride)
        vm.saveThemeRecipe(record, themeRecipe)
        dirty = false
        Toast.makeText(context, "已保存：${fields.name.ifBlank { record.name }}", Toast.LENGTH_SHORT).show()
    }

    // 返回手势：系统返回/预测性返回也回到上一层，而不是退出 App
    BackHandler(onBack = onBack)
    val sky = rememberSky()
    Box(modifier = Modifier.fillMaxSize().edgeSwipeBack(onBack = onBack)) {
        // 静态背景层：顶栏毛玻璃的静态模糊源（列表滚动不触发整屏重捕）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sky(sky)
                .background(EmberTheme.colors.bg),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：玻璃 + 边缘高光，返回在左上角，statusBarsPadding 避让状态栏 + 再留 12dp（不贴最高处）
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .emberGlass(sky = sky, atTop = false),
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(FaIcons.ArrowLeft, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fields.name.ifBlank { record.name },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (dirty) "有未保存的修改" else "角色详情与编辑",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(FaIcons.EllipsisVertical, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("开始聊天") },
                            leadingIcon = { Icon(FaIcons.PaperPlane, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenChat(vm.openOrResume(record.id, fields.name.ifBlank { record.name }))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 JSON") },
                            leadingIcon = { Icon(FaIcons.ShareNodes, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                exportLauncher.launch("${fields.name.ifBlank { record.name }}.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (record.pinned) "取消置顶" else "置顶") },
                            leadingIcon = { Icon(FaIcons.Star, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                vm.togglePin(record)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除角色", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(FaIcons.TrashCan, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 168.dp),
            ) {
                // 头部：大头像 + 名字 + 描述 + 统计
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        if (record.avatarPath != null && File(record.avatarPath).exists()) {
                            AsyncImage(
                                model = File(record.avatarPath),
                                contentDescription = record.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .emberShadow(
                                        color = (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.4f),
                                        radius = 14.dp,
                                        spread = 1.dp,
                                        offset = DpOffset(0.dp, 5.dp),
                                        alpha = 0.45f,
                                    )
                                    .clip(CircleShape)
                                    .border(2.dp, (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.5f), CircleShape),
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .emberShadow(
                                        color = (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.4f),
                                        radius = 14.dp,
                                        spread = 1.dp,
                                        offset = DpOffset(0.dp, 5.dp),
                                        alpha = 0.45f,
                                    )
                                    .clip(CircleShape)
                                    .border(2.dp, (seed ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.5f), CircleShape)
                                    .background(
                                    Brush.linearGradient(
                                        if (seed != null) {
                                            listOf(lerp(seed, MaterialTheme.colorScheme.surface, 0.55f), seed)
                                        } else {
                                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                                        },
                                    ),
                                ),
                            ) {
                                Text(
                                    record.name.take(1),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fields.description.ifBlank { "（无描述）" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("世界书 ${entries.size} 条", seed)
                                StatChip("开场白 ${fields.alternateGreetings.size}", seed)
                                if (fields.tags.isNotBlank()) StatChip("标签 ${fields.tags.split(',').count { it.isNotBlank() }}", seed)
                            }
                        }
                    }
                    HorizontalDivider()
                }

                item {
                    SectionCard("基础字段") {
                        FieldRow("名字", fields.name) {
                            editingKey = "name"; fieldDraft = fields.name
                        }
                        FieldRow("描述", fields.description) {
                            editingKey = "description"; fieldDraft = fields.description
                        }
                        FieldRow("性格", fields.personality) {
                            editingKey = "personality"; fieldDraft = fields.personality
                        }
                        FieldRow("场景", fields.scenario) {
                            editingKey = "scenario"; fieldDraft = fields.scenario
                        }
                        FieldRow("开场白", fields.firstMes) {
                            editingKey = "first_mes"; fieldDraft = fields.firstMes
                        }
                        FieldRow("示例对话", fields.mesExample) {
                            editingKey = "mes_example"; fieldDraft = fields.mesExample
                        }
                    }
                }

                item {
                    SectionCard("备用开场白", "${fields.alternateGreetings.size} 个") {
                        if (fields.alternateGreetings.isEmpty()) {
                            Text(
                                "没有备用开场白。点击下方按钮新增，新会话可从备用开场白开始。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                        }
                        fields.alternateGreetings.forEachIndexed { i, g ->
                            GreetingRow(
                                text = g,
                                onEdit = { editingGreetingIdx = i; greetingDraft = g },
                                onDelete = {
                                    fields = fields.copy(alternateGreetings = fields.alternateGreetings.filterIndexed { j, _ -> j != i })
                                    dirty = true
                                },
                            )
                        }
                        FilledTonalButton(
                            onClick = { editingGreetingIdx = fields.alternateGreetings.size; greetingDraft = "" },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text("＋ 新增开场白") }
                    }
                }

                item {
                    SectionCard("正则（该卡）", "${regexScripts.size} 条") {
                        if (regexScripts.isEmpty()) {
                            Text(
                                "没有该卡正则。新增后需在下文开启“允许此角色应用该卡正则”才会生效（对齐官方 data.extensions.regex_scripts + character_allowed_regex）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                        }
                        regexScripts.forEachIndexed { i, script ->
                            RegexRow(
                                script = script,
                                onEdit = { editingRegexIdx = i },
                                onToggle = {
                                    regexScripts = regexScripts.mapIndexed { j, s -> if (j == i) s.copy(disabled = !s.disabled) else s }
                                    dirty = true
                                },
                            )
                        }
                        FilledTonalButton(
                            onClick = { addingRegex = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text("＋ 新增正则") }
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("允许此角色应用该卡正则", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "对齐官方 character_allowed_regex：不勾选时该卡正则不会生效。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            EmberSwitch(
                                checked = regexAllowed,
                                onCheckedChange = { on ->
                                    regexAllowed = on
                                    val list = GlobalRegexPrefs.characterAllowedRegex(context).toMutableList()
                                    val key = "${record.id}.png"
                                    if (on && key !in list) list += key
                                    if (!on) list.remove(key)
                                    GlobalRegexPrefs.saveCharacterAllowed(context, list)
                                    // 显示管线缓存了脚本表：放行/收回必须整体失效，
                                    // 否则聊天页拿着旧空表直到重启（卡17开场白不渲染的根因）
                                    com.emberinn.app.data.DisplayCacheVersion.bump()
                                },
                            )
                        }
                    }
                }

                item {
                    SectionCard("变量（该卡）", "${variables.size} 个") {
                        if (variables.isEmpty()) {
                            Text(
                                "没有该卡变量。变量以 {{getvar::键}} 在提示词/宏里引用（README 自定义扩展）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                            )
                        }
                        variables.toList().forEachIndexed { i, pair ->
                            SimpleEditRow(
                                title = pair.first,
                                subtitle = pair.second,
                                onEdit = { editingVarKey = pair.first; varDraftKey = pair.first; varDraftValue = pair.second },
                                onDelete = {
                                    variables = variables - pair.first
                                    dirty = true
                                },
                            )
                        }
                        FilledTonalButton(
                            onClick = { editingVarKey = ""; varDraftKey = ""; varDraftValue = "" },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text("＋ 新增变量") }
                    }
                }

                item {
                    SectionCard("模型覆盖", if (modelOverride.isEmpty()) "跟随全局" else "已覆盖") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { modelOverrideExpanded = !modelOverrideExpanded },
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "模型 / 上下文 / 采样（本角色覆盖全局）",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    modelOverride.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                if (modelOverrideExpanded) FaIcons.ChevronUp else FaIcons.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (modelOverrideExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { editingModelOverride = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("编辑覆盖") }
                                if (!modelOverride.isEmpty()) {
                                    FilledTonalButton(
                                        onClick = {
                                            modelOverride = ModelOverride()
                                            dirty = true
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("清除（跟随全局）", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard("主题配方", if (themeRecipe.isEmpty()) "跟随全局" else "已覆盖") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { themeRecipeExpanded = !themeRecipeExpanded },
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "seed / 背景 / 形状 / 字体 / 风格 / 浅深锁定",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    themeRecipe.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                if (themeRecipeExpanded) FaIcons.ChevronUp else FaIcons.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (themeRecipeExpanded) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { editingThemeRecipe = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("编辑配方") }
                                FilledTonalButton(
                                    onClick = {
                                        themeRecipe = ThemeRecipe()
                                        dirty = true
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("恢复全局", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }

                item {
                    SectionCard("高级") {
                        FieldRow("系统提示", fields.systemPrompt) {
                            editingKey = "system_prompt"; fieldDraft = fields.systemPrompt
                        }
                        FieldRow("剧情后指令", fields.postHistoryInstructions) {
                            editingKey = "post_history_instructions"; fieldDraft = fields.postHistoryInstructions
                        }
                        FieldRow(
                            "深度提示",
                            fields.depthPrompt.ifBlank { "深度 ${fields.depthPromptDepth} · 角色 ${fields.depthPromptRole}" },
                        ) { editingDepth = true }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("话痨程度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(90.dp))
                            EmberSlider(
                                value = fields.talkativeness,
                                onValueChange = { fields = fields.copy(talkativeness = it); dirty = true },
                                valueRange = 0f..1f,
                                steps = 19,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                when {
                                    fields.talkativeness < 0.3f -> "安静"
                                    fields.talkativeness < 0.7f -> "适中"
                                    else -> "话多"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(40.dp),
                            )
                        }
                        FieldRow("作者", fields.creator) {
                            editingKey = "creator"; fieldDraft = fields.creator
                        }
                        FieldRow("版本", fields.characterVersion) {
                            editingKey = "character_version"; fieldDraft = fields.characterVersion
                        }
                        FieldRow("创作者备注", fields.creatorNotes) {
                            editingKey = "creator_notes"; fieldDraft = fields.creatorNotes
                        }
                        FieldRow("标签", fields.tags) {
                            editingKey = "tags"; fieldDraft = fields.tags
                        }
                    }
                }

                // README/用户要求：世界书收进一张卡片、默认折叠，放在详情页最底部
                item {
                    SectionCard("世界书", "${entries.size} 条") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { worldBookExpanded = !worldBookExpanded }.padding(vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (worldBookExpanded) "收起世界书条目" else "展开管理与查看世界书条目",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    if (worldBookExpanded) "点击收起" else "共 ${entries.size} 条 · 点击展开",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Icon(
                                if (worldBookExpanded) FaIcons.ChevronUp else FaIcons.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (worldBookExpanded) {
                            Text("关联外置世界（data.extensions.world）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                FilterChip(selected = worldLink.isEmpty(), onClick = {
                                    worldLink = ""
                                    vm.saveWorldLink(record, "")
                                }, label = { Text("（无）") }, modifier = Modifier.padding(end = 6.dp))
                                worldStore.list().forEach { w ->
                                    FilterChip(
                                        selected = worldLink == w.name,
                                        onClick = {
                                            worldLink = w.name
                                            vm.saveWorldLink(record, w.name)
                                        },
                                        label = { Text(w.displayName) },
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }
                            }
                            if (entries.isEmpty()) {
                                Text(
                                    "没有世界书条目。新增关键词条目后，聊到关键词时内容会自动注入上下文。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                                )
                            }
                            entries.forEachIndexed { i, e ->
                                WorldEntryRow(
                                    entry = e,
                                    onEdit = { editingEntryIdx = i },
                                    onToggle = {
                                        entries = entries.mapIndexed { j, item -> if (j == i) item.copy(enabled = !item.enabled) else item }
                                        dirty = true
                                    },
                                )
                            }
                            FilledTonalButton(
                                onClick = { addingEntry = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            ) { Text("＋ 新增条目") }
                        }
                    }
                }
            }
        }

        // 底部固定保存栏
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Button(
                onClick = save,
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (dirty) "保存修改" else "没有修改",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    // ---- 字段编辑对话框 ----
    editingKey?.let { key ->
        val label = when (key) {
            "name" -> "名字"
            "description" -> "描述"
            "personality" -> "性格"
            "scenario" -> "场景"
            "mes_example" -> "示例对话"
            "system_prompt" -> "系统提示"
            "post_history_instructions" -> "剧情后指令"
            "creator" -> "作者"
            "character_version" -> "版本"
            "creator_notes" -> "创作者备注"
            else -> "标签"
        }
        val multiline = key != "name" && key != "tags" && key != "creator" && key != "character_version"
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("编辑$label") },
            text = {
                EmberTextField(
                    value = fieldDraft,
                    onValueChange = { fieldDraft = it },
                    minLines = if (multiline) 3 else 1,
                    maxLines = if (multiline) 10 else 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    setField(key, fieldDraft)
                    editingKey = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("取消") }
            },
        )
    }

    // ---- 深度提示对话框 ----
    if (editingDepth) {
        var prompt by remember(fields.depthPrompt) { mutableStateOf(fields.depthPrompt) }
        var depth by remember(fields.depthPromptDepth) { mutableStateOf(fields.depthPromptDepth) }
        var role by remember(fields.depthPromptRole) { mutableStateOf(fields.depthPromptRole) }
        AlertDialog(
            onDismissRequest = { editingDepth = false },
            title = { Text("编辑深度提示") },
            text = {
                Column {
                    EmberTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("（空）") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmberTextField(
                        value = depth,
                        onValueChange = { depth = it.filter { c -> c.isDigit() } },
                        label = { Text("注入深度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("system", "user", "assistant").forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fields = fields.copy(
                        depthPrompt = prompt,
                        depthPromptDepth = depth.ifBlank { "4" },
                        depthPromptRole = role,
                    )
                    dirty = true
                    editingDepth = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingDepth = false }) { Text("取消") }
            },
        )
    }

    // ---- 备用开场白编辑 ----
    editingGreetingIdx?.let { idx ->
        val isNew = idx >= fields.alternateGreetings.size
        AlertDialog(
            onDismissRequest = { editingGreetingIdx = null },
            title = { Text(if (isNew) "新增开场白" else "编辑开场白") },
            text = {
                EmberTextField(
                    value = greetingDraft,
                    onValueChange = { greetingDraft = it },
                    minLines = 3,
                    maxLines = 10,
                    placeholder = { Text("角色说的第一句话") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = greetingDraft.trim()
                    if (trimmed.isNotEmpty()) {
                        fields = if (isNew) {
                            fields.copy(alternateGreetings = fields.alternateGreetings + trimmed)
                        } else {
                            fields.copy(alternateGreetings = fields.alternateGreetings.mapIndexed { j, g -> if (j == idx) trimmed else g })
                        }
                        dirty = true
                    }
                    editingGreetingIdx = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingGreetingIdx = null }) { Text("取消") }
            },
        )
    }

    // ---- 世界书条目编辑 ----
    val editingEntry = entries.getOrNull(editingEntryIdx ?: -1)
    if (editingEntry != null || addingEntry) {
        WorldEntryEditorSheet(
            initial = editingEntry ?: WorldEntryDraft(
                id = (entries.maxOfOrNull { it.id } ?: 0) + 1,
                keys = "", content = "", comment = "",
                constant = false, selective = true, enabled = true, insertionOrder = 100,
            ),
            isNew = addingEntry,
            onSave = { d ->
                if (addingEntry) {
                    entries = entries + d
                } else {
                    val i = editingEntryIdx ?: 0
                    entries = entries.mapIndexed { j, e -> if (j == i) d else e }
                }
                dirty = true
                addingEntry = false
                editingEntryIdx = null
            },
            onDelete = {
                // README 守则 6：世界书条目删除二次确认
                confirmDeleteEntry = true
            },
            onDismiss = {
                addingEntry = false
                editingEntryIdx = null
            },
        )
    }

    if (confirmDeleteEntry) {
        AlertDialog(
            onDismissRequest = { confirmDeleteEntry = false },
            title = { Text("删除这条世界书条目？") },
            text = { Text("删除后不可恢复（保存角色时生效）。") },
            confirmButton = {
                TextButton(onClick = {
                    val i = editingEntryIdx
                    if (i != null && i in entries.indices) {
                        entries = entries.filterIndexed { j, _ -> j != i }
                        dirty = true
                    }
                    addingEntry = false
                    editingEntryIdx = null
                    confirmDeleteEntry = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteEntry = false }) { Text("取消") }
            },
        )
    }

    // ---- 正则编辑弹层 ----
    val editingRegex = regexScripts.getOrNull(editingRegexIdx ?: -1)
    if (editingRegex != null || addingRegex) {
        RegexEditorSheet(
            initial = editingRegex ?: CharacterRegexScript(
                id = ((regexScripts.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0) + 1).toString(),
                scriptName = "", findRegex = "", replaceString = "",
                placement = listOf(1, 2, 5, 6), runOnEdit = true,
            ),
            isNew = addingRegex,
            onSave = { s ->
                if (addingRegex) {
                    regexScripts = regexScripts + s
                } else {
                    val i = editingRegexIdx ?: 0
                    regexScripts = regexScripts.mapIndexed { j, old -> if (j == i) s else old }
                }
                dirty = true
                addingRegex = false
                editingRegexIdx = null
            },
            onDelete = {
                val i = editingRegexIdx
                if (i != null && i in regexScripts.indices) {
                    regexScripts = regexScripts.filterIndexed { j, _ -> j != i }
                    dirty = true
                }
                addingRegex = false
                editingRegexIdx = null
            },
            onDismiss = {
                addingRegex = false
                editingRegexIdx = null
            },
        )
    }

    // ---- 变量编辑对话框 ----
    editingVarKey?.let { _ ->
        AlertDialog(
            onDismissRequest = { editingVarKey = null },
            title = { Text("编辑变量") },
            text = {
                Column {
                    EmberTextField(
                        value = varDraftKey,
                        onValueChange = { varDraftKey = it },
                        label = { Text("键（{{getvar::键}} 引用）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmberTextField(
                        value = varDraftValue,
                        onValueChange = { varDraftValue = it },
                        label = { Text("值") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val key = varDraftKey.trim()
                    if (key.isNotEmpty()) {
                        variables = variables + (key to varDraftValue)
                        dirty = true
                    }
                    editingVarKey = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingVarKey = null }) { Text("取消") }
            },
        )
    }

    // ---- 模型覆盖编辑对话框 ----
    if (editingModelOverride) {
        var mModel by remember(modelOverride) { mutableStateOf(modelOverride.model) }
        var mMaxTokens by remember(modelOverride) { mutableStateOf(modelOverride.maxTokens?.toString() ?: "") }
        var mContext by remember(modelOverride) { mutableStateOf(modelOverride.contextWindow?.toString() ?: "") }
        var mTemp by remember(modelOverride) { mutableStateOf(modelOverride.temperature?.toString() ?: "") }
        var mTopP by remember(modelOverride) { mutableStateOf(modelOverride.topP?.toString() ?: "") }
        var mPres by remember(modelOverride) { mutableStateOf(modelOverride.presencePenalty?.toString() ?: "") }
        var mFreq by remember(modelOverride) { mutableStateOf(modelOverride.frequencyPenalty?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editingModelOverride = false },
            title = { Text("模型覆盖") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    EmberTextField(
                        value = mModel,
                        onValueChange = { mModel = it },
                        label = { Text("模型（留空跟随全局）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmberTextField(
                        value = mContext,
                        onValueChange = { mContext = it.filter { c -> c.isDigit() } },
                        label = { Text("上下文上限（tokens）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    EmberTextField(
                        value = mMaxTokens,
                        onValueChange = { mMaxTokens = it.filter { c -> c.isDigit() } },
                        label = { Text("最大回复 tokens") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    EmberTextField(
                        value = mTemp,
                        onValueChange = { mTemp = it },
                        label = { Text("温度 temperature") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    EmberTextField(
                        value = mTopP,
                        onValueChange = { mTopP = it },
                        label = { Text("Top P") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    EmberTextField(
                        value = mPres,
                        onValueChange = { mPres = it },
                        label = { Text("存在惩罚 presence_penalty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    EmberTextField(
                        value = mFreq,
                        onValueChange = { mFreq = it },
                        label = { Text("频率惩罚 frequency_penalty") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    modelOverride = ModelOverride(
                        model = mModel.trim(),
                        maxTokens = mMaxTokens.toIntOrNull(),
                        contextWindow = mContext.toIntOrNull(),
                        temperature = mTemp.toDoubleOrNull(),
                        topP = mTopP.toDoubleOrNull(),
                        presencePenalty = mPres.toDoubleOrNull(),
                        frequencyPenalty = mFreq.toDoubleOrNull(),
                    )
                    dirty = true
                    editingModelOverride = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingModelOverride = false }) { Text("取消") }
            },
        )
    }

    // ---- 主题配方编辑对话框 ----
    if (editingThemeRecipe) {
        var tSeed by remember(themeRecipe) { mutableStateOf(themeRecipe.seed) }
        var tShape by remember(themeRecipe) { mutableStateOf(themeRecipe.shape) }
        var tFont by remember(themeRecipe) { mutableStateOf(themeRecipe.font) }
        var tStyle by remember(themeRecipe) { mutableStateOf(themeRecipe.style) }
        var tLock by remember(themeRecipe) { mutableStateOf(themeRecipe.lockMode) }
        AlertDialog(
            onDismissRequest = { editingThemeRecipe = false },
            title = { Text("主题配方") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    EmberTextField(
                        value = tSeed,
                        onValueChange = { tSeed = it },
                        label = { Text("seed 色（#RRGGBB，留空用角色卡取色）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("形状", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "跟随全局", "square" to "方正 4dp", "rounded" to "圆润 16dp", "circle" to "浑圆 24dp").forEach { (v, label) ->
                            FilterChip(selected = tShape == v, onClick = { tShape = v }, label = { Text(label) })
                        }
                    }
                    Text("字体", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "系统", "serif" to "衬线", "source" to "思源宋体").forEach { (v, label) ->
                            FilterChip(selected = tFont == v, onClick = { tFont = v }, label = { Text(label) })
                        }
                    }
                    Text("风格档位", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "跟随全局", "airy" to "轻盈", "calm" to "沉静", "vivid" to "鲜明").forEach { (v, label) ->
                            FilterChip(selected = tStyle == v, onClick = { tStyle = v }, label = { Text(label) })
                        }
                    }
                    Text("浅深锁定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to "跟随全局", "system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, label) ->
                            FilterChip(selected = tLock == v, onClick = { tLock = v }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val draft = ThemeRecipe(
                                    seed = tSeed.trim(),
                                    shape = tShape,
                                    font = tFont,
                                    style = tStyle,
                                    lockMode = tLock,
                                )
                                val json = CharacterCardEdit.themeRecipeToJson(draft)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TITLE, "主题配方 · ${record.name}")
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享主题配方"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("导出/分享")
                        }
                        FilledTonalButton(
                            onClick = { themeRecipeImportLauncher.launch(arrayOf("text/*", "application/json")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("导入")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    themeRecipe = ThemeRecipe(
                        seed = tSeed.trim(),
                        shape = tShape,
                        font = tFont,
                        style = tStyle,
                        lockMode = tLock,
                    )
                    dirty = true
                    editingThemeRecipe = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingThemeRecipe = false }) { Text("取消") }
            },
        )
    }

    // ---- 删除确认 ----
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${record.name}」？") },
            text = { Text("角色和它的聊天记录都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(record)
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 统一区块卡片：详情页所有分组（基础字段/开场白/正则/变量/模型覆盖/主题配方/高级/世界书）同一样式。 */
@Composable
private fun SectionCard(
    title: String,
    count: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .emberShadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                radius = 10.dp,
                offset = DpOffset(0.dp, 4.dp),
                alpha = 0.06f,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            SectionHeader(title, count)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatChip(text: String, seed: Color? = null) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = seed?.let { lerp(it, MaterialTheme.colorScheme.surfaceVariant, 0.84f) }
            ?: MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (seed != null) lerp(seed, MaterialTheme.colorScheme.onSurfaceVariant, 0.45f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                value.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(FaIcons.Pencil, contentDescription = "编辑$label", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider()
}

@Composable
private fun GreetingRow(text: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 6.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(FaIcons.Pencil, contentDescription = "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(FaIcons.TrashCan, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

@Composable
private fun WorldEntryRow(entry: WorldEntryDraft, onEdit: () -> Unit, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (entry.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.keys.ifBlank { "（无触发词）" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (entry.keys.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.constant) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "恒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (entry.selective) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.content.ifBlank { entry.comment.ifBlank { "（空内容）" } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "插入顺序 ${entry.insertionOrder}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(FaIcons.Pencil, contentDescription = "编辑条目", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
            EmberSwitch(checked = entry.enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun WorldEntryEditorSheet(
    initial: WorldEntryDraft,
    isNew: Boolean,
    onSave: (WorldEntryDraft) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var keys by remember(initial) { mutableStateOf(initial.keys) }
    var keySecondary by remember(initial) { mutableStateOf(initial.keySecondary) }
    var content by remember(initial) { mutableStateOf(initial.content) }
    var comment by remember(initial) { mutableStateOf(initial.comment) }
    var constant by remember(initial) { mutableStateOf(initial.constant) }
    var selective by remember(initial) { mutableStateOf(initial.selective) }
    var selectiveLogic by remember(initial) { mutableStateOf(initial.selectiveLogic.toString()) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var order by remember(initial) { mutableStateOf(initial.insertionOrder.toString()) }
    var position by remember(initial) { mutableStateOf(initial.position) }
    var depth by remember(initial) { mutableStateOf(initial.depth?.toString() ?: "") }
    var role by remember(initial) { mutableStateOf(initial.role) }
    var caseSensitive by remember(initial) { mutableStateOf(initial.caseSensitive) }
    var matchWholeWords by remember(initial) { mutableStateOf(initial.matchWholeWords) }
    var scanDepth by remember(initial) { mutableStateOf(initial.scanDepth?.toString() ?: "") }
    var matchPersona by remember(initial) { mutableStateOf(initial.matchPersonaDescription) }
    var matchCharDesc by remember(initial) { mutableStateOf(initial.matchCharacterDescription) }
    var matchCharPersona by remember(initial) { mutableStateOf(initial.matchCharacterPersonality) }
    var matchCharDepth by remember(initial) { mutableStateOf(initial.matchCharacterDepthPrompt) }
    var matchScenario by remember(initial) { mutableStateOf(initial.matchScenario) }
    var matchCreatorNotes by remember(initial) { mutableStateOf(initial.matchCreatorNotes) }
    var preventRecursion by remember(initial) { mutableStateOf(initial.preventRecursion) }
    var excludeRecursion by remember(initial) { mutableStateOf(initial.excludeRecursion) }
    var delayUntilRecursion by remember(initial) { mutableStateOf(initial.delayUntilRecursion.toString()) }
    var useProbability by remember(initial) { mutableStateOf(initial.useProbability) }
    var probability by remember(initial) { mutableStateOf(initial.probability.toString()) }
    var ignoreBudget by remember(initial) { mutableStateOf(initial.ignoreBudget) }
    var triggers by remember(initial) { mutableStateOf(initial.triggers) }
    var outletName by remember(initial) { mutableStateOf(initial.outletName) }
    var sticky by remember(initial) { mutableStateOf(initial.sticky?.toString() ?: "") }
    var cooldown by remember(initial) { mutableStateOf(initial.cooldown?.toString() ?: "") }
    var delay by remember(initial) { mutableStateOf(initial.delay?.toString() ?: "") }
    var group by remember(initial) { mutableStateOf(initial.group) }
    var groupWeight by remember(initial) { mutableStateOf(initial.groupWeight.toString()) }
    var groupOverride by remember(initial) { mutableStateOf(initial.groupOverride) }
    var useGroupScoring by remember(initial) { mutableStateOf(initial.useGroupScoring) }
    var filterNames by remember(initial) { mutableStateOf(initial.characterFilterNames) }
    var filterTags by remember(initial) { mutableStateOf(initial.characterFilterTags) }
    var filterExclude by remember(initial) { mutableStateOf(initial.characterFilterExclude) }
    var vectorized by remember(initial) { mutableStateOf(initial.vectorized) }
    var addMemo by remember(initial) { mutableStateOf(initial.addMemo) }
    var automationId by remember(initial) { mutableStateOf(initial.automationId) }
    var displayIndex by remember(initial) { mutableStateOf(initial.displayIndex?.toString() ?: "") }

    EmberBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (isNew) "新增世界书条目" else "编辑世界书条目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "聊到触发词时，内容自动注入上下文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            EmberTextField(
                value = keys,
                onValueChange = { keys = it },
                label = { Text("触发词（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EmberTextField(
                value = keySecondary,
                onValueChange = { keySecondary = it },
                label = { Text("次要触发词 keysecondary（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("备注（仅作者可见）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = order,
                onValueChange = { order = it.filter { c -> c.isDigit() } },
                label = { Text("插入顺序") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text("位置（官方 world_info_position）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("角色前(0)" to 0, "角色后(1)" to 1, "AN上(2)" to 2, "AN下(3)" to 3).forEach { (label, v) ->
                    FilterChip(selected = position == v, onClick = { position = v }, label = { Text(label) })
                }
            }
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("深度(4)" to 4, "EM上(5)" to 5, "EM下(6)" to 6, "出口(7)" to 7).forEach { (label, v) ->
                    FilterChip(selected = position == v, onClick = { position = v }, label = { Text(label) })
                }
            }
            EmberTextField(
                value = depth,
                onValueChange = { depth = it.filter { c -> c.isDigit() } },
                label = { Text("深度（at_depth 时生效）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "system", "user" to "user", "assistant" to "assistant").forEach { (label, v) ->
                    FilterChip(selected = role == v, onClick = { role = v }, label = { Text(label) })
                }
            }
            EmberTextField(
                value = selectiveLogic,
                onValueChange = { selectiveLogic = it.filter { c -> c.isDigit() } },
                label = { Text("选择逻辑 selectiveLogic（0=AND_ANY 1=AND_ALL）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = scanDepth,
                onValueChange = { scanDepth = it.filter { c -> c.isDigit() } },
                label = { Text("扫描深度 scanDepth（空=默认）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("大小写敏感 caseSensitive", caseSensitive) { caseSensitive = it }
            SwitchRow("整词匹配 matchWholeWords", matchWholeWords) { matchWholeWords = it }
            SwitchRow("匹配人设描述", matchPersona) { matchPersona = it }
            SwitchRow("匹配角色描述", matchCharDesc) { matchCharDesc = it }
            SwitchRow("匹配角色性格", matchCharPersona) { matchCharPersona = it }
            SwitchRow("匹配角色深度提示", matchCharDepth) { matchCharDepth = it }
            SwitchRow("匹配场景", matchScenario) { matchScenario = it }
            SwitchRow("匹配作者备注", matchCreatorNotes) { matchCreatorNotes = it }
            SwitchRow("禁止递归 preventRecursion", preventRecursion) { preventRecursion = it }
            SwitchRow("排除递归 excludeRecursion", excludeRecursion) { excludeRecursion = it }
            EmberTextField(
                value = delayUntilRecursion,
                onValueChange = { delayUntilRecursion = it.filter { c -> c.isDigit() } },
                label = { Text("延迟到递归 delayUntilRecursion") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("按概率触发 useProbability", useProbability) { useProbability = it }
            EmberTextField(
                value = probability,
                onValueChange = { probability = it.filter { c -> c.isDigit() } },
                label = { Text("概率 %（1-100）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("不计入预算 ignoreBudget", ignoreBudget) { ignoreBudget = it }
            EmberTextField(
                value = triggers,
                onValueChange = { triggers = it },
                label = { Text("生成类型触发 triggers（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = outletName,
                onValueChange = { outletName = it },
                label = { Text("出口名 outletName（{{outlet::key}}）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = sticky,
                onValueChange = { sticky = it.filter { c -> c.isDigit() } },
                label = { Text("sticky（回合数，空=关闭）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = cooldown,
                onValueChange = { cooldown = it.filter { c -> c.isDigit() } },
                label = { Text("cooldown（回合数，空=关闭）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = delay,
                onValueChange = { delay = it.filter { c -> c.isDigit() } },
                label = { Text("delay（回合数，空=关闭）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = group,
                onValueChange = { group = it },
                label = { Text("分组 group（同组互斥/加权）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = groupWeight,
                onValueChange = { groupWeight = it.filter { c -> c.isDigit() } },
                label = { Text("组权重 groupWeight") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("组内优先 groupOverride", groupOverride) { groupOverride = it }
            SwitchRow("使用组评分 useGroupScoring", useGroupScoring) { useGroupScoring = it }
            EmberTextField(
                value = filterNames,
                onValueChange = { filterNames = it },
                label = { Text("角色过滤 names（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = filterTags,
                onValueChange = { filterTags = it },
                label = { Text("角色过滤 tags（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("角色过滤取反 isExclude", filterExclude) { filterExclude = it }
            SwitchRow("向量化 vectorized（RAG）", vectorized) { vectorized = it }
            SwitchRow("addMemo", addMemo) { addMemo = it }
            EmberTextField(
                value = automationId,
                onValueChange = { automationId = it },
                label = { Text("automationId（快捷回复自动执行）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = displayIndex,
                onValueChange = { displayIndex = it.filter { c -> c.isDigit() } },
                label = { Text("编辑器排序 displayIndex（空=自动）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SwitchRow("恒定（常驻上下文）", constant) { constant = it }
            SwitchRow("选择性（配合逻辑）", selective) { selective = it }
            SwitchRow("启用", enabled) { enabled = it }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) {
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) { Text("删除条目", color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = {
                        onSave(
                            WorldEntryDraft(
                                id = initial.id,
                                keys = keys.trim(),
                                keySecondary = keySecondary.trim(),
                                content = content,
                                comment = comment,
                                constant = constant,
                                selective = selective,
                                selectiveLogic = selectiveLogic.toIntOrNull() ?: 0,
                                enabled = enabled,
                                insertionOrder = order.toIntOrNull() ?: 100,
                                position = position,
                                depth = depth.toIntOrNull(),
                                role = role,
                                caseSensitive = caseSensitive,
                                matchWholeWords = matchWholeWords,
                                scanDepth = scanDepth.toIntOrNull(),
                                matchPersonaDescription = matchPersona,
                                matchCharacterDescription = matchCharDesc,
                                matchCharacterPersonality = matchCharPersona,
                                matchCharacterDepthPrompt = matchCharDepth,
                                matchScenario = matchScenario,
                                matchCreatorNotes = matchCreatorNotes,
                                preventRecursion = preventRecursion,
                                excludeRecursion = excludeRecursion,
                                delayUntilRecursion = delayUntilRecursion.toIntOrNull() ?: 0,
                                useProbability = useProbability,
                                probability = probability.toIntOrNull() ?: 100,
                                ignoreBudget = ignoreBudget,
                                triggers = triggers.trim(),
                                outletName = outletName.trim(),
                                sticky = sticky.toIntOrNull(),
                                cooldown = cooldown.toIntOrNull(),
                                delay = delay.toIntOrNull(),
                                group = group.trim(),
                                groupWeight = groupWeight.toIntOrNull() ?: 100,
                                groupOverride = groupOverride,
                                useGroupScoring = useGroupScoring,
                                characterFilterNames = filterNames.trim(),
                                characterFilterTags = filterTags.trim(),
                                characterFilterExclude = filterExclude,
                                vectorized = vectorized,
                                addMemo = addMemo,
                                automationId = automationId.trim(),
                                displayIndex = displayIndex.toIntOrNull(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存条目") }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        EmberSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RegexRow(script: CharacterRegexScript, onEdit: () -> Unit, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (script.disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    script.scriptName.ifBlank { "（未命名正则）" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (script.scriptName.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    script.findRegex.ifBlank { "（空匹配式）" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "替换为 ${script.replaceString.ifBlank { "（空）" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(FaIcons.Pencil, contentDescription = "编辑正则", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
            }
            EmberSwitch(checked = !script.disabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun RegexEditorSheet(
    initial: CharacterRegexScript,
    isNew: Boolean,
    onSave: (CharacterRegexScript) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scriptName by remember(initial) { mutableStateOf(initial.scriptName) }
    var findRegex by remember(initial) { mutableStateOf(initial.findRegex) }
    var replaceString by remember(initial) { mutableStateOf(initial.replaceString) }
    var trimStrings by remember(initial) { mutableStateOf(initial.trimStrings.joinToString(", ")) }
    var placement by remember(initial) { mutableStateOf(initial.placement.toSet()) }
    var disabled by remember(initial) { mutableStateOf(initial.disabled) }
    var markdownOnly by remember(initial) { mutableStateOf(initial.markdownOnly) }
    var promptOnly by remember(initial) { mutableStateOf(initial.promptOnly) }
    var runOnEdit by remember(initial) { mutableStateOf(initial.runOnEdit) }
    var minDepth by remember(initial) { mutableStateOf(initial.minDepth?.toString() ?: "") }
    var maxDepth by remember(initial) { mutableStateOf(initial.maxDepth?.toString() ?: "") }
    var substituteRegex by remember(initial) { mutableStateOf(initial.substituteRegex) }

    val placementOptions = listOf(
        1 to "用户输入",
        2 to "AI 输出",
        5 to "世界书",
        6 to "推理",
    )

    EmberBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (isNew) "新增该卡正则" else "编辑正则",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "字段对齐官方 RegexScriptData（char-data.js），保存进 data.extensions.regex_scripts。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            EmberTextField(
                value = scriptName,
                onValueChange = { scriptName = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EmberTextField(
                value = findRegex,
                onValueChange = { findRegex = it },
                label = { Text("匹配式（支持 /pat/flags）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = replaceString,
                onValueChange = { replaceString = it },
                label = { Text("替换为（支持 $1 / $<name> / {{match}}）") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            EmberTextField(
                value = trimStrings,
                onValueChange = { trimStrings = it },
                label = { Text("裁剪串（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "应用位置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                placementOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = value in placement,
                        onClick = {
                            placement = if (value in placement) placement - value else placement + value
                        },
                        label = { Text(label) },
                    )
                }
            }
            SwitchRow("禁用", disabled) { disabled = it }
            SwitchRow("仅 Markdown 显示", markdownOnly) { markdownOnly = it }
            SwitchRow("仅提示词", promptOnly) { promptOnly = it }
            SwitchRow("编辑消息时也执行", runOnEdit) { runOnEdit = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                EmberTextField(
                    value = minDepth,
                    onValueChange = { minDepth = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("最小深度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                EmberTextField(
                    value = maxDepth,
                    onValueChange = { maxDepth = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("最大深度") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "匹配式宏替换",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0 to "不替换", 1 to "原样替换", 2 to "转义替换").forEach { (value, label) ->
                    FilterChip(
                        selected = substituteRegex == value,
                        onClick = { substituteRegex = value },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) {
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = {
                        onSave(
                            CharacterRegexScript(
                                id = initial.id,
                                scriptName = scriptName.trim(),
                                findRegex = findRegex.trim(),
                                replaceString = replaceString,
                                trimStrings = trimStrings.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                placement = placementOptions.map { it.first }.filter { it in placement },
                                disabled = disabled,
                                markdownOnly = markdownOnly,
                                promptOnly = promptOnly,
                                runOnEdit = runOnEdit,
                                minDepth = minDepth.toIntOrNull(),
                                maxDepth = maxDepth.toIntOrNull(),
                                substituteRegex = substituteRegex,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun SimpleEditRow(
    title: String,
    subtitle: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
            Text(
                title.ifBlank { "（空）" },
                style = MaterialTheme.typography.titleSmall,
                color = if (title.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(FaIcons.Pencil, contentDescription = "编辑", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(FaIcons.TrashCan, contentDescription = "删除", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

/** 模型覆盖摘要（UI 展示用）。 */
private fun ModelOverride.summary(): String {
    val parts = mutableListOf<String>()
    if (model.isNotBlank()) parts += model
    contextWindow?.let { parts += "上下文 ${it}" }
    maxTokens?.let { parts += "回复 ${it}" }
    temperature?.let { parts += "温度 $it" }
    topP?.let { parts += "topP $it" }
    presencePenalty?.let { parts += "pres $it" }
    frequencyPenalty?.let { parts += "freq $it" }
    return if (parts.isEmpty()) "未设置，跟随全局（点击展开编辑）" else parts.joinToString(" · ")
}

/** 是否完全未设置（跟随全局）。 */
private fun ModelOverride.isEmpty(): Boolean =
    model.isBlank() && maxTokens == null && contextWindow == null && temperature == null &&
        topP == null && presencePenalty == null && frequencyPenalty == null

/** 主题配方摘要（UI 展示用）。 */
private fun ThemeRecipe.summary(): String {
    val parts = mutableListOf<String>()
    if (seed.isNotBlank()) parts += "seed $seed"
    if (shape == "square") parts += "方正"
    if (shape == "rounded") parts += "圆润"
    if (shape == "circle") parts += "浑圆"
    if (font == "serif" || font == "source") parts += "衬线"
    if (style == "airy") parts += "轻盈"
    if (style == "calm") parts += "沉静"
    if (style == "vivid") parts += "鲜明"
    if (lockMode == "system") parts += "锁定跟随系统"
    if (lockMode == "light") parts += "锁定浅色"
    if (lockMode == "dark") parts += "锁定深色"
    return if (parts.isEmpty()) "未设置，跟随全局（点击展开编辑）" else parts.joinToString(" · ")
}

/** 是否完全未设置（跟随全局）。 */
private fun ThemeRecipe.isEmpty(): Boolean =
    seed.isBlank() && shape.isBlank() && font.isBlank() && style.isBlank() && lockMode.isBlank()

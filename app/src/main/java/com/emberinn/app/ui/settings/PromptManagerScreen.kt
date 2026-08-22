@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberinn.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.PromptAssemblyCache
import com.emberinn.app.data.PromptManagerPrefs
import com.emberinn.app.ui.components.EmberBottomSheet
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.engine.prompt.PromptCollection
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptManagerCore
import com.emberinn.engine.prompt.PromptOrderEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Prompt Manager（官方 PromptManager 面板字段）：顺序 + 提示项 + marker/system_prompt，全局存储；
 *  dryRun 提示词预览在聊天顶栏菜单（会话菜单 → 提示词预览）。 */
@Composable
fun PromptManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prompts by remember { mutableStateOf(PromptManagerPrefs.prompts(context)) }
    // 官方 1.18 PromptManager global 策略：全局一份顺序（character_id=100000），无角色分叉。
    var order by remember { mutableStateOf(PromptManagerPrefs.order(context)) }
    var editTarget by remember { mutableStateOf<PromptItem?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }
    var inspectTarget by remember { mutableStateOf<PromptItem?>(null) }
    // 触屏拖动排序：长按整行上下拖动（官方 sortable 拖拽的移动端等价），无 ↑↓ 按钮。
    // draggingOrderId 跟踪当前拖拽项，graphicsLayer 让行跟随手指，跨越半行高即换位。
    var draggingOrderId by remember { mutableStateOf<String?>(null) }
    var dragOrderOffset by remember { mutableFloatStateOf(0f) }
    val orderHeights = remember { mutableMapOf<String, Int>() }

    fun save(p: List<PromptItem>, o: List<PromptOrderEntry>) {
        prompts = p
        order = o
        PromptManagerPrefs.savePrompts(context, p)
        PromptManagerPrefs.saveOrder(context, null, o)
    }

    /** 官方 handleFullExport：只导非 system_prompt、非 marker 的用户提示项 + 全局顺序（条目数组）。 */
    fun exportJson(): String {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val exportPrompts = prompts.filter { !it.systemPrompt && !it.marker }
        return json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject {
                put("version", JsonPrimitive(1))
                put("type", JsonPrimitive("full"))
                put("data", buildJsonObject {
                    put("prompts", kotlinx.serialization.json.JsonArray(exportPrompts.map { json.encodeToJsonElement(it) }))
                    put("prompt_order", kotlinx.serialization.json.JsonArray(order.map { json.encodeToJsonElement(it) }))
                })
            },
        )
    }

    /** 官方 import()：validateObject + mergeKeepNewer（同名后写覆盖）+ 全局顺序 Object.assign 按下标替换。 */
    fun importJson(text: String): String? {
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return "导入失败：不是有效 JSON"
        val version = (parsed["version"] as? JsonPrimitive)?.content
        val data = parsed["data"] as? JsonObject ?: return "导入失败：结构不符（缺少 data）"
        val importedPrompts = (data["prompts"] as? JsonArray) ?: return "导入失败：结构不符（缺少 prompts）"
        if (version != "1" || parsed["type"] !is JsonPrimitive) return "导入失败：结构不符（version/type）"
        val decoded = importedPrompts.mapNotNull { el ->
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromJsonElement(com.emberinn.engine.prompt.PromptItem.serializer(), el)
            }.getOrNull()
        }
        // mergeKeepNewer：既有 + 导入，按 identifier 去重，后写（导入）覆盖
        val merged = LinkedHashMap<String, PromptItem>()
        prompts.forEach { merged[it.identifier] = it }
        decoded.forEach { merged[it.identifier] = it }
        val mergedPrompts = merged.values.toList()
        // 全局顺序：官方 Object.assign(promptOrder, imported) —— 按下标替换，短的保留原有尾部
        val importedOrder = (data["prompt_order"] as? JsonArray)?.mapNotNull { el ->
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromJsonElement(com.emberinn.engine.prompt.PromptOrderEntry.serializer(), el)
            }.getOrNull()
        } ?: emptyList()
        val mergedOrder = importedOrder.toMutableList()
        if (mergedOrder.size < order.size) mergedOrder.addAll(order.subList(mergedOrder.size, order.size))
        save(mergedPrompts, mergedOrder)
        return null
    }

    var importMessage by remember { mutableStateOf<String?>(null) }
    // 官方 handleCharacterReset 的确认语义；用页面内联确认（AlertDialog 在该玻璃页曾出现点击无响应，不再用模态）
    var resetConfirming by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        importJson(text)?.let { err -> importMessage = err }
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "提示词管理器（Prompt Manager）", onBack = onBack, sky = settingsSky)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("说明", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "对齐官方 PromptManager（1.18 global 策略）：顺序决定提示项注入次序，提示项决定内容/角色/位置/深度；全局顺序存 character_id=100000，与官方 preset 互导。提示词预览在聊天会话菜单（dryRun）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                item {
                    Text("全局顺序（官方 global 策略，character_id=100000）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { exportLauncher.launch("st-prompts-${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM_dd_yyyy"))}.json") }) { Text("导出全部") }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("导入") }
                        if (resetConfirming) {
                            Text("确认恢复官方默认顺序？", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                            TextButton(onClick = {
                                PromptManagerPrefs.resetOrderToDefault(context)
                                order = PromptManagerPrefs.order(context)
                                resetConfirming = false
                                importMessage = "已重置为官方默认顺序（不会删除任何提示项）"
                            }) { Text("确认") }
                            TextButton(onClick = { resetConfirming = false }) { Text("取消") }
                        } else {
                            TextButton(onClick = { resetConfirming = true }) { Text("重置顺序") }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("注入顺序", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(
                            "总 Token：${PromptAssemblyCache.lastMessages?.sumOf { it.tokens } ?: 0}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (order.isEmpty()) {
                    item { Text("未自定义顺序，使用官方默认顺序。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                importMessage?.let {
                    item { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
                order.forEachIndexed { i, entry ->
                    item(key = "order-${entry.identifier}") {
                        val isDragging = draggingOrderId == entry.identifier
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragging) dragOrderOffset else 0f }
                                .onSizeChanged { orderHeights[entry.identifier] = it.height }
                                .pointerInput(entry.identifier) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingOrderId = entry.identifier
                                            dragOrderOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (draggingOrderId == entry.identifier) {
                                                dragOrderOffset += amount.y
                                                val h = (orderHeights[entry.identifier] ?: 0).coerceAtLeast(1)
                                                var crossed = (dragOrderOffset / h).toInt()
                                                while (crossed != 0) {
                                                    val idx = order.indexOfFirst { it.identifier == entry.identifier }
                                                    val target = idx + crossed
                                                    if (target !in order.indices || target == idx) break
                                                    val list = order.toMutableList()
                                                    list.add(target, list.removeAt(idx))
                                                    order = list
                                                    PromptManagerPrefs.saveOrder(context, null, order)
                                                    dragOrderOffset -= crossed * h
                                                    crossed = (dragOrderOffset / h).toInt()
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingOrderId = null
                                            dragOrderOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingOrderId = null
                                            dragOrderOffset = 0f
                                        },
                                    )
                                },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("≡", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(24.dp))
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.identifier, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                EmberSwitch(
                                    checked = entry.enabled,
                                    onCheckedChange = { on ->
                                        order = order.mapIndexed { j, e -> if (j == i) e.copy(enabled = on) else e }
                                        PromptManagerPrefs.saveOrder(context, null, order)
                                    },
                                )
                                // 小的 ↑↓ 提示按钮：与长按拖动并存（官方 sortable 的移动端补充入口）
                                IconButton(onClick = {
                                    if (i > 0) {
                                        order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                                        PromptManagerPrefs.saveOrder(context, null, order)
                                    }
                                }, modifier = Modifier.size(26.dp)) {
                                    Text("↑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = {
                                    if (i < order.lastIndex) {
                                        order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                        PromptManagerPrefs.saveOrder(context, null, order)
                                    }
                                }, modifier = Modifier.size(26.dp)) {
                                    Text("↓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                IconButton(onClick = {
                                    order = order.filterIndexed { j, _ -> j != i }
                                    PromptManagerPrefs.saveOrder(context, null, order)
                                }, modifier = Modifier.size(30.dp)) { Text("×", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
                item {
                    // 官方 handleAppendPrompt：从现有提示项下拉追加到当前角色/全局顺序
                    val appendCandidates = remember(prompts, order) {
                        (PromptCollection.DEFAULT_PROMPTS.map { it.identifier } + prompts.map { it.identifier })
                            .distinct()
                            .filter { id -> order.none { it.identifier == id } }
                    }
                    var appendOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { appendOpen = true },
                            enabled = appendCandidates.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("追加提示项到顺序（官方 Append prompt）") }
                        DropdownMenu(expanded = appendOpen, onDismissRequest = { appendOpen = false }) {
                            appendCandidates.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        order = order + PromptOrderEntry(id)
                                        PromptManagerPrefs.saveOrder(context, null, order)
                                        appendOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("提示项", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = {
                            editTarget = PromptItem(identifier = "", name = "", content = "")
                            showEdit = true
                        }) { Text("新增提示项") }
                    }
                }
                // 默认提示项（只读参考，顺序启用由上方控制）
                PromptCollection.DEFAULT_PROMPTS.forEach { def ->
                    item(key = "def-${def.identifier}") {
                        PromptRow(
                            item = def,
                            userDefined = false,
                            enabledInOrder = order.firstOrNull { it.identifier == def.identifier }?.enabled
                                ?: PromptManagerCore.DEFAULT_ORDER_ENTRIES.firstOrNull { it.identifier == def.identifier }?.enabled
                                ?: true,
                            onToggleOrder = { on ->
                                order = if (order.any { it.identifier == def.identifier }) {
                                    order.map { if (it.identifier == def.identifier) it.copy(enabled = on) else it }
                                } else {
                                    order + PromptOrderEntry(def.identifier, enabled = on)
                                }
                                PromptManagerPrefs.saveOrder(context, null, order)
                            },
                            // 官方默认项可编辑：保存为用户覆盖项（同名 identifier 优先于默认）
                            onEdit = { editTarget = def; showEdit = true },
                            onInspect = { inspectTarget = def },
                            onDelete = null,
                        )
                    }
                }
                prompts.forEachIndexed { i, item ->
                    item(key = "user-$i") {
                        PromptRow(
                            item = item,
                            userDefined = true,
                            enabledInOrder = order.firstOrNull { it.identifier == item.identifier }?.enabled ?: true,
                            onToggleOrder = { on ->
                                order = if (order.any { it.identifier == item.identifier }) {
                                    order.map { if (it.identifier == item.identifier) it.copy(enabled = on) else it }
                                } else {
                                    order + PromptOrderEntry(item.identifier, enabled = on)
                                }
                                PromptManagerPrefs.saveOrder(context, null, order)
                            },
                            onEdit = { editTarget = item; showEdit = true },
                            onInspect = { inspectTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { doomed ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除提示项？") },
            text = { Text("将删除「${doomed.name.ifBlank { doomed.identifier }}」，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    prompts = prompts.filterNot { it.identifier == doomed.identifier }
                    PromptManagerPrefs.savePrompts(context, prompts)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    if (showEdit) {
        val target = editTarget ?: return
        // 官方 PromptManager handleNewPrompt：identifier 自动 uuid，编辑表单不暴露 identifier；
        // 编辑表单字段 = name/role/injection_trigger/position/depth/order/forbid_overrides/content。
        val isNew = target.identifier.isBlank()
        val promptId = target.identifier.ifBlank { java.util.UUID.randomUUID().toString() }
        var name by remember(target) { mutableStateOf(target.name) }
        var content by remember(target) { mutableStateOf(target.content) }
        var role by remember(target) { mutableStateOf(target.role) }
        var position by remember(target) { mutableStateOf(target.injectionPosition?.toString() ?: "0") }
        var depth by remember(target) { mutableStateOf(target.injectionDepth?.toString() ?: "4") }
        var injectionOrder by remember(target) { mutableStateOf(target.injectionOrder?.toString() ?: "100") }
        var trigger by remember(target) { mutableStateOf(target.injectionTrigger.toSet()) }
        var forbid by remember(target) { mutableStateOf(target.forbidOverrides) }
        val triggerOptions = listOf(
            "normal" to "Normal", "continue" to "Continue", "impersonate" to "Impersonate",
            "swipe" to "Swipe", "regenerate" to "Regenerate", "quiet" to "Quiet",
        )
        val resettable = promptId in setOf("main", "nsfw", "jailbreak", "enhanceDefinitions")
        EmberBottomSheet(onDismissRequest = { showEdit = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (isNew) "新增提示项" else "编辑提示项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                    Text(
                        "identifier（自动生成，只读）：$promptId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    EmberTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    EmberTextField(
                        value = content,
                        onValueChange = { if (!target.marker) content = it },
                        label = { Text(if (target.marker) "内容（marker：由外部注入，不可编辑）" else "内容（支持 {{user}}/{{char}} 宏）") },
                        readOnly = target.marker,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("角色（官方 role）", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "system", "user" to "user", "assistant" to "assistant").forEach { (v, label) ->
                            FilterChip(selected = role == v, onClick = { role = v }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("注入位置（官方 injection_position）", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0" to "Relative（相对顺序）", "1" to "In-chat（对话内）").forEach { (v, label) ->
                            FilterChip(selected = position == v, onClick = { position = v }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    EmberTextField(
                        value = depth,
                        onValueChange = { depth = it.filter { c -> c.isDigit() } },
                        label = { Text("注入深度 injection_depth（默认4，0=末条之后）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    EmberTextField(
                        value = injectionOrder,
                        onValueChange = { injectionOrder = it.filter { c -> c.isDigit() } },
                        label = { Text("注入顺序 injection_order（默认100）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("注入触发（官方 injection_trigger，多选；空=全部）", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(triggerOptions) { (v, label) ->
                            FilterChip(
                                selected = v in trigger,
                                onClick = {
                                    trigger = if (v in trigger) trigger - v else trigger + v
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("禁止覆盖（forbid_overrides）", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        EmberSwitch(checked = forbid, onCheckedChange = { forbid = it })
                    }
                    if (resettable) {
                        TextButton(onClick = {
                            val def = PromptCollection.DEFAULT_PROMPTS.firstOrNull { it.identifier == promptId }
                            if (def != null) {
                                name = def.name
                                content = def.content
                                forbid = false
                            }
                        }) { Text("恢复默认（官方 Reset）") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val updated = PromptItem(
                            identifier = promptId,
                            name = name.ifBlank { promptId },
                            content = content,
                            role = role,
                            enabled = true,
                            marker = target.marker,
                            systemPrompt = if (isNew) false else target.systemPrompt,
                            injectionPosition = position.toIntOrNull(),
                            injectionDepth = depth.toIntOrNull(),
                            injectionOrder = injectionOrder.toIntOrNull() ?: 100,
                            injectionTrigger = trigger.toList(),
                            forbidOverrides = forbid,
                        )
                        val existing = prompts.indexOfFirst { it.identifier == promptId }
                        prompts = if (isNew || existing < 0) prompts + updated
                        else prompts.map { if (it.identifier == promptId) updated else it }
                        PromptManagerPrefs.savePrompts(context, prompts)
                        showEdit = false
                    }) { Text("保存") }
                    TextButton(onClick = { showEdit = false }) { Text("取消") }
                }
            }
        }

    // 官方 handleInspect：显示最近一次总装中该 identifier 的消息集合（role/content/tokens）。
    inspectTarget?.let { inspected ->
        val msgs = remember(inspected) {
            PromptAssemblyCache.lastMessages?.filter { it.identifier == inspected.identifier } ?: emptyList()
        }
        EmberBottomSheet(onDismissRequest = { inspectTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "检查：${inspected.name.ifBlank { inspected.identifier }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(6.dp))
                if (msgs.isEmpty()) {
                    Text(
                        "该提示项在最近一次总装中没有消息。先发送一条消息或使用聊天菜单的“提示词预览（dryRun）”后，这里才会列出内容（官方 PromptManager.messages 同语义）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(msgs) { m ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    "${m.role} · ${m.tokens}t",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    m.content.ifBlank { "（无内容）" },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { inspectTarget = null }) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun PromptRow(
    item: PromptItem,
    userDefined: Boolean,
    enabledInOrder: Boolean,
    onToggleOrder: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onInspect: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    // 官方 prompt_manager_list：每行显示 token 数 + 图标（marker/global/important/user/injection/角色）
    val tokens = PromptAssemblyCache.lastMessages
        ?.filter { it.identifier == item.identifier }
        ?.sumOf { it.tokens } ?: 0
    val icons = buildList {
        if (item.marker) add("📌" to "Marker")
        if (!item.marker && item.systemPrompt && !item.forbidOverrides) add("🌐" to "Global Prompt")
        if (!item.marker && item.systemPrompt && item.forbidOverrides) add("★" to "Important Prompt")
        if (!item.marker && !item.systemPrompt) add("※" to "Preset Prompt")
        if (item.injectionPosition == 1) add("💉" to "In-Chat Injection")
        if (item.role == "user") add("👤" to "Prompt will be sent as User")
        if (item.role == "assistant") add("🤖" to "Prompt will be sent as Assistant")
        if (item.identifier in PromptAssemblyCache.overriddenPrompts) add("🪪" to "Pulled from a character card")
    }
    // 官方 isPromptEditAllowed / isPromptToggleAllowed：marker 项默认不可编辑/开关，
    // 强制名单（charDescription/charPersonality/scenario/personaDescription/worldInfoBefore/After，
    // toggle 另含 main/chatHistory/dialogueExamples）除外。
    val forceEdit = setOf(
        "charDescription", "charPersonality", "scenario", "personaDescription",
        "worldInfoBefore", "worldInfoAfter",
    )
    val forceToggle = forceEdit + setOf("main", "chatHistory", "dialogueExamples")
    val editAllowed = item.identifier in forceEdit || !item.marker
    val toggleAllowed = !(item.marker && item.identifier !in forceToggle)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onEdit != null && editAllowed) { onEdit?.invoke() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name.ifBlank { item.identifier }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    icons.forEach { (icon, title) ->
                        Spacer(Modifier.width(4.dp))
                        Text(icon, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(item.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (item.injectionPosition == 1 && item.injectionDepth != null) {
                        Spacer(Modifier.width(6.dp))
                        Text("@ ${item.injectionDepth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (!userDefined) {
                        Spacer(Modifier.width(6.dp))
                        Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    item.content.ifBlank { "（空内容）" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (tokens > 0) "$tokens" else "-",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            EmberSwitch(checked = enabledInOrder, enabled = toggleAllowed, onCheckedChange = onToggleOrder)
            if (onInspect != null) {
                IconButton(onClick = onInspect, modifier = Modifier.size(38.dp)) {
                    Text("查", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (userDefined && onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Text("×", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

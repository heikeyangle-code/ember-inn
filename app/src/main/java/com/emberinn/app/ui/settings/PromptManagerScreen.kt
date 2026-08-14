package com.emberinn.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.CharacterStore
import com.emberinn.app.data.PromptManagerPrefs
import com.emberinn.app.ui.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import com.emberinn.app.ui.components.EmberEmptyState
import com.emberinn.engine.prompt.PromptCollection
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.prompt.PromptManagerCore
import com.emberinn.engine.prompt.PromptOrderEntry

/** Prompt Manager（官方 PromptManager 面板字段）：顺序 + 提示项 + marker/system_prompt，全局存储；
 *  dryRun 提示词预览在聊天顶栏菜单（会话菜单 → 提示词预览）。 */
@Composable
fun PromptManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var prompts by remember { mutableStateOf(PromptManagerPrefs.prompts(context)) }
    val characters = remember { CharacterStore(context).list() }
    var selectedChar by remember { mutableStateOf<String?>(null) }
    var order by remember(selectedChar) { mutableStateOf(PromptManagerPrefs.order(context, selectedChar)) }
    var editTarget by remember { mutableStateOf<PromptItem?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }
    var newOrderId by remember { mutableStateOf("") }

    fun save(p: List<PromptItem>, o: List<PromptOrderEntry>) {
        prompts = p
        order = o
        PromptManagerPrefs.savePrompts(context, p)
        PromptManagerPrefs.saveOrder(context, selectedChar, o)
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
                                "对齐官方 PromptManager：顺序决定提示项注入次序，提示项决定内容/角色/位置/深度。App 当前为全局条目 + 全局顺序（每角色顺序登记扩展）；dryRun 预览登记下一步。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                item {
                    Text("顺序作用对象（官方 prompt_order 按角色 id）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "global") {
                            FilterChip(selected = selectedChar == null, onClick = { selectedChar = null }, label = { Text("全局") })
                        }
                        items(characters.size, key = { i -> characters[i].id }) { i ->
                            FilterChip(selected = selectedChar == characters[i].id, onClick = { selectedChar = characters[i].id }, label = { Text(characters[i].name) })
                        }
                    }
                }
                item {
                    Text("注入顺序", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (order.isEmpty()) {
                    item { Text("未自定义顺序，使用官方默认顺序。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                order.forEachIndexed { i, entry ->
                    item(key = "order-${entry.identifier}") {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.identifier, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                EmberSwitch(
                                    checked = entry.enabled,
                                    onCheckedChange = { on ->
                                        order = order.mapIndexed { j, e -> if (j == i) e.copy(enabled = on) else e }
                                        PromptManagerPrefs.saveOrder(context, selectedChar, order)
                                    },
                                )
                                IconButton(onClick = {
                                    if (i > 0) {
                                        order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                                        PromptManagerPrefs.saveOrder(context, selectedChar, order)
                                    }
                                }, modifier = Modifier.size(30.dp)) { Text("↑", style = MaterialTheme.typography.labelMedium) }
                                IconButton(onClick = {
                                    if (i < order.lastIndex) {
                                        order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                        PromptManagerPrefs.saveOrder(context, selectedChar, order)
                                    }
                                }, modifier = Modifier.size(30.dp)) { Text("↓", style = MaterialTheme.typography.labelMedium) }
                                IconButton(onClick = {
                                    order = order.filterIndexed { j, _ -> j != i }
                                    PromptManagerPrefs.saveOrder(context, selectedChar, order)
                                }, modifier = Modifier.size(30.dp)) { Text("×", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        EmberTextField(
                            value = newOrderId,
                            onValueChange = { newOrderId = it },
                            label = { Text("追加顺序项 identifier") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val id = newOrderId.trim()
                            if (id.isNotEmpty()) {
                                order = order + PromptOrderEntry(id)
                                PromptManagerPrefs.saveOrder(context, selectedChar, order)
                                newOrderId = ""
                            }
                        }) { Text("追加") }
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
                                PromptManagerPrefs.saveOrder(context, selectedChar, order)
                            },
                            // 官方默认项可编辑：保存为用户覆盖项（同名 identifier 优先于默认）
                            onEdit = { editTarget = def; showEdit = true },
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
                                PromptManagerPrefs.saveOrder(context, selectedChar, order)
                            },
                            onEdit = { editTarget = item; showEdit = true },
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
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(if (isNew) "新增提示项" else "编辑提示项") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).height(520.dp)) {
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
            },
            confirmButton = {
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
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PromptRow(
    item: PromptItem,
    userDefined: Boolean,
    enabledInOrder: Boolean,
    onToggleOrder: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
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
                    Spacer(Modifier.width(8.dp))
                    Text(item.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
            EmberSwitch(checked = enabledInOrder, enabled = toggleAllowed, onCheckedChange = onToggleOrder)
            if (userDefined && onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Text("×", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

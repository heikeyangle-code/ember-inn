package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.EmberTheme
import com.emberinn.app.ui.design.components.ShellInput
import com.emberinn.app.ui.icons.FaIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.ProviderState
import com.emberinn.app.data.SettingsSnapshotStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 数据与隐私：备份（导出 zip）/ 数据位置 / 清除全部（二次确认）。README：数据透明 + 可撤销有确认。 */
@Composable
fun DataPrivacyScreen(onBack: () -> Unit) {
    val c = EmberTheme.colors
    val context = LocalContext.current

    var showClearConfirm by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf(SettingsSnapshotStore.list(context)) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotName by remember { mutableStateOf("") }
    var confirmRestoreName by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exporting = true
            Thread {
                runCatching {
                    val bytes = buildBackupZip(context)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }.onSuccess {
                    android.os.Handler(context.mainLooper).post {
                        exporting = false
                        Toast.makeText(context, "已导出备份到 下载/EmberInn", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    android.os.Handler(context.mainLooper).post {
                        exporting = false
                        Toast.makeText(context, "导出失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    SettingsGlassPage { settingsSky ->
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "数据与隐私", onBack = onBack, sky = settingsSky)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            DataRow(
                icon = FaIcons.FileLines,
                title = "导出全部数据（备份）",
                subtitle = "角色卡 / 聊天记录 / 会话 / 头像 / 提供商配置 → 一个 zip",
                enabled = !exporting,
                trailing = if (exporting) "导出中…" else "导出",
                onClick = { exportLauncher.launch("EmberInn-备份-${System.currentTimeMillis().toString().takeLast(10)}.zip") },
            )
            DataRow(
                icon = FaIcons.Folder,
                title = "数据存储位置",
                subtitle = context.filesDir.absolutePath + "\n数据仅保存在本机，不上传任何服务器",
                enabled = false,
                onClick = {},
            )
            DataRow(
                icon = FaIcons.TrashCan,
                title = "清除全部数据",
                subtitle = "删除所有角色、聊天、会话与提供商配置，不可撤销",
                danger = true,
                onClick = { showClearConfirm = true },
            )
            // 设置快照（官方 user.js 设置快照语义：命名保存/恢复/删除 SharedPreferences + 提供商档案）
            DataRow(
                icon = FaIcons.FileLines,
                title = "设置快照",
                subtitle = "保存/恢复设置与提供商配置（不含聊天数据）；恢复后重启 App 完全生效",
                trailing = "新建",
                onClick = {
                    snapshotName = ""
                    showSnapshotDialog = true
                },
            )
            snapshots.forEach { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, vertical = 7.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("设置快照", color = c.inkMute, fontSize = 12.sp)
                    }
                    Text(
                        "恢复",
                        color = c.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { confirmRestoreName = name }.padding(6.dp),
                    )
                    Icon(
                        FaIcons.TrashCan,
                        contentDescription = "删除快照",
                        tint = c.danger,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                SettingsSnapshotStore.delete(context, name)
                                snapshots = SettingsSnapshotStore.list(context)
                                Toast.makeText(context, "已删除快照", Toast.LENGTH_SHORT).show()
                            }
                            .padding(2.dp),
                    )
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    if (showSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showSnapshotDialog = false },
            title = { Text("新建设置快照") },
            text = {
                ShellInput(
                    value = snapshotName,
                    onValueChange = { snapshotName = it },
                    label = "快照名称",
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = SettingsSnapshotStore.create(context, snapshotName.trim())
                    showSnapshotDialog = false
                    snapshots = SettingsSnapshotStore.list(context)
                    Toast.makeText(
                        context,
                        if (ok) "已创建快照" else "快照名称无效",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showSnapshotDialog = false }) { Text("取消") }
            },
        )
    }
    confirmRestoreName?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmRestoreName = null },
            title = { Text("恢复快照“$name”？") },
            text = { Text("会覆盖当前设置与提供商配置，建议先导出备份；恢复后需重启 App 完全生效。") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = SettingsSnapshotStore.restore(context, name)
                    confirmRestoreName = null
                    ProviderState.refresh(null)
                    Toast.makeText(context, if (ok) "已恢复快照，重启后完全生效" else "恢复失败", Toast.LENGTH_SHORT).show()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreName = null }) { Text("取消") }
            },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部数据？") },
            text = { Text("会删除所有角色卡、聊天、会话、世界书、群聊、快照、媒体、提供商配置和本地文件，此操作不可撤销。建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        listOf(
                            "characters", "chats", "sessions", "avatars", "provider", "media",
                            "worlds", "groups", "snapshots", "expressions", "fonts", "vector",
                        ).forEach { name ->
                            File(context.filesDir, name).deleteRecursively()
                        }
                        // 快捷回复：目录 + legacy 单文件
                        File(context.filesDir, "quick-replies").deleteRecursively()
                        File(context.filesDir, "quick-replies.json").delete()
                    }
                    showClearConfirm = false
                    ProviderState.refresh(null)
                    Toast.makeText(context, "已清除全部本地数据", Toast.LENGTH_SHORT).show()
                }) { Text("清除", color = EmberTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
    }
}

@Composable
private fun DataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val c = EmberTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (danger) c.danger else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = c.inkMute, fontSize = 12.sp)
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = if (enabled) c.accent else c.inkMute, fontSize = 13.sp)
        }
    }
}

/** 打包备份：characters/sessions/chats/avatars/provider 全部进 zip（保留相对路径）。 */
private fun buildBackupZip(context: android.content.Context): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        val dirs = listOf("characters", "sessions", "chats", "avatars", "provider", "media")
        dirs.forEach { dirName ->
            val dir = File(context.filesDir, dirName)
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry("${dirName}/${file.name}"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
        }
    }
    return out.toByteArray()
}

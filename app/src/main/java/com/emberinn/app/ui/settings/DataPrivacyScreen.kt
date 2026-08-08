package com.emberinn.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.emberinn.app.data.ProviderState
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 数据与隐私：备份（导出 zip）/ 数据位置 / 清除全部（二次确认）。README：数据透明 + 可撤销有确认。 */
@Composable
fun DataPrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var showClearConfirm by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text("数据与隐私", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("备份 / 导出 / 清除数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DataRow(
                icon = Icons.Filled.Description,
                title = "导出全部数据（备份）",
                subtitle = "角色卡 / 聊天记录 / 会话 / 头像 / 提供商配置 → 一个 zip",
                enabled = !exporting,
                trailing = if (exporting) "导出中…" else "导出",
                onClick = { exportLauncher.launch("EmberInn-备份-${System.currentTimeMillis().toString().takeLast(10)}.zip") },
            )
            DataRow(
                icon = Icons.Filled.Folder,
                title = "数据存储位置",
                subtitle = context.filesDir.absolutePath + "\n数据仅保存在本机，不上传任何服务器",
                enabled = false,
                onClick = {},
            )
            DataRow(
                icon = Icons.Filled.Delete,
                title = "清除全部数据",
                subtitle = "删除所有角色、聊天、会话与提供商配置，不可撤销",
                danger = true,
                onClick = { showClearConfirm = true },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部数据？") },
            text = { Text("会删除所有角色卡、聊天记录、会话和提供商配置，此操作不可撤销。建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        listOf("characters", "chats", "sessions", "avatars", "provider", "media").forEach { name ->
                            File(context.filesDir, name).deleteRecursively()
                        }
                    }
                    showClearConfirm = false
                    ProviderState.refresh(null)
                    Toast.makeText(context, "已清除全部本地数据", Toast.LENGTH_SHORT).show()
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
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
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
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

package com.emberinn.app.data

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 设置快照：对齐官方 user.js 的命名设置快照语义（保存/加载/列表/删除）。
 * 本 App 快照内容 = 全部 SharedPreferences（shared_prefs/*.xml）+ 提供商档案（provider/*.json）。
 * 边界登记：SharedPreferences 有进程内缓存，恢复后需重启 App 完全生效（官方会热重载设置，App 侧登记）。
 */
object SettingsSnapshotStore {

    private fun dir(context: Context): File = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun list(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.extension == "zip" }
            ?.map { it.nameWithoutExtension }
            ?.sortedDescending()
            ?: emptyList()

    fun create(context: Context, name: String): Boolean = runCatching {
        val safe = sanitize(name)
        if (safe.isBlank()) return false
        val out = File(dir(context), "$safe.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("shared_prefs/${f.name}"))
                zip.write(f.readBytes())
                zip.closeEntry()
            }
            val providerDir = File(context.filesDir, "provider")
            providerDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("provider/${f.name}"))
                zip.write(f.readBytes())
                zip.closeEntry()
            }
        }
        true
    }.getOrDefault(false)

    fun restore(context: Context, name: String): Boolean = runCatching {
        val zipFile = File(dir(context), "${sanitize(name)}.zip")
        if (!zipFile.exists()) return false
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = when {
                    entry.name.startsWith("shared_prefs/") ->
                        File(context.applicationInfo.dataDir, "shared_prefs/${entry.name.removePrefix("shared_prefs/")}")
                    entry.name.startsWith("provider/") ->
                        File(context.filesDir, "provider/${entry.name.removePrefix("provider/")}")
                    else -> null
                }
                if (target != null && !entry.isDirectory) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
        true
    }.getOrDefault(false)

    fun delete(context: Context, name: String) {
        File(dir(context), "${sanitize(name)}.zip").delete()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "snapshot" }
}

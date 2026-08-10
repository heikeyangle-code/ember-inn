package com.emberinn.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 氛围字体下载（README UI 质感清单 7：字体真正落地，不只是“可下载”）。
 * 当前支持霞鹜文楷（LXGW WenKai）：从官方 Release 拉 zip → 解出 Regular TTF →
 * filesDir/fonts/lxgw.ttf；MainActivity 用 Typeface 加载，选择后即时生效。
 */
object FontManager {

    private const val LXGW_VERSION = "v1.330"
    private const val LXGW_ZIP_URL =
        "https://github.com/lxgw/LxgwWenKai/releases/download/$LXGW_VERSION/lxgw-wenkai-$LXGW_VERSION.zip"
    private const val FONT_FILE = "lxgw.ttf"
    private const val MIN_VALID_BYTES = 100_000L

    fun lxgwFile(context: Context): File? {
        val f = File(File(context.filesDir, "fonts"), FONT_FILE)
        return f.takeIf { it.exists() && it.length() > MIN_VALID_BYTES }
    }

    suspend fun ensureLxgw(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            lxgwFile(context)?.let { return@runCatching it }
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val target = File(dir, FONT_FILE)
            val zip = File(context.cacheDir, "lxgw-wenkai-$LXGW_VERSION.zip")
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(LXGW_ZIP_URL).build()).execute().use { resp ->
                check(resp.isSuccessful) { "下载失败（HTTP ${resp.code}）" }
                val body = resp.body ?: error("下载内容为空")
                body.byteStream().use { input ->
                    zip.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            ZipInputStream(zip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                var found = false
                while (entry != null) {
                    if (entry.name.endsWith("LXGWWenKai-Regular.ttf")) {
                        target.outputStream().use { out -> zis.copyTo(out) }
                        found = true
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                check(found) { "压缩包里没有找到 LXGWWenKai-Regular.ttf" }
            }
            zip.delete()
            check(target.length() > MIN_VALID_BYTES) { "字体文件异常（可能下载不完整）" }
            target
        }
    }
}

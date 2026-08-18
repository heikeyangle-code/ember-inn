package com.emberinn.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 氛围字体下载（README UI 质感清单 7：字体真正落地，不只是“可下载”）。
 * - Noto Sans（酒馆官方 --mainFontFamily）：Google Fonts TTF 直下 → filesDir/fonts/NotoSans-*.ttf
 * - MainActivity 用 Typeface 加载，选择后即时生效；WebView 兜底 CSS 用同一批 TTF（file:// 指向 filesDir）。
 * - 霞鹜文楷（70MB 下载）已下线：cleanupLegacy() 启动时静默回收旧文件。
 */
object FontManager {

    private val NOTO_FILES = listOf(
        "NotoSans-Regular" to "https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSans/NotoSans-Regular.ttf",
        "NotoSans-Bold" to "https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSans/NotoSans-Bold.ttf",
        "NotoSans-Italic" to "https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSans/NotoSans-Italic.ttf",
        "NotoSans-BoldItalic" to "https://github.com/googlefonts/noto-fonts/raw/main/hinted/ttf/NotoSans/NotoSans-BoldItalic.ttf",
    )
    private const val MIN_VALID_BYTES = 100_000L

    /** 回收已下线的字体文件（含旧版 lxgw.ttf 与缓存 zip），释放用户存储。 */
    fun cleanupLegacy(context: Context) {
        val fontsDir = File(context.filesDir, "fonts")
        File(fontsDir, "lxgw.ttf").delete()
        context.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith("lxgw-wenkai-")) it.delete()
        }
    }

    /** 酒馆官方 Noto Sans 4 面（Regular/Bold/Italic/BoldItalic）：原生与 WebView 共用同一批 TTF。 */
    fun notoFiles(context: Context): List<File> =
        NOTO_FILES.map { (name, _) -> File(File(context.filesDir, "fonts"), "$name.ttf") }
            .filter { it.exists() && it.length() > MIN_VALID_BYTES }

    fun notoReady(context: Context): Boolean = notoFiles(context).size == NOTO_FILES.size

    suspend fun ensureNoto(context: Context): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            if (notoReady(context)) return@runCatching notoFiles(context)
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            for ((name, url) in NOTO_FILES) {
                val target = File(dir, "$name.ttf")
                if (target.exists() && target.length() > MIN_VALID_BYTES) continue
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    check(resp.isSuccessful) { "下载失败（HTTP ${resp.code}）" }
                    val body = resp.body ?: error("下载内容为空")
                    body.byteStream().use { input -> target.outputStream().use { out -> input.copyTo(out) } }
                }
                check(target.length() > MIN_VALID_BYTES) { "字体文件异常（可能下载不完整）" }
            }
            notoFiles(context)
        }
    }
}

package com.emberinn.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 官方 assets 扩展（public/scripts/extensions/assets/index.js + src/endpoints/assets.js）1:1 翻译：
 * 资产浏览 / 安装 / 删除 / 已装检测，按类型分目录落盘。
 *
 * 与官方对齐点：
 * - KNOWN_TYPES = extension/character/ambient/bgm/blip（index.js:55-61，需求要求的 5 类）
 * - 服务端 VALID_CATEGORIES = bgm/ambient/blip/live2d/vrm/character/temp（assets.js:14）；
 *   本实现以 KNOWN_TYPES 5 类为入口，validateCategory 兼容服务端全集。
 * - populateAssetsMenu：按 type 分桶 + 按 name 排序（index.js:243-283）
 * - buildAssetTypeSection：构建某类型 section（index.js:211-237）
 * - isAssetInstalled：extension 查已装列表 / character 查 avatar / 其它查文件名包含（index.js:350-369）
 * - installAsset：下载 URL → 落盘 assets/<type>/<filename>（index.js:378-413 + assets.js:190-259）
 * - deleteAsset：删除 assets/<type>/<filename>（index.js:421-448 + assets.js:269-304）
 * - updateCurrentAssets = GET /api/assets/get：扫 assets/<type>/ 目录（index.js:512-524 + assets.js:108-180）
 * - validateAssetFileName：仅 [a-zA-Z0-9_\-.] + 非 UNSAFE 扩展 + 非点开头（assets.js:21-52）
 * - previewAsset：音频扩展名 mp3/ogg/wav 预览（index.js:319-339）
 *
 * 存储布局（Android filesDir 下，对齐官方 user assets 目录）：
 *   filesDir/assets/<type>/<filename>     已安装资产文件
 *   filesDir/assets/.installed.json       已装清单（每 type 一个 JSONArray），JSONObject 持久化
 */
object AssetsService {

    /** 官方 KNOWN_TYPES（index.js:55-61），需求要求的 5 类。 */
    val KNOWN_TYPES = linkedMapOf(
        "extension" to "Extensions",
        "character" to "Characters",
        "ambient" to "Ambient sounds",
        "bgm" to "Background music",
        "blip" to "Blip sounds",
    )

    /** 服务端 VALID_CATEGORIES（assets.js:14），validateCategory 兼容全集。 */
    private val VALID_CATEGORIES = listOf("bgm", "ambient", "blip", "live2d", "vrm", "character", "temp", "extension")

    /** 官方 UNSAFE_EXTENSIONS（src/constants.js:64+），禁止安装的可执行类扩展名。 */
    private val UNSAFE_EXTENSIONS = listOf(
        ".php", ".exe", ".com", ".dll", ".pif", ".application", ".gadget", ".msi",
        ".jar", ".cmd", ".bat", ".reg", ".sh", ".py", ".js", ".jse", ".jsp",
        ".pdf", ".html", ".htm", ".hta", ".vb", ".vbs", ".vbe", ".wsf", ".wsh", ".ps1",
    )

    /** 官方 previewAsset 的音频扩展名（index.js:321）。 */
    private val AUDIO_EXTENSIONS = listOf(".mp3", ".ogg", ".wav")

    /** 资产对象（index.js JSON 元素：id/name/description/url/type + 可选 tool/highlight）。 */
    data class Asset(
        val id: String,
        val name: String,
        val description: String,
        val url: String,
        val type: String,
        val tool: Boolean = false,
        val highlight: Boolean = false,
    )

    /** 单个已装资产条目（对应 updateCurrentAssets 输出 items：{filename, url}）。 */
    data class InstalledAsset(
        val type: String,
        val filename: String,
        val path: String,    // 相对 "assets/<type>/<filename>"
        val size: Long,
    )

    // ---------------- 类型 / 校验（index.js:55-61, assets.js:21-52） ----------------

    /** KNOWN_TYPES 是否含某类型。 */
    fun isKnownType(type: String?): Boolean = type != null && KNOWN_TYPES.containsKey(type)

    /** 服务端 VALID_CATEGORIES 校验（assets.js:207-215）。 */
    fun isValidCategory(type: String?): Boolean = type != null && VALID_CATEGORIES.contains(type)

    /**
     * validateAssetFileName（assets.js:21-52）：
     * - 仅允许 [a-zA-Z0-9_\-.]
     * - 扩展名不得在 UNSAFE_EXTENSIONS
     * - 不得以 '.' 开头
     * - sanitize 后须等于原名（防保留名/超长名）
     * 返回 null=合法，非 null=错误信息。
     */
    fun validateAssetFileName(input: String): String? {
        if (!Regex("^[a-zA-Z0-9_\\-.]+$").matches(input)) {
            return "Illegal character in filename; only alphanumeric, '_', '-' are accepted."
        }
        val ext = input.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty() && UNSAFE_EXTENSIONS.contains(".$ext")) {
            return "Forbidden file extension."
        }
        if (input.startsWith(".")) {
            return "Filename cannot start with '.'"
        }
        if (sanitize(input) != input) {
            return "Reserved or long filename."
        }
        return null
    }

    /** sanitize-filename 近似（assets.js:44 调用）：剥离路径分隔符与控制字符。 */
    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("[\\x00-\\x1f]"), "")

    // ---------------- 已装资产（index.js:350-369, 512-524 + assets.js:108-180） ----------------

    /**
     * updateCurrentAssets = GET /api/assets/get（index.js:512-524 + assets.js:108-180）：
     * 扫 filesDir/assets/<type>/ 下所有文件（跳过 .placeholder），返回 type→JSONArray(filename)。
     */
    fun currentAssets(context: Context): JSONObject {
        val root = rootDir(context)
        val out = JSONObject()
        if (!root.exists()) return out
        root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.forEach { dir ->
                val type = dir.name
                if (type == "temp") return@forEach // 服务端跳过 temp（assets.js:120-121）
                val arr = JSONArray()
                dir.listFiles { f -> f.isFile && f.name != ".placeholder" }
                    ?.forEach { arr.put("assets/$type/${it.name}") }
                out.put(type, arr)
            }
        persistInstalledManifest(context, out)
        return out
    }

    /** listAssets：取某类型已装文件列表（assets.js:165-173 Other assets 分支）。 */
    fun listAssets(context: Context, type: String): List<InstalledAsset> {
        if (!isValidCategory(type)) return emptyList()
        val dir = typeDir(context, type)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name != ".placeholder" }
            ?.map { f ->
                InstalledAsset(
                    type = type,
                    filename = f.name,
                    path = "assets/$type/${f.name}",
                    size = f.length(),
                )
            }?.sortedBy { it.filename.lowercase(Locale.ROOT) }
            ?: emptyList()
    }

    /**
     * isAssetInstalled（index.js:350-369）：
     * - extension：查已装扩展名列表（third-party/ 前缀剥离后包含 filename）
     * - character：查已装角色 avatar 列表（调用方传 characters）
     * - 其它：filesDir/assets/<type>/ 下任意文件名包含 filename
     */
    fun isAssetInstalled(context: Context, type: String, filename: String): Boolean {
        val list = listAssets(context, type)
        return list.any { it.filename.contains(filename) || filename.contains(it.filename) }
    }

    /** isAssetInstalled 的扩展变体（index.js:353-356）：third-party/ 前缀剥离后包含 filename。 */
    fun isExtensionInstalled(installedExtensionNames: List<String>, filename: String): Boolean {
        val marker = "third-party/"
        return installedExtensionNames
            .filter { it.startsWith(marker) }
            .map { it.removePrefix(marker) }
            .any { it.contains(filename) || filename.contains(it) }
    }

    /** isAssetInstalled 的角色变体（index.js:358-360）：avatar 列表包含 filename。 */
    fun isCharacterInstalled(characterAvatars: List<String>, filename: String): Boolean =
        characterAvatars.any { it.contains(filename) || filename.contains(it) }

    // ---------------- 安装 / 删除（index.js:378-448 + assets.js:190-304） ----------------

    /**
     * installAsset（index.js:378-413 + assets.js:190-259）：
     * 下载 URL → temp → 移到 assets/<type>/<filename>。
     * - extension 类型走 installExtension（调用方提供 installer 回调，对齐官方 installExtension）
     * - character 类型下载后返回字节数组由调用方走角色导入流程（对齐 processDroppedFiles）
     * - 其它类型直接落盘
     * 返回：character → ByteArray；extension → installer 返回值；其它 → Boolean。
     */
    suspend fun installAsset(
        context: Context,
        url: String,
        type: String,
        filename: String,
        extensionInstaller: (suspend (url: String) -> Boolean)? = null,
    ): InstallResult = withIO {
        require(isValidUrl(url)) { "Invalid URL" }
        require(isValidCategory(type)) { "Unsupported asset category" }
        val validation = validateAssetFileName(filename)
        require(validation == null) { validation ?: "Invalid filename" }

        if (type == "extension") {
            val installer = extensionInstaller ?: return@withIO InstallResult.Extension(false)
            return@withIO InstallResult.Extension(installer(url))
        }

        val temp = File(typeDir(context, "temp"), filename).apply { parentFile?.mkdirs() }
        if (temp.exists()) temp.delete()
        downloadTo(url, temp)

        if (type == "character") {
            // 对齐 assets.js:241-248：character 返回内容由调用方走 processDroppedFiles
            val bytes = temp.readBytes()
            temp.delete()
            return@withIO InstallResult.Character(bytes)
        }

        val target = File(typeDir(context, type), filename).apply { parentFile?.mkdirs() }
        temp.copyTo(target, overwrite = true)
        temp.delete()
        InstallResult.Success(target)
    }

    /** 直接写入字节数据（供本地资产/角色导入流程使用，绕过下载）。 */
    fun installAssetBytes(context: Context, type: String, filename: String, bytes: ByteArray): File? {
        val validation = validateAssetFileName(filename); if (validation != null) return null
        if (!isValidCategory(type)) return null
        val target = File(typeDir(context, type), filename).apply { parentFile?.mkdirs() }
        target.writeBytes(bytes)
        return target
    }

    /** deleteAsset（index.js:421-448 + assets.js:269-304）：删 assets/<type>/<filename>。 */
    fun deleteAsset(context: Context, type: String, filename: String): Boolean {
        require(isValidCategory(type)) { "Unsupported asset category" }
        val validation = validateAssetFileName(filename); require(validation == null) { validation ?: "Invalid filename" }
        val file = File(typeDir(context, type), filename)
        if (!file.exists()) return false
        return file.delete()
    }

    /** deleteAsset 的扩展变体（index.js:425-430）：extension 走 deleteExtension。 */
    suspend fun deleteExtensionAsset(
        filename: String,
        extensionDeleter: suspend (String) -> Unit,
    ) {
        extensionDeleter(filename)
    }

    // ---------------- 浏览菜单（index.js:211-283） ----------------

    /**
     * buildAssetList（populateAssetsMenu index.js:243-283 + buildAssetTypeSection index.js:220-233）：
     * 把远端 assets JSON 按 type 分桶，每桶按 name 排序。
     * 入参 json：JSONArray of {id,name,description,url,type[,tool,highlight]}
     * 返回 type→List<Asset>。extension 优先（index.js:257）。
     */
    fun buildAssetList(json: JSONArray): Map<String, List<Asset>> {
        val buckets = LinkedHashMap<String, MutableList<Asset>>()
        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue
            val type = obj.optString("type")
            if (type.isEmpty()) continue
            val asset = Asset(
                id = obj.optString("id"),
                name = obj.optString("name"),
                description = obj.optString("description"),
                url = obj.optString("url"),
                type = type,
                tool = obj.optBoolean("tool", false),
                highlight = obj.optBoolean("highlight", false),
            )
            buckets.getOrPut(type) { mutableListOf() }.add(asset)
        }
        // extension 优先（index.js:257）
        val sortedKeys = buckets.keys.sortedByDescending { it == "extension" }
        return sortedKeys.associateWith { k ->
            buckets[k]!!.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    /** buildAssetTypeSection 的等价：返回某类型的已排序 Asset 列表（index.js:220-233）。 */
    fun buildAssetListForType(json: JSONArray, type: String): List<Asset> =
        buildAssetList(json)[type] ?: emptyList()

    /** parseAvailableAssets：把远端 JSON 串解析为 JSONArray（downloadAssetsList index.js:289-313）。 */
    fun parseAvailableAssets(jsonStr: String): JSONArray? = runCatching { JSONArray(jsonStr) }.getOrNull()

    // ---------------- 预览（index.js:319-339） ----------------

    /** previewAsset：音频扩展名判定（index.js:323）。 */
    fun isPreviewableAudio(filename: String): Boolean {
        val lower = filename.lowercase(Locale.ROOT)
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    // ---------------- 内部辅助 ----------------

    private fun rootDir(context: Context): File =
        File(context.filesDir, "assets").apply { mkdirs() }

    private fun typeDir(context: Context, type: String): File =
        File(rootDir(context), type).apply { mkdirs() }

    /** ensureFoldersExist（assets.js:84-96）：保证 VALID_CATEGORIES 各目录存在。 */
    fun ensureFoldersExist(context: Context) {
        VALID_CATEGORIES.forEach { typeDir(context, it).mkdirs() }
    }

    /** isValidUrl（util.js，index.js 多处调用）：URL 合法性。 */
    fun isValidUrl(url: String): Boolean = runCatching {
        val u = java.net.URI(url)
        !u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()
    }.getOrDefault(false)

    /** getAuthorFromUrl 近似（index.js:160）：取 host。 */
    fun getAuthorFromUrl(url: String): String? = runCatching { java.net.URI(url).host }.getOrNull()

    private fun downloadTo(url: String, target: File) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            check(resp.isSuccessful) { "Unexpected response ${resp.code}" }
            val body = resp.body ?: error("Empty response body")
            body.byteStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        }
    }

    private suspend fun <T> withIO(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

    /** installAsset 结果联合类型（对齐 index.js:378-413 三分支）。 */
    sealed class InstallResult {
        data class Success(val file: File) : InstallResult()
        data class Character(val bytes: ByteArray) : InstallResult()
        data class Extension(val ok: Boolean) : InstallResult()
    }

    // ---- 已装清单 JSON 持久化（JSONObject/JSONArray） ----

    private fun manifestFile(context: Context): File =
        File(rootDir(context), ".installed.json")

    /** 把 currentAssets 快照写盘（.installed.json），JSONArray 持久化。 */
    private fun persistInstalledManifest(context: Context, manifest: JSONObject) {
        manifestFile(context).writeText(manifest.toString())
    }

    /** 读已装清单快照（调试/启动预热用）。 */
    fun readInstalledManifest(context: Context): JSONObject {
        val f = manifestFile(context)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }
}

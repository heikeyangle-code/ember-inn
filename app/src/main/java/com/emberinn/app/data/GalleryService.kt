package com.emberinn.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 官方 gallery 扩展（public/scripts/extensions/gallery/index.js）1:1 翻译：
 * 角色图片库列表 / 删除 / 缩略图，配套 folder 覆盖与排序设置。
 *
 * 与官方对齐点：
 * - SORT 枚举 NAME_ASC/NAME_DESC/DATE_DESC/DATE_ASC（index.js:63-68）；
 *   按需求追加 SIZE_ASC/SIZE_DESC，对应 sortField=size。
 * - defaultSettings { folders:{}, sort:DATE_ASC }（index.js:70-73）
 * - initSettings 落盘 settings JSON（index.js:78-94）
 * - getGalleryFolder：folders[avatar] ?? char.name（index.js:101-103）
 * - getGalleryItems：列目录 → 按 sort 排序 → 视频生成缩略图（index.js:112-152）；
 *   服务端 getImages 用 fs.statSync 取 mtime 作为 date（util.js:668-701），此处用 File.lastModified。
 * - getGalleryFolders：列 gallery 根下子目录（index.js:158-174 + endpoints/images.js:115-131）
 * - deleteGalleryItem：删文件（index.js:180-185 + endpoints/images.js:133-155）
 * - setSortOrder / getSortOrder（index.js:191-203）
 * - updateGalleryFolder / restoreGalleryFolder（index.js:569-616）
 * - isVideo：VIDEO_EXTENSIONS 正则（index.js:24 + constants.js:31）
 * - MEDIA_REQUEST_TYPE 位掩码 IMAGE|VIDEO（constants.js:126-130）
 *
 * 存储布局（Android filesDir 下，对齐官方 user/images 目录）：
 *   filesDir/gallery/<folder>/<file>           图片/视频文件
 *   filesDir/gallery/.settings.json            gallery 设置（folders + sort），JSONObject 持久化
 *   filesDir/gallery/.items-cache.json         每文件夹 items 元数据缓存（name/date/size/src/srct），JSONArray 持久化
 *   filesDir/gallery/.thumbnails/<folder>/<name>.png   视频缩略图缓存
 */
object GalleryService {

    /** 官方 VIDEO_EXTENSIONS（constants.js:31）。 */
    private val VIDEO_EXTENSIONS = listOf("mp4", "avi", "mov", "wmv", "flv", "webm", "3gp", "mkv", "mpg")

    /** 图片扩展名白名单（对齐服务端 mime image 前缀过滤）。 */
    private val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

    /** 官方 MEDIA_REQUEST_TYPE 位掩码（constants.js:126-130）。 */
    private const val MEDIA_IMAGE = 0b001
    private const val MEDIA_VIDEO = 0b010
    private const val MEDIA_REQUEST_IMAGE_OR_VIDEO = MEDIA_IMAGE or MEDIA_VIDEO

    /** 官方 SORT 枚举（index.js:63-68）+ 按需求追加的 size 排序。 */
    enum class Sort(val value: String, val field: String, val order: String, val label: String) {
        NAME_ASC("nameAsc", "name", "asc", "Name (A-Z)"),
        NAME_DESC("nameDesc", "name", "desc", "Name (Z-A)"),
        DATE_DESC("dateDesc", "date", "desc", "Newest"),
        DATE_ASC("dateAsc", "date", "asc", "Oldest"),
        SIZE_ASC("sizeAsc", "size", "asc", "Smallest"),
        SIZE_DESC("sizeDesc", "size", "desc", "Largest");

        companion object {
            fun fromValue(v: String?): Sort = values().firstOrNull { it.value == v } ?: DATE_ASC
        }
    }

    data class GalleryItem(
        val src: String,         // 相对路径，如 "gallery/<folder>/<file>"
        val srct: String,        // 缩略图路径（图片同 src；视频为 .thumbnails 下 png）
        val title: String = "",  // 可选标题（index.js:135）
        val name: String,        // 文件名
        val date: Long,          // mtime（ms）
        val size: Long,          // bytes
    )

    // ---------------- settings（index.js:70-94, 191-203） ----------------

    /** initSettings：确保 settings JSON 存在且字段齐全（index.js:78-94）。 */
    fun initSettings(context: Context) {
        val settings = readSettings(context)
        var dirty = false
        if (!settings.has("folders")) { settings.put("folders", JSONObject()); dirty = true }
        if (!settings.has("sort")) { settings.put("sort", Sort.DATE_ASC.value); dirty = true }
        if (dirty) writeSettings(context, settings)
    }

    /** getSortOrder（index.js:201-203）。 */
    fun getSortOrder(context: Context): String =
        readSettings(context).optString("sort").ifEmpty { Sort.DATE_ASC.value }

    /** setSortOrder（index.js:191-195）。 */
    fun setSortOrder(context: Context, order: String) {
        val settings = readSettings(context)
        settings.put("sort", order)
        writeSettings(context, settings)
    }

    // ---------------- folder 覆盖（index.js:101-103, 569-616） ----------------

    /** getGalleryFolder：folders[avatar] ?? name（index.js:101-103）。 */
    fun getGalleryFolder(context: Context, avatar: String?, name: String?): String {
        val folders = readSettings(context).optJSONObject("folders") ?: JSONObject()
        val key = avatar?.takeIf { it.isNotBlank() }
        val override = key?.let { folders.optString(it, "") }
        return override?.takeIf { it.isNotBlank() } ?: (name?.takeIf { it.isNotBlank() } ?: "default")
    }

    /**
     * updateGalleryFolder：设置 folder 覆盖（index.js:569-593）。
     * newUrl == 角色名时删除覆盖（恢复默认），否则写入覆盖。
     * avatar / name 由调用方传入（对应官方 context.characters[characterId].avatar/name）。
     */
    fun updateGalleryFolder(context: Context, newUrl: String, avatar: String?, name: String?) {
        require(newUrl.isNotBlank()) { "Folder name cannot be empty" }
        require(!avatar.isNullOrBlank()) { "Character PNG ID is not found" }
        val settings = readSettings(context)
        val folders = settings.optJSONObject("folders") ?: JSONObject().also { settings.put("folders", it) }
        if (newUrl == name) {
            folders.remove(avatar)
        } else {
            folders.put(avatar, newUrl)
        }
        writeSettings(context, settings)
    }

    /** restoreGalleryFolder：删除 folder 覆盖（index.js:598-616）。 */
    fun restoreGalleryFolder(context: Context, avatar: String?) {
        require(!avatar.isNullOrBlank()) { "Character PNG ID is not found" }
        val settings = readSettings(context)
        val folders = settings.optJSONObject("folders") ?: return
        require(folders.has(avatar)) { "No folder override found" }
        folders.remove(avatar)
        writeSettings(context, settings)
    }

    // ---------------- items（index.js:112-152, 158-174, 180-185） ----------------

    /**
     * getGalleryItems：列出 folder 下的图片/视频并排序（index.js:112-152）。
     * 视频会生成缩略图（getVideoThumbnail，index.js:140-145）。
     * 排序由 getSortOrder 决定；服务端 getImages 支持 name/date（util.js:668-701），
     * 本实现追加 size（按需求）。
     */
    fun getGalleryItems(context: Context, folder: String): List<GalleryItem> {
        val safe = sanitizeFolder(folder.ifBlank { "default" })
        val dir = folderDir(context, safe)
        if (!dir.exists()) return emptyList()

        val sort = Sort.fromValue(getSortOrder(context))
        val items = dir.listFiles { f -> f.isFile && isMedia(f.name, MEDIA_REQUEST_IMAGE_OR_VIDEO) }
            ?.map { f -> buildItem(safe, f) }
            ?.sortedWith(comparator(sort))
            ?: emptyList()

        // 若 order=desc 服务端会 images.reverse()（endpoints/images.js:105-107）
        val ordered = if (sort.order == "desc") items.reversed() else items
        persistItemsCache(context, safe, ordered)
        return ordered
    }

    /** 官方 listGalleryCommand 语义：返回 src 数组的 JSON 字符串（index.js:785-798）。 */
    fun getGalleryItemsJson(context: Context, folder: String): String {
        val arr = JSONArray()
        getGalleryItems(context, folder).forEach { arr.put(it.src) }
        return arr.toString()
    }

    /** getGalleryFolders：列 gallery 根下所有子文件夹（index.js:158-174 + endpoints/images.js:115-131）。 */
    fun getGalleryFolders(context: Context): List<String> {
        val root = rootDir(context)
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.map { it.name }
            ?.sortedBy { it.lowercase(Locale.ROOT) }
            ?: emptyList()
    }

    /** deleteGalleryItem：删除指定 url 的文件（index.js:180-185 + endpoints/images.js:133-155）。 */
    fun deleteGalleryItem(context: Context, url: String): Boolean {
        val file = urlToFile(context, url)
        if (!file.exists()) return false
        val deleted = file.delete()
        if (deleted) {
            // 同步清理视频缩略图
            val thumb = thumbFile(context, url)
            if (thumb.exists()) thumb.delete()
        }
        return deleted
    }

    /**
     * 获取缩略图路径（index.js:140-145）：
     *  - 图片：直接返回 src（官方 item.srct 默认 = src）
     *  - 视频：经 MediaMetadataRetriever 抽帧到 .thumbnails/<folder>/<name>.png
     *
     *  注：MediaMetadataRetriever 在 API 34+ 才实现 AutoCloseable，故不用 .use{}；
     *  release() 自 API 1 起可用（API 29 起标 @Deprecated 但仍可用），兼容 minSdk 26。
     */
    @Suppress("DEPRECATION")
    fun getThumbnail(context: Context, item: GalleryItem): String {
        if (!isVideo(item.name)) return item.src
        val thumb = thumbFile(context, item.src)
        if (thumb.exists()) return thumb.absolutePath
        thumb.parentFile?.mkdirs()
        val src = File(rootDir(context), item.src.removePrefix("gallery/"))
        if (!src.exists()) return item.src
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(src.absolutePath)
            val maxSide = (150 * 1.5).toInt() // 官方 maxSide = round(150 * 1.5)（index.js:141）
            val bitmap = retriever.getFrameAtTime(1_000_000) ?: return item.src
            val scaled = scaleDown(bitmap, maxSide)
            thumb.outputStream().use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
            thumb.absolutePath
        } catch (_: Throwable) {
            item.src
        } finally {
            retriever.release()
        }
    }

    // ---------------- 内部辅助 ----------------

    private fun buildItem(folder: String, file: File): GalleryItem {
        val rel = "gallery/$folder/${file.name}"
        return GalleryItem(
            src = rel,
            srct = rel, // 视频 srct 在 getThumbnail 时替换
            title = "",
            name = file.name,
            date = file.lastModified(),
            size = file.length(),
        )
    }

    /** comparator 对齐服务端 getSortFunction（util.js:669-678）：name 用 Collator，date 用 mtime。size 为本实现追加。 */
    private fun comparator(sort: Sort): Comparator<GalleryItem> = when (sort.field) {
        "name" -> compareBy { it.name.lowercase(Locale.ROOT) }
        "date" -> compareBy { it.date }
        "size" -> compareBy { it.size }
        else -> compareBy { it.name.lowercase(Locale.ROOT) }
    }

    /** isVideo：官方 VIDEO_EXTENSIONS 正则（index.js:24）。 */
    fun isVideo(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext.isNotEmpty() && VIDEO_EXTENSIONS.contains(ext)
    }

    /** isMedia：服务端按 mime 前缀过滤（util.js:684-698），此处用扩展名近似。 */
    private fun isMedia(name: String, type: Int): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty()) return false
        val isImg = IMAGE_EXTENSIONS.contains(ext)
        val isVid = VIDEO_EXTENSIONS.contains(ext)
        return ((type and MEDIA_IMAGE) != 0 && isImg) || ((type and MEDIA_VIDEO) != 0 && isVid)
    }

    private fun scaleDown(src: android.graphics.Bitmap, maxSide: Int): android.graphics.Bitmap {
        val w = src.width; val h = src.height
        val scale = minOf(maxSide.toFloat() / w, maxSide.toFloat() / h, 1f)
        if (scale >= 1f) return src
        return android.graphics.Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    /** sanitize folder 名（近似 getSanitizedFilename，移除路径分隔符）。 */
    private fun sanitizeFolder(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "default" }

    // ---- 文件路径辅助 ----

    private fun rootDir(context: Context): File =
        File(context.filesDir, "gallery").apply { mkdirs() }

    private fun folderDir(context: Context, folder: String): File =
        File(rootDir(context), sanitizeFolder(folder))

    private fun urlToFile(context: Context, url: String): File {
        // url 形如 "gallery/<folder>/<file>" 或 "<folder>/<file>"
        val rel = url.removePrefix("gallery/").removePrefix("gallery")
        return File(rootDir(context), rel)
    }

    private fun thumbFile(context: Context, url: String): File {
        val rel = url.removePrefix("gallery/").removePrefix("gallery")
        return File(File(rootDir(context), ".thumbnails"), "$rel.png")
    }

    // ---- settings / items JSON 持久化（JSONObject/JSONArray） ----

    private fun settingsFile(context: Context): File =
        File(rootDir(context), ".settings.json")

    private fun readSettings(context: Context): JSONObject {
        val f = settingsFile(context)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeSettings(context: Context, settings: JSONObject) {
        settingsFile(context).writeText(settings.toString())
    }

    /** 官方 extensionSettings.gallery.folders 的快照（调试/迁移用）。 */
    fun settingsJson(context: Context): JSONObject = readSettings(context)

    /** 把当前 folder 的 items 元数据写缓存（.items-cache.json），JSONArray 持久化。 */
    private fun persistItemsCache(context: Context, folder: String, items: List<GalleryItem>) {
        val cacheFile = File(rootDir(context), ".items-cache.json")
        val cache = runCatching { JSONObject(cacheFile.readText()) }.getOrDefault(JSONObject())
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("src", item.src)
                put("srct", item.srct)
                put("title", item.title)
                put("name", item.name)
                put("date", item.date)
                put("size", item.size)
            })
        }
        cache.put(sanitizeFolder(folder), arr)
        cacheFile.writeText(cache.toString())
    }

    /** 读 folder 的 items 元数据缓存（JSONArray）。 */
    fun readItemsCache(context: Context, folder: String): JSONArray {
        val cacheFile = File(rootDir(context), ".items-cache.json")
        if (!cacheFile.exists()) return JSONArray()
        val cache = runCatching { JSONObject(cacheFile.readText()) }.getOrNull() ?: return JSONArray()
        return cache.optJSONArray(sanitizeFolder(folder)) ?: JSONArray()
    }

    /** 时间戳 → 可读串（上传文件默认名场景，对齐 addAttachment 的 SimpleDateFormat 用法）。 */
    fun timestampName(): String =
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT).format(Date())
}

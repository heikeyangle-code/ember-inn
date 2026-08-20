package com.emberinn.app.data

import android.content.Context
import java.io.File

/**
 * ComfyUI 多 workflow 存储（对齐官方 stable-diffusion 扩展的 comfy_workflow 文件语义）。
 *
 * 官方对照（SillyTavern/public/scripts/extensions/stable-diffusion/）：
 *   - 活动 workflow = extension_settings.sd.comfy_workflow（文件名，默认 Default_Comfy_Workflow.json）
 *   - workflow 文件存 data/default-user/content（Default_Comfy_Workflow.json / Char_Avatar_Comfy_Workflow.json 随仓库分发）
 *   - 管理操作：/api/sd/comfy/{workflows,workflow,save-workflow,delete-workflow,rename-workflow}
 *   - 编辑器 comfyWorkflowEditor.html 占位符：%prompt%/%negative_prompt%/%model%/%vae%/%sampler%/%scheduler%/
 *     %steps%/%scale%/%denoise%/%clip_skip%/%width%/%height%/%user_avatar%/%char_avatar%/%seed% + 自定义
 *
 * App 落地：workflow 文件存 filesDir/comfy-workflows/{name}.json（官方文件语义），
 * 活动文件名存 ember_comfy_workflow prefs（默认 Default_Comfy_Workflow.json）。
 * 兼容迁移：旧 ember_services.sd_comfy_workflow（单字符串 JSON）→ Default_Comfy_Workflow.json。
 * 首次运行内嵌官方默认 workflow。
 */
class ComfyWorkflowStore(context: Context) {

    private val dir = File(context.filesDir, "comfy-workflows")
    private val prefs = context.getSharedPreferences("ember_comfy_workflow", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("ember_services", Context.MODE_PRIVATE)

    init {
        dir.mkdirs()
        migrateLegacy()
        if (workflows().isEmpty()) {
            write(DEFAULT_NAME, DEFAULT_WORKFLOW)
        }
        if (active() !in workflows()) {
            setActive(workflows().firstOrNull() ?: DEFAULT_NAME)
        }
    }

    /** 全部 workflow 文件名（含 .json），字典序。 */
    fun workflows(): List<String> =
        (dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) } ?: emptyArray())
            .map { it.name }
            .sortedBy { it.lowercase() }

    /** 读指定 workflow 内容；不存在返回 null。 */
    fun read(name: String): String? =
        runCatching { File(dir, sanitize(name)).takeIf { it.isFile }?.readText() }.getOrNull()

    /** 写（新增或覆盖）workflow；name 按文件规则清洗。 */
    fun write(name: String, workflow: String) {
        val fileName = sanitize(name)
        if (fileName.isBlank() || fileName == "default") return
        dir.mkdirs()
        File(dir, fileName).writeText(workflow)
    }

    /** 删除 workflow；至少保留 1 个；删除活动项时自动切到剩余第一个。 */
    fun delete(name: String) {
        if (workflows().size <= 1) return
        val fileName = sanitize(name)
        File(dir, fileName).delete()
        if (active() == fileName) setActive(workflows().firstOrNull() ?: DEFAULT_NAME)
    }

    /** 重命名；新名已存在 / 非法 / 未变返回 false。 */
    fun rename(oldName: String, newName: String): Boolean {
        val oldFileName = sanitize(oldName)
        val newFileName = normalizeName(newName)
        if (newFileName.isBlank() || newFileName == oldFileName) return false
        if (File(dir, newFileName).exists()) return false
        val oldFile = File(dir, oldFileName)
        if (!oldFile.isFile || !oldFile.renameTo(File(dir, newFileName))) return false
        if (active() == oldFileName) setActive(newFileName)
        return true
    }

    /** 当前活动 workflow 名。 */
    fun active(): String = prefs.getString("active", DEFAULT_NAME) ?: DEFAULT_NAME

    fun setActive(name: String) {
        prefs.edit().putString("active", sanitize(name)).apply()
    }

    /** 当前活动 workflow 的 JSON 内容（标准 ComfyUI 与 RunPod 共用，对应官方 comfy_workflow 读取点）。 */
    fun activeWorkflowJson(): String = read(active()) ?: ""

    // ---- 迁移 ----

    /** 旧版单字符串 sd_comfy_workflow → Default_Comfy_Workflow.json（仅当默认文件不存在）。 */
    private fun migrateLegacy() {
        if (File(dir, DEFAULT_NAME).exists()) return
        val legacy = legacyPrefs.getString("sd_comfy_workflow", "") ?: ""
        if (legacy.isBlank()) return
        File(dir, DEFAULT_NAME).writeText(legacy)
        legacyPrefs.edit().remove("sd_comfy_workflow").apply()
    }

    private fun normalizeName(name: String): String {
        val trimmed = name.trim()
        val withExt = if (trimmed.lowercase().endsWith(".json")) trimmed else "$trimmed.json"
        return sanitize(withExt)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "default" }

    companion object {
        const val DEFAULT_NAME = "Default_Comfy_Workflow.json"

        /** 官方默认 workflow（SillyTavern/default/content/Default_Comfy_Workflow.json 原样）。 */
        val DEFAULT_WORKFLOW = """
            {
                "3": {
                    "class_type": "KSampler",
                    "inputs": {
                        "cfg": "%scale%",
                        "denoise": 1,
                        "latent_image": [
                            "5",
                            0
                        ],
                        "model": [
                            "4",
                            0
                        ],
                        "negative": [
                            "7",
                            0
                        ],
                        "positive": [
                            "6",
                            0
                        ],
                        "sampler_name": "%sampler%",
                        "scheduler": "%scheduler%",
                        "seed": "%seed%",
                        "steps": "%steps%"
                    }
                },
                "4": {
                    "class_type": "CheckpointLoaderSimple",
                    "inputs": {
                        "ckpt_name": "%model%"
                    }
                },
                "5": {
                    "class_type": "EmptyLatentImage",
                    "inputs": {
                        "batch_size": 1,
                        "height": "%height%",
                        "width": "%width%"
                    }
                },
                "6": {
                    "class_type": "CLIPTextEncode",
                    "inputs": {
                        "clip": [
                            "4",
                            1
                        ],
                        "text": "%prompt%"
                    }
                },
                "7": {
                    "class_type": "CLIPTextEncode",
                    "inputs": {
                        "clip": [
                            "4",
                            1
                        ],
                        "text": "%negative_prompt%"
                    }
                },
                "8": {
                    "class_type": "VAEDecode",
                    "inputs": {
                        "samples": [
                            "3",
                            0
                        ],
                        "vae": [
                            "4",
                            2
                        ]
                    }
                },
                "9": {
                    "class_type": "SaveImage",
                    "inputs": {
                        "filename_prefix": "SillyTavern",
                        "images": [
                            "8",
                            0
                        ]
                    }
                }
            }
        """.trimIndent()
    }
}

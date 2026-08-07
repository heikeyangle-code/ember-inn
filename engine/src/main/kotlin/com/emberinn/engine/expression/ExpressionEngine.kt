package com.emberinn.engine.expression

import kotlin.random.Random

/**
 * 表情精灵引擎（对齐官方 extensions/expressions + endpoints/sprites.js 纯逻辑）：
 * 文件名→标签、图片元数据、按标签分组排序、按表达式选立绘。
 * DOM 显示/动画/LLM 分类属于 App/服务层。
 */
object ExpressionEngine {

    const val RESET_SPRITE_LABEL = "#reset"

    private val labelRegex = Regex("""^(.+?)(?:[-\.].*?)?$""")
    private val extensionRegex = Regex("""\.[^/.]+$""")

    data class SpriteEntry(val label: String, val path: String)

    data class ExpressionImage(
        val expression: String,
        val fileName: String,
        val title: String,
        val imageSrc: String,
        val type: String = "success",
        val isCustom: Boolean? = null,
    )

    data class ExpressionGroup(
        val label: String,
        val files: MutableList<ExpressionImage>,
    )

    data class ExpressionSettings(
        val fallbackExpression: String? = null,
        val allowMultiple: Boolean = false,
        val rerollIfSame: Boolean = false,
        val customLabels: Set<String> = emptySet(),
    )

    /** 对齐 sprites.js GET /get：filename 转小写后提取主标签（joy / joy-1 / joy.expressive → joy）。 */
    fun labelFromFilename(fileName: String): String {
        val lower = fileName.lowercase()
        return labelRegex.find(lower)?.groupValues?.get(1) ?: lower
    }

    /** 对齐 expressions getExpressionImageData。 */
    fun imageData(sprite: SpriteEntry, customLabels: Set<String>? = null): ExpressionImage {
        val fileName = sprite.path.substringAfterLast('/').substringBefore('?')
        val title = fileName.replace(extensionRegex, "")
        return ExpressionImage(
            expression = sprite.label,
            fileName = fileName,
            title = title,
            imageSrc = sprite.path,
            isCustom = customLabels?.contains(sprite.label),
        )
    }

    /** 对齐 getSpritesList 分组：同标签合并，主文件排最前，其余标 additional。 */
    fun groupSprites(
        sprites: List<SpriteEntry>,
        customLabels: Set<String>? = null,
    ): List<ExpressionGroup> {
        val groups = mutableListOf<ExpressionGroup>()
        for (sprite in sprites) {
            val image = imageData(sprite, customLabels)
            val existing = groups.firstOrNull { it.label == sprite.label }
            if (existing != null) {
                existing.files += image
            } else {
                groups += ExpressionGroup(sprite.label, mutableListOf(image))
            }
        }
        for (group in groups) {
            group.files.sortWith { a, b ->
                when {
                    a.title == group.label && b.title == group.label -> 0
                    a.title == group.label -> -1
                    b.title == group.label -> 1
                    else -> a.title.compareTo(b.title)
                }
            }
            for (i in 1 until group.files.size) {
                group.files[i] = group.files[i].copy(type = "additional")
            }
        }
        return groups
    }

    /**
     * 对齐 chooseSpriteForExpression：
     * fallback 表情、多立绘随机、rerollIfSame 排除上一张、overrideSpriteFile 指定文件。
     */
    fun chooseSprite(
        folderName: String,
        expression: String,
        spriteCache: Map<String, List<ExpressionGroup>>,
        settings: ExpressionSettings = ExpressionSettings(),
        prevSrc: String? = null,
        overrideFile: String? = null,
        random: () -> Double = { Random.nextDouble() },
    ): ExpressionImage? {
        val cache = spriteCache[folderName] ?: return null
        if (expression == RESET_SPRITE_LABEL) return null

        var group = cache.firstOrNull { it.label == expression }
        if (group?.files?.isNotEmpty() != true && settings.fallbackExpression != null) {
            group = cache.firstOrNull { it.label == settings.fallbackExpression }
        }
        if (group?.files?.isNotEmpty() != true) return null

        var spriteFile = group.files.first()
        if (overrideFile != null) {
            spriteFile = group.files.firstOrNull { it.fileName == overrideFile } ?: spriteFile
        } else if (settings.allowMultiple && group.files.size > 1) {
            var possible: List<ExpressionImage> = group.files
            if (settings.rerollIfSame) {
                possible = possible.filter { prevSrc == null || it.imageSrc != prevSrc }
            }
            if (possible.isEmpty()) return null
            spriteFile = possible[((random() * possible.size).toInt()).coerceIn(0, possible.size - 1)]
        }
        return spriteFile
    }
}

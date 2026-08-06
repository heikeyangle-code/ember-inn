package com.emberinn.engine.card

/** 官方支持的 5 种角色卡导入格式（docs/FORMATS.md）。 */
enum class CardFormat { PNG, JSON, CHARX, YAML, BYAF }

interface CharacterCardImporter {
    /** 返回角色卡 JSON 字符串（V2/V3 均可，后续统一归一）。 */
    fun import(data: ByteArray, format: CardFormat): String
}

object CardImporter : CharacterCardImporter {

    override fun import(data: ByteArray, format: CardFormat): String = when (format) {
        CardFormat.PNG -> CharacterCardCodec.readFromPng(data)
        CardFormat.JSON -> String(data, Charsets.UTF_8)
        CardFormat.CHARX -> CharXImporter.cardJson(data)
        CardFormat.YAML, CardFormat.BYAF ->
            error("YAML/BYAF 导入按官方 importFromYaml / byaf.js 映射实现（docs/FORMATS.md），下一步完成")
    }
}

object CharXImporter {

    /** CharX = ZIP，card.json 即 V3 data（对齐官方 src/charx.js）。 */
    fun cardJson(zipBytes: ByteArray): String {
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "card.json" || entry.name.endsWith("/card.json")) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        error("CharX: card.json not found")
    }
}

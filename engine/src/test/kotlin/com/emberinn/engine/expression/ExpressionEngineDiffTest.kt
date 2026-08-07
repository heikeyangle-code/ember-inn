package com.emberinn.engine.expression

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：expressions/index.js + endpoints/sprites.js 表情精灵纯逻辑。
 * fixture 由 scripts/diff/expression-engine-official.mjs 生成，禁止手改。
 */
class ExpressionEngineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `expression engine matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/expression-engine.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")
            val method = body.getValue("method").jsonPrimitive.content

            val actual = when (method) {
                "labelFromFilename" -> JsonPrimitive(
                    ExpressionEngine.labelFromFilename(body.getValue("fileName").jsonPrimitive.content),
                )
                "imageData" -> {
                    val sprite = body.getValue("sprite").jsonObject
                    val custom = body["settings"]?.jsonObject?.get("expressions")?.jsonObject
                        ?.get("custom")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
                    serializeImage(
                        ExpressionEngine.imageData(
                            ExpressionEngine.SpriteEntry(
                                label = sprite["label"]!!.jsonPrimitive.content,
                                path = sprite["path"]!!.jsonPrimitive.content,
                            ),
                            custom,
                        ),
                    )
                }
                "groupSprites" -> {
                    // 官方 getExpressionImageData 读全局 extension_settings，不读 groupSprites 参数
                    val custom: Set<String>? = null
                    val sprites = body.getValue("sprites").jsonArray.map { el ->
                        ExpressionEngine.SpriteEntry(
                            label = el.jsonObject["label"]!!.jsonPrimitive.content,
                            path = el.jsonObject["path"]!!.jsonPrimitive.content,
                        )
                    }
                    JsonArray(ExpressionEngine.groupSprites(sprites, custom).map { group ->
                        buildJsonObject {
                            put("label", JsonPrimitive(group.label))
                            put("files", JsonArray(group.files.map { serializeImage(it) }))
                        }
                    })
                }
                "choose" -> {
                    val cache = parseCache(body["spriteCache"]?.jsonObject ?: JsonObject(emptyMap()))
                    val settings = parseSettings(body["settings"]?.jsonObject ?: JsonObject(emptyMap()))
                    val randomValue = body["random"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.5
                    val chosen = ExpressionEngine.chooseSprite(
                        folderName = body.getValue("folderName").jsonPrimitive.content,
                        expression = body.getValue("expression").jsonPrimitive.content,
                        spriteCache = cache,
                        settings = settings,
                        prevSrc = body["prevSrc"]?.jsonPrimitive?.content,
                        overrideFile = body["overrideFile"]?.jsonPrimitive?.content,
                        random = { randomValue },
                    )
                    chosen?.let { serializeChoose(it) } ?: JsonNull
                }
                else -> error("unknown method $method")
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun parseCache(obj: JsonObject): Map<String, List<ExpressionEngine.ExpressionGroup>> =
        obj.mapValues { (_, groupsEl) ->
            groupsEl.jsonArray.map { g ->
                val go = g.jsonObject
                ExpressionEngine.ExpressionGroup(
                    label = go["label"]!!.jsonPrimitive.content,
                    files = go["files"]!!.jsonArray.map { f ->
                        val fo = f.jsonObject
                        ExpressionEngine.ExpressionImage(
                            expression = fo["expression"]?.jsonPrimitive?.content ?: go["label"]!!.jsonPrimitive.content,
                            fileName = fo["fileName"]!!.jsonPrimitive.content,
                            title = fo["title"]!!.jsonPrimitive.content,
                            imageSrc = fo["imageSrc"]!!.jsonPrimitive.content,
                        )
                    }.toMutableList(),
                )
            }
        }

    private fun parseSettings(obj: JsonObject): ExpressionEngine.ExpressionSettings {
        val e = obj["expressions"]?.jsonObject ?: JsonObject(emptyMap())
        return ExpressionEngine.ExpressionSettings(
            fallbackExpression = e["fallback_expression"]?.jsonPrimitive?.content,
            allowMultiple = e["allowMultiple"]?.jsonPrimitive?.content == "true",
            rerollIfSame = e["rerollIfSame"]?.jsonPrimitive?.content == "true",
            customLabels = e["custom"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
        )
    }

    private fun serializeImage(i: ExpressionEngine.ExpressionImage): JsonElement = buildJsonObject {
        put("expression", JsonPrimitive(i.expression))
        put("fileName", JsonPrimitive(i.fileName))
        put("title", JsonPrimitive(i.title))
        put("imageSrc", JsonPrimitive(i.imageSrc))
        put("type", JsonPrimitive(i.type))
        if (i.isCustom != null) put("isCustom", JsonPrimitive(i.isCustom))
    }

    private fun serializeChoose(i: ExpressionEngine.ExpressionImage): JsonElement = buildJsonObject {
        put("fileName", JsonPrimitive(i.fileName))
        put("title", JsonPrimitive(i.title))
        put("imageSrc", JsonPrimitive(i.imageSrc))
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}

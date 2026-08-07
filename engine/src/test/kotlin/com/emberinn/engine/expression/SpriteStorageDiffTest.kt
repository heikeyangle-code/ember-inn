package com.emberinn.engine.expression

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：sprites.js getSpritesPath / importRisuSprites。
 * fixture 由 scripts/diff/sprites-storage-official.mjs 生成，禁止手改。
 */
class SpriteStorageDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sprite storage matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/sprites-storage.json"))
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
                "path" -> SpriteStorage.spritesPath(
                    charactersRoot = "/chars",
                    name = body.getValue("name").jsonPrimitive.content,
                    isSubfolder = body["isSubfolder"]?.jsonPrimitive?.content == "true",
                )?.let { JsonPrimitive(it) } ?: JsonNull

                "risu" -> {
                    val dataJson = body.getValue("data").toString()
                    val result = SpriteStorage.extractRisuSprites(dataJson, charactersRoot = "/chars")
                    if (result == null) {
                        JsonNull
                    } else {
                        val name = result.data["data"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                        val path = SpriteStorage.spritesPath("/chars", name, false) ?: ""
                        buildJsonObject {
                            put(
                                "writtenFiles",
                                JsonArray(result.written.map {
                                    buildJsonObject {
                                        put("spritesPath", JsonPrimitive(path))
                                        put("filename", JsonPrimitive(it.label + ".png"))
                                    }
                                }),
                            )
                            put("data", result.data)
                        }
                    }
                }
                else -> error("unknown method $method")
            }

            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}

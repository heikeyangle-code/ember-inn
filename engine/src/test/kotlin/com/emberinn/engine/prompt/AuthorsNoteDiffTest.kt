package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
 * 官方行为差分：authors-note.js 默认值解析 + world-info.js ANWithWI。
 * fixture 由 scripts/diff/authors-note-official.mjs 生成，禁止手改。
 */
class AuthorsNoteDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `authors note matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/authors-note.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val actual = when (body.getValue("method").jsonPrimitive.content) {
                "resolve" -> {
                    val defaults = body["defaults"]?.jsonObject ?: JsonObject(emptyMap())
                    val meta = body["meta"]?.jsonObject ?: JsonObject(emptyMap())
                    val note = AuthorsNoteEngine.resolve(
                        AuthorsNoteMetadata(
                            prompt = meta["prompt"]?.jsonPrimitive?.content,
                            interval = meta["interval"]?.jsonPrimitive?.content?.toIntOrNull(),
                            position = meta["position"]?.jsonPrimitive?.content?.toIntOrNull(),
                            depth = meta["depth"]?.jsonPrimitive?.content?.toIntOrNull(),
                            role = meta["role"]?.jsonPrimitive?.content?.toIntOrNull(),
                        ),
                        AuthorsNoteSettings(
                            default = defaults["default"]?.jsonPrimitive?.content ?: "",
                            defaultPosition = defaults["defaultPosition"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                            defaultDepth = defaults["defaultDepth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4,
                            defaultInterval = defaults["defaultInterval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                            defaultRole = defaults["defaultRole"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        ),
                    )
                    buildJsonObject {
                        put("prompt", JsonPrimitive(note.content))
                        put("interval", JsonPrimitive(note.interval))
                        put("position", JsonPrimitive(note.position))
                        put("depth", JsonPrimitive(note.depth))
                        put("role", JsonPrimitive(roleValue(note.role)))
                    }
                }
                "compose" -> JsonPrimitive(
                    AuthorsNoteEngine.composeWithWorldInfo(
                        original = body["original"]?.jsonPrimitive?.content ?: "",
                        top = body["top"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        bottom = body["bottom"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    ),
                )
                else -> error("unknown method")
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun roleValue(role: String): Int = when (role) { "user" -> 1; "assistant" -> 2; else -> 0 }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}

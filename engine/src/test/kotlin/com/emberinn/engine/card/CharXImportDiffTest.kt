package com.emberinn.engine.card

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：src/charx.js CharXParser + characters.js importFromCharX。
 * fixture 由 scripts/diff/charx-import-official.mjs 生成（JSZip/yauzl 等价打桩），禁止手改。
 */
class CharXImportDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `charx import matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/charx-import.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expectedRoot = case.getValue("expected").jsonObject
            val expected = expectedRoot.getValue("resultChar")

            val zipBytes = Base64.getDecoder().decode(body.getValue("zipBase64").jsonPrimitive.content)
            val actual = json.parseToJsonElement(
                CharXImporter.cardJson(zipBytes, now = "2026-08-08T00:00:00.000Z"),
            )
            assertEquals("case $id card", canonical(expected), canonical(actual))

            val assets = CharXImporter.extractAssets(zipBytes)
            assertEquals(
                "case $id icon",
                canonical(expectedRoot["parsedIcon"] ?: JsonNull),
                canonical(serializeIcon(assets.icon)),
            )
            assertEquals(
                "case $id assets",
                canonical(expectedRoot.getValue("parsedAssets")),
                canonical(JsonArray(assets.assets.map { serializeAsset(it) })),
            )
        }
    }


    private fun serializeIcon(icon: CharXImporter.CharXAsset?): JsonElement = icon?.let {
        buildJsonObject {
            put("type", JsonPrimitive(it.type))
            put("name", JsonPrimitive(it.name))
            put("ext", JsonPrimitive(it.ext))
            put("zipPath", JsonPrimitive(it.zipPath))
            put("order", JsonPrimitive(it.order))
        }
    } ?: JsonNull

    private fun serializeAsset(a: CharXImporter.CharXAsset): JsonElement = buildJsonObject {
        put("type", JsonPrimitive(a.type))
        put("name", JsonPrimitive(a.name))
        put("ext", JsonPrimitive(a.ext))
        put("zipPath", JsonPrimitive(a.zipPath))
        put("order", JsonPrimitive(a.order))
        put("storageCategory", JsonPrimitive(a.storageCategory ?: ""))
        put("baseName", JsonPrimitive(a.baseName ?: ""))
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}

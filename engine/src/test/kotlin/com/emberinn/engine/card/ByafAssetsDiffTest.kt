package com.emberinn.engine.card

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：BYAF 资源提取（getCharacterImages / getChatBackgrounds）。
 * fixture 由 scripts/diff/byaf-assets-official.mjs 生成（逐字提取官方方法体），禁止手改。
 */
class ByafAssetsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `byaf assets match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/byaf-assets.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val files = args.getValue("files").jsonObject.mapValues { (_, v) ->
                Base64.getDecoder().decode(v.jsonPrimitive.content)
            }
            val character = args.getValue("character").jsonObject
            val characterPath = args.getValue("characterPath").jsonPrimitive.content
            val scenarios = args.getValue("scenarios").jsonArray.map { it.jsonObject }
            val expected = case.getValue("expected").jsonObject

            val defaultAvatar = "DEFAULT_AVATAR".toByteArray()
            val images = ByafImporter.extractCharacterImages(files, character, characterPath, defaultAvatar)
            val backgrounds = ByafImporter.extractChatBackgrounds(files, character, scenarios)

            val expectedImages = expected.getValue("images").jsonArray
            assertEquals("case $id images", expectedImages.size, images.size)
            for (i in expectedImages.indices) {
                val exp = expectedImages[i].jsonObject
                val act = images[i]
                assertEquals("case $id image $i filename", exp["filename"]?.jsonPrimitive?.content, act.filename)
                assertEquals("case $id image $i data", exp["image"]?.jsonPrimitive?.content, String(act.data, Charsets.UTF_8))
                assertEquals("case $id image $i label", exp["label"]?.jsonPrimitive?.content, act.label)
            }

            val expectedBgs = expected.getValue("backgrounds").jsonArray
            assertEquals("case $id backgrounds", expectedBgs.size, backgrounds.size)
            for (i in expectedBgs.indices) {
                val exp = expectedBgs[i].jsonObject
                val act = backgrounds[i]
                assertEquals("case $id bg $i name", exp["name"]?.jsonPrimitive?.content, act.name)
                assertEquals("case $id bg $i data", exp["data"]?.jsonPrimitive?.content, String(act.data, Charsets.UTF_8))
                assertEquals(
                    "case $id bg $i paths",
                    exp["paths"]?.jsonArray?.map { it.jsonPrimitive.content },
                    act.paths,
                )
            }
        }
    }
}

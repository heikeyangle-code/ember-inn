package com.emberinn.engine.card

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
 * 官方行为差分：characters.js importFromByaf 完整导入计划。
 * fixture 由 scripts/diff/byaf-import-official.mjs 生成，禁止手改。
 */
class ByafImportDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `byaf import plan matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/byaf-import.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonObject

            val zip = buildZip(body)
            val plan = ByafImporter.importPlan(
                zipBytes = zip,
                userName = body["userName"]?.jsonPrimitive?.content ?: "用户",
                now = "2026-08-08T00:00:00.000Z",
                chatNow = "2026-08-08@00h00m00s000ms",
                preservedFileName = body["preservedFileName"]?.jsonPrimitive?.content,
                existingFiles = body["exists"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            )

            val actual = buildJsonObject {
                put("fileName", JsonPrimitive(plan.fileName))
                put("writeCharacterResult", buildJsonObject {
                    put("avatar", JsonPrimitive("buffer:" + Base64.getEncoder().encodeToString(plan.avatar ?: ByteArray(0))))
                    put("card", json.parseToJsonElement(plan.cardJson))
                    put("fileName", JsonPrimitive(plan.fileName))
                })
                put("writtenChats", JsonArray(plan.chats.map {
                    buildJsonObject {
                        put("filePath", JsonPrimitive(it.filePath))
                        put("content", JsonPrimitive(it.content))
                    }
                }))
                put("writtenBackgrounds", JsonArray(plan.backgrounds.map {
                    buildJsonObject {
                        put("filePath", JsonPrimitive(it.filePath))
                        put("data", JsonPrimitive(Base64.getEncoder().encodeToString(it.data)))
                    }
                }))
                put("writtenIcons", JsonArray(plan.icons.map {
                    buildJsonObject {
                        put("filePath", JsonPrimitive(it.filePath))
                        put("data", JsonPrimitive(Base64.getEncoder().encodeToString(it.data)))
                    }
                }))
            }
            assertEquals("case $id", canonical(expected), canonical(actual))
        }
    }

    private fun buildZip(body: JsonObject): ByteArray {
        val byaf = body["byafData"]!!.jsonObject
        val scenarios = byaf["scenarios"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val character = byaf["character"]!!.jsonObject.toMutableMap()
        val images = byaf["images"]?.jsonArray ?: JsonArray(emptyList())

        val characterWithImages = character.toMutableMap()
        characterWithImages["images"] = JsonArray(images.mapIndexed { i, el ->
            val o = el.jsonObject
            buildJsonObject {
                put("path", JsonPrimitive("img/icon$i.png"))
                put("label", o["label"]?.jsonPrimitive?.content ?: "")
            }
        })

        val entries = linkedMapOf<String, ByteArray>()
        entries["manifest.json"] = buildJsonObject {
            put("characters", JsonArray(listOf(JsonPrimitive("character.json"))))
            put("scenarios", JsonArray(scenarios.indices.map { JsonPrimitive("scenario$it.json") }))
            put("author", JsonObject(emptyMap()))
        }.toString().toByteArray()
        entries["character.json"] = JsonObject(characterWithImages).toString().toByteArray()
        scenarios.forEachIndexed { i, s -> entries["scenario$i.json"] = s.toString().toByteArray() }
        images.forEachIndexed { i, el ->
            val o = el.jsonObject
            entries["img/icon$i.png"] = decodeBuffer(o["image"]!!)
        }
        byaf["chatBackgrounds"]?.jsonArray?.forEach { bgEl ->
            val bg = bgEl.jsonObject
            val path = bg["paths"]!!.jsonArray.first().jsonPrimitive.content
            entries[path] = decodeBuffer(bg["data"]!!)
        }
        return zipOf(entries)
    }

    private fun decodeBuffer(el: JsonElement): ByteArray = when (el) {
        is JsonNull -> ByteArray(0)
        is JsonPrimitive -> Base64.getDecoder().decode(el.content)
        is JsonObject -> {
            val data = el["data"]!!.jsonArray
            data.map { it.jsonPrimitive.content.toInt().toByte() }.toByteArray()
        }
        else -> ByteArray(0)
    }

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun canonical(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.toSortedMap().mapValues { (_, v) -> canonical(v) })
        is JsonArray -> JsonArray(el.map { canonical(it) })
        else -> el
    }
}

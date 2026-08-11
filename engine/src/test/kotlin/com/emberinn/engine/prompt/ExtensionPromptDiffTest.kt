package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：script.js setExtensionPrompt/getExtensionPrompt/getExtensionPromptByName + injectCallback。 */
class ExtensionPromptDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun substitute(text: String): String =
        text.replace("{{user}}", "User").replace("{{char}}", "Char")

    @Test
    fun `inject callback mapping matches official fixtures`() {
        val cases = load("extension-prompt.json", "inject")
        for (case in cases) {
            val id = case.id
            val body = case.body
            val expected = case.expected
            val spec = ExtensionPromptEngine.parseInject(
                idRaw = body["id"]?.jsonPrimitive?.contentOrNull,
                valueRaw = body["value"]?.jsonPrimitive?.contentOrNull,
                positionRaw = body["position"]?.let {
                    if (it is JsonObject) null else it.jsonPrimitive.contentOrNull
                },
                depthRaw = body["depth"]?.let {
                    if (it is JsonObject) null else it.jsonPrimitive.contentOrNull ?: it.jsonPrimitive.intOrNull
                },
                roleRaw = body["role"]?.let {
                    if (it is JsonObject) null else it.jsonPrimitive.contentOrNull ?: it.jsonPrimitive.intOrNull
                },
                scanRaw = body["scan"]?.jsonPrimitive?.let {
                    isTrueBoolean(it.contentOrNull ?: "")
                } ?: false,
            )
            val exp = expected.jsonObject
            assertEquals("case $id id", exp["id"]!!.jsonPrimitive.content, spec.id)
            assertEquals("case $id value", exp["value"]!!.jsonPrimitive.content, spec.value)
            assertEquals("case $id prefixedId", exp["prefixedId"]!!.jsonPrimitive.content, ExtensionPromptEngine.SCRIPT_PROMPT_KEY + spec.id)
            assertEquals("case $id position", exp["position"]!!.jsonPrimitive.intOrNull, spec.position)
            assertEquals("case $id depth", exp["depth"]!!.jsonPrimitive.intOrNull, spec.depth)
            assertEquals("case $id scan", exp["scan"]!!.jsonPrimitive.booleanOrNull, spec.scan)
            assertEquals("case $id role", exp["role"]!!.jsonPrimitive.intOrNull, spec.role)
        }
    }

    @Test
    fun `getExtensionPrompt matches official fixtures`() {
        val cases = load("extension-prompt.json", "set-get")
        for (case in cases) {
            val id = case.id
            val body = case.body
            val store = storeOf(body["entries"]!!.jsonArray.map { it.jsonObject })
            val expected = case.expected.jsonPrimitive.content
            val actual = ExtensionPromptEngine.get(
                entries = store,
                position = body["position"]!!.jsonPrimitive.intOrNull ?: ExtensionPromptEngine.POSITION_IN_PROMPT,
                depth = body["depth"]?.jsonPrimitive?.intOrNull,
                separator = body["separator"]?.jsonPrimitive?.content ?: "\n",
                role = body["role"]?.jsonPrimitive?.intOrNull,
                wrap = body["wrap"]?.jsonPrimitive?.booleanOrNull ?: true,
                substitute = ::substitute,
            )
            assertEquals("case $id", expected, actual)
        }
    }

    @Test
    fun `getExtensionPromptByName matches official fixtures`() {
        val cases = load("extension-prompt.json", "by-name")
        for (case in cases) {
            val id = case.id
            val body = case.body
            val store = storeOf(body["entries"]!!.jsonArray.map { it.jsonObject })
            val expected = case.expected.jsonPrimitive.content
            val actual = ExtensionPromptEngine.getByName(
                entries = store,
                key = body["key"]?.jsonPrimitive?.contentOrNull ?: "",
                substitute = ::substitute,
            )
            assertEquals("case $id", expected, actual)
        }
    }

    private fun storeOf(entries: List<JsonObject>): Map<String, ExtensionPromptEngine.Entry> {
        val store = mutableMapOf<String, ExtensionPromptEngine.Entry>()
        for (item in entries) {
            val key = item["key"]!!.jsonPrimitive.content
            ExtensionPromptEngine.set(
                store = store,
                key = key,
                value = item["value"]?.jsonPrimitive?.content ?: "",
                position = item["position"]!!.jsonPrimitive.intOrNull ?: ExtensionPromptEngine.POSITION_IN_PROMPT,
                depth = item["depth"]?.jsonPrimitive?.intOrNull ?: 0,
                scan = item["scan"]?.jsonPrimitive?.booleanOrNull ?: false,
                role = item["role"]?.jsonPrimitive?.intOrNull ?: ExtensionPromptEngine.ROLE_SYSTEM,
            )
        }
        return store
    }

    private fun isTrueBoolean(v: String): Boolean =
        v.lowercase() in setOf("true", "on", "1", "yes", "y")

    private data class Case(val id: String, val body: JsonObject, val expected: kotlinx.serialization.json.JsonElement)

    private fun load(resource: String, mode: String): List<Case> {
        val root = json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/diff/$resource")).readText(),
        ).jsonObject
        return root.getValue("cases").jsonArray.mapNotNull { el ->
            val c = el.jsonObject
            val body = c.getValue("args").jsonObject.getValue("body").jsonObject
            if (body["mode"]?.jsonPrimitive?.content != mode) return@mapNotNull null
            Case(c.getValue("id").jsonPrimitive.content, body, c.getValue("expected"))
        }
    }
}

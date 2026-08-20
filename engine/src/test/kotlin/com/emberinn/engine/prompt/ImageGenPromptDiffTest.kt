package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：stable-diffusion 扩展 prompt 纯逻辑——
 * getGenerationType / getQuietPrompt / stringFormat / parseInteractiveTrigger / promptTemplates 逐字。
 * fixture 由 scripts/diff/imagegen-prompt-official.mjs 生成，禁止手改。
 */
class ImageGenPromptDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `imagegen prompt logic matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/imagegen-prompt.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            fun s(key: String): String = args[key]?.jsonPrimitive?.contentOrNull ?: ""
            fun b(key: String): Boolean = args[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            fun i(key: String): Int = args[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

            when (kind) {
                "interactive" -> {
                    val actual = ImageGenPromptEngine.parseInteractiveTrigger(s("message"))
                    if (expected === JsonNull || (expected is JsonPrimitive && expected.content == "null")) {
                        assertEquals("case $id", null, actual)
                    } else {
                        val eo = (expected as JsonObject)
                        val em = eo["mode"]?.jsonPrimitive?.content?.toIntOrNull() ?: -999
                        val es = eo["subject"]?.jsonPrimitive?.contentOrNull ?: ""
                        val t = checkNotNull(actual) { "case $id: expected a trigger but got null" }
                        assertEquals("case $id mode", em, t.mode)
                        assertEquals("case $id subject", es, t.subject)
                    }
                }
                "genType" -> {
                    val actual = ImageGenPromptEngine.getGenerationType(s("prompt"), b("multimodal"), b("freeExtend"))
                    assertEquals("case $id", (expected as JsonPrimitive).content, actual.toString())
                }
                "template" -> {
                    val key = s("key")
                    val actual = ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES[key] ?: error("template key $key not found")
                    assertEquals("case $id", (expected as JsonPrimitive).content, actual)
                }
                "quiet" -> {
                    val actual = ImageGenPromptEngine.getQuietPrompt(i("mode"), s("trigger"), ImageGenPromptEngine.DEFAULT_PROMPT_TEMPLATES)
                    assertEquals("case $id", (expected as JsonPrimitive).content, actual)
                }
                "fmt" -> {
                    val fmtArgs = args["args"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList()
                    val actual = ImageGenPromptEngine.stringFormat(s("format"), *fmtArgs.toTypedArray())
                    assertEquals("case $id", (expected as JsonPrimitive).content, actual)
                }
                "reply" -> {
                    val actual = ImageGenPromptEngine.processReply(s("str"), b("minimal"))
                    assertEquals("case $id", (expected as JsonPrimitive).content, actual)
                }
                else -> error("unknown kind $kind")
            }
        }
    }
}

package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js messageFormatting 纯文本子集（步骤 1-7 + 名字前缀剥离）。
 * fixture 由 scripts/diff/message-formatting-official.mjs 生成，禁止手改。
 * 打桩（与生成器一致）：macroSubstitute = {{user}}→Alice；regexApply = text + |r{placement}:{depth}。
 */
class MessageFormattingDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `message formatting matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/message-formatting.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject
            val pu = body.getValue("powerUser").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content

            val actual = MessageFormattingEngine.format(
                mes = body.str("mes"),
                chName = body["chName"]?.jsonPrimitive?.content,
                isSystem = body.bool("isSystem"),
                isUser = body.bool("isUser"),
                isNarrator = body.bool("isNarrator"),
                messageId = body.int("messageId"),
                isReasoning = body.bool("isReasoning"),
                settings = MessageFormattingSettings(
                    userPromptBias = pu.str("user_prompt_bias"),
                    showUserPromptBias = pu.bool("show_user_prompt_bias", true),
                    autoFixMarkdown = pu.bool("auto_fix_generated_markdown"),
                    encodeTags = pu.bool("encode_tags"),
                    reasoningPrefix = pu.str("reasoning_prefix"),
                    reasoningSuffix = pu.str("reasoning_suffix"),
                    allowName2Display = pu.bool("allow_name2_display"),
                ),
                depth = body["depth"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                macroSubstitute = { it.replace("{{user}}", "Alice") },
                regexApply = { text, placement, depth -> text + "|r$placement:" + (depth?.toString() ?: "-") },
            )

            assertEquals("case $id", expected, actual.text)
        }
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default
}

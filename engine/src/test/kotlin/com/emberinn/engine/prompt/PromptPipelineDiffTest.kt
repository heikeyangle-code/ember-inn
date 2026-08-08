package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
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
 * 官方行为差分：提示词总装整链（prepareOpenAIMessages + populateChatCompletion）。
 * fixture 由 scripts/diff/prepare-messages-official.mjs 生成（官方函数逐字提取 + 同一提示集合注入），禁止手改。
 * preparePromptsForChatCompletion 本身另有 7 例差分；本测试锁定“顺序/控制提示/历史/示例/continue/squash”整链。
 */
class PromptPipelineDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `prompt pipeline matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/prepare-messages.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonArray

            val input = buildInput(body)
            val prompts = buildPrompts(body)
            val actual = PromptPipeline.prepareWithPrompts(input, prompts).messages

            if (expected.size != actual.size) {
                println("SQUASHFLAG=" + input.squashSystemMessages)
                println("CASE $id: " + actual.joinToString(" || ") { "[${it.role}|${it.name}|${it.identifier}|${it.content.take(30)}]" })
            }
            assertEquals("case $id count", expected.size, actual.size)
            for (i in expected.indices) {
                val exp = expected[i].jsonObject
                val act = actual[i]
                assertEquals("case $id msg $i role", exp["role"]?.jsonPrimitive?.content, act.role)
                assertEquals("case $id msg $i content", exp["content"]?.jsonPrimitive?.content, act.content)
                val expName = exp["name"]?.let { if (it is JsonPrimitive && !it.isString) null else it.jsonPrimitive.content }
                assertEquals("case $id msg $i name", expName, act.name)
            }
        }
    }

    private fun buildInput(body: JsonObject): PromptPipeline.PrepareInput {
        val name1 = body["name1"]?.jsonPrimitive?.content ?: "User"
        val name2 = body["name2"]?.jsonPrimitive?.content ?: "Char"
        val selectedGroup = body["selectedGroup"]?.jsonPrimitive?.content == "true"
        val messages = body["messages"]?.jsonArray?.map { m ->
            val obj = m.jsonObject
            PromptMessage(
                role = obj["role"]?.jsonPrimitive?.content ?: "user",
                content = obj["content"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content,
                identifier = obj["identifier"]?.jsonPrimitive?.content ?: "chatHistory",
            )
        } ?: emptyList()
        val rawExamples = body["mesExamples"]?.jsonPrimitive?.content ?: ""
        val blocks = PromptUtils.parseMesExamples(rawExamples)
        val examples = PromptPipeline.setOpenAIMessageExamples(
            blocks, name1, name2,
            selectedGroup = selectedGroup,
            groupNames = body["groupNames"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        )

        return PromptPipeline.PrepareInput(
            name2 = name2,
            charDescription = body["charDescription"]?.jsonPrimitive?.content ?: "",
            charPersonality = body["charPersonality"]?.jsonPrimitive?.content ?: "",
            scenario = body["scenario"]?.jsonPrimitive?.content ?: "",
            worldInfoBefore = body["worldInfoBefore"]?.jsonPrimitive?.content ?: "",
            worldInfoAfter = body["worldInfoAfter"]?.jsonPrimitive?.content ?: "",
            bias = body["bias"]?.jsonPrimitive?.content ?: "",
            type = body["type"]?.jsonPrimitive?.content ?: "generate",
            quietPrompt = body["quietPrompt"]?.jsonPrimitive?.content ?: "",
            cyclePrompt = body["cyclePrompt"]?.jsonPrimitive?.content ?: "",
            messages = messages,
            messageExamples = examples,
            env = MacroEnv(user = body["name1"]?.jsonPrimitive?.content ?: "User", char = name2),
            maxContextTokens = body["maxContextTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 8192,
            maxTokens = body["maxTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 256,
            tokenCounter = TokenCounter { text -> maxOf(1, Math.ceil(text.length / 4.0).toInt()) },
            continuePrefill = body["continuePrefill"]?.jsonPrimitive?.content == "true",
            assistantPrefill = body["assistantPrefill"]?.jsonPrimitive?.content ?: "",
            chatCompletionSource = body["chatCompletionSource"]?.jsonPrimitive?.content ?: "openai",
            pinExamples = body["pinExamples"]?.jsonPrimitive?.content == "true",
            selectedGroup = selectedGroup,
            namesBehavior = body["namesBehavior"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            sendIfEmpty = body["sendIfEmpty"]?.jsonPrimitive?.content ?: "",
            squashSystemMessages = body["squashSystemMessages"]?.jsonPrimitive?.content != "false",
            newChatPrompt = if (selectedGroup) {
                body["newGroupChatPrompt"]?.jsonPrimitive?.content ?: "undefined"
            } else {
                body["newChatPrompt"]?.jsonPrimitive?.content ?: "New chat:"
            },
        )
    }

    private fun buildPrompts(body: JsonObject): PromptItems {
        val items = body["promptCollection"]?.jsonArray?.map { el ->
            val p = el.jsonObject
            PromptItem(
                identifier = p["identifier"]?.jsonPrimitive?.content ?: "",
                name = p["name"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content ?: "",
                content = p["content"]?.jsonPrimitive?.content ?: "",
                role = p["role"]?.jsonPrimitive?.content ?: "system",
                systemPrompt = p["system_prompt"]?.jsonPrimitive?.content != "false",
                injectionPosition = p["injection_position"]?.jsonPrimitive?.content?.toIntOrNull(),
                injectionDepth = p["injection_depth"]?.jsonPrimitive?.content?.toIntOrNull(),
                position = p["position"]?.jsonPrimitive?.content,
                extension = p["extension"]?.jsonPrimitive?.content == "true",
            )
        } ?: emptyList()
        return PromptItems(items)
    }


}

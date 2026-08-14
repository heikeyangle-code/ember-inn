package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：openai.js parseOpenAIChatLogprobs / parseOpenAITextLogprobs / parseChatCompletionLogprobs。
 * fixture 由 scripts/diff/logprobs-official.mjs 生成，禁止手改。
 */
class LogprobsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `logprobs parsing matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/logprobs.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case["expected"]

            val actual: List<LogprobsEngine.TokenLogprobs>? = when (kind) {
                "chat" -> LogprobsEngine.parseOpenAIChatLogprobs(args["data"] as? JsonObject)
                "text" -> LogprobsEngine.parseOpenAITextLogprobs(args["data"] as? JsonObject)
                else -> LogprobsEngine.parseChatCompletionLogprobs(
                    data = args["data"] as? JsonObject,
                    source = args["source"]?.jsonPrimitive?.content ?: "",
                    textCompletionModel = (args["models"] as? JsonArray)?.any { it.jsonPrimitive.content == "davinci" } == true,
                )
            }

            if (expected == null || expected == JsonNull) {
                assertEquals("case $id", null, actual)
            } else {
                val expArr = expected.jsonArray
                val act = checkNotNull(actual) { "case $id expected list but got null" }
                assertEquals("case $id size", expArr.size, act.size)
                expArr.forEachIndexed { i, e ->
                    val eo = e.jsonObject
                    val ao = act[i]
                    assertEquals("case $id[$i] token", eo["token"]!!.jsonPrimitive.content, ao.token)
                    val expTop = eo["topLogprobs"]!!.jsonArray
                    assertEquals("case $id[$i] top size", expTop.size, ao.topLogprobs.size)
                    expTop.forEachIndexed { j, t ->
                        val pair = t.jsonArray
                        assertEquals("case $id[$i][$j] token", pair[0].jsonPrimitive.content, ao.topLogprobs[j].first)
                        assertEquals("case $id[$i][$j] logprob", pair[1].jsonPrimitive.content.toDouble(), ao.topLogprobs[j].second, 1e-9)
                    }
                }
            }
        }
    }
}

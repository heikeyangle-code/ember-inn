package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：scripts/cfg-scale.js getGuidanceScale / getCustomSeparator / getCfgPrompt。
 * fixture 由 scripts/diff/cfg-prompt-official.mjs 生成，禁止手改。
 * 打桩（与生成器一致）：macroSubstitute = {{user}}→Alice；getCharaFilename = 直接传 charaName。
 * 边界：charaCfg 缺失 + groupchat 覆盖时官方抛 TypeError，差分不生成该用例（Kotlin 空安全登记）。
 */
class CfgPromptDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `cfg prompt matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/cfg-prompt.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected").jsonObject

            val ext = args.getValue("extensionSettings").jsonObject
            val cfgObj = ext["cfg"]?.jsonObject
            val globalObj = cfgObj?.getValue("global")?.jsonObject
            val charaArr = cfgObj?.get("chara")?.jsonArray ?: JsonArray(emptyList())
            val meta = args.getValue("chatMetadata").jsonObject

            val global = CfgPromptEngine.CfgGlobal(
                guidanceScale = globalObj?.get("guidance_scale")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0,
                negativePrompt = globalObj?.get("negative_prompt")?.jsonPrimitive?.content ?: "",
                positivePrompt = globalObj?.get("positive_prompt")?.jsonPrimitive?.content ?: "",
            )
            val chara = charaArr.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == args["charaName"]?.jsonPrimitive?.content }
                ?.jsonObject?.let {
                    CfgPromptEngine.CfgChara(
                        name = it["name"]?.jsonPrimitive?.content ?: "",
                        guidanceScale = it["guidance_scale"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                        negativePrompt = it["negative_prompt"]?.jsonPrimitive?.content ?: "",
                        positivePrompt = it["positive_prompt"]?.jsonPrimitive?.content ?: "",
                    )
                }
            val chat = CfgPromptEngine.CfgChat(
                guidanceScale = meta["cfg_guidance_scale"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                negativePrompt = meta["cfg_negative_prompt"]?.jsonPrimitive?.content ?: "",
                positivePrompt = meta["cfg_positive_prompt"]?.jsonPrimitive?.content ?: "",
                promptCombine = meta["cfg_prompt_combine"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() } ?: emptyList(),
                groupchatIndividualChars = meta["cfg_groupchat_individual_chars"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                promptInsertionDepth = meta["cfg_prompt_insertion_depth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                promptSeparator = meta["cfg_prompt_separator"]?.jsonPrimitive?.content,
            )
            val selectedGroup = args["selectedGroup"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val isNegative = args["isNegative"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

            val actualGuidance = CfgPromptEngine.getGuidanceScale(global, chara, chat, selectedGroup)
            val expectedGuidance = expected["guidance"]
            if (expectedGuidance == null || (expectedGuidance is JsonPrimitive && expectedGuidance.content == "null")) {
                assertEquals("case $id guidance", null, actualGuidance)
            } else {
                val g = expectedGuidance.jsonObject
                val expectedType = g["type"]!!.jsonPrimitive.content.toInt()
                val expectedValue = g["value"]?.let { if (it.jsonPrimitive.content == "null") null else it.jsonPrimitive.content.toDouble() }
                assertEquals("case $id guidance type", expectedType, actualGuidance!!.type)
                assertEquals("case $id guidance value", expectedValue, actualGuidance.value)
            }

            val actualPrompt = if (actualGuidance != null) {
                CfgPromptEngine.getCfgPrompt(actualGuidance, isNegative, chat, chara, global) {
                    it.replace("{{user}}", "Alice")
                }
            } else null
            val expectedPrompt = expected["prompt"]
            if (expectedPrompt == null || (expectedPrompt is JsonPrimitive && expectedPrompt.content == "null")) {
                assertEquals("case $id prompt", null, actualPrompt)
            } else {
                val p = expectedPrompt.jsonObject
                assertEquals("case $id prompt value", p["value"]!!.jsonPrimitive.content, actualPrompt!!.value)
                assertEquals("case $id prompt depth", p["depth"]!!.jsonPrimitive.content.toInt(), actualPrompt.depth)
            }
        }
    }
}

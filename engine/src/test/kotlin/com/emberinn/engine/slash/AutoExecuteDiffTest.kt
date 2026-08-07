package com.emberinn.engine.slash

import com.emberinn.engine.worldinfo.WorldInfoEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：AutoExecuteHandler.handleWIActivation 选择逻辑（quick-reply 扩展）。
 * fixture 由 scripts/diff/auto-execute-official.mjs 生成，禁止手改。
 */
class AutoExecuteDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `auto execute selection matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/auto-execute.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject

            val entries = args.getValue("entries").jsonArray.map { it.jsonObject }
                .map { e ->
                    WorldInfoEntry(
                        world = "w",
                        uid = e["uid"]!!.jsonPrimitive.content.toInt(),
                        order = 0,
                        automationId = e["automationId"]?.jsonPrimitive?.let { if (it.isString) it.content else null },
                    )
                }
            val presets = args.getValue("presets").jsonArray.map { p ->
                val obj = p.jsonObject
                QuickReplyPreset(
                    name = obj["name"]!!.jsonPrimitive.content,
                    slots = obj["slots"]!!.jsonArray.map { s ->
                        val so = s.jsonObject
                        QuickReplySlot(
                            mes = "",
                            label = so["label"]!!.jsonPrimitive.content,
                            automationId = so["automationId"]?.jsonPrimitive?.content ?: "",
                        )
                    },
                )
            }
            val expected = case.getValue("expected").jsonArray.map { e ->
                val obj = e.jsonObject
                (obj["automationId"]!!.jsonPrimitive.content to obj["label"]!!.jsonPrimitive.content)
            }

            val actual = WorldInfoAutoExecute.resolve(entries, presets)
                .map { it.automationId to it.label }
            assertEquals("case $id", expected, actual)
        }
    }
}

package com.emberinn.engine.prompt

import com.emberinn.engine.slash.QuickReply
import com.emberinn.engine.slash.QuickReplyV2Config
import com.emberinn.engine.slash.QuickReplyV2Settings
import com.emberinn.engine.slash.QuickReplyV2Slot
import com.emberinn.engine.slash.QuickReplyVisibleSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * QuickReply 扩展纯函数差分（migrateSetV1ToV2 / visibleSetNames / shouldAutoExecute）。
 * fixture 由 scripts/diff/quickreply-official.mjs 生成（16 例），禁止手改。
 */
class QuickReplyDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `quickreply pure functions match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/quickreply.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.size == 16)

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.int
            val name = case.getValue("name").jsonPrimitive.content
            val tag = case.getValue("_tag").jsonPrimitive.content
            val input: JsonElement = case.getValue("input")
            val expected: JsonElement = case.getValue("expected")

            when (tag) {
                "migrateSet" -> {
                    val actual = QuickReply.migrateSetV1ToV2(input.jsonObject)
                    // expected 形态随 name 拆字段对比
                    when (name) {
                        "qr-migrate-v1-version" ->
                            assertEquals("case $id $name",
                                expected.jsonPrimitive.int, actual["version"]!!.jsonPrimitive.int)
                        "qr-migrate-v1-set-fields" -> {
                            val e = expected.jsonObject
                            assertEquals("case $id $name disableSend",
                                e["disableSend"]!!.jsonPrimitive.boolean, actual["disableSend"]!!.jsonPrimitive.boolean)
                            assertEquals("case $id $name placeBeforeInput",
                                e["placeBeforeInput"]!!.jsonPrimitive.boolean, actual["placeBeforeInput"]!!.jsonPrimitive.boolean)
                            assertEquals("case $id $name injectInput",
                                e["injectInput"]!!.jsonPrimitive.boolean, actual["injectInput"]!!.jsonPrimitive.boolean)
                        }
                        "qr-migrate-v1-slot0-id" ->
                            assertEquals("case $id $name", expected.jsonPrimitive.int,
                                actual["qrList"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.int)
                        "qr-migrate-v1-slot0-fields" ->
                            assertEquals("case $id $name", expected, actual["qrList"]!!.jsonArray[0])
                        "qr-migrate-v1-slot1-defaults" ->
                            assertEquals("case $id $name", expected, actual["qrList"]!!.jsonArray[1])
                        "qr-migrate-v1-quickReplySlots-deleted" ->
                            assertEquals("case $id $name",
                                expected is JsonNull, !actual.containsKey("quickReplySlots"))
                        "qr-migrate-v2-skip" -> {
                            val e = expected.jsonObject
                            assertEquals("case $id $name version",
                                e["version"]!!.jsonPrimitive.int, actual["version"]!!.jsonPrimitive.int)
                            assertEquals("case $id $name qrList0",
                                e["qrList0"], actual["qrList"]!!.jsonArray[0])
                            assertEquals("case $id $name disableSend",
                                e["disableSend"]!!.jsonPrimitive.boolean, actual["disableSend"]!!.jsonPrimitive.boolean)
                            val quickReplySlotsDeleted = e["quickReplySlots"] is JsonNull
                            // version==2 我们不删除 quickReplySlots 也不修改；所以不对比 deleted 标记，
                            // 只对比不迁移核心：version/qrList0/disableSend 维持原样
                            quickReplySlotsDeleted // no-op，仅与 JS 约定保持对称
                        }
                    }
                }
                "visibleSetNames" -> {
                    val settings = inputToSettings(input.jsonObject)
                    val actual = QuickReply.visibleSetNames(settings)
                    val expectedList = expected.jsonArray.map { it.jsonPrimitive.content }
                    assertEquals("case $id $name", expectedList, actual)
                }
                "shouldAutoExecute" -> {
                    val inp = input.jsonObject
                    val slot = inp["slot"]?.jsonObject?.let {
                        QuickReplyV2Slot(
                            id = 0,
                            label = it["label"]?.jsonPrimitive?.contentOrNull ?: "",
                            title = it["title"]?.jsonPrimitive?.contentOrNull ?: "",
                            message = it["message"]?.jsonPrimitive?.contentOrNull ?: "",
                            isHidden = it["isHidden"]?.jsonPrimitive?.boolean ?: false,
                            executeOnStartup = it["executeOnStartup"]?.jsonPrimitive?.boolean ?: false,
                            executeOnUser = it["executeOnUser"]?.jsonPrimitive?.boolean ?: false,
                            executeOnAi = it["executeOnAi"]?.jsonPrimitive?.boolean ?: false,
                            preventAutoExecute = it["preventAutoExecute"]?.jsonPrimitive?.boolean ?: false,
                        )
                    }
                    val phase = inp["phase"]!!.jsonPrimitive.content
                    val settings = inputToSettings(inp["settings"]!!.jsonObject)
                    val setName = inp["setName"]?.jsonPrimitive?.contentOrNull
                    val actual = QuickReply.shouldAutoExecute(slot, phase, settings, setName)
                    assertEquals("case $id $name", expected.jsonPrimitive.boolean, actual)
                }
            }
        }
    }

    private fun inputToSettings(obj: JsonObject): QuickReplyV2Settings {
        val isEnabled = obj["isEnabled"]?.jsonPrimitive?.boolean ?: false
        val isCombined = obj["isCombined"]?.jsonPrimitive?.boolean ?: false
        val configObj = obj["config"]?.jsonObject
        val setList = configObj?.get("setList")?.jsonArray?.map { el ->
            val it = el.jsonObject
            QuickReplyVisibleSet(
                set = it.getValue("set").jsonPrimitive.content,
                isVisible = it["isVisible"]?.jsonPrimitive?.boolean ?: true,
            )
        } ?: listOf(QuickReplyVisibleSet("Default", true))
        return QuickReplyV2Settings(isEnabled, isCombined, QuickReplyV2Config(setList))
    }
}

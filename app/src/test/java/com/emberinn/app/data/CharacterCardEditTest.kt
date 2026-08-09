package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁角色详情页读写：官方位置（extensions.depth_prompt / talkativeness）、世界书未知字段保留、v1 归一。 */
class CharacterCardEditTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val v2Card = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "name": "旧名",
          "description": "旧描述",
          "talkativeness": 0.3,
          "data": {
            "name": "角色A",
            "description": "描述",
            "personality": "性格",
            "scenario": "场景",
            "first_mes": "你好",
            "mes_example": "示例",
            "alternate_greetings": ["备选开场"],
            "extensions": {
              "talkativeness": 0.7,
              "depth_prompt": { "prompt": "挖掘 {{char}} 的内心", "depth": 4, "role": "system" }
            },
            "character_book": {
              "name": "Book",
              "entries": [
                {
                  "id": 1,
                  "keys": ["地点", "city"],
                  "content": "世界内容",
                  "comment": "备注",
                  "constant": true,
                  "selective": false,
                  "enabled": true,
                  "insertion_order": 50,
                  "probability": 80,
                  "extensions": { "vectorized": true }
                },
                {
                  "key": "旧词",
                  "content": "旧内容",
                  "disable": true,
                  "order": 30,
                  "probability": 30
                }
              ]
            }
          }
        }
    """.trimIndent()

    private fun dataOf(raw: String) = json.parseToJsonElement(raw).jsonObject["data"]!!.jsonObject

    @Test
    fun `read fields from official extensions locations`() {
        val fields = CharacterCardEdit.readFields(v2Card, "fallback", "fallback desc")
        assertEquals("角色A", fields.name)
        assertEquals("挖掘 {{char}} 的内心", fields.depthPrompt)
        assertEquals("4", fields.depthPromptDepth)
        assertEquals("system", fields.depthPromptRole)
        assertEquals(0.7f, fields.talkativeness)
        assertEquals("你好", fields.firstMes)
        assertEquals(listOf("备选开场"), fields.alternateGreetings)
        assertEquals("描述, 性格, 场景", listOf(fields.description, fields.personality, fields.scenario).joinToString(", "))
    }

    @Test
    fun `read world entries supports v2 keys array and v1 key plus disable`() {
        val entries = CharacterCardEdit.readWorldEntries(v2Card)
        assertEquals(2, entries.size)
        val first = entries[0]
        assertEquals(1, first.id)
        assertEquals("地点, city", first.keys)
        assertEquals("世界内容", first.content)
        assertTrue(first.constant)
        assertEquals(false, first.selective)
        assertTrue(first.enabled)
        assertEquals(50, first.insertionOrder)
        val second = entries[1]
        assertEquals(2, second.id)
        assertEquals("旧词", second.keys)
        assertEquals(false, second.enabled)
        assertEquals(30, second.insertionOrder)
    }

    @Test
    fun `save fields writes official locations and mirrors root fields`() {
        val fields = CharacterCardEdit.readFields(v2Card, "fallback", "fallback desc").copy(
            name = "新名",
            description = "新描述",
            depthPrompt = "新深度提示",
            depthPromptDepth = "8",
            depthPromptRole = "user",
            talkativeness = 0.2f,
            tags = " 冒险, 悬疑 ",
        )
        val saved = CharacterCardEdit.applyFields(v2Card, fields)
        val root = json.parseToJsonElement(saved).jsonObject
        val data = dataOf(saved)

        // 官方位置：extensions
        val ext = data["extensions"]!!.jsonObject
        val dp = ext["depth_prompt"]!!.jsonObject
        assertEquals("新深度提示", dp["prompt"]!!.jsonPrimitive.content)
        assertEquals(8, dp["depth"]!!.jsonPrimitive.intOrNull)
        assertEquals("user", dp["role"]!!.jsonPrimitive.content)
        assertEquals(0.2f, ext["talkativeness"]!!.jsonPrimitive.floatOrNull)
        // 旧顶层字段已归一移除
        assertNull(data["depth_prompt"])
        assertNull(data["talkativeness"])
        // 根字段同步（readFromV2 提升集）
        assertEquals("新名", root["name"]!!.jsonPrimitive.content)
        assertEquals("新描述", root["description"]!!.jsonPrimitive.content)
        assertEquals(listOf("冒险", "悬疑"), data["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("冒险", "悬疑"), root["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(0.2f, root["talkativeness"]!!.jsonPrimitive.floatOrNull)

        // 保存后再读，字段不丢
        val reread = CharacterCardEdit.readFields(saved, "fallback", "fallback desc")
        assertEquals("新名", reread.name)
        assertEquals("新深度提示", reread.depthPrompt)
        assertEquals("8", reread.depthPromptDepth)
        assertEquals("user", reread.depthPromptRole)
        assertEquals(0.2f, reread.talkativeness)
    }

    @Test
    fun `save world entries preserves unknown fields and normalizes v1 to v2`() {
        val original = CharacterCardEdit.readWorldEntries(v2Card)
        val modified = original.mapIndexed { i, e ->
            if (i == 0) e.copy(content = "新内容", enabled = false) else e.copy(keys = "旧词, 别名")
        }
        val saved = CharacterCardEdit.applyWorldEntries(v2Card, modified)
        val entries = dataOf(saved)["character_book"]!!.jsonObject["entries"]!!.jsonArray

        val first = entries[0].jsonObject
        assertEquals("新内容", first["content"]!!.jsonPrimitive.content)
        assertEquals(false, first["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(80, first["probability"]!!.jsonPrimitive.intOrNull)
        assertEquals(true, first["extensions"]!!.jsonObject["vectorized"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("before_char", first["position"]!!.jsonPrimitive.content)
        assertNull(first["key"])
        assertNull(first["order"])
        assertNull(first["disable"])

        val second = entries[1].jsonObject
        assertEquals(listOf("旧词", "别名"), second["keys"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(30, second["probability"]!!.jsonPrimitive.intOrNull)
        assertNull(second["key"])
        assertNull(second["disable"])

        // 保存后再读，编辑结果一致
        val reread = CharacterCardEdit.readWorldEntries(saved)
        assertEquals("新内容", reread[0].content)
        assertEquals(false, reread[0].enabled)
        assertEquals("旧词, 别名", reread[1].keys)
    }

    @Test
    fun `v1 card without data wrapper is editable in place`() {
        val v1 = """
            {
              "name": "V1角色",
              "description": "V1描述",
              "character_book": { "entries": [ { "key": "关键词", "content": "内容", "enabled": true } ] }
            }
        """.trimIndent()
        val fields = CharacterCardEdit.readFields(v1, "fallback", "fallback desc")
        assertEquals("V1角色", fields.name)
        assertEquals("V1描述", fields.description)
        val saved = CharacterCardEdit.applyFields(v1, fields.copy(name = "改名", depthPrompt = "深度"))
        val root = json.parseToJsonElement(saved).jsonObject
        assertNull(root["data"])
        assertEquals("改名", root["name"]!!.jsonPrimitive.content)
        val reread = CharacterCardEdit.readFields(saved, "fallback", "fallback desc")
        assertEquals("改名", reread.name)
        assertEquals("深度", reread.depthPrompt)
    }

    @Test
    fun `root level character book is readable and migrates into data on save`() {
        val rootBookCard = """
            {"name":"卡","data":{"name":"卡"},"character_book":{"entries":[{"key":"词","content":"内容","enabled":true}]}}
        """.trimIndent()
        val entries = CharacterCardEdit.readWorldEntries(rootBookCard)
        assertEquals(1, entries.size)
        assertEquals("词", entries[0].keys)
        val saved = CharacterCardEdit.applyWorldEntries(rootBookCard, entries.map { it.copy(content = "改后") })
        val reread = CharacterCardEdit.readWorldEntries(saved)
        assertEquals("改后", reread[0].content)
        // 保存后统一落在 data.character_book（官方位置）
        val data = dataOf(saved)
        assertEquals(1, data["character_book"]!!.jsonObject["entries"]!!.jsonArray.size)
    }

    @Test
    fun `read and save character regex scripts keeps unknown fields`() {
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","extensions":{"regex_scripts":[
              {"id":"r1","scriptName":"改口","findRegex":"/你好/","replaceString":"哈喽",
               "placement":[1,2],"disabled":false,"markdownOnly":false,"promptOnly":false,
               "runOnEdit":true,"minDepth":null,"maxDepth":null,"substituteRegex":0,
               "customFlag":true}
            ]}}}
        """.trimIndent()
        val scripts = CharacterCardEdit.readRegexScripts(card)
        assertEquals(1, scripts.size)
        assertEquals("改口", scripts[0].scriptName)
        assertEquals("/你好/", scripts[0].findRegex)
        assertEquals(listOf(1, 2), scripts[0].placement)
        assertTrue(scripts[0].runOnEdit)

        val saved = CharacterCardEdit.applyRegexScripts(
            card,
            scripts.map { it.copy(findRegex = "/你好|您好/", disabled = true, minDepth = 3) },
        )
        val root = json.parseToJsonElement(saved).jsonObject
        val scriptsJson = root["data"]!!.jsonObject["extensions"]!!.jsonObject["regex_scripts"]!!.jsonArray
        val first = scriptsJson[0].jsonObject
        assertEquals("/你好|您好/", first["findRegex"]!!.jsonPrimitive.content)
        assertEquals(true, first["disabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(3, first["minDepth"]!!.jsonPrimitive.intOrNull)
        assertEquals(true, first["customFlag"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(listOf(1, 2), first["placement"]!!.jsonArray.map { it.jsonPrimitive.intOrNull })

        val reread = CharacterCardEdit.readRegexScripts(saved)
        assertEquals("/你好|您好/", reread[0].findRegex)
        assertTrue(reread[0].disabled)
        assertEquals(3, reread[0].minDepth)
    }

    @Test
    fun `read and save per-character variables and quick replies`() {
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","extensions":{
              "emberinn_variables":{"k":"v"},
              "quick_replies":[
                {"id":"q1","label":"打招呼","mes":"/echo 你好","enabled":true,"custom":1}
              ]
            }}}
        """.trimIndent()
        assertEquals("v", CharacterCardEdit.readVariables(card)["k"])

        val qrs = CharacterCardEdit.readQuickReplies(card)
        assertEquals(1, qrs.size)
        assertEquals("打招呼", qrs[0].label)
        assertEquals("/echo 你好", qrs[0].mes)
        assertTrue(qrs[0].enabled)

        val saved = CharacterCardEdit.applyQuickReplies(card, qrs.map { it.copy(mes = "/echo 嗨", enabled = false) })
        val reread = CharacterCardEdit.readQuickReplies(saved)
        assertEquals("/echo 嗨", reread[0].mes)
        assertEquals(false, reread[0].enabled)
        // 未知字段保留
        val savedRoot = json.parseToJsonElement(saved).jsonObject
        val first = savedRoot["data"]!!.jsonObject["extensions"]!!.jsonObject["quick_replies"]!!.jsonArray[0].jsonObject
        assertEquals(1, first["custom"]!!.jsonPrimitive.intOrNull)

        // 空值变量不落盘
        val savedVars = CharacterCardEdit.applyVariables(saved, mapOf("a" to "1", "空" to ""))
        assertEquals(mapOf("a" to "1"), CharacterCardEdit.readVariables(savedVars))
    }
}

package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住“App→引擎”接线契约（曾因“旧的在前”导致 continue 错当最老消息）：
 * 1. generate 输出始终时间正序；
 * 2. continue 默认 nudge 路径选中最后一条 AI（其文本出现在末尾 continueNudge 集合里，且 nudge 提示收尾）。
 */
class ChatPromptFactoryTest {

    private fun msg(isUser: Boolean, text: String, name: String) = buildJsonObject {
        put("name", name)
        put("is_user", isUser)
        put("is_system", false)
        put("send_date", "2026-08-09T00:00:00Z")
        put("mes", text)
        put("extra", buildJsonObject {})
    }

    @Test
    fun `generate output is chronological`() {
        val history = listOf(
            msg(true, "第一条", "User"),
            msg(false, "回复一", "小炭"),
            msg(true, "第二条", "User"),
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
        )
        val contents = result.messages.map { it.content }
        val i1 = contents.indexOf("第一条")
        val i2 = contents.indexOf("回复一")
        val i3 = contents.indexOf("第二条")
        assertTrue(i1 >= 0 && i2 > i1 && i3 > i2)
    }

    @Test
    fun `extra media is parsed and inlined into the outgoing message`() {
        val history = listOf(
            buildJsonObject {
                put("name", "User")
                put("is_user", true)
                put("is_system", false)
                put("send_date", "2026-08-09T00:00:00Z")
                put("mes", "看图")
                put(
                    "extra",
                    buildJsonObject {
                        put(
                            "media",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "image")
                                        put("url", "data:image/png;base64,AA==")
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            imageInlining = true,
        )
        val hit = result.messages.firstOrNull { it.content == "看图" && it.media?.isNotEmpty() == true }
        assertTrue(hit != null)
        assertEquals("data:image/png;base64,AA==", hit!!.media!!.first().url)
    }

    @Test
    fun `media file path is converted to data url before inlining`() {
        val tmp = java.io.File.createTempFile("media-test", ".png")
        tmp.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val history = listOf(
            buildJsonObject {
                put("name", "User")
                put("is_user", true)
                put("is_system", false)
                put("send_date", "2026-08-09T00:00:00Z")
                put("mes", "看图")
                put(
                    "extra",
                    buildJsonObject {
                        put(
                            "media",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "image")
                                        put("url", tmp.absolutePath)
                                        put("title", "a.png")
                                        put("source", "upload")
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            imageInlining = true,
        )
        val hit = result.messages.firstOrNull { it.content == "看图" && it.media?.isNotEmpty() == true }
        assertTrue(hit != null)
        assertTrue(hit!!.media!!.first().url.startsWith("data:image/png;base64,"))
        tmp.delete()
    }

    @Test
    fun `continue nudge targets the last assistant message`() {
        val history = listOf(
            msg(true, "问", "User"),
            msg(false, "旧回复", "小炭"),
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            type = "continue",
            cyclePrompt = "旧回复",
        )
        val contents = result.messages.map { it.content }
        assertTrue(contents.any { it.contains("旧回复") })
        assertEquals("[Continue your last message without repeating its original content.]", contents.last())
    }

    @Test
    fun `chardepthprompt macro expands from extensions depth prompt`() {
        // 官方位置 data.extensions.depth_prompt（char-data.js）；parseCard 必须解析对象而非返回空串
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","extensions":{"depth_prompt":{"prompt":"深层设定文本","depth":4,"role":"system"}}}}
        """.trimIndent()
        val history = listOf(msg(true, "{{chardepthprompt}}", "User"))
        val result = ChatPromptFactory().prepare(
            characterRawJson = card,
            history = history,
            userName = "User",
            charName = "角色",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
        )
        assertTrue(result.messages.any { it.content.contains("深层设定文本") })
    }

    @Test
    fun `character regex applies to user message before prompt`() {
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","extensions":{"regex_scripts":[
              {"id":"r1","scriptName":"改口","findRegex":"/你好/","replaceString":"哈喽","placement":[1],"runOnEdit":true}
            ]}}}
        """.trimIndent()
        val history = listOf(msg(true, "你好", "User"))
        val result = ChatPromptFactory().prepare(
            characterRawJson = card,
            history = history,
            userName = "User",
            charName = "角色",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
        )
        assertTrue(result.messages.any { it.content.contains("哈喽") })
    }

    @Test
    fun `chat metadata overrides character fields`() {
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","system_prompt":"卡系统提示","scenario":"卡场景","mes_example":"卡示例"}}
        """.trimIndent()
        val metadata = buildJsonObject {
            put("system_prompt", "会话系统提示")
            put("scenario", "会话场景")
        }
        val result = ChatPromptFactory().prepare(
            characterRawJson = card,
            history = listOf(msg(true, "问", "User")),
            userName = "User",
            charName = "角色",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            chatMetadata = metadata,
        )
        val contents = result.messages.map { it.content }.joinToString("\n")
        assertTrue(contents.contains("会话系统提示"))
        assertTrue(contents.contains("会话场景"))
        assertTrue(!contents.contains("卡系统提示"))
        assertTrue(!contents.contains("卡场景"))
    }

    @Test
    fun `bias macro is extracted from user input and stripped from message`() {
        val history = listOf(msg(true, "问{{bias:悄悄说}}", "User"))
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
        )
        val contents = result.messages.map { it.content }.joinToString("\n")
        assertTrue(contents.contains("悄悄说"))
        assertTrue(!contents.contains("{{bias"))
    }

    @Test
    fun `bias is not injected for continue`() {
        val history = listOf(
            msg(true, "问{{bias:悄悄说}}", "User"),
            msg(false, "旧回复", "小炭"),
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            type = "continue",
            cyclePrompt = "旧回复",
        )
        assertTrue(!result.messages.any { it.content.contains("悄悄说") })
    }
}

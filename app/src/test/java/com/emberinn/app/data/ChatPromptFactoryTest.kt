package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.emberinn.engine.worldinfo.EmbeddingProvider
import com.emberinn.engine.worldinfo.InMemoryVectorStore
import com.emberinn.engine.worldinfo.StringHash
import com.emberinn.engine.worldinfo.VectorChatSettings
import com.emberinn.engine.worldinfo.VectorItem
import com.emberinn.engine.prompt.PromptItem
import com.emberinn.engine.worldinfo.VectorSettings

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
    fun `character regex applies only when scoped allowed (official character_allowed_regex)`() {
        val card = """
            {"spec":"chara_card_v2","name":"角色","data":{"name":"角色","extensions":{"regex_scripts":[
              {"id":"r1","scriptName":"改口","findRegex":"/你好/","replaceString":"哈喽","placement":[1],"runOnEdit":true}
            ]}}}
        """.trimIndent()
        val history = listOf(msg(true, "你好", "User"))
        // 官方 getScriptsByType(SCOPED)：allowedOnly 且角色不在 character_allowed_regex 中 → 不生效
        val denied = ChatPromptFactory().prepare(
            characterRawJson = card,
            history = history,
            userName = "User",
            charName = "角色",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
        )
        assertTrue(denied.messages.none { it.content.contains("哈喽") })
        // 角色在允许列表 → 生效
        val allowed = ChatPromptFactory().prepare(
            characterRawJson = card,
            history = history,
            userName = "User",
            charName = "角色",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            regexScopedAllowed = true,
        )
        assertTrue(allowed.messages.any { it.content.contains("哈喽") })
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

    @Test
    fun `persona description is injected into prompt`() {
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = listOf(msg(true, "问", "User")),
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            personaDescription = "我是 {{user}} 的助手，话痨模式。",
            personaInPrompt = true,
        )
        assertTrue(result.messages.any { it.content.contains("话痨模式") })
    }

    @Test
    fun `vector memory rearranges history and injects memory prompt`() {
        val store = InMemoryVectorStore(TestEmbedding())
        val oldMessage = "森林里的故事"
        store.insert(
            "chat",
            listOf(VectorItem(StringHash.get(oldMessage), oldMessage, 0)),
        )
        val history = listOf(
            msg(false, "篝火旁的余烬", "小炭"),
            msg(false, oldMessage, "小炭"),
            msg(true, "今天下雨", "User"),
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            vectorStore = store,
            vectorChatSettings = VectorChatSettings(
                enabledChats = true,
                query = 1,
                protect = 1,
                insert = 2,
                scoreThreshold = 0.0,
            ),
            vectorWorldSettings = VectorSettings(),
        )
        val all = result.messages.joinToString("\n") { it.content }
        assertTrue(all.contains("Past events:"))
        assertTrue(all.contains(oldMessage))
        // 旧消息被移出历史进入记忆提示，不再以聊天消息形式出现
        assertTrue(result.messages.none { it.content == oldMessage })
    }

    @Test
    fun `vectorized world info entry is force activated without keyword match`() {
        val store = InMemoryVectorStore(TestEmbedding())
        val content = "常驻知识：森林里住着白鹿"
        // 官方 activateWorldInfo 按 world 分组：collectionId = world_<hash(world)>（角色卡内嵌书 world=character）
        store.insert(
            "world_" + StringHash.get("character"),
            listOf(VectorItem(StringHash.get(content), content, 1)),
        )
        val characterJson = buildJsonObject {
            put("spec", "chara_card_v2")
            put(
                "data",
                buildJsonObject {
                    put("name", "小炭")
                    put(
                        "character_book",
                        buildJsonObject {
                            put(
                                "entries",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("keys", JsonArray(listOf(JsonPrimitive("完全不匹配"))))
                                            put("content", JsonPrimitive(content))
                                            put("vectorized", JsonPrimitive(true))
                                        },
                                    ),
                                ),
                            )
                        },
                    )
                },
            )
        }.toString()
        val history = listOf(msg(true, "今天聊下雨", "User"))
        val result = ChatPromptFactory().prepare(
            characterRawJson = characterJson,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            vectorStore = store,
            vectorChatSettings = VectorChatSettings(),
            vectorWorldSettings = VectorSettings(enabled = true, query = 1, maxEntries = 5, scoreThreshold = 0.0),
        )
        assertTrue(result.activatedWorldInfo.any { it.content == content })
    }

    /** 确定性测试嵌入：字符哈希到 64 维单位向量（相似性不参与断言，只验证接线）。 */
    private class TestEmbedding : EmbeddingProvider {
        override fun embed(texts: List<String>): List<List<Double>> = texts.map { text ->
            val v = DoubleArray(64)
            text.forEach { ch -> v[ch.code and 0x3F] += 1.0 }
            val norm = kotlin.math.sqrt(v.sumOf { it * it })
            v.map { if (norm > 0.0) it / norm else 0.0 }
        }
    }

    @Test
    fun `group depth prompts are injected as in chat extensions`() {
        val history = listOf(
            msg(false, "回应", "小炭"),
            msg(true, "你好", "User"),
        )
        val result = ChatPromptFactory().prepare(
            characterRawJson = null,
            history = history,
            userName = "User",
            charName = "小炭",
            model = "gpt-4o",
            maxContextTokens = 10000,
            maxTokens = 256,
            inChatExtensions = listOf(
                PromptItem(
                    identifier = "groupDepthPrompt0",
                    name = "群聊深度提示 1",
                    content = "群聊深度提示文本",
                    role = "system",
                    injectionDepth = 1,
                    injectionOrder = 100,
                ),
            ),
        )
        assertTrue(result.messages.any { it.content == "群聊深度提示文本" })
    }
}

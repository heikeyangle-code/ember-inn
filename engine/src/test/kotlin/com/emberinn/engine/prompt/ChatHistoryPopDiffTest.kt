package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.worldinfo.TokenCounter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：populateChatHistory（openai.js）。
 * fixture 由 scripts/diff/chat-history-pop-official.mjs 生成（Message/PromptManager/预算打桩），禁止手改。
 */
class ChatHistoryPopDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat history population matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/chat-history-pop.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            val messages = body["messages"]?.jsonArray.orEmpty().map { m ->
                val obj = m.jsonObject
                PromptMessage(
                    role = obj["role"]?.jsonPrimitive?.content ?: "user",
                    content = obj["content"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content,
                    injected = obj["injected"]?.jsonPrimitive?.content == "true",
                )
            }
            val selectedGroup = body["selected_group"]?.jsonPrimitive?.content == "true"
            val newChatPrompt = if (selectedGroup) {
                body["new_group_chat_prompt"]?.jsonPrimitive?.content ?: ""
            } else {
                body["new_chat_prompt"]?.jsonPrimitive?.content ?: ""
            }

            val chatCompletion = ChatCompletion(TokenHandler(TokenCounter { it.length }))
            chatCompletion.setTokenBudget(100000, 0)
            val prompts = PromptItems()
            prompts.add(PromptItem(identifier = "chatHistory", name = "chatHistory", role = "user", content = ""))
            if (selectedGroup) {
                prompts.add(PromptItem(identifier = "groupNudge", name = "groupNudge", role = "system", content = body["groupNudgeContent"]?.jsonPrimitive?.content ?: ""))
            }

            ChatHistoryPopulator.populate(
                messages = messages,
                chatCompletion = chatCompletion,
                prompts = prompts,
                handler = TokenHandler(TokenCounter { it.length }),
                type = body["type"]?.jsonPrimitive?.content ?: "normal",
                newChatPrompt = newChatPrompt,
                env = MacroEnv(user = "", char = ""),
                selectedGroup = selectedGroup,
                sendIfEmpty = body["send_if_empty"]?.jsonPrimitive?.content ?: "",
                cyclePrompt = body["cyclePrompt"]?.jsonPrimitive?.content ?: "",
                continueNudgePrompt = body["continue_nudge_prompt"]?.jsonPrimitive?.content ?: "[Continue your last message without repeating its original content.]",
            )

            val actual = JsonArray(
                chatCompletion.entries.mapNotNull { it as? ChatEntry.Collection }.map { col ->
                    buildJsonObject {
                        put("name", JsonPrimitive(col.collection.identifier))
                        put("msgs", JsonArray(col.collection.items.map { m ->
                            buildJsonObject {
                                put("role", JsonPrimitive(m.role))
                                put("content", JsonPrimitive(m.content))
                                put("identifier", JsonPrimitive(m.identifier ?: ""))
                            }
                        }))
                    }
                },
            )
            if (id == "basic") {
                println("DEBUG messages.size=" + messages.size)
                println("DEBUG messages=" + messages.joinToString { it.content })
                println("DEBUG entries=" + chatCompletion.entries.filterIsInstance<ChatEntry.Collection>().flatMap { it.collection.items }.joinToString { "[" + it.content + "|" + (it.identifier ?: "") + "]" })
            }
            assertEquals("case $id", expected, actual)
        }
    }
}

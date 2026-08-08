package com.emberinn.app.data

import com.emberinn.engine.macros.ChatMessage
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.prompt.CharacterCardFieldsEngine
import com.emberinn.engine.prompt.CharacterCardSource
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.prompt.PromptAssembler
import com.emberinn.engine.prompt.PromptPipeline
import com.emberinn.engine.prompt.PromptUtils
import com.emberinn.engine.worldinfo.GlobalScanData
import com.emberinn.engine.worldinfo.TokenCounterFactory
import com.emberinn.engine.worldinfo.WorldBookEntryParser
import com.emberinn.engine.worldinfo.WorldInfoEntry
import com.emberinn.engine.worldinfo.WorldInfoScanner
import com.emberinn.engine.worldinfo.WorldInfoSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 提示词工厂：角色卡 + 聊天历史 → PromptPipeline 总装 → 最终 CompletionMessage。
 * App 发消息唯一入口（世界书/人设/作者注释/示例/历史全部在引擎内完成）。
 */
class ChatPromptFactory {

    /** 对齐官方 openai.js default_impersonation_prompt。 */
    companion object {
        const val DEFAULT_IMPERSONATION_PROMPT =
            "[Write your next reply from the point of view of {{user}}, using the chat history so far as a guideline for the writing style of {{user}}. Don't write as {{char}} or system. Don't describe actions of {{char}}.]"
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Prepared(
        val messages: List<CompletionMessage>,
        val activatedWorldInfo: List<WorldInfoEntry>,
    )

    fun prepare(
        characterRawJson: String?,
        history: List<JsonElement>,
        userName: String,
        charName: String,
        model: String,
        maxContextTokens: Int = 8192,
        maxTokens: Int = 512,
        type: String = "generate",
        continuePrefill: Boolean = false,
        impersonationPrompt: String = DEFAULT_IMPERSONATION_PROMPT,
    ): Prepared {
        val parsed = characterRawJson?.let { runCatching { parseCard(it) }.getOrNull() }
        val fields = CharacterCardFieldsEngine.fields(parsed?.source)
        val env = MacroEnv(user = userName, char = charName)
        val tokenCounter = TokenCounterFactory.forModel(model)

        // 历史消息（JSONL → 引擎 ChatMessage → PromptMessage）
        val chatMessages = history.mapNotNull { el ->
            val obj = el.jsonObject
            val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
            val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ChatMessage(
                mes = mes,
                isUser = isUser,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
            )
        }
        val historyMessages = PromptAssembler.toOpenAiMessages(chatMessages, user = userName)
            .map { it.copy(identifier = "chatHistory") }

        // 世界书扫描（角色卡内嵌 character_book）
        val scanner = WorldInfoScanner(tokenCounter = tokenCounter)
        val wiResult = scanner.scan(
            chat = chatMessages.map { it.mes },
            maxContext = maxContextTokens,
            entries = parsed?.worldEntries ?: emptyList(),
            settings = WorldInfoSettings(),
            global = GlobalScanData(characterName = charName),
        )

        // 示例对话
        val examples = if (fields.mesExamples.isNotEmpty()) {
            val blocks = PromptUtils.parseMesExamples(fields.mesExamples)
            PromptPipeline.setOpenAIMessageExamples(blocks, userName, charName)
        } else emptyList()

        val result = PromptPipeline.prepare(
            PromptPipeline.PrepareInput(
                name2 = charName,
                charDescription = fields.description,
                charPersonality = fields.personality,
                scenario = fields.scenario,
                worldInfoBefore = wiResult.worldInfoBefore,
                worldInfoAfter = wiResult.worldInfoAfter,
                messages = historyMessages,
                messageExamples = examples,
                env = env,
                maxContextTokens = maxContextTokens,
                maxTokens = maxTokens,
                tokenCounter = tokenCounter,
                type = type,
                continuePrefill = continuePrefill,
                impersonationPrompt = impersonationPrompt,
            ),
        )
        return Prepared(result.messages, wiResult.activated)
    }

    private data class ParsedCard(
        val source: CharacterCardSource,
        val worldEntries: List<WorldInfoEntry>,
    )

    private fun parseCard(raw: String): ParsedCard {
        val root = json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject ?: root
        val source = CharacterCardSource(
            name = str(data, "name"),
            description = str(data, "description"),
            personality = str(data, "personality"),
            scenario = str(data, "scenario"),
            mesExample = str(data, "mes_example"),
            firstMessage = str(data, "first_mes"),
            systemPrompt = str(data, "system_prompt"),
            postHistoryInstructions = str(data, "post_history_instructions"),
            characterVersion = str(data, "character_version"),
            creatorNotes = str(data, "creator_notes"),
            depthPrompt = str(data, "depth_prompt"),
            alternateGreetings = data["alternate_greetings"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
        )
        val entries = data["character_book"]?.jsonObject?.get("entries")?.jsonArray
            ?.mapIndexedNotNull { i, el ->
                runCatching { WorldBookEntryParser.parse(el.jsonObject, "character", i) }.getOrNull()
            } ?: emptyList()
        return ParsedCard(source, entries)
    }

    private fun str(obj: JsonObject, key: String): String =
        obj[key]?.jsonPrimitive?.contentOrNull ?: ""
}

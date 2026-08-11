package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 script.js getStoppingStrings + power-user.js getCustomStoppingStrings 的引擎移植。
 * Instruct 停止串复用已差分的 InstructMode.stoppingSequences。
 */
data class CustomStoppingConfig(
    val rawJson: String = "",
    val macro: Boolean = true,
    val ephemeral: List<String> = emptyList(),
    val limit: Int = 0,
    val substitute: (String) -> String = { it },
)

data class StoppingStringsConfig(
    val isImpersonate: Boolean = false,
    val isContinue: Boolean = false,
    val namesAsStopStrings: Boolean = true,
    val name1: String = "User",
    val name2: String = "Char",
    val chatLastIsUser: Boolean = false,
    val groupMemberNames: List<String> = emptyList(),
    val selectedGroup: Boolean = false,
    val singleLine: Boolean = false,
    val instruct: InstructSettings = InstructSettings(),
    val context: ContextSettings = ContextSettings(),
    val env: MacroEnv = MacroEnv(user = "User", char = "Char"),
    val custom: CustomStoppingConfig = CustomStoppingConfig(),
)

object StoppingStringsEngine {

    private val json = Json { ignoreUnknownKeys = true }

    fun customStoppingStrings(config: CustomStoppingConfig): List<String> {
        val permanent = runCatching {
            if (config.rawJson.isBlank()) return@runCatching emptyList()
            val parsed = json.parseToJsonElement(config.rawJson)
            val array = parsed as? JsonArray ?: return@runCatching emptyList()
            val strings = array.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .filter { it.isNotEmpty() }
            if (config.macro) strings.map(config.substitute) else strings
        }.getOrDefault(emptyList())

        val strings = permanent + config.ephemeral
        return if (config.limit > 0) strings.take(config.limit) else strings
    }

    fun getStoppingStrings(
        api: String,
        config: StoppingStringsConfig,
    ): List<String> {
        if (api == "openai") {
            return customStoppingStrings(config.custom)
        }

        val result = mutableListOf<String>()

        if (config.namesAsStopStrings) {
            val charString = "\n${config.name2}:"
            val userString = "\n${config.name1}:"
            result += if (config.isImpersonate) charString else userString
            result += userString
            if (config.isContinue && config.chatLastIsUser) {
                result += charString
            }
            if (config.selectedGroup && (config.name2.isNotEmpty() || config.isImpersonate)) {
                config.groupMemberNames
                    .filter { it.isNotEmpty() && it != config.name2 }
                    .forEach { result += "\n$it:" }
            }
        }

        result += InstructMode.stoppingSequences(
            name1 = config.name1,
            name2 = config.name2,
            instruct = config.instruct,
            context = config.context,
            env = config.env,
        )
        result += customStoppingStrings(config.custom)

        if (config.singleLine) {
            result.add(0, "\n")
        }

        return result.filter { it.isNotEmpty() }.distinct()
    }
}

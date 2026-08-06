package com.emberinn.engine.prompt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** 对齐官方 instruct-mode.js names_behavior_types。 */
@Serializable(with = NamesBehavior.Serializer::class)
enum class NamesBehavior(val value: String) {
    NONE("none"),
    FORCE("force"),
    ALWAYS("always");

    companion object {
        fun fromValue(value: String?): NamesBehavior =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: FORCE
    }

    object Serializer : KSerializer<NamesBehavior> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("NamesBehavior", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: NamesBehavior) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): NamesBehavior =
            fromValue(decoder.decodeString())
    }
}

/** 对齐官方 force_output_sequence：FIRST=1 / LAST=2；NONE=0（不强制）。 */
enum class ForceOutputSequence {
    NONE,
    FIRST,
    LAST;

    companion object {
        fun fromValue(value: Int): ForceOutputSequence = when (value) {
            1 -> FIRST
            2 -> LAST
            else -> NONE
        }
    }
}

/** 官方 extension_prompt_types 中与 story string 位置相关的取值。 */
object StoryStringPosition {
    const val IN_PROMPT = 0
    const val IN_CHAT = 1
    const val BEFORE_PROMPT = 2
}

/**
 * Instruct 模式设置（对齐官方 power-user.js power_user.instruct 默认值 +
 * migrateInstructModeSettings 补全的字段）。
 */
@Serializable
data class InstructSettings(
    val enabled: Boolean = false,
    @SerialName("name")
    val preset: String = "Alpaca",
    @SerialName("input_sequence")
    val inputSequence: String = "### Instruction:",
    @SerialName("input_suffix")
    val inputSuffix: String = "",
    @SerialName("output_sequence")
    val outputSequence: String = "### Response:",
    @SerialName("output_suffix")
    val outputSuffix: String = "",
    @SerialName("system_sequence")
    val systemSequence: String = "",
    @SerialName("system_suffix")
    val systemSuffix: String = "",
    @SerialName("last_system_sequence")
    val lastSystemSequence: String = "",
    @SerialName("first_input_sequence")
    val firstInputSequence: String = "",
    @SerialName("first_output_sequence")
    val firstOutputSequence: String = "",
    @SerialName("last_input_sequence")
    val lastInputSequence: String = "",
    @SerialName("last_output_sequence")
    val lastOutputSequence: String = "",
    @SerialName("story_string_prefix")
    val storyStringPrefix: String = "",
    @SerialName("story_string_suffix")
    val storyStringSuffix: String = "",
    @SerialName("stop_sequence")
    val stopSequence: String = "",
    val wrap: Boolean = true,
    val macro: Boolean = true,
    @SerialName("names_behavior")
    val namesBehavior: NamesBehavior = NamesBehavior.FORCE,
    @SerialName("activation_regex")
    val activationRegex: String = "",
    @SerialName("bind_to_context")
    val bindToContext: Boolean = false,
    @SerialName("user_alignment_message")
    val userAlignmentMessage: String = "",
    @SerialName("system_same_as_user")
    val systemSameAsUser: Boolean = false,
    @SerialName("sequences_as_stop_strings")
    val sequencesAsStopStrings: Boolean = true,
    @SerialName("skip_examples")
    val skipExamples: Boolean = false,
)

/** 上下文模板设置（对齐官方 power-user.js power_user.context）。 */
@Serializable
data class ContextSettings(
    @SerialName("name")
    val preset: String = "Default",
    @SerialName("story_string")
    val storyString: String = PromptAssembler.DEFAULT_STORY_STRING,
    @SerialName("chat_start")
    val chatStart: String = "***",
    @SerialName("example_separator")
    val exampleSeparator: String = "***",
    @SerialName("use_stop_strings")
    val useStopStrings: Boolean = true,
    @SerialName("names_as_stop_strings")
    val namesAsStopStrings: Boolean = true,
    @SerialName("story_string_position")
    val storyStringPosition: Int = StoryStringPosition.IN_PROMPT,
    @SerialName("story_string_role")
    val storyStringRole: Int = 0,
    @SerialName("story_string_depth")
    val storyStringDepth: Int = 1,
)

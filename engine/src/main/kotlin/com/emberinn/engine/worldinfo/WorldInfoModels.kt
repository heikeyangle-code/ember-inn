package com.emberinn.engine.worldinfo

/** 官方 world-info.js 常量。 */
object WorldInfoConstants {
    const val MAX_SCAN_DEPTH = 1000
    const val DEFAULT_DEPTH = 4

    // world_info_logic
    const val AND_ANY = 0
    const val NOT_ALL = 1
    const val NOT_ANY = 2
    const val AND_ALL = 3

    // world_info_position
    const val POSITION_BEFORE = 0
    const val POSITION_AFTER = 1
    const val POSITION_AN_TOP = 2
    const val POSITION_AN_BOTTOM = 3
    const val POSITION_AT_DEPTH = 4
    const val POSITION_EM_TOP = 5
    const val POSITION_EM_BOTTOM = 6
    const val POSITION_OUTLET = 7

    // scan_state
    const val STATE_NONE = 0
    const val STATE_INITIAL = 1
    const val STATE_RECURSION = 2
    const val STATE_MIN_ACTIVATIONS = 3
}

/** 对齐官方 getWorldInfoSettings 的默认值。 */
data class WorldInfoSettings(
    val depth: Int = 2,
    val minActivations: Int = 0,
    val minActivationsDepthMax: Int = 0,
    val budgetPercent: Int = 25,
    val budgetCap: Int = 0,
    val recursive: Boolean = false,
    val caseSensitive: Boolean = false,
    val matchWholeWords: Boolean = false,
    val maxRecursionSteps: Int = 0,
)

/** 与聊天无关的扫描文本（人设/角色字段等），对齐 WIGlobalScanData。 */
data class GlobalScanData(
    val personaDescription: String = "",
    val characterDescription: String = "",
    val characterPersonality: String = "",
    val characterDepthPrompt: String = "",
    val scenario: String = "",
    val creatorNotes: String = "",
    val trigger: String = "normal",
)

/** 世界书条目（字段对齐官方 WIScanEntry 在本阶段使用的部分）。 */
data class WorldInfoEntry(
    val world: String,
    val uid: Int,
    val order: Int,
    val name: String = "",
    val keys: List<String> = emptyList(),
    val keySecondary: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val position: Int = WorldInfoConstants.POSITION_BEFORE,
    val depth: Int? = null,
    val role: String? = null,
    val selective: Boolean = false,
    val selectiveLogic: Int? = null,
    val caseSensitive: Boolean? = null,
    val matchWholeWords: Boolean? = null,
    val scanDepth: Int? = null,
    val matchPersonaDescription: Boolean = false,
    val matchCharacterDescription: Boolean = false,
    val matchCharacterPersonality: Boolean = false,
    val matchCharacterDepthPrompt: Boolean = false,
    val matchScenario: Boolean = false,
    val matchCreatorNotes: Boolean = false,
    val preventRecursion: Boolean = false,
    val excludeRecursion: Boolean = false,
    val delayUntilRecursion: Boolean? = null,
    val useProbability: Boolean = false,
    val probability: Int = 100,
    val ignoreBudget: Boolean = false,
    val triggers: List<String> = emptyList(),
    val decorators: List<String> = emptyList(),
    val outletName: String? = null,
)

data class EmEntry(val position: Int, val content: String)
data class DepthEntry(val depth: Int, val role: String, val entries: List<String>)

data class WorldInfoResult(
    val worldInfoBefore: String,
    val worldInfoAfter: String,
    val emEntries: List<EmEntry>,
    val anBefore: List<String>,
    val anAfter: List<String>,
    val depthEntries: List<DepthEntry>,
    val outletEntries: Map<String, List<String>>,
    val activated: List<WorldInfoEntry>,
)

fun interface TokenCounter { fun count(text: String): Int }
fun interface RandomProvider { fun nextDouble(): Double }
fun interface MacroSubstituter { fun substitute(text: String): String }

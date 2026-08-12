package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.engine.worldinfo.WorldInfoSettings

/** 世界书扫描设置（对齐官方 World Info 面板：深度/递归/预算/大小写/整词）。 */
object WorldInfoPrefs {

    private const val NAME = "ember_worldinfo"

    fun read(context: Context): WorldInfoSettings {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return WorldInfoSettings(
            depth = sp.getInt("depth", 2),
            minActivations = sp.getInt("min_activations", 0),
            minActivationsDepthMax = sp.getInt("min_activations_depth_max", 0),
            budgetPercent = sp.getInt("budget_percent", 25),
            budgetCap = sp.getInt("budget_cap", 0),
            recursive = sp.getBoolean("recursive", false),
            caseSensitive = sp.getBoolean("case_sensitive", false),
            matchWholeWords = sp.getBoolean("match_whole_words", false),
            maxRecursionSteps = sp.getInt("max_recursion_steps", 0),
            useGroupScoring = sp.getBoolean("use_group_scoring", false),
        )
    }

    fun save(context: Context, settings: WorldInfoSettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("depth", settings.depth)
            .putInt("min_activations", settings.minActivations)
            .putInt("min_activations_depth_max", settings.minActivationsDepthMax)
            .putInt("budget_percent", settings.budgetPercent)
            .putInt("budget_cap", settings.budgetCap)
            .putBoolean("recursive", settings.recursive)
            .putBoolean("case_sensitive", settings.caseSensitive)
            .putBoolean("match_whole_words", settings.matchWholeWords)
            .putInt("max_recursion_steps", settings.maxRecursionSteps)
            .putBoolean("use_group_scoring", settings.useGroupScoring)
            .apply()
    }

    /** 官方 world_info_include_names：扫描文本是否带名字前缀。 */
    fun includeNames(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean("include_names", true)

    fun saveIncludeNames(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("include_names", enabled)
            .apply()
    }

    /** 官方 settings.world_info.globalSelect：全局始终生效的外置世界。 */
    fun globalSelect(context: Context): List<String> =
        (context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getStringSet("global_select", emptySet()) ?: emptySet()).toList()

    fun saveGlobalSelect(context: Context, worlds: List<String>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("global_select", worlds.toSet())
            .apply()
    }

    /** 官方 world_info_insertion_strategy：0=EVENLY 1=CHARACTER_FIRST 2=GLOBAL_FIRST。 */
    fun insertionStrategy(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt("insertion_strategy", 1)

    fun saveInsertionStrategy(context: Context, strategy: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("insertion_strategy", strategy)
            .apply()
    }
}

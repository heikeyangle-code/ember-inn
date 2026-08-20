package com.emberinn.engine.regex

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 真实行为比对（阶段2·正则执行时机）：
 * fixture 由 .scratch/audit/probe6-regex.mjs 在官方 SillyTavern 真实浏览器中生成
 * （真实 getRegexedString + 真实 getTokenCountAsync + 真实 checkWorldInfo）。
 * 覆盖用户要求里最容易有隐藏细节的三个时机问题：
 *   A. WORLD_INFO 正则：条目内容在「扫描完成后的 BUILDING PROMPT 阶段」被替换（isPrompt=true）
 *   B. promptOnly 聊天正则先于世界书扫描生效（getChat 先过正则再喂 checkWorldInfo）
 *   C. 默认脚本（非 promptOnly）在 prompt 阶段不运行（isPrompt=true 只跑 promptOnly/markdownOnly），
 *      仅在发送/显示阶段（isPrompt/isMarkdown 均 false）运行。
 * 官方实证结果（probe6-regex.mjs 输出）：
 *   A: raw.before = "珍宝密码"（条目「宝藏密码」→WI 正则「/宝藏/→珍宝」→「珍宝密码」）
 *   B: prompt 阶段 "请说密语"→"请说开锁"，checkWorldInfo(['请说开锁']) 命中「门开了」；
 *      原始 "请说密语" 扫描不命中
 *   C: send="乙"（默认脚本运行），prompt="甲"（默认脚本不运行）
 */
class RegexTimingDiffTest {

    // 官方 probe6-regex.mjs 场景 B 的 prompt 阶段结果：默认脚本不打 promptOnly 不替换
    @Test
    fun `promptOnly chat regex applies before world info scan`() {
        val scripts = listOf(
            RegexPipelineScript(
                findRegex = "(密语)", replaceString = "开锁",
                placement = listOf(1), markdownOnly = false, promptOnly = true,
                runOnEdit = true, disabled = false, substituteRegex = 0,
            ),
        )
        // 发送/显示阶段（isPrompt 未设）：promptOnly 脚本此时不跑 → 仍为原文
        val sendPhase = RegexPipelineEngine.apply(raw = "请说密语", placement = 1, scripts = scripts)
        // prompt 阶段（isPrompt=true）：promptOnly 脚本运行 → 替换
        val promptPhase = RegexPipelineEngine.apply(raw = "请说密语", placement = 1, scripts = scripts, isPrompt = true)

        assertEquals("官方 send 阶段（probe6 B send=请说密语）", "请说密语", sendPhase)
        assertEquals("官方 prompt 阶段（probe6 B prompt=请说开锁）", "请说开锁", promptPhase)
    }

    @Test
    fun `wi content regex applied at building prompt phase after scan`() {
        val scripts = listOf(
            RegexPipelineScript(
                findRegex = "(宝藏)", replaceString = "珍宝",
                placement = listOf(5), markdownOnly = false, promptOnly = true,
                runOnEdit = true, disabled = false, substituteRegex = 0,
            ),
        )
        // 官方 A：raw.before = "珍宝密码"，即 WI 条目的 content 在 BUILDING PROMPT 阶段过正则
        val built = RegexPipelineEngine.apply(
            raw = "宝藏密码", placement = 5, scripts = scripts, isPrompt = true,
        )
        assertEquals("官方 WI 内容正则（probe6 A 珍宝密码）", "珍宝密码", built)
    }

    @Test
    fun `default script runs on send but not on prompt phase`() {
        val scripts = listOf(
            RegexPipelineScript(
                findRegex = "(甲)", replaceString = "乙",
                placement = listOf(1), markdownOnly = false, promptOnly = false,
                runOnEdit = true, disabled = false, substituteRegex = 0,
            ),
        )
        // 官方 C：send="乙"（默认脚本运行），prompt="甲"（prompt 阶段不运行）
        val sendPhase = RegexPipelineEngine.apply(raw = "甲", placement = 1, scripts = scripts)
        val promptPhase = RegexPipelineEngine.apply(raw = "甲", placement = 1, scripts = scripts, isPrompt = true)
        assertEquals("官方 send 阶段（probe6 C send=乙）", "乙", sendPhase)
        assertEquals("官方 prompt 阶段（probe6 C prompt=甲）", "甲", promptPhase)
    }
}
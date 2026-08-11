package com.emberinn.engine.prompt

import com.emberinn.engine.worldinfo.EmEntry

/**
 * 官方 script.js baseChatReplace + generate 中“Add message example WI”的 1:1 移植。
 * 世界书 EM 示例：baseChatReplace（宏替换 + collapse + 去 \r）→ parseMesExamples →
 * before(0) unshift / after(1) push，插入到角色卡 mes_example 解析结果同一数组。
 */
object ExampleAssembler {

    /**
     * 官方 baseChatReplace(value, name1Override, name2Override)：
     * substituteParams(replaceCharacterCard:false) → power_user.collapse_newlines → 去 \r。
     * 差分脚本以 substitute 注入等价宏替换（引擎侧 MacroEnv 由调用方提供）。
     */
    fun baseChatReplace(
        value: String,
        substitute: (String) -> String = { it },
        collapseNewlines: Boolean = false,
    ): String {
        if (value.isEmpty()) return value
        var out = substitute(value)
        if (collapseNewlines) out = PromptUtils.collapseNewlines(out)
        return out.replace("\r", "")
    }

    /**
     * 官方 generate（script.js:4591-4604 逐字语义）：
     * 角色卡 mes_example 先 parseMesExamples，再把每个 WI EM 条目
     * baseChatReplace → parseMesExamples → before unshift / after push。
     */
    fun assembleWithWorldExamples(
        baseMesExamples: String,
        emEntries: List<EmEntry>,
        substitute: (String) -> String = { it },
        collapseNewlines: Boolean = false,
        isInstruct: Boolean = false,
        exampleSeparator: String = "",
        mainApiIsOpenAi: Boolean = true,
    ): List<String> {
        val result = PromptUtils.parseMesExamples(
            baseMesExamples,
            isInstruct,
            exampleSeparator,
            mainApiIsOpenAi,
        ).toMutableList()
        for (em in emEntries) {
            if (em.content.isEmpty()) continue
            val formatted = baseChatReplace(em.content, substitute, collapseNewlines)
            val cleaned = PromptUtils.parseMesExamples(
                formatted,
                isInstruct,
                exampleSeparator,
                mainApiIsOpenAi,
            )
            if (em.position == 0) {
                result.addAll(0, cleaned)
            } else {
                result.addAll(cleaned)
            }
        }
        return result
    }
}

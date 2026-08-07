package com.emberinn.engine.prompt

/** 提示词工具（对齐 script.js collapseNewlines / parseMesExamples）。 */
object PromptUtils {

    fun collapseNewlines(text: String): String = text.replace(Regex("\n+"), "\n")

    fun parseMesExamples(
        examplesStr: String,
        isInstruct: Boolean = false,
        exampleSeparator: String = "",
        mainApiIsOpenAi: Boolean = true,
    ): List<String> {
        if (examplesStr.isEmpty() || examplesStr == "<START>") return emptyList()
        var value = examplesStr
        if (!value.startsWith("<START>")) {
            value = "<START>\n" + value.trim()
        }
        val separator = if (exampleSeparator.isNotEmpty()) "$exampleSeparator\n" else ""
        val blockHeading = if (mainApiIsOpenAi || isInstruct) "<START>\n" else separator
        return value.split(Regex("<START>", RegexOption.IGNORE_CASE))
            .drop(1)
            .map { block -> blockHeading + block.trim() + "\n" }
    }
}

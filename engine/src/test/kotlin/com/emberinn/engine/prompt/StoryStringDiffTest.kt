package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：power-user.js renderStoryString（Handlebars noEscape 编译 + 去前导换行 + 补尾换行）。
 * 引擎侧走 StoryStringRenderer + 官方尾处理（去前导换行/补尾换行）；
 * {{trim}} 等宏的删除由 macros 差分（MacroEngine legacy-trim）单独覆盖，官方脚本此处 substituteParams=恒等。
 * fixture 由 scripts/diff/story-string-official.mjs 生成，禁止手改。
 */
class StoryStringDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `story string render matches official handlebars fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/story-string.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val template = args["template"]?.jsonPrimitive?.content ?: ""
            val params = args["params"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            val expected = case.getValue("expected").jsonPrimitive.content

            val rendered = StoryStringRenderer.render(template, params)
            val normalized = rendered.replace(Regex("""^\n+"""), "").let {
                if (it.isNotEmpty() && !it.endsWith("\n")) it + "\n" else it
            }
            assertEquals("case $id", expected, normalized)
        }
    }
}

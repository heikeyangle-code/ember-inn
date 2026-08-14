package com.emberinn.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 显示管线 App 侧剩余工具：流式定界符补齐（fixMarkdown/encode_tags 已迁 engine 差分）。 */
class DisplayPipelineTest {

    @Test
    fun `streaming delimiters are balanced while not final`() {
        assertEquals("你好*", DisplayPipeline.balanceStreamingDelimiters("你好*"))
        assertEquals("他说\"你好\"", DisplayPipeline.balanceStreamingDelimiters("他说\"你好"))
        assertEquals("code\n```", DisplayPipeline.balanceStreamingDelimiters("code\n```"))
        assertEquals("wave\n~~~", DisplayPipeline.balanceStreamingDelimiters("wave\n~~~"))
        // 偶数不改；final 不改
        assertEquals("**ok**", DisplayPipeline.balanceStreamingDelimiters("**ok**"))
        assertEquals("你好*", DisplayPipeline.balanceStreamingDelimiters("你好*", isFinal = true))
    }
}

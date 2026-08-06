package com.emberinn.engine.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorsNoteTest {

    @Test
    fun `compose merges wi before and after`() {
        assertEquals(
            "前\n正文\n后",
            AuthorsNoteBuilder.compose("正文", listOf("前"), listOf("后"), allowWIScan = true),
        )
        assertEquals("正文\n后", AuthorsNoteBuilder.compose("正文", emptyList(), listOf("后"), allowWIScan = true))
        assertEquals("正文", AuthorsNoteBuilder.compose("正文", emptyList(), emptyList(), allowWIScan = true))
        assertEquals("原样", AuthorsNoteBuilder.compose("原样", listOf("前"), listOf("后"), allowWIScan = false))
    }
}

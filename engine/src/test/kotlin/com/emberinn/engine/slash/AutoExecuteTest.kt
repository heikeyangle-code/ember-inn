package com.emberinn.engine.slash

import com.emberinn.engine.worldinfo.WorldInfoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoExecuteTest {

    private fun wi(uid: Int, automationId: String?) = WorldInfoEntry(
        world = "w",
        uid = uid,
        order = 1,
        automationId = automationId,
    )

    @Test
    fun `resolve matches activated entries automation ids`() {
        val presets = listOf(
            QuickReplyPreset(
                name = "auto",
                slots = listOf(
                    QuickReplySlot(mes = "/pass 1", label = "a1", automationId = "auto_1"),
                    QuickReplySlot(mes = "/pass 2", label = "a2", automationId = "auto_2"),
                    QuickReplySlot(mes = "/pass 3", label = "a3", automationId = ""),
                ),
            ),
        )
        val matched = WorldInfoAutoExecute.resolve(
            activatedEntries = listOf(wi(1, "auto_2"), wi(2, null)),
            presets = presets,
        )
        assertEquals(listOf("a2"), matched.map { it.label })
    }

    @Test
    fun `handler performs and restores prevent stack`() {
        val handler = AutoExecuteHandler()
        assertTrue(handler.checkExecute())
        val presets = listOf(
            QuickReplyPreset(
                name = "p",
                slots = listOf(
                    QuickReplySlot(mes = "/pass 1", label = "a", automationId = "x", preventAutoExecute = true),
                ),
            ),
        )
        // 对齐官方 performAutoExecute：执行后 finally 弹栈，checkExecute 恢复 true
        handler.performAutoExecute(WorldInfoAutoExecute.resolve(listOf(wi(1, "x")), presets), presets)
        assertTrue(handler.checkExecute())
        // 正常无 prevent 时也允许
        val normal = QuickReplyPreset(
            name = "p2",
            slots = listOf(QuickReplySlot(mes = "/pass 1", label = "b", automationId = "y")),
        )
        val h2 = AutoExecuteHandler()
        h2.performAutoExecute(listOf(normal.slots.first()), listOf(normal))
        assertTrue(h2.checkExecute())
    }

    @Test
    fun `withPrevent pushes and pops prevent stack per slot`() {
        val handler = AutoExecuteHandler()
        assertTrue(handler.checkExecute())
        val preventing = QuickReplySlot(mes = "/echo a", label = "a", preventAutoExecute = true)
        val normal = QuickReplySlot(mes = "/echo b", label = "b")
        handler.withPrevent(preventing) {
            assertTrue(!handler.checkExecute())
            handler.withPrevent(normal) {
                assertTrue(handler.checkExecute())
            }
            assertTrue(!handler.checkExecute())
        }
        assertTrue(handler.checkExecute())
    }
}

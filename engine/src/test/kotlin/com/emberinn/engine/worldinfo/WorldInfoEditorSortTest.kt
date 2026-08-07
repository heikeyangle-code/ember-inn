package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldInfoEditorSortTest {

    private fun e(uid: Int, order: Int = 0, displayIndex: Int? = null, constant: Boolean = false, disable: Boolean = false) =
        WorldInfoEntry(world = "w", uid = uid, order = order, displayIndex = displayIndex, constant = constant, disable = disable)

    @Test
    fun `custom sorts by display index then order desc then uid asc`() {
        val sorted = WorldInfoEditorSort.sort(
            listOf(
                e(uid = 1, order = 5, displayIndex = 2),
                e(uid = 2, order = 5, displayIndex = 2),
                e(uid = 3, order = 9, displayIndex = 1),
            ),
        )
        assertEquals(listOf(3, 1, 2), sorted.map { it.uid })
    }

    @Test
    fun `priority puts constant first and disabled last`() {
        val sorted = WorldInfoEditorSort.sort(
            listOf(
                e(uid = 1, disable = true),
                e(uid = 2, constant = true),
                e(uid = 3),
            ),
            rule = "priority",
        )
        assertEquals(listOf(2, 3, 1), sorted.map { it.uid })
    }

    @Test
    fun `default field sorts by field with direction`() {
        val sorted = WorldInfoEditorSort.sort(
            listOf(e(uid = 1, order = 3), e(uid = 2, order = 1), e(uid = 3, order = 2)),
            rule = "default",
            order = "desc",
            field = "order",
        )
        assertEquals(listOf(1, 3, 2), sorted.map { it.uid })
    }
}

package com.emberinn.engine.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupSchedulerTest {

    private val group = GroupChat(
        id = "g1",
        name = "测试群",
        members = listOf("甲", "乙", "丙"),
        disabledMembers = listOf("丙"),
    )

    @Test
    fun `swap mode cycles enabled members`() {
        assertEquals("乙", GroupScheduler.nextSpeaker(group, "甲"))
        assertEquals("甲", GroupScheduler.nextSpeaker(group, "乙"))
        // 最后一个启用成员之后回到第一个
        assertEquals("甲", GroupScheduler.nextSpeaker(group.copy(disabledMembers = emptyList()), "丙"))
        // 无历史 → 第一个启用成员
        assertEquals("甲", GroupScheduler.nextSpeaker(group, null))
        // 历史发言人被禁用 → 从头
        assertEquals("甲", GroupScheduler.nextSpeaker(group, "丙"))
    }

    @Test
    fun `append modes return all members`() {
        assertEquals(listOf("甲", "乙"), GroupScheduler.speakersForTurn(group.copy(generationMode = GroupGenerationMode.APPEND)))
        assertEquals(listOf("甲", "乙", "丙"), GroupScheduler.speakersForTurn(group.copy(generationMode = GroupGenerationMode.APPEND_DISABLED)))
        assertEquals(emptyList<String>(), GroupScheduler.speakersForTurn(group))
    }

    @Test
    fun `empty group returns null`() {
        assertNull(GroupScheduler.nextSpeaker(group.copy(members = emptyList()), null))
    }
}

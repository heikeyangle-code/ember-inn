package com.emberinn.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁住“消息类斜杠命令 → App 动作”的接线契约（解析器由引擎差分覆盖）。 */
class AppSlashExecutorTest {

    private class FakeActions : SlashMessageActions {
        val calls = mutableListOf<String>()

        override fun sendAsCharacter(name: String, text: String, at: Int?, avatar: String?, compact: Boolean): ManualSendResult {
            calls += "sendas:$name:$text:$at:${avatar ?: ""}:$compact"; return ManualSendResult(text, "{}")
        }
        override fun sendAsUser(text: String, name: String?, at: Int?, compact: Boolean): ManualSendResult {
            calls += "send:$text:${name ?: ""}:$at:$compact"; return ManualSendResult(text, "{}")
        }
        override fun sendSystemMessage(text: String, name: String): String { calls += "sys:$name:$text"; return "" }
        override fun setNarratorName(name: String): String { calls += "sysname:$name"; return "" }
        override fun sendComment(text: String): String { calls += "comment:$text"; return "" }
        override fun getSetMessageRole(at: Int, role: String): String { calls += "role:$at:$role"; return "assistant" }
        override fun getSetMessageName(at: Int, name: String): String { calls += "name:$at:$name"; return "小红" }
        override fun hideMessage(index: Int, hidden: Boolean): String { calls += "hide:$index:$hidden"; return "" }
        override fun deleteMessagesByName(name: String): Int { calls += "delname:$name"; return 2 }
        override fun addSwipe(text: String, switch: Boolean): String { calls += "addswipe:$switch:$text"; return "1" }
        override fun deleteSwipe(id: Int?): String { calls += "delswipe:$id"; return "0" }
        override fun renameChat(name: String): String { calls += "renamechat:$name"; return "" }
        override fun chatName(): String { calls += "getchatname"; return "会话A" }
        override fun setInput(text: String): String { calls += "setinput:$text"; return text }
        override fun setBackground(text: String): String { calls += "bg:$text"; return "bg.png" }
        override fun impersonate(prompt: String): String { calls += "impersonate:$prompt"; return "" }
        override suspend fun continueChat(prompt: String): String { calls += "continue:$prompt"; return "" }
        override suspend fun regenerateChat(): String { calls += "regenerate"; return "" }
        override suspend fun swipeChat(direction: String): String { calls += "swipe:$direction"; return "" }
        override fun selectPersona(name: String, mode: String): String { calls += "persona:$mode:$name"; return "" }
        override fun applyPreset(name: String): String { calls += "preset:$name"; return "Default" }
        override suspend fun triggerGeneration(await: Boolean): String { calls += "trigger:$await"; return "" }
        override suspend fun generateText(prompt: String, length: Int?): String { calls += "gen:$prompt:$length"; return "生成文本" }
        override suspend fun generateRaw(prompt: String, system: String, prefill: String, length: Int?, instruct: Boolean, asRole: String, stop: String, trim: Boolean): String {
            calls += "genraw:$system:$prefill:$length:$instruct:$asRole:$stop:$trim:$prompt"
            return "原始生成"
        }
        override suspend fun summarize(text: String, source: String?, prompt: String?, quiet: Boolean): String {
            calls += "summarize:$source:$prompt:$quiet:$text"
            return "摘要文本"
        }
        override fun injectScript(text: String, id: String, position: String, depth: Int, role: String, scan: Boolean, ephemeral: Boolean, filter: String?): String {
            calls += "inject:$id:$position:$depth:$role:$scan:$ephemeral:${filter ?: ""}:$text"
            return "abc12345"
        }
        override fun notify(text: String) { calls += "notify:$text" }
    }

    @Test
    fun `sendas requires name and forwards text`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/sendas name=小炭 你好 世界")
        assertEquals(listOf("sendas:小炭:你好 世界:null::false"), a.calls)
    }

    @Test
    fun `sendas without name falls back to empty name for viewmodel default`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/sendas 你好")
        // 官方 sendas 缺省 name 不报错，由 ChatViewModel 兜底当前角色名
        assertEquals(listOf("sendas::你好:null::false"), a.calls)
    }

    @Test
    fun `sys and comment insert messages in chain`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/sys 雪很大 | /comment 这是评论")
        assertEquals(listOf("sys::雪很大", "comment:这是评论"), a.calls)
    }

    @Test
    fun `message role and name target at`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/message-role at=-1 assistant | /message-name at=0 小红")
        assertEquals(listOf("role:-1:assistant", "name:0:小红"), a.calls)
    }

    @Test
    fun `hide unhide and delname with notify`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/hide 2-4 | /unhide | /delname 小明")
        assertEquals(listOf("hide:2:true", "hide:-1:false", "delname:小明", "notify:已删除 2 条消息"), a.calls)
    }

    @Test
    fun `swipe add and delete`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/addswipe switch=true 新回复 | /delswipe 2")
        assertEquals(listOf("addswipe:true:新回复", "delswipe:2"), a.calls)
    }

    @Test
    fun `renamechat getchatname setinput bg impersonate forward to actions`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute(
            "/renamechat 新会话 | /getchatname | /setinput 你好世界 | /bg clear | /impersonate 写一句旁白",
        )
        assertEquals(
            listOf("renamechat:新会话", "getchatname", "setinput:你好世界", "bg:clear", "impersonate:写一句旁白"),
            a.calls,
        )
    }

    @Test
    fun `persona set forwards name and mode`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/persona-set mode=lookup 小红 | /persona 小明")
        assertEquals(listOf("persona:lookup:小红", "persona:all:小明"), a.calls)
    }

    @Test
    fun `gen and genraw run via async executor`() = runBlocking {
        val a = FakeActions()
        val out1 = AppSlashExecutor(a).executeAsync("/gen 你好")
        val out2 = AppSlashExecutor(a).executeAsync("/genraw system=系统 prefill=开头 length=100 你好世界")
        assertEquals("生成文本", out1)
        assertEquals("原始生成", out2)
        assertEquals(listOf("gen:你好:null", "genraw:系统:开头:100:true:system:[]:true:你好世界"), a.calls)
    }

    @Test
    fun `trigger forwards to generation`() {
        val a = FakeActions()
        AppSlashExecutor(a).execute("/trigger")
        assertEquals(listOf("trigger:false"), a.calls)
    }

    @Test
    fun `inject forwards text and named args`() {
        val a = FakeActions()
        val out = AppSlashExecutor(a).execute("/inject id=note position=chat depth=3 role=user ephemeral=true 记住这条设定")
        assertEquals(listOf("inject:note:chat:3:user:false:true::记住这条设定"), a.calls)
        assertEquals("abc12345", out)
    }

    @Test
    fun `pure engine commands still work through app executor`() {
        val a = FakeActions()
        val out = AppSlashExecutor(a).execute("/upper 你好 | /echo")
        assertEquals("你好", out)
        assertEquals(0, a.calls.size)
    }
}

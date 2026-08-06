package com.emberinn.engine.macros

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEngineTest {

    private val env = MacroEnv(user = "玩家", char = "柳春娘")

    @Test
    fun `user and char macros substitute`() {
        assertEquals("玩家和柳春娘", MacroEngine.substitute("{{user}}和{{char}}", env))
    }

    @Test
    fun `iso time and date formats`() {
        val out = MacroEngine.substitute("{{isodate}} {{isotime}}", env)
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(out))
    }

    @Test
    fun `random picks one of list`() {
        val list = listOf("a", "b", "c")
        repeat(20) {
            val out = MacroEngine.substitute("{{random::a::b::c}}", env)
            assertTrue(out in list)
        }
    }

    @Test
    fun `roll dice with modifier`() {
        repeat(30) {
            val v = MacroEngine.substitute("{{roll::2d6+4}}", env).toInt()
            assertTrue(v in 6..16)
        }
    }

    @Test
    fun `pick is stable for same input`() {
        val same = MacroEngine.substitute("{{pick::红::蓝::绿}}", env)
        val same2 = MacroEngine.substitute("{{pick::红::蓝::绿}}", env)
        assertEquals(same, same2)
    }

    @Test
    fun `unknown macro stays as-is`() {
        assertEquals("{{unknownmacro::x}}", MacroEngine.substitute("{{unknownmacro::x}}", env))
    }

    @Test
    fun `scoped if truthy and else branch`() {
        assertEquals("有用户", MacroEngine.substitute("{{if user}}有用户{{else}}没有{{/if}}", env))
        assertEquals("没有", MacroEngine.substitute("{{if 0}}有{{else}}没有{{/if}}", env))
    }

    @Test
    fun `scoped if inverted`() {
        assertEquals("有", MacroEngine.substitute("{{if !user}}没有{{else}}有{{/if}}", env))
    }

    @Test
    fun `nested scoped if`() {
        assertEquals("ABC", MacroEngine.substitute("{{if user}}A{{if char}}B{{/if}}C{{/if}}", env))
    }

    @Test
    fun `inline if macro`() {
        assertEquals("你好", MacroEngine.substitute("{{if user::你好}}", env))
        assertEquals("", MacroEngine.substitute("{{if 0::你好}}", env))
    }

    @Test
    fun `space form roll works`() {
        repeat(20) {
            val v = MacroEngine.substitute("{{roll 1d6}}", env).toInt()
            assertTrue(v in 1..6)
        }
    }
    
    @Test
    fun `variable macros set get inc dec and shorthand`() {
        val local = MemoryVariableStore()
        val env2 = env.copy(local = local)

        assertEquals("", MacroEngine.substitute("{{setvar::hp::100}}", env2))
        assertEquals("100", MacroEngine.substitute("{{getvar::hp}}", env2))
        assertEquals("101", MacroEngine.substitute("{{incvar::hp}}", env2))
        assertEquals("100", MacroEngine.substitute("{{decvar::hp}}", env2))
        assertEquals("true", MacroEngine.substitute("{{hasvar::hp}}", env2))
        assertEquals("100", MacroEngine.substitute("{{.hp}}", env2))
    }

    @Test
    fun `addvar numeric and string append`() {
        val local = MemoryVariableStore()
        val env2 = env.copy(local = local)
        MacroEngine.substitute("{{setvar::n::5}}", env2)
        MacroEngine.substitute("{{addvar::n::3}}", env2)
        assertEquals("8", MacroEngine.substitute("{{getvar::n}}", env2))
        MacroEngine.substitute("{{addvar::s::ab}}", env2)
        assertEquals("0ab", MacroEngine.substitute("{{getvar::s}}", env2))
    }

    @Test
    fun `if with variable condition`() {
        val local = MemoryVariableStore()
        local.set("flag", "true")
        val env2 = env.copy(local = local)
        assertEquals("开了", MacroEngine.substitute("{{if .flag}}开了{{else}}关了{{/if}}", env2))
    }


    @Test
    fun `character field macros with aliases`() {
        val fields = CharacterFields(
            description = "描述A",
            personality = "性格B",
            scenario = "场景C",
            creatorNotes = "备注D",
            version = "v3.6",
            firstMessage = "开场",
            alternateGreetings = listOf("备选1", "备选2"),
            mesExamplesRaw = "\n<START>\n示例一\n<START>\n示例二\n",
            charPrompt = "主提示",
        )
        val env2 = env.copy(character = fields, group = "甲,乙", notChar = "乙")
        assertEquals("描述A", MacroEngine.substitute("{{description}}", env2))
        assertEquals("性格B", MacroEngine.substitute("{{personality}}", env2))
        assertEquals("场景C", MacroEngine.substitute("{{scenario}}", env2))
        assertEquals("备注D", MacroEngine.substitute("{{creatorNotes}}", env2))
        assertEquals("v3.6", MacroEngine.substitute("{{charVersion}}", env2))
        assertEquals("主提示", MacroEngine.substitute("{{charPrompt}}", env2))
        assertEquals("开场", MacroEngine.substitute("{{greeting}}", env2))
        assertEquals("备选1", MacroEngine.substitute("{{greeting::1}}", env2))
        assertEquals("备选2", MacroEngine.substitute("{{greeting::2}}", env2))
        assertEquals("示例一示例二", MacroEngine.substitute("{{mesExamples}}", env2))
        assertEquals("甲,乙", MacroEngine.substitute("{{group}}", env2))
        assertEquals("乙", MacroEngine.substitute("{{notChar}}", env2))
    }


    @Test
    fun `chat and state macros`() {
        val chat = listOf(
            ChatMessage(mes = "你好", isUser = true),
            ChatMessage(mes = "你好呀", isUser = false, swipes = listOf("第一版", "第二版"), swipeId = 1),
        )
        val env2 = env.copy(
            chat = chat,
            maxContextTokens = 32000,
            maxResponseTokens = 1000,
            maxPromptTokens = 31000,
            input = "输入中",
            lastGenerationType = "normal",
        )
        assertEquals("你好呀", MacroEngine.substitute("{{lastMessage}}", env2))
        assertEquals("1", MacroEngine.substitute("{{lastMessageId}}", env2))
        assertEquals("你好", MacroEngine.substitute("{{lastUserMessage}}", env2))
        assertEquals("你好呀", MacroEngine.substitute("{{lastCharMessage}}", env2))
        assertEquals("0-1", MacroEngine.substitute("{{allChatRange}}", env2))
        assertEquals("2", MacroEngine.substitute("{{lastSwipeId}}", env2))
        assertEquals("2", MacroEngine.substitute("{{currentSwipeId}}", env2))
        assertEquals("32000", MacroEngine.substitute("{{maxContextTokens}}", env2))
        assertEquals("1000", MacroEngine.substitute("{{maxResponseTokens}}", env2))
        assertEquals("31000", MacroEngine.substitute("{{maxPrompt}}", env2))
        assertEquals("输入中", MacroEngine.substitute("{{input}}", env2))
        assertEquals("normal", MacroEngine.substitute("{{lastGenerationType}}", env2))
        assertEquals(" ", MacroEngine.substitute("{{space}}", env2))
        assertEquals("\n", MacroEngine.substitute("{{newline}}", env2))
        assertEquals("cba", MacroEngine.substitute("{{reverse::abc}}", env2))
        assertEquals("", MacroEngine.substitute("{{// 注释}}", env2))
    }
}

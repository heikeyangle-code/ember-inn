package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方行为差分：TTS 扩展各后端 fetchTtsGeneration / fetchNativeTtsGeneration 构造的 request body。
 * fixture 由 scripts/diff/tts-services-official.mjs 生成，禁止手改。
 *
 * 覆盖 11 个已接云端后端：
 * - elevenlabs（shouldInvolveExtendedSettings 分支：turbo 不加 style/use_speaker_boost，v3 系列加）
 * - openai（gpt-4o-mini-tts + characterName + 非空 instructions → 加 instructions）
 * - edge / azure（简单 flat body）
 * - novel / pollinations（splitRecursive 分块，body.text 取首块）
 * - minimax（clamp + defaultSettings 兜底，pitch=Math.round||default）
 * - volcengine / chutes（|| 短路：空串→af_heart，0→1）
 * - google-native（useReverseProxy 分支：isValidUrl 校验）
 * - google-translate（splitRecursive 分块，body.text 是数组）
 */
class TtsServicesDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tts backends match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/tts-services.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty()) { "no tts-services cases" }

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            when (kind) {
                "elevenlabs" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.elevenLabsRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "openai" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val characterName = args.nullableStr("characterName")
                    val instructions = args.nullableStr("characterInstructions")
                    val actual = TtsRequestEngine.openAiRequestBody(settings, inputText, voiceId, characterName, instructions)
                    assertEquals("case $id", expected, actual)
                }
                "edge" -> {
                    val settings = args.obj("settings")
                    val text = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.edgeRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "azure" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.azureRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "novel" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.novelRequestBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "minimax" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val language = args.nullableStr("language")
                    val actual = TtsRequestEngine.minimaxRequestBody(settings, inputText, voiceId, language)
                    assertEquals("case $id", expected, actual)
                }
                "volcengine" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceSpeaker = args.str("voiceSpeaker")
                    val actual = TtsRequestEngine.volcengineRequestBody(settings, text, voiceSpeaker)
                    assertEquals("case $id", expected, actual)
                }
                "chutes" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.nullableStr("voiceId")
                    val actual = TtsRequestEngine.chutesRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "pollinations" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.pollinationsRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "google-native" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.str("voiceId")
                    val oaiSettings = args.obj("oaiSettings")
                    val actual = TtsRequestEngine.googleNativeRequestBody(settings, text, voiceId, oaiSettings)
                    assertEquals("case $id", expected, actual)
                }
                "google-translate" -> {
                    val settings = args.obj("settings")
                    val text = args.str("text")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.googleTranslateRequestBody(settings, text, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    @Test
    fun `splitRecursive matches official utils behavior`() {
        // 逐字对照官方 utils.js splitRecursive JSDoc 例：
        // splitRecursive('Hello, world!', 3) → ['Hel', 'lo,', 'wor', 'ld!']
        val result = TtsRequestEngine.splitRecursive("Hello, world!", 3)
        assertEquals(listOf("Hel", "lo,", "wor", "ld!"), result)

        // length<=0 → [input]（官方：if (length <= 0) return [input]）
        assertEquals(listOf("abc"), TtsRequestEngine.splitRecursive("abc", 0))
        assertEquals(listOf("abc"), TtsRequestEngine.splitRecursive("abc", -1))

        // 空串 → ['']（官方：''.split('\n\n')=['']，结果 ['']）
        assertEquals(listOf(""), TtsRequestEngine.splitRecursive("", 10))

        // 短文本（≤ length）→ [input]
        assertEquals(listOf("hi"), TtsRequestEngine.splitRecursive("hi", 10))

        // 多分隔符分块：以 \n\n 分割，每段仍 ≤ length 则合并
        val multi = TtsRequestEngine.splitRecursive("para1\n\npara2\n\npara3", 100)
        assertEquals(listOf("para1\n\npara2\n\npara3"), multi)

        // 长文本含换行：先按 \n\n 切分，每段仍 ≤ length 则保留
        val mix = TtsRequestEngine.splitRecursive("line1\nline2\nline3", 100)
        assertEquals(listOf("line1\nline2\nline3"), mix)

        // 长文本按 \n 切分（无 \n\n）：每段切到 ≤ length
        // 注：官方 splitRecursive 在 length=1 且无可用 delim 时会无限递归（JS 也会 RangeError），
        // 此处用 length=2 避免（delim='' 时每字 1<2 → listOf(p)，merge 合并为 2 字块）
        val parts2 = TtsRequestEngine.splitRecursive("abcdef", 2)
        assertEquals(listOf("ab", "cd", "ef"), parts2)
    }

    // ---------- helpers ----------
    private fun JsonObject.obj(key: String): JsonObject = this[key]!!.jsonObject
    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
    private fun JsonObject.nullableStr(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

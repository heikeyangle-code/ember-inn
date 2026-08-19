package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方行为差分：TTS 扩展本地后端 fetchTtsGeneration / generateTts 内联请求体/URL 纯逻辑。
 * fixture 由 scripts/diff/tts-local-official.mjs 生成，禁止手改。
 *
 * 覆盖 13 个本地后端（38 例）：
 * - alltalk（4 例：V1/V2 + RVC char/both 分支；form 编码 space=+）
 * - chatterbox（5 例：predefined-defaults/empty-fallback/clone-ref/seed-positive/seed-zero-passes；
 *   'ref_' 前缀 clone 分支、seed>=0 短路、空 voiceId 回退 predefined_voice、Math.random 打桩 0）
 * - coqui（4 例：simple/multilingual-language/three-tokens-speaker/quotes-stripped；
 *   replaceAll ] "/split('[')、multilingual language 分支、parseInt('none')=null）
 * - cosyvoice（2 例：defaults/streaming-true；if(streaming) 加 streaming=1）
 * - gpt-sovits-adapter（2 例：defaults/en；getCharacters 打桩空串）
 * - gpt-sovits-v2（3 例：defaults/bracket-stripped/en；replaceSpeaker 正则去 [..]）
 * - gsvi（2 例：defaults/space-encoded；URLSearchParams query 字符串，CJK URL 编码）
 * - sbvits2（4 例：defaults/br-replaced/assist-and-ref/style-multi-dash；split('-') 解构、
 *   <br>→\n、assist_text/reference_audio_path truthy 分支、style 多 dash join）
 * - silerotts（2 例：defaults/special-chars；flat body）
 * - speecht5（1 例：speaker 作不透明入参）
 * - tts-webui（3 例：defaults/streaming-true/filters-unknown-keys；chatterboxParams 15 字段过滤）
 * - vits（4 例：vits-defaults/w2v2-emotion/bert-full/bert-no-style-text；forceNoStreaming=true 锁非流式，
 *   model_type 分支 VITS/W2V2-VITS/BERT-VITS2 + text_prompt/style_text truthy）
 * - xtts（2 例：defaults/other-language；processText 死代码不调用）
 *
 * 边界（不差分，登记见 TtsRequestEngine KDoc 与 HANDOFF 4.4）：
 * - kokoro / kokoro-worker / openai-compatible / system 与官方不同源（Worker / ST 代理），不差分。
 * - speecht5 speaker 值源不同（官方运行时拉取嵌入，App 用 voiceId 直传），body 字段集合一致。
 * - xtts processText 在官方 fetchTtsGeneration 不调用，App 也不调用。
 */
class TtsLocalDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tts local backends match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/tts-local.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty()) { "no tts-local cases" }

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            when (kind) {
                "alltalk" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.allTalkForm(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "chatterbox" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    // fixture 打桩 Math.random=()=>0 → Math.floor(0*2147483648)=0
                    val actual = TtsRequestEngine.chatterboxBody(settings, inputText, voiceId, randomSeed = 0L)
                    assertEquals("case $id", expected, actual)
                }
                "coqui" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.coquiBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "cosyvoice" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.cosyVoiceBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "gpt-sovits-adapter" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.gptSoVitsAdapterBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "gpt-sovits-v2" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.gptSoVitsV2Body(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "gsvi" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.gsviQuery(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "sbvits2" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.sbvits2Query(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "silerotts" -> {
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.sileroBody(inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "speecht5" -> {
                    val inputText = args.str("inputText")
                    val speakerData = args.str("speakerData")
                    val actual = TtsRequestEngine.speechT5Body(inputText, speakerData)
                    assertEquals("case $id", expected, actual)
                }
                "tts-webui" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.ttsWebuiBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                "vits" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val forceNoStreaming = args["forceNoStreaming"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    val actual = TtsRequestEngine.vitsForm(settings, inputText, voiceId, forceNoStreaming)
                    assertEquals("case $id", expected, actual)
                }
                "xtts" -> {
                    val settings = args.obj("settings")
                    val inputText = args.str("inputText")
                    val voiceId = args.str("voiceId")
                    val actual = TtsRequestEngine.xttsBody(settings, inputText, voiceId)
                    assertEquals("case $id", expected, actual)
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    // ---------- helpers ----------
    private fun JsonObject.obj(key: String): JsonObject = this[key]!!.jsonObject
    private fun JsonObject.str(key: String): String = this[key]!!.jsonPrimitive.content
}

package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：script.js swipe/generatedTextFiltered/extractMultiSwipes。
 * fixture 由 scripts/diff/swipe-official.mjs 生成，禁止手改。
 */
class SwipeDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `swipe matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/swipe.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected")

            when (body.getValue("method").jsonPrimitive.content) {
                "allowed" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content == "true",
                    SwipeEngine.isSwipingAllowed(
                        chatLength = body["chat"]?.jsonArray.orEmpty().size,
                        swipesEnabled = body.bool("swipesEnabled", true),
                        swipesHidden = body.bool("swipesHidden"),
                        isGenerating = body.bool("isGenerating"),
                        midSwipe = body.bool("midSwipe"),
                    ),
                )
                "swipeable" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content == "true",
                    SwipeEngine.isMessageSwipeable(
                        messageId = body.int("messageId"),
                        chatLength = body["chat"]?.jsonArray.orEmpty().size,
                        thisEditMesId = body["thisEditMesId"]?.jsonPrimitive?.content?.toIntOrNull(),
                        editing = body.bool("midSwipe"),
                        message = body["message"]?.jsonObject?.toSwipeMessage() ?: SwipeMessage(),
                    ),
                )
                "overswipe" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content,
                    SwipeEngine.getOverswipeBehavior(
                        messageId = body.int("messageId"),
                        message = body["message"]?.jsonObject?.toSwipeMessage() ?: SwipeMessage(),
                        chatTainted = body.bool("chatTainted"),
                    ),
                )
                "ensure" -> {
                    val message = body["message"]?.jsonObject?.toSwipeMessage() ?: SwipeMessage()
                    val result = SwipeEngine.ensureSwipes(message)
                    assertEquals("case $id updated", expected.jsonObject["updated"]?.jsonPrimitive?.content == "true", result.updated)
                    assertEquals("case $id swipes", expected.jsonObject["message"]?.jsonObject?.get("swipes")?.jsonArray.orEmpty().map { it.jsonPrimitive.content }, result.message.swipes.orEmpty())
                    assertEquals("case $id swipe_id", expected.jsonObject["message"]?.jsonObject?.get("swipe_id")?.jsonPrimitive?.content?.toIntOrNull(), result.message.swipeId)
                }
                "filtered" -> assertEquals(
                    "case $id",
                    expected.jsonPrimitive.content == "true",
                    SwipeEngine.generatedTextFiltered(
                        text = body["text"]?.jsonPrimitive?.content ?: "",
                        minimumLength = body.int("minimumLength"),
                        blacklist = body["blacklist"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                        threshold = body.int("threshold"),
                    ),
                )
                "multi" -> assertEquals(
                    "case $id",
                    expected.jsonArray.map { it.jsonPrimitive.content },
                    SwipeEngine.extractMultiSwipes(
                        data = body["data"],
                        type = body["type"]?.jsonPrimitive?.content ?: "normal",
                        mainApi = body["mainApi"]?.jsonPrimitive?.content ?: "openai",
                        textgenType = body["textgenType"]?.jsonPrimitive?.content,
                        cleanUpConfig = CleanUpConfig(),
                    ),
                )
            }
        }
    }

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default

    private fun JsonObject.toSwipeMessage(): SwipeMessage {
        val extra = this["extra"]?.jsonObject
        return SwipeMessage(
            mes = this["mes"]?.jsonPrimitive?.content ?: "",
            isUser = this["is_user"]?.jsonPrimitive?.content == "true",
            isSystem = this["is_system"]?.jsonPrimitive?.content == "true",
            swipes = this["swipes"]?.jsonArray?.map { it.jsonPrimitive.content },
            swipeId = this["swipe_id"]?.jsonPrimitive?.content?.toIntOrNull(),
            swipeInfo = this["swipe_info"]?.jsonArray?.map { it.jsonObject },
            isSmallSys = extra?.get("isSmallSys")?.jsonPrimitive?.content == "true",
            swipeable = extra?.get("swipeable")?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
            overswipeBehavior = extra?.get("overswipe_behavior")?.jsonPrimitive?.content,
        )
    }
}

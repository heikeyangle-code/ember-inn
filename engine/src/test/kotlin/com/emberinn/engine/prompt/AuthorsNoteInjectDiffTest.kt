package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** 官方行为差分：authors-note.js shouldInject 判定。 */
class AuthorsNoteInjectDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `authors note inject matches official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/authors-note-inject.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val body = case.getValue("args").jsonObject.getValue("body").jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content == "true"
            val actual = AuthorsNoteEngine.shouldInjectNote(
                lastUserMessageNumber = body["userMessages"]?.jsonPrimitive?.content?.toInt() ?: 0,
                interval = body["interval"]?.jsonPrimitive?.content?.toInt() ?: 0,
            )
            assertEquals("case $id", expected, actual)
        }
    }
}

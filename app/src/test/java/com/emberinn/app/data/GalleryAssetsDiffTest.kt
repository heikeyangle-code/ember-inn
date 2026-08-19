package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gallery 4 种排序字面值 + Assets 类型集（5 类）差分，对齐官方 gallery/index.js + assets/index.js。 */
class GalleryAssetsDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `gallery+assets matches official fixtures`() {
        val res = checkNotNull(javaClass.getResource("/diff/gallery-assets.json"))
        val root = json.parseToJsonElement(res.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertEquals(5, cases.size)

        for (el in cases) {
            val c = el.jsonObject
            val id = c.getValue("id").jsonPrimitive.int
            val tag = c.getValue("_tag").jsonPrimitive.content
            val expected = c.getValue("expected").jsonObject

            when (tag) {
                "gallery-sort" -> {
                    val v = expected.getValue("value").jsonPrimitive.content
                    val sort = GalleryService.Sort.fromValue(v)
                    val eValue = expected["value"]!!.jsonPrimitive.content
                    val eField = expected["field"]!!.jsonPrimitive.content
                    val eOrder = expected["order"]!!.jsonPrimitive.content
                    assertEquals("case $id sort.value", eValue, sort.value)
                    assertEquals("case $id sort.field", eField, sort.field)
                    assertEquals("case $id sort.order", eOrder, sort.order)
                }
                "assets-types" -> {
                    val expectedSet = expected["types"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    assertEquals("case $id types count", 5, expectedSet.size)
                    val actualSet = AssetsService.KNOWN_TYPES.keys.toSet()
                    assertEquals("case $id assets types", expectedSet, actualSet)
                    expectedSet.forEach { t ->
                        assertTrue("case $id isKnownType($t)", AssetsService.isKnownType(t))
                    }
                }
            }
        }
    }
}

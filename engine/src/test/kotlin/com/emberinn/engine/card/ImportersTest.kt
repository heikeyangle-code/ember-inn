package com.emberinn.engine.card

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportersTest {

    @Test
    fun `yaml import maps official fields`() {
        val yaml = """
            name: 测试角色
            context: 背景描述
            greeting: 你好呀
        """.trimIndent()
        val json = YamlImporter.import(yaml.toByteArray())
        assertTrue(json.contains("\"name\":\"测试角色\""))
        assertTrue(json.contains("\"description\":\"背景描述\""))
        assertTrue(json.contains("\"first_mes\":\"你好呀\""))
        assertTrue(json.contains("\"spec\":\"chara_card_v2\""))
        assertTrue(json.contains("\"talkativeness\":0.5"))
    }

    @Test
    fun `byaf import maps macros lore and greetings`() {
        val manifest = """{"characters":["character.json"],"scenarios":["s1.json","s2.json"],"author":{"name":"作者","backyardURL":"https://by"}}"""
        val character = """{"name":"角色A","displayName":"显示名","persona":"#{user}:与#{character}:以及{user}","isNSFW":true,"loreItems":[{"key":"地点, 人物","value":"#{user}:在这里"}]}"""
        val s1 = """{"firstMessages":[{"text":"第一开场"}],"narrative":"叙事","formattingInstructions":"指令","exampleMessages":[{"text":"示例#{user}"}]}"""
        val s2 = """{"firstMessages":[{"text":"第二开场"}]}"""
        val zip = zipOf(
            "manifest.json" to manifest,
            "character.json" to character,
            "s1.json" to s1,
            "s2.json" to s2,
        )
        val json = ByafImporter.import(zip)
        assertTrue(json.contains("\"name\":\"角色A\""))
        assertTrue(json.contains("\"display_name\":\"显示名\""))
        assertTrue(json.contains("\"description\":\"{{user}}:与{{char}}:以及{{user}}\""))
        assertTrue(json.contains("\"first_mes\":\"第一开场\""))
        assertTrue(json.contains("\"第二开场\""))
        assertTrue(json.contains("\"keys\":[\"地点\",\"人物\"]"))
        assertTrue(json.contains("\"tags\":[\"nsfw\"]"))
    }

    @Test
    fun `v3 normalize hoists fields with defaults`() {
        val v3 = """{"spec":"chara_card_v3","data":{"name":"N","description":"D","extensions":{"talkativeness":0.7,"fav":true}}}"""
        val out = V2Normalizer.normalize(v3)
        assertTrue(out.contains("\"name\":\"N\""))
        assertTrue(out.contains("\"talkativeness\":0.7"))
        assertTrue(out.contains("\"fav\":true"))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

package com.emberinn.engine.worldinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoScannerTest {

    private fun entry(
        uid: Int,
        order: Int,
        keys: List<String> = emptyList(),
        content: String = "",
        constant: Boolean = false,
        position: Int = WorldInfoConstants.POSITION_BEFORE,
        selective: Boolean = false,
        keySecondary: List<String> = emptyList(),
        selectiveLogic: Int? = null,
        sticky: Int? = null,
        cooldown: Int? = null,
        group: String? = null,
        groupOverride: Boolean? = null,
        useGroupScoring: Boolean? = null,
        useProbability: Boolean = false,
        probability: Int = 100,
        scanDepth: Int? = null,
        depth: Int? = null,
        role: String? = null,
    ) = WorldInfoEntry(
        world = "w", uid = uid, order = order, keys = keys, content = content,
        constant = constant, position = position, selective = selective, keySecondary = keySecondary,
        selectiveLogic = selectiveLogic, sticky = sticky, cooldown = cooldown,
        group = group, groupOverride = groupOverride, useGroupScoring = useGroupScoring,
        useProbability = useProbability, probability = probability, hash = uid.toLong(),
        scanDepth = scanDepth, depth = depth, role = role,
    )

    @Test
    fun `constant and keyword entries assemble before and after`() {
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            chat = listOf("这里有钥匙"),
            maxContext = 100,
            entries = listOf(
                entry(1, 1, content = "常驻", constant = true, position = WorldInfoConstants.POSITION_BEFORE),
                entry(2, 2, keys = listOf("钥匙"), content = "触发", position = WorldInfoConstants.POSITION_AFTER),
            ),
            settings = WorldInfoSettings(budgetPercent = 100),
        )
        assertEquals("常驻", result.worldInfoBefore)
        assertEquals("触发", result.worldInfoAfter)
        assertEquals(2, result.activated.size)
    }

    @Test
    fun `recursion activates entry matched by recurse buffer`() {
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            chat = listOf("线索"),
            maxContext = 100,
            entries = listOf(
                entry(1, 1, keys = listOf("线索"), content = "暗门"),
                entry(2, 2, keys = listOf("暗门"), content = "宝藏"),
            ),
            settings = WorldInfoSettings(recursive = true, budgetPercent = 100),
        )
        assertTrue(result.activated.any { it.uid == 2 })
        assertTrue(result.worldInfoBefore.contains("宝藏"))
    }

    @Test
    fun `AND ALL secondary keywords require all matches`() {
        val scanner = WorldInfoScanner()
        val e = entry(1, 1, keys = listOf("门"), content = "X", selective = true,
            keySecondary = listOf("钥匙", "锁"), selectiveLogic = WorldInfoConstants.AND_ALL)

        val partial = scanner.scan(listOf("门和钥匙"), 100, listOf(e), WorldInfoSettings(budgetPercent = 100))
        assertTrue(partial.activated.isEmpty())

        val full = scanner.scan(listOf("门 钥匙 锁"), 100, listOf(e), WorldInfoSettings(budgetPercent = 100))
        assertEquals(1, full.activated.size)
    }

    @Test
    fun `budget overflow stops further entries`() {
        val scanner = WorldInfoScanner(tokenCounter = TokenCounter { it.length })
        val result = scanner.scan(
            chat = listOf("a b"),
            maxContext = 10,
            entries = listOf(
                entry(1, 2, keys = listOf("a"), content = "aaaaaa"),
                entry(2, 1, keys = listOf("b"), content = "bbbbbb"),
            ),
            settings = WorldInfoSettings(budgetPercent = 100),
        )
        assertEquals(1, result.activated.size)
        assertTrue(result.activated.first().uid == 1)
    }

    @Test
    fun `min activations advances scan depth`() {
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            chat = listOf("第一句", "钥匙"),
            maxContext = 100,
            entries = listOf(entry(1, 1, keys = listOf("钥匙"), content = "找到了")),
            settings = WorldInfoSettings(budgetPercent = 100, minActivations = 1, depth = 1),
        )
        assertEquals(1, result.activated.size)
        assertTrue(result.worldInfoBefore.contains("找到了"))
    }

    @Test
    fun `sticky keeps entry active in next scan without key match`() {
        val e = entry(1, 1, keys = listOf("a"), content = "粘住", sticky = 2)
        val scanner = WorldInfoScanner()
        val first = scanner.scan(listOf("a"), 100, listOf(e), WorldInfoSettings(budgetPercent = 100))
        assertTrue(first.timedMetadata.sticky.containsKey("w.1"))

        val second = scanner.scan(
            chat = listOf("x", "b"),
            maxContext = 100,
            entries = listOf(e),
            settings = WorldInfoSettings(budgetPercent = 100),
            timedMetadata = first.timedMetadata,
        )
        assertTrue(second.activated.any { it.uid == 1 })
    }

    @Test
    fun `cooldown suppresses entry during its window`() {
        val e = entry(1, 1, keys = listOf("a"), content = "冷", cooldown = 3)
        val scanner = WorldInfoScanner()
        val first = scanner.scan(listOf("a"), 100, listOf(e), WorldInfoSettings(budgetPercent = 100))
        assertTrue(first.timedMetadata.cooldown.containsKey("w.1"))

        val second = scanner.scan(
            chat = listOf("a", "b"),
            maxContext = 100,
            entries = listOf(e),
            settings = WorldInfoSettings(budgetPercent = 100),
            timedMetadata = first.timedMetadata,
        )
        assertTrue(second.activated.isEmpty())
    }

    @Test
    fun `inclusion group override picks highest order`() {
        val e1 = entry(1, 1, keys = listOf("门"), content = "A", group = "g", groupOverride = true)
        val e2 = entry(2, 5, keys = listOf("门"), content = "B", group = "g", groupOverride = true)
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            listOf("门"), 100, listOf(e1, e2), WorldInfoSettings(budgetPercent = 100),
        )
        assertEquals(1, result.activated.size)
        assertTrue(result.activated.first().uid == 2)
    }

    @Test
    fun `group scoring keeps highest key score`() {
        val e1 = entry(1, 1, keys = listOf("a", "b"), content = "高", group = "g", useGroupScoring = true)
        val e2 = entry(2, 2, keys = listOf("a"), content = "低", group = "g", useGroupScoring = true)
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            listOf("a b"), 100, listOf(e1, e2),
            WorldInfoSettings(budgetPercent = 100, useGroupScoring = true),
        )
        assertEquals(1, result.activated.size)
        assertTrue(result.activated.first().uid == 1)
    }


    @Test
    fun `character filter excludes non-matching character`() {
        val e = entry(1, 1, keys = listOf("门"), content = "X").copy(
            characterFilter = CharacterFilter(names = listOf("柳春娘"), isExclude = false),
        )
        val scanner = WorldInfoScanner()

        val wrong = scanner.scan(
            listOf("门"), 100, listOf(e),
            WorldInfoSettings(budgetPercent = 100),
            global = GlobalScanData(characterName = "关东"),
        )
        assertTrue(wrong.activated.isEmpty())

        val right = scanner.scan(
            listOf("门"), 100, listOf(e),
            WorldInfoSettings(budgetPercent = 100),
            global = GlobalScanData(characterName = "柳春娘"),
        )
        assertEquals(1, right.activated.size)
    }

    @Test
    fun `sticky entry does not re-roll probability`() {
        val e = entry(1, 1, keys = listOf("a"), content = "粘住", sticky = 2)
        val scanner = WorldInfoScanner(random = RandomProvider { 99.0 }) // 随机必然失败
        // 第一次无概率检查，激活并建立 sticky
        val first = scanner.scan(listOf("a"), 100, listOf(e), WorldInfoSettings(budgetPercent = 100))
        assertTrue(first.timedMetadata.sticky.containsKey("w.1"))

        val eProb = e.copy(useProbability = true, probability = 1)
        val second = scanner.scan(
            chat = listOf("x", "y"),
            maxContext = 100,
            entries = listOf(eProb),
            settings = WorldInfoSettings(budgetPercent = 100),
            timedMetadata = first.timedMetadata,
        )
        // sticky 激活中：官方 verifyProbability 直接返回 true，不重掷
        assertTrue(second.activated.any { it.uid == 1 })
    }

    @Test
    fun `regex transformer applies to chat and content`() {
        val scanner = WorldInfoScanner(
            messageTransformer = { it.replace("钥匙", "锁") },
            contentTransformer = { it.replace("宝藏", "秘宝") },
        )
        val result = scanner.scan(
            chat = listOf("钥匙"),
            maxContext = 100,
            entries = listOf(entry(1, 1, keys = listOf("锁"), content = "宝藏")),
            settings = WorldInfoSettings(budgetPercent = 100),
        )
        assertEquals(1, result.activated.size)
        assertTrue(result.worldInfoBefore.contains("秘宝"))
    }

    @Test
    fun `negative scan depth never activates (official invalid WI scan depth)`() {
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            chat = listOf("钥匙"),
            maxContext = 100,
            entries = listOf(entry(1, 1, keys = listOf("钥匙"), content = "X", scanDepth = -1)),
            settings = WorldInfoSettings(budgetPercent = 100),
        )
        assertTrue(result.activated.isEmpty())
        assertEquals("", result.worldInfoBefore)
    }

    @Test
    fun `negative at depth injection depth is ignored`() {
        val scanner = WorldInfoScanner()
        val result = scanner.scan(
            chat = listOf("钥匙"),
            maxContext = 100,
            entries = listOf(entry(1, 1, keys = listOf("钥匙"), content = "X", position = WorldInfoConstants.POSITION_AT_DEPTH, depth = -1, role = "system")),
            settings = WorldInfoSettings(budgetPercent = 100),
        )
        assertEquals(1, result.activated.size)
        // 官方 WIDepthEntries 同样会收集负深度组；负深度在提示词总装层被忽略（populationInjectionPrompts 0..maxDepth）
        assertEquals(1, result.depthEntries.size)
        assertEquals(-1, result.depthEntries.first().depth)
    }
}

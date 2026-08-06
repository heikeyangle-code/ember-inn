package com.emberinn.engine.macros

/**
 * seedrandom v3.0.5 默认（ARC4）逐位移植，用于 {{pick}} 与官方完全一致。
 */
class SeedRandom(seed: String) {

    private companion object {
        const val WIDTH = 256
        const val MASK = WIDTH - 1
        const val CHUNKS = 6
        const val DIGITS = 52
        // 官方 v3：startdenom = width ^ chunks = 256^6 = 2^48
        val START_DENOM: Double = Math.pow(WIDTH.toDouble(), CHUNKS.toDouble())
        val SIGNIFICANCE: Double = Math.pow(2.0, DIGITS.toDouble())
        val OVERFLOW: Double = SIGNIFICANCE * 2.0
    }

    private val arc4: Arc4

    init {
        // mixkey(flatten(seed), key) —— seed 为字符串，flatten 原样返回
        val key = IntArray(seed.length)
        var smear = 0
        var j = 0
        var idx = 0
        while (idx < seed.length) {
            smear = smear xor (key[MASK and j] * 19)
            key[MASK and j] = MASK and (smear + seed[idx].code)
            j++
            idx++
        }
        arc4 = Arc4(key)
    }

    fun nextDouble(): Double {
        var n = arc4.g(CHUNKS).toDouble()
        var d = START_DENOM
        var x = 0.0
        while (n < SIGNIFICANCE) {
            n = (n + x) * WIDTH
            d *= WIDTH
            x = arc4.g(1).toDouble()
        }
        while (n >= OVERFLOW) {
            n /= 2.0
            d /= 2.0
            x = Math.floor(x / 2.0)
        }
        return (n + x) / d
    }

    private class Arc4(key: IntArray) {
        val s = IntArray(WIDTH)
        var i = 0
        var j = 0

        init {
            for (k in 0 until WIDTH) s[k] = k
            // 官方：空 key [] 视为 [0]
            val keyArr = if (key.isEmpty()) intArrayOf(0) else key
            val keyLen = keyArr.size
            // 官方 KSA 用局部 j（me.j 保持 0，g 从 0 开始）
            var ksaJ = 0
            for (k in 0 until WIDTH) {
                val t = s[k]
                ksaJ = MASK and (ksaJ + keyArr[k % keyLen] + t)
                s[k] = s[ksaJ]
                s[ksaJ] = t
            }
            g(WIDTH) // RC4-drop[256]
        }

        fun g(count: Int): Long {
            var r = 0L
            var c = count
            while (c-- > 0) {
                i = MASK and (i + 1)
                val t = s[i]
                j = MASK and (j + t)
                val oldSj = s[j]
                val sum = oldSj + t
                s[i] = oldSj
                s[j] = t
                r = r * WIDTH + s[MASK and sum]
            }
            return r
        }
    }
}

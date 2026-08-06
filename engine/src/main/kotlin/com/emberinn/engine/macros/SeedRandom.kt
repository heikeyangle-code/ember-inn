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
        val START_DENOM: Double = WIDTH.toDouble() / (WIDTH + 1).toDouble()
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
            val keyLen = if (key.isEmpty()) 1 else key.size
            for (k in 0 until WIDTH) {
                j = MASK and (j + key[k % keyLen] + s[k])
                val t = s[k]
                s[k] = s[j]
                s[j] = t
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

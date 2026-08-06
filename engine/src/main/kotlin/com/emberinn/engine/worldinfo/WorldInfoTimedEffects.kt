package com.emberinn.engine.worldinfo

/**
 * 对齐官方 WorldInfoTimedEffects：sticky / cooldown / delay 时间效果。
 * sticky 结束自动进入 cooldown（protected=true）；元数据随聊天持久化。
 */
class WorldInfoTimedEffects(
    private val chatLength: Int,
    private val entries: List<WorldInfoEntry>,
    private val metadata: TimedEffectsMetadata,
    private val isDryRun: Boolean = false,
) {
    private val stickyBuffer = mutableListOf<WorldInfoEntry>()
    private val cooldownBuffer = mutableListOf<WorldInfoEntry>()
    private val delayBuffer = mutableListOf<WorldInfoEntry>()

    private fun key(entry: WorldInfoEntry): String = "${entry.world}.${entry.uid}"
    private fun effect(type: String, entry: WorldInfoEntry, isProtected: Boolean): TimedEffect =
        TimedEffect(
            hash = entry.hash,
            start = chatLength,
            end = chatLength + (if (type == "sticky") entry.sticky ?: 0 else entry.cooldown ?: 0),
            protected = isProtected,
        )

    fun checkTimedEffects() {
        if (!isDryRun) {
            checkType("sticky", stickyBuffer) { entry ->
                val cd = entry.cooldown ?: return@checkType
                if (cd == 0) return@checkType
                val k = key(entry)
                metadata.cooldown[k] = effect("cooldown", entry, true)
                cooldownBuffer.add(entry)
            }
            checkType("cooldown", cooldownBuffer) {}
        }
        for (entry in entries) {
            val d = entry.delay ?: continue
            if (d > 0 && chatLength < d) delayBuffer.add(entry)
        }
    }

    private fun checkType(type: String, buffer: MutableList<WorldInfoEntry>, onEnded: (WorldInfoEntry) -> Unit) {
        val map = if (type == "sticky") metadata.sticky else metadata.cooldown
        val effects = map.toList()
        for ((k, value) in effects) {
            val entry = entries.firstOrNull { it.hash == value.hash }

            if (chatLength <= value.start && !value.protected) {
                map.remove(k)
                continue
            }
            if (entry == null) {
                if (chatLength >= value.end) map.remove(k)
                continue
            }
            val configured = if (type == "sticky") entry.sticky ?: 0 else entry.cooldown ?: 0
            if (configured == 0) {
                map.remove(k)
                continue
            }
            if (chatLength >= value.end) {
                map.remove(k)
                onEnded(entry)
                continue
            }
            buffer.add(entry)
        }
    }

    fun isEffectActive(type: String, entry: WorldInfoEntry): Boolean {
        val buffer = when (type) {
            "sticky" -> stickyBuffer
            "cooldown" -> cooldownBuffer
            "delay" -> delayBuffer
            else -> return false
        }
        return buffer.any { it.hash == entry.hash }
    }

    fun setTimedEffects(activated: List<WorldInfoEntry>) {
        if (isDryRun) return
        for (entry in activated) {
            val k = key(entry)
            if ((entry.sticky ?: 0) > 0 && !metadata.sticky.containsKey(k)) {
                metadata.sticky[k] = effect("sticky", entry, false)
            }
            if ((entry.cooldown ?: 0) > 0 && !metadata.cooldown.containsKey(k)) {
                metadata.cooldown[k] = effect("cooldown", entry, false)
            }
        }
    }

    fun cleanUp() {
        stickyBuffer.clear()
        cooldownBuffer.clear()
        delayBuffer.clear()
    }
}

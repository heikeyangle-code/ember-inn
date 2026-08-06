package com.emberinn.engine.macros

/**
 * 变量存储接口（局部=聊天变量，全局=设置变量，对齐官方 variables.js）。
 * get 缺失返回 null；set/delete 为副作用；has 判断存在。
 */
interface VariableStore {
    fun get(name: String): String?
    fun set(name: String, value: String)
    fun delete(name: String)
    fun has(name: String): Boolean
}

/** 空实现：所有读取为空、写入丢弃（默认环境）。 */
object EmptyVariableStore : VariableStore {
    override fun get(name: String): String? = null
    override fun set(name: String, value: String) {}
    override fun delete(name: String) {}
    override fun has(name: String): Boolean = false
}

/** 内存实现（测试/运行时用）。 */
class MemoryVariableStore : VariableStore {
    private val map = linkedMapOf<String, String>()
    override fun get(name: String): String? = map[name]
    override fun set(name: String, value: String) { map[name] = value }
    override fun delete(name: String) { map.remove(name) }
    override fun has(name: String): Boolean = map.containsKey(name)
}

/** 对齐官方 addLocalVariable/addGlobalVariable：数组 push / 字符串拼接 / 数字相加。 */
fun addVariable(store: VariableStore, name: String, value: String): String {
    val current = store.get(name) ?: "0"
    runCatching {
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(current)
        if (parsed is kotlinx.serialization.json.JsonArray) {
            val list = parsed.toMutableList()
            list += kotlinx.serialization.json.Json.parseToJsonElement(value)
            val newValue = kotlinx.serialization.json.JsonArray(list).toString()
            store.set(name, newValue)
            return newValue
        }
    }
    val increment = value.toDoubleOrNull()
    val currentNum = current.toDoubleOrNull()
    return if (increment == null || currentNum == null) {
        val stringValue = current + value
        store.set(name, stringValue)
        stringValue
    } else {
        val newValue = currentNum + increment
        val text = if (newValue % 1.0 == 0.0) newValue.toLong().toString() else newValue.toString()
        store.set(name, text)
        text
    }
}

package com.emberinn.engine.provider

/**
 * 官方 openai.js sortModelsBy / groupModelsByVendor 纯逻辑 1:1 移植（差分 model-sort-official.mjs）。
 * 打桩登记：官方 filter（electronhub endpoints / chutes affine / aimlapi type）为 DOM 加载函数内联行，App 层实现。
 */
object ModelSortEngine {

    /** 模型元数据投影（官方模型对象字段子集）。 */
    data class ModelMeta(
        val id: String = "",
        val name: String? = null,
        val contextLength: Long? = null,
        /** 官方 openrouter/nanogpt：pricing.prompt。 */
        val pricingPrompt: Double? = null,
        /** 官方 openrouter/nanogpt：pricing.completion。 */
        val pricingCompletion: Double? = null,
        /** 官方 chutes/electronhub：pricing.input。 */
        val pricingInput: Double? = null,
        /** 官方 chutes/electronhub：pricing.output。 */
        val pricingOutput: Double? = null,
        val tokens: Long? = null,
        val infoName: String? = null,
        val infoContextLength: Long? = null,
        val infoDeveloper: String? = null,
        val endpoints: List<String>? = null,
        val type: String? = null,
    )

    /** 官方 sortModelsBy（逐字语义；Array.prototype.sort 稳定，Kotlin sortedWith 稳定）。 */
    fun sortModelsBy(data: List<ModelMeta>, property: String, source: String): List<ModelMeta> {
        return when (source) {
            "openrouter" -> data.sortedWith { a, b ->
                when {
                    property == "context_length" -> ((b.contextLength ?: 0L) - (a.contextLength ?: 0L)).toInt()
                    property == "pricing.input" || property == "pricing.prompt" ->
                        ((a.pricingPrompt ?: 0.0) - (b.pricingPrompt ?: 0.0)).compareTo(0)
                    property == "pricing.output" || property == "pricing.completion" ->
                        ((a.pricingCompletion ?: 0.0) - (b.pricingCompletion ?: 0.0)).compareTo(0)
                    a.name != null && b.name != null -> a.name.compareTo(b.name)
                    else -> 0
                }
            }
            "chutes" -> data.sortedWith { a, b ->
                when {
                    property == "context_length" -> ((b.contextLength ?: 0L) - (a.contextLength ?: 0L)).toInt()
                    property == "pricing.input" || property == "pricing.prompt" ->
                        ((a.pricingInput ?: 0.0) - (b.pricingInput ?: 0.0)).compareTo(0)
                    property == "pricing.output" || property == "pricing.completion" ->
                        ((a.pricingOutput ?: 0.0) - (b.pricingOutput ?: 0.0)).compareTo(0)
                    a.id.isNotEmpty() && b.id.isNotEmpty() -> a.id.compareTo(b.id)
                    else -> 0
                }
            }
            "electronhub" -> data.sortedWith { a, b ->
                when {
                    property == "context_length" -> ((b.tokens ?: 0L) - (a.tokens ?: 0L)).toInt()
                    property == "pricing.input" || property == "pricing.prompt" ->
                        ((a.pricingInput ?: 0.0) - (b.pricingInput ?: 0.0)).compareTo(0)
                    property == "pricing.output" || property == "pricing.completion" ->
                        ((a.pricingOutput ?: 0.0) - (b.pricingOutput ?: 0.0)).compareTo(0)
                    a.name != null && b.name != null -> a.name.compareTo(b.name)
                    else -> 0
                }
            }
            "nanogpt" -> data.sortedWith { a, b ->
                when {
                    property == "context_length" -> ((b.contextLength ?: 0L) - (a.contextLength ?: 0L)).toInt()
                    property == "pricing.input" || property == "pricing.prompt" ->
                        ((a.pricingPrompt ?: 0.0) - (b.pricingPrompt ?: 0.0)).compareTo(0)
                    property == "pricing.output" || property == "pricing.completion" ->
                        ((a.pricingCompletion ?: 0.0) - (b.pricingCompletion ?: 0.0)).compareTo(0)
                    a.name != null && b.name != null -> a.name.compareTo(b.name)
                    else -> 0
                }
            }
            "aimlapi" -> data.sortedWith { a, b ->
                when {
                    property == "context_length" ->
                        ((b.infoContextLength ?: 0L) - (a.infoContextLength ?: 0L)).toInt()
                    a.infoName != null && b.infoName != null -> a.infoName.compareTo(b.infoName)
                    else -> 0
                }
            }
            else -> data
        }
    }

    /** 官方 groupModelsByVendor（逐字语义；Map 保持 vendor 插入序）。 */
    fun groupModelsByVendor(array: List<ModelMeta>, source: String): LinkedHashMap<String, List<ModelMeta>> {
        val result = LinkedHashMap<String, MutableList<ModelMeta>>()
        fun add(vendor: String, model: ModelMeta) {
            result.getOrPut(vendor) { mutableListOf() }.add(model)
        }
        when (source) {
            "openrouter" -> array.forEach { add(it.id.substringBefore('/'), it) }
            "electronhub" -> array.forEach {
                val vendor = (it.name ?: it.id).ifBlank { "Other" }
                    .substringBefore(':').trim().ifBlank { "Other" }
                add(vendor, it)
            }
            "nanogpt" -> array.forEach {
                val vendorPart = if ('/' in it.id) it.id.substringBefore('/') else it.id.substringBefore('-')
                val vendor = vendorPart.trim().lowercase().ifBlank { "Other" }
                add(vendor, it)
            }
            "chutes" -> array.forEach { add(it.id.substringBefore('/'), it) }
            "aimlapi" -> array.forEach { add(it.infoDeveloper ?: "Other", it) }
            else -> result[""] = array.toMutableList()
        }
        return LinkedHashMap(result.mapValues { (_, v) -> v.toList() })
    }

    /** 官方模型列表 filter（load*Models 内联行，逐字语义）。 */
    fun filterModelsBySource(data: List<ModelMeta>, source: String): List<ModelMeta> = when (source) {
        "electronhub" -> data.filter { m -> m.endpoints?.any { it == "/v1/chat/completions" } == true }
        "chutes" -> data.filter { m -> m.id.isNotEmpty() && !m.id.lowercase().contains("affine") }
        "aimlapi" -> data.filter { m -> m.type == "chat-completion" }
        else -> data
    }
}

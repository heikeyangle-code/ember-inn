package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 官方 chat-templates.js 纯逻辑 1:1 移植（差分 chat-template-official.mjs / ChatTemplateDiffTest）。
 * - deriveTemplatesFromChatTemplate：chat_template 哈希表 + 子串启发式 → 派生 context/instruct 模板名；
 * - bindModelTemplates：把当前 context/instruct 预设绑定到模型 id / chat template hash。
 * 打桩登记：toastr 提示（官方仅 UI 提示，不影响返回值/映射）。
 */
object ChatTemplateEngine {

    /** 官方 hash_derivations（逐字，SillyTavern 1.18.0 / 8172dcd）。 */
    private val hashDerivations = mapOf(
        "e10ca381b1ccc5cf9db52e371f3b6651576caee0a630b452e2816b2d404d4b65" to "Llama 3 Instruct",
        "5816fce10444e03c2e9ee1ef8a4a1ea61ae7e69e438613f3b17b69d0426223a4" to "Llama 3 Instruct",
        "73e87b1667d87ab7d7b579107f01151b29ce7f3ccdd1018fdc397e78be76219d" to "Llama 3 Instruct",
        "e16746b40344d6c5b5265988e0328a0bf7277be86f1c335156eae07e29c82826" to "Mistral V2 & V3",
        "26a59556925c987317ce5291811ba3b7f32ec4c647c400c6cc7e3a9993007ba7" to "Mistral V2 & V3",
        "e4676cb56dffea7782fd3e2b577cfaf1e123537e6ef49b3ec7caa6c095c62272" to "Mistral V3-Tekken",
        "3c4ad5fa60dd8c7ccdf82fa4225864c903e107728fcaf859fa6052cb80c92ee9" to "Mistral V7",
        "3934d199bfe5b6fab5cba1b5f8ee475e8d5738ac315f21cb09545b4e665cc005" to "Mistral V7",
        "ecd6ae513fe103f0eb62e8ab5bfa8d0fe45c1074fa398b089c93a7e70c15cfd6" to "Gemma 2",
        "87fa45af6cdc3d6a9e4dd34a0a6848eceaa73a35dcfe976bd2946a5822a38bf3" to "Gemma 2",
        "7de1c58e208eda46e9c7f86397df37ec49883aeece39fb961e0a6b24088dd3c4" to "Gemma 2",
        "3b54f5c219ae1caa5c0bb2cdc7c001863ca6807cf888e4240e8739fa7eb9e02e" to "Command R",
        "ac7498a36a719da630e99d48e6ebc4409de85a77556c2b6159eeb735bcbd11df" to "Tulu",
        "54d400beedcd17f464e10063e0577f6f798fa896266a912d8a366f8a2fcc0bca" to "DeepSeek-V2.5",
        "b6835114b7303ddd78919a82e4d9f7d8c26ed0d7dfc36beeb12d524f6144eab1" to "DeepSeek-V2.5",
        "854b703e44ca06bdb196cc471c728d15dbab61e744fe6cdce980086b61646ed1" to "GLM-4",
        "aab20feb9bc6881f941ea649356130ffbc4943b3c2577c0991e1fba90de5a0fc" to "Moonshot AI",
        "70da0d2348e40aaf8dad05f04a316835fd10547bd7e3392ce337e4c79ba91c01" to "OpenAI Harmony",
        "a4c9919cbbd4acdd51ccffe22da049264b1b73e59055fa58811a99efbd7c8146" to "OpenAI Harmony",
    )

    /** 官方 substr_derivations（逐字）。 */
    private val substrDerivations = listOf(
        "Moonshot AI" to listOf(
            "<|im_user|>user<|im_middle|>",
            "<|im_assistant|>assistant<|im_middle|>",
            "<|im_end|>",
        ),
        "OpenAI Harmony" to listOf(
            "<|start|>user<|message|>",
            "<|start|>assistant<|channel|>final<|message|>",
            "<|end|>",
        ),
        "ChatML" to listOf("<|im_start|>user", "<|im_start|>assistant", "<|im_end|>"),
    )

    data class DerivedTemplates(val context: String?, val instruct: String?)

    /** 官方 deriveTemplatesFromChatTemplate（chat_template 空 → not_found；哈希命中优先；子串启发式兜底）。 */
    fun deriveTemplatesFromChatTemplate(chatTemplate: String, hash: String): DerivedTemplates {
        if (chatTemplate.trim().isEmpty()) return DerivedTemplates(null, null)
        hashDerivations[hash]?.let { return DerivedTemplates(it, it) }
        for ((derivation, substr) in substrDerivations) {
            if (substr.all { chatTemplate.contains(it) }) return DerivedTemplates(derivation, derivation)
        }
        return DerivedTemplates(null, null)
    }

    data class BindResult(val powerUser: JsonObject, val bound: Boolean)

    /** 官方 bindModelTemplates：映射按模型 id / chat template hash 存；koboldcpp 前缀不写模型 id 键。 */
    fun bindModelTemplates(powerUser: JsonObject, onlineStatus: String): BindResult {
        if (onlineStatus == "no_connection") return BindResult(powerUser, false)
        val chatTemplateHash = stringOf(powerUser["chat_template_hash"])
        val mappings = (powerUser["model_templates_mappings"] as? JsonObject)
            ?.toMutableMap()
            ?: mutableMapOf()

        val existing = (mappings[onlineStatus] as? JsonObject)
            ?: (if (chatTemplateHash.isNotEmpty()) mappings[chatTemplateHash] as? JsonObject else null)
            ?: JsonObject(emptyMap())
        val bindMutable = existing.toMutableMap()

        val contextPreset = stringOf(powerUser["context"]?.jsonObject?.get("preset"))
        val instructEnabled = jsTruthy(powerUser["instruct"]?.jsonObject?.get("enabled"))
        val instructPreset = stringOf(powerUser["instruct"]?.jsonObject?.get("preset"))
        val contextDerived = jsTruthy(powerUser["context_derived"])
        val instructDerived = jsTruthy(powerUser["instruct_derived"])

        val bindingsMatch = contextPreset == stringOf(bindMutable["context"]) &&
            (!instructEnabled || instructPreset == stringOf(bindMutable["instruct"]))
        val bound = mutableListOf<String>()

        if (bindingsMatch) {
            mappings.remove(chatTemplateHash)
            mappings.remove(onlineStatus)
        } else {
            if (contextDerived && contextPreset != stringOf(bindMutable["context"])) {
                bound += "$contextPreset context preset"
                bindMutable["context"] = JsonPrimitive(contextPreset)
            }
            if (instructEnabled && instructDerived && instructPreset != stringOf(bindMutable["instruct"])) {
                bound += "$instructPreset instruct preset"
                bindMutable["instruct"] = JsonPrimitive(instructPreset)
            }
            if (bound.isEmpty()) return BindResult(powerUser, false)
            val updated = JsonObject(bindMutable)
            if (!onlineStatus.startsWith("koboldcpp/ggml-model-")) {
                mappings[onlineStatus] = updated
            }
            if (chatTemplateHash.isNotEmpty()) {
                mappings[chatTemplateHash] = updated
            }
        }

        val out = powerUser.toMutableMap()
        out["model_templates_mappings"] = JsonObject(mappings)
        return BindResult(JsonObject(out), true)
    }

    private fun stringOf(el: JsonElement?): String = (el as? JsonPrimitive)?.content ?: ""

    private fun jsTruthy(el: JsonElement?): Boolean = when (el) {
        null, is JsonNull -> false
        is JsonPrimitive -> when {
            el.isString -> el.content.isNotEmpty()
            el.content == "true" -> true
            el.content == "false" -> false
            else -> el.content.toDoubleOrNull()?.let { it != 0.0 } ?: true
        }
        else -> true
    }
}

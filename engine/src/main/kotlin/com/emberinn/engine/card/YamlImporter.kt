package com.emberinn.engine.card

import java.time.Instant
import org.yaml.snakeyaml.Yaml

/**
 * YAML 角色卡导入，对齐官方 importFromYaml：
 * name / context→description / greeting→first_mes / create_date / chat / 其余空字段 / talkativeness 0.5。
 */
object YamlImporter {

    fun import(
        data: ByteArray,
        now: String = Instant.now().toString(),
        chatNow: String = V2Normalizer.humanizedDateTime(),
    ): String {
        val text = String(data, Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val yaml = Yaml().load<Map<String, Any?>>(text) ?: error("YAML 为空")
        val name = CardSanitize.sanitizeName(yaml["name"]?.toString() ?: "")
        return V2Normalizer.buildV2FromLegacy(
            name = name,
            description = yaml["context"]?.toString() ?: "",
            firstMes = yaml["greeting"]?.toString() ?: "",
            createDate = now,
            chat = "$name - $chatNow",
            creatorComment = "",
            personality = "",
            scenario = "",
            talkativeness = 0.5,
            creator = "",
            includeRootCreator = true,
            tags = emptyList(),
        )
    }
}

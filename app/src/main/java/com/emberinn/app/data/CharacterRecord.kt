package com.emberinn.app.data

import kotlinx.serialization.Serializable

@Serializable
data class CharacterRecord(
    val id: String,
    val name: String,
    val description: String,
    val rawJson: String,
    val avatarPath: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val seedColor: Long? = null,
    /** CharX/V3 资产落盘路径（官方 assets：background/voice），App 层资源入库。 */
    val backgroundPath: String? = null,
    val voicePath: String? = null,
)

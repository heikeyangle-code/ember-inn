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
)

package dev.vector.proxy.model

import java.util.UUID

data class GameProfile(
    val uuid: UUID,
    val username: String,
    val properties: List<ProfileProperty> = emptyList(),
)

data class ProfileProperty(
    val name: String,
    val value: String,
    val signature: String? = null,
)

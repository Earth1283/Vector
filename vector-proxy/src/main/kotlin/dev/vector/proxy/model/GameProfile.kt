package dev.vector.proxy.model

import kotlinx.serialization.Serializable
import java.util.UUID

data class GameProfile(
    val uuid: UUID,
    val username: String,
    val properties: List<ProfileProperty> = emptyList(),
)

@Serializable
data class ProfileProperty(
    val name: String,
    val value: String,
    val signature: String? = null,
)

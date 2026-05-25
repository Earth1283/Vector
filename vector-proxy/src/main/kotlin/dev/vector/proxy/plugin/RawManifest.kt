package dev.vector.proxy.plugin

import dev.vector.api.plugin.PluginLanguage
import dev.vector.api.plugin.PluginManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawManifest(
    val id: String,
    val name: String = "",
    val version: String,
    @SerialName("api-version") val apiVersion: String = "1.0",
    val entrypoint: String,
    val language: String = "KOTLIN",
    @SerialName("hard-deps") val hardDeps: List<String> = emptyList(),
    @SerialName("soft-deps") val softDeps: List<String> = emptyList(),
) {
    fun toManifest() = PluginManifest(
        id = id,
        name = name.ifBlank { id },
        version = version,
        apiVersion = apiVersion,
        entrypoint = entrypoint,
        language = PluginLanguage.valueOf(language.uppercase()),
        hardDeps = hardDeps,
        softDeps = softDeps,
    )
}

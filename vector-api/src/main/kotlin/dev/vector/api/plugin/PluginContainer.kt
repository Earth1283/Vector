package dev.vector.api.plugin

import kotlinx.coroutines.CoroutineScope

data class PluginContainer(
    val manifest: PluginManifest,
    val instance: Any,
    val scope: CoroutineScope,
    val classLoader: ClassLoader,
)

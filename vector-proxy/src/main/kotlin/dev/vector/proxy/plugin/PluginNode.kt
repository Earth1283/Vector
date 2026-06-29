package dev.vector.proxy.plugin

import dev.vector.api.plugin.PluginManifest
import java.nio.file.Path

data class PluginNode(
    val manifest: PluginManifest,
    val jarPath: Path,
    val isVelocityCompat: Boolean = false,
)

fun computeWaves(nodes: List<PluginNode>): List<List<PluginNode>> {
    val remaining = nodes.toMutableList()
    val loaded = mutableSetOf<String>()
    val waves = mutableListOf<List<PluginNode>>()

    while (remaining.isNotEmpty()) {
        val wave = remaining.filter { node ->
            node.manifest.hardDeps.all { it in loaded }
        }
        check(wave.isNotEmpty()) {
            "Circular dependency or unresolvable hard-deps among: ${remaining.map { it.manifest.id }}"
        }
        waves.add(wave)
        wave.forEach { loaded.add(it.manifest.id); remaining.remove(it) }
    }
    return waves
}

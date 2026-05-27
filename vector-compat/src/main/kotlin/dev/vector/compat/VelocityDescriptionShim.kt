package dev.vector.compat

import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.plugin.meta.PluginDependency
import java.nio.file.Path
import java.util.Optional

class VelocityDescriptionShim(
    private val manifest: VelocityManifest,
    private val source: Path? = null,
) : PluginDescription {

    override fun getId(): String = manifest.id

    override fun getName(): Optional<String> = Optional.ofNullable(manifest.name)

    override fun getVersion(): Optional<String> = Optional.ofNullable(manifest.version)

    override fun getDescription(): Optional<String> = Optional.ofNullable(manifest.description)

    override fun getUrl(): Optional<String> = Optional.ofNullable(manifest.url)

    override fun getAuthors(): List<String> = manifest.authors

    override fun getDependencies(): Collection<PluginDependency> =
        manifest.dependencies.map { dep -> PluginDependency(dep.id, null, dep.optional) }

    override fun getSource(): Optional<Path> = Optional.ofNullable(source)
}
